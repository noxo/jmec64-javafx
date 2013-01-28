/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.drive;

import de.joergjahnke.c64.core.C1541;
import de.joergjahnke.c64.core.C64FileEntry;

/**
 * Abstract class for reading/writing the information from/to a disk image
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public abstract class DiskDriveHandler extends DriveHandler {

    /**
     * bytes per sector on a C64 disk
     */
    public final static int BYTES_PER_SECTOR = 256;
    /**
     * first track number
     */
    protected final static int FIRST_TRACK = 1;
    /**
     * highest track number
     */
    protected final static int LAST_TRACK = 40;
    /**
     * track containing the disk directory
     */
    protected final static int DIRECTORY_TRACK = 18;
    /**
     * track containing the BAM
     */
    protected final static int BAM_SECTOR = 0;
    /**
     * first directory sector
     */
    protected final static int FIRST_DIRECTORY_SECTOR = 1;
    /**
     * location of the disk name on the first directory sector
     */
    protected final static int DISK_LABEL_INDEX = 144;
    /**
     * number of bytes to reserve for the disk label
     */
    protected final static int DISK_LABEL_LENGTH = 16;
    /**
     * index of the first BAM entry on the first directory sector
     */
    protected final static int FIRST_BAM_ENTRY_INDEX = 4;
    /**
     * number of BAM entry bytes
     */
    protected final static int BAM_LENGTH = 0x90 - 4;
    /**
     * number of bytes per BAM entry
     */
    protected final static int BAM_ENTRY_LENGTH = 4;
    /**
     * index of first usable byte on a sector
     */
    protected final static int FIRST_USABLE_BYTE_INDEX = 2;
    /**
     * length of a directory entry
     */
    protected final static int DIRECTORY_ENTRY_LENGTH = 32;
    /**
     * usable bytes per sector
     */
    protected final static int USABLE_BYTES_PER_SECTOR = BYTES_PER_SECTOR - FIRST_USABLE_BYTE_INDEX;
    /**
     * number of sectors per track
     */
    public final static int[] SECTORS_PER_TRACK = {
        21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21, 21,
        19, 19, 19, 19, 19, 19, 19,
        18, 18, 18, 18, 18, 18,
        17, 17, 17, 17, 17, 17, 17, 17, 17, 17
    };
    /**
     * The current track on the disk (1-40)
     */
    protected int currentTrack;
    /**
     * The current sector on the disk (0-20)
     */
    protected int currentSector;

    /**
     * Get the track the drive handler is currently pointing to
     * 
     * @return  track number (0-35)
     */
    public int getCurrentTrack() {
        return this.currentTrack;
    }

    /**
     * Get the sector the drive handler is currently pointing to
     * 
     * @return  sector number (0-21)
     */
    public int getCurrentSector() {
        return this.currentSector;
    }

    /**
     * Create a C64FileEntry from the bytes of a directory entry
     * 
     * @param   dirEntryBytes   32 bytes of a C64 disk files' directory entry
     * @return  C64FileEntry containing the relevant file information
     */
    protected final C64FileEntry createFileEntry(final byte[] dirEntryBytes) {
        // read the file entry
        final int fileType = dirEntryBytes[ 2] & 0xff;
        final int firstTrack = dirEntryBytes[ 3] & 0xff;
        final int firstSector = dirEntryBytes[ 4] & 0xff;
        final String filename = readC64Filename(dirEntryBytes, 5, 16);
        final int relTrack = dirEntryBytes[ 21] & 0xff;
        final int relSector = dirEntryBytes[ 22] & 0xff;
        final int relLength = dirEntryBytes[ 23] & 0xff;
        final int fileLength = (dirEntryBytes[ 30] & 0xff) + (dirEntryBytes[ 31] & 0xff) * 256;

        // return this file entry
        return new C64FileEntry(filename, fileType, fileLength, firstTrack, firstSector, relTrack, relSector, relLength);
    }

    /**
     * Change the current disk position to a given block
     *
     * @param   track   track (1-40) to access
     * @param   sector  sector (0-20) to access
     */
    public void gotoBlock(final int track, final int sector) {
        this.currentTrack = track;
        this.currentSector = sector;
    }

    /**
     * Write data to the current block
     * 
     * @param   bytes   256 bytes of data to be written to the current block
     * @throws  IllegalArgumentException if the number of bytes exceed the block length
     */
    public void writeBlock(final byte[] bytes) {
        if (bytes.length != BYTES_PER_SECTOR) {
            throw new IllegalArgumentException("Block must contain " + BYTES_PER_SECTOR + " bytes to write!");
        }

        writeBlock(bytes, BYTES_PER_SECTOR);
    }

    /**
     * Read the current block of disk data
     *
     * @return  array containing data of the block read
     * @throws  RuntimeException if not all bytes could be read
     */
    public byte[] readBlock() {
        // notify observers about the read operation and the block read
        setChanged(true);
        notifyObservers(C1541.READING);
        setChanged(true);
        notifyObservers(getCurrentTrack() + "," + getCurrentSector());

        // do the read operation
        return readBlockImpl();
    }

    /**
     * Write data to the current block
     * 
     * @param   bytes   data to be written to the current block, at max. 256 bytes
     * @param   numBytes    number of bytes to be written
     * @throws  IllegalArgumentException if the number of bytes exceed the block length
     */
    public void writeBlock(final byte[] bytes, final int numBytes) {
        // notify observers about the write operation and the block written
        setChanged(true);
        notifyObservers(C1541.WRITING);
        setChanged(true);
        notifyObservers(getCurrentTrack() + "," + getCurrentSector());

        // do the write operation
        writeBlockImpl(bytes, numBytes);
    }

    // abstract methods to be implemented by subclasses
    /**
     * Read the current block of disk data
     *
     * @return  array containing data of the block read
     * @throws  RuntimeException if not all bytes could be read
     */
    protected abstract byte[] readBlockImpl();

    /**
     * Write data to the current block
     * 
     * @param   bytes   data to be written to the current block, at max. 256 bytes
     * @param   numBytes    number of bytes to be written
     * @throws  IllegalArgumentException if the number of bytes exceed the block length
     */
    protected abstract void writeBlockImpl(final byte[] bytes, final int numBytes);

    /**
     * Get the disk ID bytes
     * 
     * @return  2 bytes used for the disk ID
     */
    public abstract int[] getDiskID();
}
