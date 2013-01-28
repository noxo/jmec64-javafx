/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.c64.drive.DiskDriveHandler;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Extends the CPU6502 class for the specific handling of the C1541 floppy drive's CPU.<br>
 * <br>
 * For a ROM listing of the C1541 see <a href='http://www.htu.tugraz.at/~herwig/c64/1541rom.php'>http://www.htu.tugraz.at/~herwig/c64/1541rom.php</a>,
 * for a memory map see <a href='http://www.htu.tugraz.at/~herwig/c64/1541mem.php'>http://www.htu.tugraz.at/~herwig/c64/1541mem.php</a>.<br>
 *
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class C1541CPU6502 extends CPU6502 {

    /**
     * opcode for entering the patched DC routine
     */
    private static final int OPCODE_DCROUTINE = 0x02;
    /**
     * opcode for skipping the ROM test
     */
    private static final int OPCODE_SKIPROMTEST = 0x12;
    /**
     * opcode for entering the patched wait loop
     */
    private static final int OPCODE_WAITLOOP = 0x22;
    /**
     * opcode for entering the patched print file name routine
     */
    private static final int OPCODE_PRINTFILENAME = 0x32;
    /**
     * opcode for entering the patched file write, part 1
     */
    private static final int OPCODE_FILEWRITE1 = 0x42;
    /**
     * opcode for entering the patched file write, part 2
     */
    private static final int OPCODE_FILEWRITE2 = 0x52;
    /**
     * opcode for entering the patched file write, part 3
     */
    private static final int OPCODE_FILEWRITE3 = 0x62;
    /**
     * RAM size
     */
    private final static int RAM_SIZE = 0x0800;
    /**
     * ROM offset, the ROM starts at memory $2000
     */
    private final static int ROM_OFFSET = RAM_SIZE;
    /**
     * Floppy ROM size
     */
    private final static int FLOPPY_ROM_SIZE = 0x4000;
    /**
     * Floppy ROM start address
     */
    private final static int FLOPPY_ROM_ADDRESS = 0xc000;
    /**
     * Memory offset for floppy ROM
     */
    private final static int FLOPPY_ROM_OFFSET = ROM_OFFSET - FLOPPY_ROM_ADDRESS;
    /**
     * buffer memory address for job 0
     */
    private final static int BUFFER0_MEMORY = 0x0300;
    /**
     * track and sector for buffer 0
     */
    private final static int BUFFER0_TRACKSECTOR = 0x0006;
    // floppy jobcodes
    /**
     * Read sector
     */
    private final static int JOB_READ_SECTOR = 0x80;
    /**
     * Write sector
     */
    private final static int JOB_WRITE_SECTOR = 0x90;
    /**
     * Verify sector
     */
    private final static int JOB_VERIFY_SECTOR = 0xa0;
    /**
     * Search sector
     */
    private final static int JOB_SEARCH_SECTOR = 0xb0;
    /**
     * Bump head
     */
    private final static int JOB_BUMP = 0xc0;
    /**
     * Execute buffer program
     */
    private final static int JOB_EXECUTE = 0xd0;
    /**
     * Execute buffer program after drive startup
     */
    private final static int JOB_EXECUTE_WITH_STARTUP = 0xe0;
    // status messages
    /**
     * OK
     */
    private final static int STATUS_OK = 0x01;
    /**
     * block not found
     */
    private final static int STATUS_BLOCK_NOT_FOUND = 0x04;
    /**
     * disk is write-protected
     */
    private final static int STATUS_WRITE_PROTECT = 0x08;
    /**
     * no disk in drive
     */
    private final static int STATUS_NO_DISK = 0x0f;
    /**
     * Extended C1541 instructions
     */
    private final static int XDI = -1;
    /**
     * the C1541 instance we belong to
     */
    private final C1541 c1541;

    /**
     * Create a new 6502 CPU for the disk drive core
     *
     * @param   c1541   the c1541 instance we belong to
     */
    public C1541CPU6502(final C1541 c1541) {
        super(c1541, RAM_SIZE + FLOPPY_ROM_SIZE);
        this.ramSize = RAM_SIZE;
        this.c1541 = c1541;
        setLogger(this.c1541.getLogger());
        installROMs();
        setPC(getStartAddress());
    }

    /**
     * We return the adjusted value of the PC which might also point to the ROM area
     *
     * @return  adjusted program counter
     */
    protected final int getPC() {
        return this.pc >= FLOPPY_ROM_ADDRESS ? this.pc + FLOPPY_ROM_OFFSET : this.pc;
    }

    protected final byte readByte(final int address) {
        // read from correct RAM/ROM area or IO chips
        switch (address & 0xf000) {
            // read from floppy RAM?
            case 0x0000:
                return this.memory[address & 0x07ff];

            // read from IO chips?
            case 0x1000:
                switch (address & 0xff00) {
                    case 0x1800:
                        return (byte)this.c1541.getVIA(0).readRegister(address & 0xf);

                    case 0x1c00:
                        return (byte)this.c1541.getVIA(1).readRegister(address & 0xf);

                    default:
                        return 0;
                }

            // read from floppy ROM?
            case 0xc000:
            case 0xd000:
            case 0xe000:
            case 0xf000:
                return this.memory[FLOPPY_ROM_OFFSET + address];

            // return 0
            default:
                return 0;
        }
    }

    protected final void writeByte(final int address, final byte data) {
        // write to RAM or IO chips
        switch (address & 0xf000) {
            // write to floppy RAM?
            case 0x0000:
                this.memory[address & 0x07ff] = data;
                break;

            // write to IO chips?
            case 0x1000:
                switch (address & 0xff00) {
                    case 0x1800:
                        this.c1541.getVIA(0).writeRegister(address & 0xf, data & 0xff);
                        break;

                    case 0x1c00:
                        this.c1541.getVIA(1).writeRegister(address & 0xf, data & 0xff);
                        break;

                    default:
                        break;
                }
                break;

            // do nothing
            default:
                break;
        }
    }

    /**
     * Reset the CPU and clear the RAM
     */
    public void reset() {
        super.reset();

        // clear RAM
        for (int i = 0; i < RAM_SIZE; ++i) {
            this.memory[i] = 0;
        }

        // set program counter to default start address
        setPC(getStartAddress());
    }

    /**
     * Read floppy-ROM
     */
    protected final void installROMs() {
        loadROM("/roms/floppy.c64", FLOPPY_ROM_ADDRESS + FLOPPY_ROM_OFFSET, FLOPPY_ROM_SIZE);
    }

    // we use some special instructions to emulate the disk controller
    /**
     * Write extended instructions to some ROM addresses
     */
    protected void patchROMs() {
        // add some extended instructions to the CPUs instruction set
        // - add an extended instruction for disk controller job routine
        addInstruction(new CPU6502Instruction("XI0", OPCODE_DCROUTINE, XDI, CPU6502Instruction.IMPLIED, 0));
        // - add an extended instruction to skip ROM test (as we patched the ROM)
        addInstruction(new CPU6502Instruction("XI1", OPCODE_SKIPROMTEST,XDI, CPU6502Instruction.IMPLIED, 0));
        // - add an extended instruction for stopping the device when entering the wait-loop
        addInstruction(new CPU6502Instruction("XI2", OPCODE_WAITLOOP,XDI, CPU6502Instruction.IMPLIED, 0));
        // - add an extended instruction for printing the file name when opening a file
        addInstruction(new CPU6502Instruction("XI3", OPCODE_PRINTFILENAME,XDI, CPU6502Instruction.IMMEDIATE, 0));
        // - add extended instructions for correcting problems with file writes
        addInstruction(new CPU6502Instruction("XI4", OPCODE_FILEWRITE1,XDI, CPU6502Instruction.IMMEDIATE, 0));
        addInstruction(new CPU6502Instruction("XI5", OPCODE_FILEWRITE2,XDI, CPU6502Instruction.IMMEDIATE, 0));
        addInstruction(new CPU6502Instruction("XI6", OPCODE_FILEWRITE3,XDI, CPU6502Instruction.IMMEDIATE, 0));

        // write special disk controller job routine instruction to $f2b0
        this.memory[FLOPPY_ROM_OFFSET + 0xf2b0] = OPCODE_DCROUTINE;
        // skip ROM test at $eac9
        this.memory[FLOPPY_ROM_OFFSET + 0xeac9] = OPCODE_SKIPROMTEST;
        // when entering the wait-loop we stop the device
        this.memory[FLOPPY_ROM_OFFSET + 0xebff] = OPCODE_WAITLOOP;
        // print file name when opening a file
        this.memory[FLOPPY_ROM_OFFSET + 0xd7b4] = OPCODE_PRINTFILENAME;
        // correct file write routine
        this.memory[FLOPPY_ROM_OFFSET + 0xf58c] = OPCODE_FILEWRITE1;
        this.memory[FLOPPY_ROM_OFFSET + 0xf5a3] = OPCODE_FILEWRITE2;
        this.memory[FLOPPY_ROM_OFFSET + 0xfcb1] = OPCODE_FILEWRITE3;
        this.memory[FLOPPY_ROM_OFFSET + 0xfcdc] = OPCODE_FILEWRITE3;
    }

    /**
     * We set the overflow flag to true when the Disk Controller has a byte available
     */
    protected void emulateInstruction(final CPU6502Instruction instruction) {
        this.overflowFlag |= ((VIA6522_DC) this.c1541.getVIA(1)).isByteReady();
        super.emulateInstruction(instruction);
    }

    /**
     * We have some special instructions patched in the Kernal ROM that we interpret here.
     */
    protected void emulateExtendedInstruction(final CPU6502Instruction instruction) {
        switch (instruction.opCode) {
            // when not fully emulating the disk controller we handle the disk controller's IRQ routine separately
            case OPCODE_DCROUTINE:
                if (!this.c1541.isEmulateDiskController()) {
                    emulateDiskControllerIRQRoutine();
                } else {
                    // execute command normally
                    emulateInstruction(getInstruction(0xba));
                }
                break;

            // we skip the ROM test
            case OPCODE_SKIPROMTEST:
                setPC(0xeaea);
                break;

            // when entering the wait-loop, we stop the device
            case OPCODE_WAITLOOP:
                // emulate the CLI operation at $ebff
                emulateInstruction(getInstruction(0x58));
                // stop the drive
                this.c1541.stop();
                break;

            // print the file name when opening a file
            case OPCODE_PRINTFILENAME: {
                // emulate the LDA operation at $d7b4
                emulateInstruction(getInstruction(0xa5));

                // determine and print the file name
                final StringBuffer filename = new StringBuffer();

                for (int i = 0x200; i < 0x210; ++i) {
                    if (this.memory[i] > 0) {
                        filename.append((char) this.memory[i]);
                    } else {
                        break;
                    }
                }
                getLogger().info("Opening file '" + filename + "'");
                break;
            }

            // we skip some operations when writing to the disk
            case OPCODE_FILEWRITE1:
                ((VIA6522_DC) this.c1541.getVIA(1)).proceedToNextSync();
                setPC(0xf594);
                break;
            case OPCODE_FILEWRITE2:
                ((VIA6522_DC) this.c1541.getVIA(1)).writeSync();
                setPC(0xf5b1);
                break;
            case OPCODE_FILEWRITE3:
                ((VIA6522_DC) this.c1541.getVIA(1)).writeSync();
                setPC(this.pc + 11);
                break;

            default:
                super.emulateExtendedInstruction(instruction);
        }
    }

    /**
     * Interpret job commands at $00-$04 (buffer for $05 not in RAM, so we ignore this)
     */
    protected void emulateDiskControllerIRQRoutine() {
        // clear IRQ by reading $1c04
        readByte(0x1c04);

        // interpret job
        for (int m = 0x00; m < 0x05; ++m) {
            // get job parameters
            final int cmd = readByte(m) & 0xf0;
            final int track = readByte(BUFFER0_TRACKSECTOR + m * 2) & 0xff;
            final int sector = readByte(BUFFER0_TRACKSECTOR + m * 2 + 1) & 0xff;
            final int bufferAdr = BUFFER0_MEMORY + 0x100 * m;

            // mark the drive as active if we get a command
            if (cmd > 0) {
                this.c1541.markActive();
                if (C1541.DEBUG) {
                    System.out.println("Buffer=" + m + ", job=$" + Integer.toHexString(cmd) + " track=" + track + ", sector=" + sector);
                }
            }

            // interpret the command
            writeByte(0x3f, (byte) m);
            switch (cmd) {
                case JOB_READ_SECTOR: {
                    // read block and write block data to buffer
                    this.c1541.getDriveHandler().gotoBlock(track, sector);

                    final byte[] bytes = this.c1541.getDriveHandler().readBlock();

                    for (int i = 0; i < bytes.length; ++i) {
                        writeByte(bufferAdr + i, bytes[i]);
                    }

                    // store last read sector
                    writeByte(0x4c, (byte) sector);
                    // status was OK
                    writeByte(m, (byte) STATUS_OK);
                    break;
                }
                case JOB_WRITE_SECTOR: {
                    // copy block data form buffer to disk
                    this.c1541.getDriveHandler().gotoBlock(track, sector);

                    final byte[] bytes = new byte[DiskDriveHandler.BYTES_PER_SECTOR];

                    for (int i = 0; i < bytes.length; ++i) {
                        bytes[i] = readByte(bufferAdr + i);
                    }
                    this.c1541.getDriveHandler().writeBlock(bytes);

                    // store last read sector
                    writeByte(0x4c, (byte) sector);
                    // status was OK
                    writeByte(m, (byte) STATUS_OK);
                    break;
                }
                case JOB_VERIFY_SECTOR:
                case JOB_BUMP:
                    // we do nothing and report that everything was fine
                    writeByte(m, (byte) STATUS_OK);
                    break;
                case JOB_SEARCH_SECTOR:
                    // we pretend we had moved to the given track & sector
                    writeByte(0x22, (byte) track);
                    writeByte(0x43, (byte) DiskDriveHandler.SECTORS_PER_TRACK[track - 1]);
                    writeByte(0x4d, (byte) sector);
                    // status was OK
                    writeByte(m, (byte) STATUS_OK);
                    break;
                case JOB_EXECUTE:
                case JOB_EXECUTE_WITH_STARTUP:
                    throw new RuntimeException("Executing jobs not yet implemented!");
                //break;
                default:
                    ;
            }
        }

        // return to main IRQ routine
        setPC(0xfac6);
    }

    public void serialize(final DataOutputStream out) throws IOException {
        super.serialize(out);
        out.writeInt(this.irqs.size());
        for (int i = 0; i < this.irqs.size(); ++i) {
            out.writeUTF(this.irqs.elementAt(i).getClass().getName());
        }
        out.writeInt(this.nmis.size());
        for (int i = 0; i < this.nmis.size(); ++i) {
            out.writeUTF(this.nmis.elementAt(i).getClass().getName());
        }
    }

    public void deserialize(final DataInputStream in) throws IOException {
        super.deserialize(in);

        int size = in.readInt();

        this.irqs.removeAllElements();
        for (int i = 0; i < size; ++i) {
            final String className = in.readUTF();

            if (VIA6522_BC.class.getName().equals(className)) {
                this.irqs.addElement(this.c1541.getVIA(0));
            } else if (VIA6522_DC.class.getName().equals(className)) {
                this.irqs.addElement(this.c1541.getVIA(1));
            } else {
                throw new IllegalStateException("Unsupported interrupt type for deserialization: " + className + "!");
            }
        }

        size = in.readInt();
        this.nmis.removeAllElements();
        for (int i = 0; i < size; ++i) {
            final String className = in.readUTF();

            if (VIA6522_BC.class.getName().equals(className)) {
                this.irqs.addElement(this.c1541.getVIA(0));
            } else if (VIA6522_DC.class.getName().equals(className)) {
                this.irqs.addElement(this.c1541.getVIA(1));
            } else {
                throw new IllegalStateException("Unsupported interrupt type for deserialization: " + className + "!");
            }
        }
    }
}
