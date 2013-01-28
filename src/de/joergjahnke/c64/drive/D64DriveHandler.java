/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.drive;

import de.joergjahnke.c64.core.C64FileEntry;
import java.io.ByteArrayOutputStream;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Vector;

/**
 * Class for reading/writing the directory information of a .d64 file.<br>
 * <br>
 * For a good documentation on the .d64 format see <a href='http://ist.uwaterloo.ca/%7Eschepers/formats/D64.TXT'>http://ist.uwaterloo.ca/%7Eschepers/formats/D64.TXT</a>.<br>
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class D64DriveHandler extends DiskDriveHandler {
    // holds the information whether a sector is allocated or free
    private boolean[] isAllocated;
    // the two bytes used for the disk ID
    private int[] diskID = new int[2];

    public void mount(final byte[] bytes) {
        this.imageType = RANDOM;
        this.bytes = bytes;
        this.currentTrack = 1;
        this.currentSector = 0;

        // reserve space for the BAM
        this.isAllocated = new boolean[calculateTotalSectors(LAST_TRACK)];

        // read the first directory block
        gotoBlock(DIRECTORY_TRACK, BAM_SECTOR);
        final byte[] blockBytes = readBlock();

        // determine and store disk ID
        setDiskID(new int[]{bytes[0xa2] & 0xff, bytes[0xa3] & 0xff});

        // determine disk label
        this.label = readC64Filename(blockBytes, DISK_LABEL_INDEX, DISK_LABEL_LENGTH);

        // determine the number of free blocks on the disk, this is inside the BAM at $04-$8f in blocks of 4 bytes
        freeBlocks = 0;
        for (int i = FIRST_BAM_ENTRY_INDEX,  track = 1,  block = 0; i < FIRST_BAM_ENTRY_INDEX + BAM_LENGTH; i += BAM_ENTRY_LENGTH, ++track) {
            freeBlocks += blockBytes[i];

            final int ba = blockBytes[i + 1] + blockBytes[i + 2] * 0x100 + blockBytes[i + 3] * 0x10000;

            for (int j = 0; j < SECTORS_PER_TRACK[track - 1]; ++j) {
                this.isAllocated[block] = (ba & (1 << j)) == 0;
                ++block;
            }
        }
    }

    public Enumeration directoryElements() {
        return new D64DirectoryEnumeration(false) {

            private C64FileEntry nextEntry = null;

            public Object nextElement() {
                // we use the prefetched entry if possible
                C64FileEntry result = this.nextEntry;

                this.nextEntry = null;

                // get current entry information
                while (null == result) {
                    final byte[] dirEntryBytes = (byte[]) super.nextElement();
                    final int fileType = dirEntryBytes[2] & 0xff;

                    // we skip removed files
                    if (fileType != 0) {
                        result = createFileEntry(dirEntryBytes);
                    }
                }

                return result;
            }

            public boolean hasMoreElements() {
                try {
                    this.nextEntry = (C64FileEntry) nextElement();
                    return true;
                } catch (NoSuchElementException e) {
                    return false;
                }
            }
        };
    }

    /**
     * Read a disk file
     *
     * @param   fileEntry   contains file information about the file to load
     * @return  array containing the file data, the first two bytes denote the address the program wants to be loaded at
     */
    public byte[] readFile(final C64FileEntry fileEntry) {
        // the result stream to write to
        final ByteArrayOutputStream out = new ByteArrayOutputStream(fileEntry.blocks * BYTES_PER_SECTOR);

        for (D64FileEnumeration en = new D64FileEnumeration(fileEntry.firstTrack, fileEntry.firstSector); en.hasMoreElements();) {
            final byte[] blockBytes = (byte[]) en.nextElement();

            // copy program data from sector to stream
            out.write(blockBytes, FIRST_USABLE_BYTE_INDEX, en.getUsableBytes());
        }

        // convert the bytes from the stream to an array and return this
        return out.toByteArray();
    }

    /**
     * Write a file to the disk
     *
     * @param   filename    name of file to write
     * @param   bytes   file data
     * @throws  IllegalStateException if the file cannot be overwritten or there is not enough space on the disk
     */
    public void writeFile(final String filename, final byte[] bytes) {
        // old file should be overwritten?
        String filename_ = filename;

        if (filename_.charAt(0) == '@') {
            // then modify the filename and overwrite the old version
            filename_ = filename_.substring(filename_.startsWith("@:") ? 2 : 1);
            deleteFile(filename_);
        }

        // check whether we have enough space
        if (calculateFreeBlocks() * USABLE_BYTES_PER_SECTOR < bytes.length) {
            throw new IllegalStateException("Not enough space on the disk");
        }

        // store first block for later use
        int block = getFirstFreeBlock(0);
        final BlockIdentifier firstBlockID = getBlockIdentifier(block);

        // we count the number of blocks
        int blocks = 0;

        // we work until no bytes are left to write
        for (int bytesLeft = bytes.length; bytesLeft > 0; bytesLeft -= USABLE_BYTES_PER_SECTOR) {
            // allocate the currently selected block
            this.isAllocated[block] = true;
            --this.freeBlocks;

            // get next free block
            final int nextBlock = getFirstFreeBlock(block);
            final BlockIdentifier nextBlockID = getBlockIdentifier(nextBlock);

            // create sector with data
            final byte[] blockBytes = new byte[BYTES_PER_SECTOR];

            // - the first two bytes point to the next block
            if (bytesLeft > USABLE_BYTES_PER_SECTOR) {
                blockBytes[0] = (byte) nextBlockID.track;
                blockBytes[1] = (byte) nextBlockID.sector;
            } else {
                blockBytes[1] = (byte) (bytesLeft + FIRST_USABLE_BYTE_INDEX);
            }

            // - then we have 254 bytes of data
            System.arraycopy(bytes, bytes.length - bytesLeft, blockBytes, FIRST_USABLE_BYTE_INDEX, Math.min(bytesLeft, USABLE_BYTES_PER_SECTOR));

            // write data to the designated block on the disk
            gotoBlock(getBlockIdentifier(block).track, getBlockIdentifier(block).sector);
            writeBlock(blockBytes);

            // proceed to next block
            block = nextBlock;
            ++blocks;
        }

        // store new entry in the directory
        for (D64DirectoryEnumeration en = new D64DirectoryEnumeration(true); en.hasMoreElements();) {
            // get current entry information
            final byte[] dirEntryBytes = (byte[]) en.nextElement();
            final int fileType = dirEntryBytes[2] & 0xff;

            // we have an empty entry?
            if (fileType == 0) {
                // then store the file data inside this entry
                dirEntryBytes[2] = (byte) 0x82;
                dirEntryBytes[3] = (byte) firstBlockID.track;
                dirEntryBytes[4] = (byte) firstBlockID.sector;
                writeC64Filename(dirEntryBytes, 5, 16, filename_.trim());
                for (int i = 21; i < 30; ++i) {
                    dirEntryBytes[i] = 0;
                }
                dirEntryBytes[30] = (byte) (blocks % 256);
                dirEntryBytes[31] = (byte) (blocks / 256);

                // and save the entry to disk
                en.saveElement(dirEntryBytes);

                // we quit once we have saved the entry
                break;
            }
        }

        // write the modified BAM
        writeBAM();
    }

    /**
     * Delete a file from the disk
     *
     * @param   filename    name of file to delete
     */
    public void deleteFile(final String filename) {
        // scan file entries for the file to delete
        for (D64DirectoryEnumeration en = new D64DirectoryEnumeration(false); en.hasMoreElements();) {
            // get current entry information
            final byte[] dirEntryBytes = (byte[]) en.nextElement();
            final int fileType = dirEntryBytes[2] & 0xff;
            final C64FileEntry fileEntry = createFileEntry(dirEntryBytes);

            // we found a file name matching the one we want to delete?
            if (fileType != 0 && matches(fileEntry, filename.trim(), null)) {
                // mark the file as deleted and free its sectors
                dirEntryBytes[2] = 0;
                en.saveElement(dirEntryBytes);

                for (D64FileEnumeration en2 = new D64FileEnumeration(fileEntry.firstTrack, fileEntry.firstSector); en2.hasMoreElements();) {
                    this.isAllocated[getCurrentBlock()] = false;
                    ++this.freeBlocks;
                }
            }
        }

        // write the modified BAM
        writeBAM();
    }

    /**
     * Rename a file
     *
     * @param   oldFilename the old filename
     * @param   newFilename the new filename replacing the old one
     * @throws  IllegalStateException if the new filename already exists
     */
    public void renameFile(final String oldFilename, final String newFilename) {
        // collect currently existing filenames as we must not rename to an existing file name
        final Vector filenames = new Vector();

        for (Enumeration en = directoryElements(); en.hasMoreElements();) {
            filenames.addElement(((C64FileEntry) en.nextElement()).filename.trim());
        }

        if (filenames.contains(newFilename.trim())) {
            throw new IllegalStateException("File '" + newFilename + "' already exists!");
        }

        // scan file entries for the file to rename
        for (D64DirectoryEnumeration en = new D64DirectoryEnumeration(false); en.hasMoreElements();) {
            // get current entry information
            final byte[] dirEntryBytes = (byte[]) en.nextElement();
            final int fileType = dirEntryBytes[2] & 0xff;

            // we found the entry for the file to delete?
            if (fileType != 0 && readC64Filename(dirEntryBytes, 5, 16).trim().equals(oldFilename.trim())) {
                writeC64Filename(dirEntryBytes, 5, 16, newFilename);
                en.saveElement(dirEntryBytes);

                // we stop when we have found the file
                break;
            }
        }
    }

    /**
     * The the linear block no. for the current track and sector
     * 
     * @return  block no. (0-768)
     */
    private int getCurrentBlock() {
        return this.getBlockNo(new BlockIdentifier(this.currentTrack, this.currentSector));
    }

    /**
     * Read the current block of disk data
     *
     * @return  array containing data of the block read
     * @throws  RuntimeException if not all bytes could be read
     */
    protected byte[] readBlockImpl() {
        final byte[] blockBytes = new byte[BYTES_PER_SECTOR];

        System.arraycopy(bytes, getCurrentBlock() * BYTES_PER_SECTOR, blockBytes, 0, BYTES_PER_SECTOR);

        return blockBytes;
    }

    /**
     * Write data to the current block
     * 
     * @param   bytes   data to be written to the current block, at max. 256 bytes
     */
    protected void writeBlockImpl(final byte[] bytes, final int numBytes) {
        if (bytes.length > BYTES_PER_SECTOR) {
            throw new IllegalArgumentException("Block data exceeds block length of " + BYTES_PER_SECTOR + " bytes!");
        }

        System.arraycopy(bytes, 0, this.bytes, getCurrentBlock() * BYTES_PER_SECTOR, numBytes);

        // the image was modified
        this.wasModified = true;
    }

    /**
     * Mark a block as allocated
     *
     * @param   block   block to allocate, using a linear addressing from 0-682/768
     * @throws  IllegalStateException if the block was already allocated prior to this operation
     */
    public void allocateBlock(final int block) {
        if (this.isAllocated[block]) {
            final int nextFreeBlock = getFirstFreeBlock(block);
            String nextFreeBlockText = "0,0";

            if (nextFreeBlock >= 0) {
                final BlockIdentifier blockID = getBlockIdentifier(nextFreeBlock);

                nextFreeBlockText = Integer.toString(blockID.track) + "," + Integer.toString(blockID.sector);
            }

            throw new IllegalStateException("Block is already allocated! Next free block is: " + nextFreeBlockText);
        } else {
            this.isAllocated[block] = true;
            this.wasModified = true;
        }
    }

    /**
     * Mark a block as allocated
     *
     * @param   track   track (1-40) to allocate
     * @param   sector  sector (0-20) to allocate
     * @throws  IllegalStateException if the block was already allocated prior to this operation
     */
    public void allocateBlock(final int track, final int sector) {
        allocateBlock(getBlockNo(new BlockIdentifier(track, sector)));
    }

    /**
     * Mark a block as free
     *
     * @param   block   block to free, using a linear addressing from 0-682/768
     */
    public void freeBlock(final int block) {
        this.wasModified = this.isAllocated[block];
        this.isAllocated[block] = false;
    }

    /**
     * Mark a block as free
     *
     * @param   track   track (1-40) to free
     * @param   sector  sector (0-20) to free
     */
    public void freeBlock(final int track, final int sector) {
        freeBlock(getBlockNo(new BlockIdentifier(track, sector)));
    }

    public int[] getDiskID() {
        return this.diskID;
    }

    /**
     * Set a new disk ID
     * 
     * @param   diskID  the new ID
     */
    protected void setDiskID(final int[] diskID) {
        this.diskID = diskID;
    }

    /**
     * Format the disk.
     * Only clears the BAM and relabels the disk
     * 
     * @param   label   new disk label
     * @param   id  disk ID, not used yet
     */
    public void format(final String label, final int id) {
        // read the first directory block
        gotoBlock(DIRECTORY_TRACK, BAM_SECTOR);
        final byte[] blockBytes = readBlock();

        // write the new label to the directory plus some additional bytes e.g. disk ID
        this.label = label;
        this.writeC64Filename(blockBytes, DISK_LABEL_INDEX, DISK_LABEL_LENGTH, label.trim());
        blockBytes[0xa0] = (byte) 0xa0;
        blockBytes[0xa1] = (byte) 0xa0;
        blockBytes[0xa2] = (byte) getDiskID()[0];
        blockBytes[0xa3] = (byte) getDiskID()[1];
        blockBytes[0xa4] = (byte) 0xa0;
        blockBytes[0xa5] = (byte) 0x32;
        blockBytes[0xa6] = (byte) 0x41;
        blockBytes[0xa7] = (byte) 0xa0;
        writeBlock(blockBytes);

        // clear the BAM
        for (int i = 0; i < this.isAllocated.length; ++i) {
            this.isAllocated[i] = false;
        }
        // reserve track 18, sector 0 and 1
        this.isAllocated[getBlockNo(new BlockIdentifier(18, 0))] = true;
        this.isAllocated[getBlockNo(new BlockIdentifier(18, 1))] = true;
        // write the new BAM
        writeBAM();
    }

    /**
     * Validating the disk normally removes all files which were not correctly closed and frees unused blocks.
     * We only do the latter task.
     */
    public void validate() {
        writeBAM();
    }

    /**
     * Calculates the total number of sectors on a disk with the given number of tracks
     * 
     * @param   tracks  number of tracks on the disk
     * @return  number of sectors on the disk
     */
    public static int calculateTotalSectors(final int tracks) {
        int result = 0;

        for (int i = 0; i < tracks; ++i) {
            result += SECTORS_PER_TRACK[i];
        }

        return result;
    }

    /**
     * Calculate the number of free blocks.
     * This method uses the information about allocated sectors to calculate the result.
     * 
     * @return  number of unallocated sectors on the disk
     */
    private int calculateFreeBlocks() {
        int result = 0;

        for (int i = 0; i < this.isAllocated.length; ++i) {
            result += this.isAllocated[i] ? 0 : 1;
        }

        return result;
    }

    /**
     * Get the first unallocated sector
     * 
     * @param   from    block ID where to start searching
     * @return  block ID of the first unallocated sector, or -1 if no free sector is available
     */
    private int getFirstFreeBlock(final int from) {
        for (int i = from; i < this.isAllocated.length; ++i) {
            if (!this.isAllocated[i]) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Get the block number for a given track and sector
     *
     * @param   track   track number (1-40)
     * @param   sector  sector number (0-21) on the current track
     * @return  block ID (0-768) corresponding to the given track and sector
     * @throws  RuntimeException if the track and sector combination is illegal
     */
    private int getBlockNo(final BlockIdentifier trackSector) {
        final int track = trackSector.track;
        final int sector = trackSector.sector;

        // check whether we try to access and illegal sector
        if (track < FIRST_TRACK || track > LAST_TRACK || sector < 0 || sector > SECTORS_PER_TRACK[track - 1]) {
            throw new RuntimeException("Illegal combination of track and sector: (" + track + "," + sector + ")");
        }

        // calculate the linear block no to go to
        int blockNo = sector;

        for (int i = 0; i < track - 1; ++i) {
            blockNo += SECTORS_PER_TRACK[i];
        }

        return blockNo;
    }

    /**
     * Get the track and sector for a given block ID
     *
     * @param   block ID (0-768)
     * @return  block identifier holding the track and sector for the given block ID
     */
    private BlockIdentifier getBlockIdentifier(final int block) {
        int track = 0, lastSPT = 0, block_ = block;

        while (block_ >= 0) {
            lastSPT = SECTORS_PER_TRACK[track++];
            block_ -= lastSPT;
        }

        return new BlockIdentifier(track, block_ + lastSPT);
    }

    /**
     * Write the block availability map
     */
    private void writeBAM() {
        gotoBlock(DIRECTORY_TRACK, BAM_SECTOR);
        final byte[] blockBytes = readBlock();

        // we always point to track 18, sector 1 as the first directory block
        blockBytes[0] = 18;
        blockBytes[1] = 1;
        // standard DOS version type is "A"
        blockBytes[2] = 0x41;
        blockBytes[3] = 0x00;

        // write BAM entries to block
        for (int i = FIRST_BAM_ENTRY_INDEX,  track = 1,  block = 0; i < FIRST_BAM_ENTRY_INDEX + BAM_LENGTH; i += BAM_ENTRY_LENGTH, ++track) {
            int ba = 0;
            int free = 0;

            for (int j = 0; j < SECTORS_PER_TRACK[track - 1]; ++j) {
                if (!this.isAllocated[block]) {
                    ba |= (1 << j);
                    ++free;
                }
                ++block;
            }

            blockBytes[i] = (byte) free;
            blockBytes[i + 1] = (byte) (ba & 0xff);
            blockBytes[i + 2] = (byte) ((ba >> 8) & 0xff);
            blockBytes[i + 3] = (byte) ((ba >> 16) & 0xff);
        }

        // write BAM block to disk
        writeBlock(blockBytes);
    }


    // inner class for the combination of track and sector on that track that identifies a sector
    class BlockIdentifier {

        public int track,  sector;

        public BlockIdentifier(final int track, final int sector) {
            this.track = track;
            this.sector = sector;
        }
    }


    // inner class that iterates over the blocks of a file
    class D64FileEnumeration implements Enumeration {
        // did we reach the last sector?
        private boolean wasLastSector = false;
        // track and sector of the next file block
        private int nextTrack;
        private int nextSector;

        /**
         * Create a new file enumeration
         * 
         * @param   firstTrack  first track of the file
         * @param   firstSector first sector of the file on the first track
         */
        public D64FileEnumeration(final int firstTrack, final int firstSector) {
            this.nextTrack = firstTrack;
            this.nextSector = firstSector;
        }

        public int getUsableBytes() {
            return this.nextTrack == 0 ? this.nextSector - 1 : USABLE_BYTES_PER_SECTOR;
        }


        // implementation of the Enumeration interface
        public boolean hasMoreElements() {
            return !this.wasLastSector;
        }

        public Object nextElement() {
            // read the next sector
            gotoBlock(this.nextTrack, this.nextSector);

            final byte[] blockBytes = readBlock();

            // get the next track and sector number
            this.nextTrack = blockBytes[0] & 0xff;
            this.nextSector = blockBytes[1] & 0xff;

            // the last sector is indicated by a track number of zero
            wasLastSector = nextTrack == 0;

            return blockBytes;
        }
    }


    // inner class that iterates over the entries of a disk directory
    class D64DirectoryEnumeration extends D64FileEnumeration {
        // did we reach the last entry?
        private boolean wasLastEntry = false;
        // bytes read from the current directory sector
        private byte[] blockBytes = null;
        // current directory entry index on the sector
        private int entryStart = BYTES_PER_SECTOR;
        // do we append new empty entries when we reach the end of the directory?
        private final boolean append;

        /**
         * Create a new directory enumeration
         * 
         * @param   append  true to insert new entries at the end of the directory, false to stop at that point
         */
        public D64DirectoryEnumeration(final boolean append) {
            super(DIRECTORY_TRACK, FIRST_DIRECTORY_SECTOR);
            this.append = append;
        }

        /**
         * Overwrite the current element with the given data
         * 
         * @param   dirEntryBytes   data for the directory entry
         */
        public void saveElement(final byte[] dirEntryBytes) {
            System.arraycopy(dirEntryBytes, FIRST_USABLE_BYTE_INDEX, this.blockBytes, this.entryStart - DIRECTORY_ENTRY_LENGTH + FIRST_USABLE_BYTE_INDEX, DIRECTORY_ENTRY_LENGTH - FIRST_USABLE_BYTE_INDEX);
            writeBlock(this.blockBytes);
        }


        // implementation of the Enumeration interface
        public boolean hasMoreElements() {
            return !this.wasLastEntry;
        }

        public Object nextElement() {
            // go to the next entry
            if (this.entryStart >= BYTES_PER_SECTOR) {
                // we have no other block to read?
                if (!super.hasMoreElements()) {
                    // we don't want to append?
                    if (!this.append) {
                        throw new NoSuchElementException("Tried to access past end of directory");
                    }

                    // find position of new block to append
                    final int firstFreeBlock = getFirstFreeBlock(getBlockNo(new BlockIdentifier(DIRECTORY_TRACK, 1)));
                    final BlockIdentifier blockID = getBlockIdentifier(firstFreeBlock);

                    // write this block's position to the current block
                    this.blockBytes[0] = (byte) blockID.track;
                    this.blockBytes[1] = (byte) blockID.sector;
                    writeBlock(this.blockBytes);

                    // go to the new, empty block
                    gotoBlock(blockID.track, blockID.sector);
                    this.blockBytes = new byte[BYTES_PER_SECTOR];
                } else {
                    // read the next directory sector
                    this.blockBytes = (byte[]) super.nextElement();
                }

                // start with first entry
                this.entryStart = 0;
            }

            // read bytes for this entry
            final byte[] dirEntryBytes = new byte[DIRECTORY_ENTRY_LENGTH];

            System.arraycopy(this.blockBytes, this.entryStart, dirEntryBytes, 0, DIRECTORY_ENTRY_LENGTH);

            // position at next entry
            this.entryStart += DIRECTORY_ENTRY_LENGTH;

            // we have no more entries when we are past the last block and its last entry and do not want to append
            this.wasLastEntry = !super.hasMoreElements() && this.entryStart >= BYTES_PER_SECTOR && !append;

            return dirEntryBytes;
        }
    }
}
