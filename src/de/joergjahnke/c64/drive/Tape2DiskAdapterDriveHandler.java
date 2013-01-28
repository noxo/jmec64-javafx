/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.drive;

import de.joergjahnke.c64.core.C64FileEntry;
import java.util.Enumeration;

/**
 * This class converts the content of a tape image for drive read access
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class Tape2DiskAdapterDriveHandler extends DiskDriveHandler {

    /**
     * constant disk ID we use when emulating tapes as disks
     */
    private final static int[] DISK_ID = {'A', 'A'};
    // handles request to the underlying tape drive format
    private final TapeDriveHandler tapeHandler;
    // emulates a D64 drive
    private D64DriveHandler diskDriveHandler = null;

    /**
     * Create a new Tape2DiskAdapterDriveHandler
     *
     * @param   tapeHandler the tape drive handler that should be emulated
     */
    public Tape2DiskAdapterDriveHandler(final TapeDriveHandler tapeHandler) {
        this.tapeHandler = tapeHandler;
    }

    public int getCurrentTrack() {
        return getDiskDriveHandler().getCurrentTrack();
    }

    public int getCurrentSector() {
        return getDiskDriveHandler().getCurrentSector();
    }

    /**
     * Retrieve the DiskDriveHandler that emulates disk operations for the tape data.
     * This method creates 
     *
     * @return  DiskDriveHandler that emulates the disk operations
     */
    private DiskDriveHandler getDiskDriveHandler() {
        if (this.diskDriveHandler == null) {
            // create an empty disk image
            final byte[] d64Bytes = new byte[D64DriveHandler.calculateTotalSectors(LAST_TRACK) * BYTES_PER_SECTOR];

            // mount this image to a D64DriveHandler
            this.diskDriveHandler = new D64DriveHandler();
            this.diskDriveHandler.mount(d64Bytes);
            this.diskDriveHandler.setDiskID(DISK_ID);
            this.diskDriveHandler.format(this.tapeHandler.getLabel(), 0);

            // add the files from the tape to the disk
            for (final Enumeration en = this.tapeHandler.directoryElements(); en.hasMoreElements();) {
                final C64FileEntry entry = (C64FileEntry) en.nextElement();
                final byte[] fileBytes = readFile(entry);

                this.diskDriveHandler.writeFile(entry.filename, fileBytes);
            }
        }
        return this.diskDriveHandler;
    }

    // implementation of abstract methods of superclass DriveHandler
    public void mount(final byte[] bytes) {
        this.tapeHandler.mount(bytes);
        this.diskDriveHandler = null;
    }

    public Enumeration directoryElements() {
        return this.tapeHandler.directoryElements();
    }

    public byte[] readFile(final C64FileEntry fileEntry) {
        return this.tapeHandler.readFile(fileEntry);
    }

    // implementation of abstract methods of superclass DiskDriveHandler
    public byte[] readBlockImpl() {
        return getDiskDriveHandler().readBlock();
    }

    public void writeBlockImpl(final byte[] bytes, final int numBytes) {
        throw new RuntimeException("Cannot write to tape files!");
    }

    public void gotoBlock(final int track, final int sector) {
        getDiskDriveHandler().gotoBlock(track, sector);
    }

    public int[] getDiskID() {
        return DISK_ID;
    }
}
