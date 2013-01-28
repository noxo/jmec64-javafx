/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.c64.drive.DiskDriveHandler;
import de.joergjahnke.common.util.Observer;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Extension of the VIA6522 class for the Disk Controller.<br>
 * <br>
 * For the group-coded recording used by the disk controller, see <a href='http://en.wikipedia.org/wiki/Group_Coded_Recording'>http://en.wikipedia.org/wiki/Group_Coded_Recording</a>.<br>
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class VIA6522_DC extends VIA6522 implements Observer {
    // do we print debug info?
    private final static boolean DEBUG = false;
    // functions of the pins of port B
    /**
     * stepper motor
     */
    private final static int PB_STEPPER_MOTOR = (1 << 0) | (1 << 1);
    /**
     * drive motor (1=on)
     */
    private final static int PB_DRIVE_MOTOR = 1 << 2;
    /**
     * drive LED state (1=on)
     */
    public final static int PB_LED = 1 << 3;
    /**
     * write protection state (1=off)
     */
    public final static int PB_WRITE_PROTECTED = 1 << 4;
    /**
     * sync signal when reading from disk
     */
    public final static int PB_SYNC = 1 << 7;
    /**
     * minimum half-track value, the minimum track number is 1, double this is the minimum half-track value
     */
    private final static int MIN_HALFTRACK = 2;
    /**
     * maximum half-track value, the maximum track number is 35, double this is the maximum half-track value
     */
    private final static int MAX_HALFTRACK = 70;

    // calculation of GCR sector
    /**
     * sync is one byte
     */
    private final static int SYNC_SIZE = 1;
    /**
     * 10 bytes header data
     */
    private final static int HEADER_SIZE = 10;
    /**
     * gap after the header is 9 bytes
     */
    private final static int GAP1_SIZE = 9;
    /**
     * 325 bytes of data
     */
    private final static int DATA_SIZE = 325;
    /**
     * gap after data is 8 bytes
     */
    private final static int GAP2_SIZE = 8;
    /**
     * total size of GCR sector is sync + header + gap1 + sync + data + gap2
     */
    private final static int GCR_SECTOR_SIZE = SYNC_SIZE + HEADER_SIZE + GAP1_SIZE + SYNC_SIZE + DATA_SIZE + GAP2_SIZE;
    // some special GCR bytes
    /**
     * GCR byte for sync
     */
    private final static int SYNC = 0x1ff;
    /**
     * GCR byte for gap
     */
    private final static int GAP = 0x55;
    // some special marks on the disk
    /**
     * start of a header
     */
    private final static int HEADER_START = 0x08;
    /**
     * start of a block
     */
    private final static int BLOCK_START = 0x07;
    /**
     * filled into gaps
     */
    private final static int GAP_DATA = 0x0f;
    /**
     * converts 4 bits of data into 5 bits on the disk
     */
    private final static int GCR_TABLE[] = {
        0x0a, 0x0b, 0x12, 0x13, 0x0e, 0x0f, 0x16, 0x17,
        0x09, 0x19, 0x1a, 0x1b, 0x0d, 0x1d, 0x1e, 0x15
    };
    /**
     * converts 5 bits from the disk into 4 bits of data
     */
    private final static int GCR_INV_TABLE[] = {
        -1, -1, -1, -1, -1, -1, -1, -1,
        -1, 0x08, 0x00, 0x01, -1, 0x0c, 0x04, 0x05,
        -1, -1, 0x02, 0x03, -1, 0x0f, 0x06, 0x07,
        -1, 0x09, 0x0a, 0x0b, -1, 0x0d, 0x0e, -1
    };
    /**
     * number of CPU cycles we wait until the next byte is read from the disk
     */
    protected final static int INTERVAL_MOVE_TO_NEXT_BYTE = 30;
    /**
     * number of CPU cycles we wait after moving out of a sector
     */
    private final static int INTERVAL_MOVE_TO_NEXT_SYNC = 1000;
    /**
     * number of CPU cycles we wait after moving to a new track
     */
    private final static int INTERVAL_MOVE_TO_NEXT_TRACK = 0;
    /**
     * this flag is set to true when the disk was changed and will reset to false after the first read access to PRB
     */
    private boolean wasDiskChanged = false;
    /**
     * current half-track number (2-70) 
     */
    private int currentHalfTrack = MIN_HALFTRACK;
    /**
     * current track
     */
    private int track = 1;
    /**
     * current sector
     */
    private int sector = 0;
    /**
     * GCR sector data
     */
    private int[] gcrSector = null;
    /**
     * current position in the GCR sector
     */
    private int gcrPos = -1;
    /**
     * shift register for writing 4 bytes as 5 GCR bytes
     */
    private long shiftGCR = 0;
    /**
     * counter for bytes written to GCR shift register
     */
    private int bytesGCR = 0;
    /**
     * was data modified on the current sector
     */
    private boolean wasSectorModified = false;
    /**
     * indicates when we have to read the next byte from the sector
     */
    protected long nextMove = 0;
    /**
     * we have another byte ready?
     */
    private boolean isByteReady = false;
    /**
     * we are in write mode?
     */
    private boolean isWriteMode = false;

    /**
     * Creates a new instance of VIA6522_BC
     *
     * @param   c1541   the C1541 we are attached to
     */
    public VIA6522_DC(final C1541 c1541) {
        super(c1541);
        c1541.addObserver(this);
    }

    public void synchronizeWithDevice(final EmulatedDevice device) {
        super.synchronizeWithDevice(device);
        this.nextMove = device.getCPU().getCycles();
    }

    /**
     * Check whether another byte is ready to be read.
     * This method also resets the state to false.
     * 
     * @return  true if the next byte can be read
     */
    public final boolean isByteReady() {
        final boolean result = this.isByteReady | (isMotorOn() && !this.c1541.isEmulateDiskRotation());

        this.isByteReady = false;
        return result;
    }

    /**
     * Check whether the drive LED is switched on
     *
     * @return  true if the LED is on
     */
    public final boolean isLEDOn() {
        return (this.registers[PRB] & PB_LED) != 0;
    }

    /**
     * Check whether the drive motor is switched on
     *
     * @return  true if the motor is on
     */
    public final boolean isMotorOn() {
        return (this.registers[PRB] & PB_DRIVE_MOTOR) != 0;
    }

    /**
     * Check whether we reached a new track.
     * If that is the case then we read the first sector of the new track.
     */
    private void halfTrackChanged() {
        // a new track was reached?
        if (this.currentHalfTrack % 2 == 0) {
            // determine new track and sector
            this.track = this.currentHalfTrack / 2;
            this.sector = 0;
            // read this sector and convert it to GCR format
            readSector();
            // it takes some time until the track is reached
            this.nextMove += INTERVAL_MOVE_TO_NEXT_TRACK;
        }
        // we mark the drive as active
        this.c1541.markActive();
    }

    /**
     * Read/write the current byte and then rotate the disk to the next byte.
     * This method will proceed to the next sector of the track after the last byte
     * of the current sector. After the last sector it will start with sector 0 again.
     */
    protected void rotateDisk() {
        // we are in write mode?
        if (this.isWriteMode && this.gcrPos >= 0) {
            // the DDR indicates that we may write data?
            if (this.registers[DDRA] == 0xff) {
                // we only mark a sector as modified when there was an actual change
                if (this.registers[PRA] != this.gcrSector[this.gcrPos]) {
                    // write data from PRA to disk
                    this.gcrSector[this.gcrPos] = this.registers[PRA];
                    this.wasSectorModified = true;
                }
            }
        }

        // proceed to next byte
        ++this.gcrPos;
        // this was the last byte of the sector?
        if (this.gcrPos >= GCR_SECTOR_SIZE) {
            // write current sector if it was modified
            if (this.wasSectorModified) {
                writeSector();
            }
            // then read next sector
            ++this.sector;
            if (this.sector >= DiskDriveHandler.SECTORS_PER_TRACK[this.track - 1]) {
                this.sector = 0;
            }
            readSector();
            // we wait a bit longer until we rotate to the sync signal
            this.nextMove += INTERVAL_MOVE_TO_NEXT_SYNC;
        }

        // we are in read mode?
        if (!this.isWriteMode && this.gcrPos >= 0) {
            // we read the next byte into PRA
            this.registers[PRA] = this.gcrSector[this.gcrPos] & 0xff;
        }

        // we have another byte ready if we are not at a sync byte or when in write mode
        this.isByteReady = this.isWriteMode | !isSync();
    }

    /**
     * Rotate the disk behind the next sync mark
     */
    protected void proceedToNextSync() {
        while (!isSync()) {
            rotateDisk();
        }
    }

    /**
     * Write a sync mark
     */
    protected void writeSync() {
        this.gcrSector[this.gcrPos] = SYNC;
        if (!this.c1541.isEmulateDiskRotation()) {
            rotateDisk();
        }
    }

    /**
     * Read a sector and convert it to GCR format.
     */
    private void readSector() {
        // - reserve data for the GCR sector
        this.gcrSector = new int[GCR_SECTOR_SIZE];
        this.gcrPos = 0;
        this.shiftGCR = 0;
        this.bytesGCR = 0;

        // - GCR sector starts with a sync byte
        this.gcrSector[this.gcrPos++] = SYNC;
        // - write the header
        final int diskID1 = this.c1541.getDriveHandler().getDiskID()[ 0];
        final int diskID2 = this.c1541.getDriveHandler().getDiskID()[ 1];

        writeGCRByte(HEADER_START);
        writeGCRByte(this.sector ^ this.track ^ diskID1 ^ diskID2);
        writeGCRByte(this.sector);
        writeGCRByte(this.track);
        writeGCRByte(diskID2);
        writeGCRByte(diskID1);
        writeGCRByte(GAP_DATA);
        writeGCRByte(GAP_DATA);
        // - write first gap
        for (int i = 0; i < GAP1_SIZE; ++i) {
            this.gcrSector[this.gcrPos++] = GAP;
        }
        // - second sync byte
        this.gcrSector[this.gcrPos++] = SYNC;

        // - block data
        this.c1541.getDriveHandler().gotoBlock(this.track, this.sector);

        if (DEBUG) {
            System.out.println("Reading block " + this.track + "," + this.sector);
        }

        final byte[] bytes = this.c1541.getDriveHandler().readBlock();

        if (bytes.length != 256) {
            throw new RuntimeException("Illegal block length of " + bytes.length + " bytes when converting to GCR block!");
        }

        int checksum = 0;

        writeGCRByte(BLOCK_START);
        for (int i = 0; i < bytes.length; ++i) {
            final int data = bytes[i] & 0xff;

            writeGCRByte(data);
            checksum ^= data;
        }
        writeGCRByte(checksum);
        writeGCRByte(0);
        writeGCRByte(0);

        // - write second gap
        for (int i = 0; i < GAP2_SIZE; ++i) {
            this.gcrSector[this.gcrPos++] = GAP;
        }

        if (this.bytesGCR != 0) {
            throw new RuntimeException("Wrong conversion of GCR bytes!");
        }
        if (this.gcrPos != GCR_SECTOR_SIZE) {
            throw new RuntimeException("GCR sector too short!");
        }

        // initialize sector
        this.wasSectorModified = false;
        this.gcrPos = -1;
        this.registers[PRA] = 0;
    }

    /**
     * Writes a byte of data to the GCR sector.
     * With every 4 bytes the data gets actually converted to GCR format and written to the GCR sector.
     * 
     * @param   data    byte to be written
     */
    private void writeGCRByte(final int data) {
        // add new data to the shoft register
        this.shiftGCR <<= 5;
        this.shiftGCR |= GCR_TABLE[data >> 4];
        this.shiftGCR <<= 5;
        this.shiftGCR |= GCR_TABLE[data & 0x0f];

        // we have collected 4 bytes of data i.e. 5 full bytes of GCR data?
        if (++this.bytesGCR >= 4) {
            // then write to GCR sector
            for (int shift = 32; shift >= 0; shift -= 8) {
                this.gcrSector[this.gcrPos++] = (int) ((this.shiftGCR >> shift) & 0xff);
            }

            // reset shift register and counter
            this.shiftGCR = 0;
            this.bytesGCR = 0;
        }
    }

    /**
     * Convert sector from GCR format and write it to the disk.
     */
    private void writeSector() {
        // reserve data for the sector bytes
        final byte[] bytes = new byte[DiskDriveHandler.BYTES_PER_SECTOR];
        this.shiftGCR = 0;
        this.bytesGCR = 0;

        // we only process the data, not the header, not the block start sign
        this.gcrPos = SYNC_SIZE + HEADER_SIZE + GAP1_SIZE + SYNC_SIZE;

        final int data = readGCRByte();

        if (data != BLOCK_START) {
            throw new RuntimeException("Writing illegal block start byte to disk!");
        }

        for (int i = 0; i < bytes.length; ++i) {
            bytes[i] = (byte) readGCRByte();
        }

        // write data to sector
        this.c1541.getDriveHandler().writeBlock(bytes);

        // we mark the drive as active
        this.c1541.markActive();
    }

    /**
     * Reads a byte of data from the GCR sector.
     * 
     * @return  byte from the GCR sector
     */
    private int readGCRByte() {
        // we have no more data inside the shift register?
        if (this.bytesGCR <= 0) {
            // reset shift register and counter
            this.shiftGCR = 0;
            this.bytesGCR = 4;

            // then read from GCR sector
            for (int shift = 32; shift >= 0; shift -= 8) {
                this.shiftGCR |= ((long) (this.gcrSector[this.gcrPos++] & 0xff) << shift);
            }
        }

        // read 10 bits of data from the shift register
        final int data = (int) ((this.shiftGCR >> (--this.bytesGCR * 10)) & 0x3ff);
        final int high = GCR_INV_TABLE[data >> 5],  low = GCR_INV_TABLE[data & 0x1f];

        if (high < 0 || low < 0) {
            throw new RuntimeException("Error when converting GCR bytes!");
        }

        return (high << 4) | low;
    }

    /**
     * Check whether a sync signal is found at the current position of the disk.
     * 
     * @return  true if a sync signal was found
     */
    private boolean isSync() {
        return this.gcrPos >= 0 && this.gcrSector[this.gcrPos] == SYNC;
    }

    /**
     * Check whether write protection is currently activated
     * 
     * @return  0 if write protection is active or the disk was changed, otherwise PB_WRITE_PROTECTED
     */
    private boolean isWriteProtect() {
        return this.wasDiskChanged;
    }

    public void reset() {
        super.reset();
        this.bytesGCR = 0;
        this.shiftGCR = 0;
        this.wasDiskChanged = false;
        this.currentHalfTrack = MIN_HALFTRACK;
        this.track = 1;
        this.sector = 0;
        this.gcrPos = -1;
    }

    /**
     * set write-protect bit when a new disk was inserted
     */
    public int readRegister(final int register) {
        switch (register) {
            case PRB: {
                final int result = (super.readRegister(register) & (0xff - PB_WRITE_PROTECTED - PB_SYNC)) | (isWriteProtect() ? 0 : PB_WRITE_PROTECTED) | (isSync() ? 0 : PB_SYNC);

                // reset flag for disk change
                this.wasDiskChanged = false;
                // we have to move the disk now if not emulating disk rotation
                if (!isSync() && !this.c1541.isEmulateDiskRotation() && this.gcrSector != null) {
                    rotateDisk();
                }

                return result;
            }

            case PRA:
            case PRA2: {
                final int result = this.registers[PRA];

                // we have to move the disk now if not emulating disk rotation
                if (!this.c1541.isEmulateDiskRotation() && this.gcrSector != null) {
                    rotateDisk();
                }

                return result;
            }

            default:
                return super.readRegister(register);
        }
    }

    /**
     * Control stepper motor on writes to PRB
     */
    public void writeRegister(final int register, final int data) {
        switch (register) {
            case PRB:
                // check for stepper motor movement
                final int oldMotor = this.registers[PRB] & PB_STEPPER_MOTOR;

                // head moves out?
                if (oldMotor == ((data + 1) & PB_STEPPER_MOTOR)) {
                    currentHalfTrack = Math.max(MIN_HALFTRACK, currentHalfTrack - 1);
                    halfTrackChanged();
                // head moves in?
                } else if (oldMotor == ((data - 1) & PB_STEPPER_MOTOR)) {
                    currentHalfTrack = Math.min(MAX_HALFTRACK, currentHalfTrack + 1);
                    halfTrackChanged();
                }
                // write new value to register
                super.writeRegister(register, data);
                break;

            case PRA:
                super.writeRegister(register, data);
                // we have to move the disk now if not emulating disk rotation
                if (!this.c1541.isEmulateDiskRotation() && this.gcrSector != null) {
                    rotateDisk();
                }
                break;

            case PCR:
                this.isWriteMode = (data & 0x20) == 0;
                super.writeRegister(register, data);
                break;

            // otherwise do nothing
            default:
                super.writeRegister(register, data);
        }
    }

    public void serialize(final DataOutputStream out) throws IOException {
        super.serialize(out);
        out.writeBoolean(this.wasDiskChanged);
        out.writeInt(this.currentHalfTrack);
        out.writeInt(this.track);
        out.writeInt(this.sector);

        final int size = this.gcrSector == null ? 0 : this.gcrSector.length;

        out.writeInt(size);
        for (int i = 0; i < size; ++i) {
            out.writeInt(this.gcrSector[i]);
        }
        out.writeInt(this.gcrPos);
        out.writeLong(this.shiftGCR);
        out.writeInt(this.bytesGCR);
        out.writeBoolean(this.wasSectorModified);
        out.writeLong(this.nextMove);
        out.writeBoolean(this.isByteReady);
        out.writeBoolean(this.isWriteMode);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        super.deserialize(in);
        this.wasDiskChanged = in.readBoolean();
        this.currentHalfTrack = in.readInt();
        this.track = in.readInt();
        this.sector = in.readInt();

        final int size = in.readInt();

        if (size > 0) {
            this.gcrSector = new int[size];
            for (int i = 0; i < size; ++i) {
                this.gcrSector[i] = in.readInt();
            }
        } else {
            this.gcrSector = null;
        }
        this.gcrPos = in.readInt();
        this.shiftGCR = in.readLong();
        this.bytesGCR = in.readInt();
        this.wasSectorModified = in.readBoolean();
        this.nextMove = in.readLong();
        this.isByteReady = in.readBoolean();
        this.isWriteMode = in.readBoolean();
    }

    // implementation of the Observer interface
    /**
     * modify sync flag if a new disk was inserted
     */
    public void update(final Object observed, final Object arg) {
        // a notification from the C1541?
        if (observed == this.c1541) {
            // a new disk was inserted?
            if (arg == C1541.DISK_MOUNTED) {
                // we note this change
                this.wasDiskChanged = true;
            }
        }
    }
}
