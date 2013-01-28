/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

/**
 * Data structure for a file entry of a .d64 or .t64 file
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class C64FileEntry {

    /**
     * File type for a file marked as deleted
     */
    public final static String TYPE_DELETED = "DEL";
    /**
     * File type for a program file
     */
    public final static String TYPE_PROGRAM = "PRG";
    /**
     * File type for a sequential file
     */
    public final static String TYPE_SEQUENTIAL = "SEQ";
    /**
     * File type for a user file
     */
    public final static String TYPE_USER = "USR";
    /**
     * File type for a relative file
     */
    public final static String TYPE_RELATIVE = "REL";
    /**
     * Unknown file type
     */
    public final static String TYPE_UNKNOWN = "   ";
    /**
     * Filename, 16 bytes, padded with spaces
     */
    public String filename;
    /**
     * File type, use method getFileType to get a string representation of the type
     */
    public final int fileType;
    /**
     * Length of file in bytes
     */
    public final int fileLength;
    /**
     * First track of the file on a disk, unused for tapes
     */
    public final int firstTrack;
    /**
     * First sector of the file on a tape, unused for tapes
     */
    public final int firstSector;
    /**
     * First track of the file for a REL file, unused for other types
     */
    public final int relTrack;
    /**
     * First sector of the file for a REL file, unused for other types
     */
    public final int relSector;
    /**
     * Record length for a REL file, unused for other types
     */
    public final int relLength;
    /**
     * Numbr of blocks used on the disk
     */
    public final int blocks;
    /**
     * Tape type of a .64 file
     */
    public final int tapeType;
    /**
     * Start address for a .t64 file, unused for disks
     */
    public final int startAddress;
    /**
     * End address for a .t64 file, unused for disks
     */
    public final int endAddress;
    /**
     * Offset from start of tape file where the program starts, unused for disks
     */
    public final int offset;

    /**
     * Creates a new instance of C64FileEntry with data from a .d64 file
     */
    public C64FileEntry(final String filename, final int fileType, final int blocks, final int firstTrack, final int firstSector, final int relTrack, final int relSector, final int relLength) {
        this.filename = filename;
        this.fileType = fileType;
        this.fileLength = 0;

        this.firstTrack = firstTrack;
        this.firstSector = firstSector;
        this.relTrack = relTrack;
        this.relSector = relSector;
        this.relLength = relLength;
        this.blocks = blocks;

        this.tapeType = 0;
        this.startAddress = 0;
        this.endAddress = 0;
        this.offset = 0;
    }

    /**
     * Creates a new instance of C64FileEntry with data from a .t64 file
     */
    public C64FileEntry(final String filename, final int fileType, final int tapeType, final int startAddress, final int endAddress, final int offset) {
        this.filename = filename;
        this.fileType = fileType == 0 || fileType >= 0x80 ? fileType : 0x82;
        this.fileLength = endAddress - startAddress;

        this.firstTrack = 0;
        this.firstSector = 0;
        this.relTrack = 0;
        this.relSector = 0;
        this.relLength = 0;
        this.blocks = (this.fileLength + 2) / 254;

        this.tapeType = tapeType;
        this.startAddress = startAddress;
        this.endAddress = endAddress;
        this.offset = offset;
    }

    /**
     * Creates a new instance of C64FileEntry with data from a .prg or .p00 file
     */
    public C64FileEntry(final String filename, final int fileType, final int startAddress, final int offset) {
        this.filename = filename;
        this.fileType = fileType;
        this.fileLength = -1;

        this.firstTrack = 0;
        this.firstSector = 0;
        this.relTrack = 0;
        this.relSector = 0;
        this.relLength = 0;
        this.blocks = 0;

        this.tapeType = 0;
        this.startAddress = startAddress;
        this.endAddress = 0;
        this.offset = offset;
    }

    /**
     * Creates a new instance of C64FileEntry for searching a given file
     *
     * @param filename  name of file to search
     * @param fileType  fileType e.g. TYPE_PROGRAM
     */
    public C64FileEntry(final String filename, final int fileType) {
        this(filename, fileType, 0, 0);
    }

    /**
     * Get a string representation of the file type
     *
     * @return  three letter file type name
     */
    public String getFileTypeName() {
        switch (this.fileType & 0x0f) {
            case 0x00:
                return TYPE_DELETED;
            case 0x01:
                return TYPE_SEQUENTIAL;
            case 0x02:
                return TYPE_PROGRAM;
            case 0x03:
                return TYPE_USER;
            case 0x04:
                return TYPE_USER;
            default:
                return TYPE_UNKNOWN;
        }
    }

    /**
     * Return file name and type
     */
    public String toString() {
        return this.filename + "," + getFileTypeName();
    }
}
