/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.drive;

import de.joergjahnke.c64.core.C64FileEntry;
import de.joergjahnke.common.util.DefaultObservable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;

/**
 * Abstract class for reading/writing the information from/to an image.<br>
 * <br>
 * For some more information on file formats see <a href='http://www.phs-edv.de/c64s/doc/technics.htm'>http://www.phs-edv.de/c64s/doc/technics.htm</a>
 * and <a href='http://www.unusedino.de/ec64/technical3.html'>http://www.unusedino.de/ec64/technical3.html</a>.<br>
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public abstract class DriveHandler extends DefaultObservable {

    /**
     * An image that can be read sequentially like a tape
     */
    public final static int SEQUENTIAL = 1;
    /**
     * An image that can be randomly accessed like a disk
     */
    public final static int RANDOM = 2;
    /**
     * data of current image
     */
    protected byte[] bytes = null;
    /**
     * current image type
     */
    protected int imageType = 0;
    /**
     * name of current disk
     */
    protected String label = "";
    /**
     * number of free blocks on the drive
     */
    protected int freeBlocks = 0;
    /**
     * was the image modified?
     */
    protected boolean wasModified = false;

    /**
     * Free resources of this object
     */
    public void destroy() {
        this.bytes = null;
        this.label = "";
    }

    /**
     * Get the disk/tapes label
     * 
     * @return  disk/tape name
     */
    public String getLabel() {
        return this.label;
    }

    /**
     * Was the image modified i.e. read operations executed?
     * 
     * @return  true if the image was modified, otherwise false
     */
    public boolean wasModified() {
        return this.wasModified;
    }

    /**
     * Get the bytes of the current image
     * 
     * @return  bytes of the current image
     */
    public byte[] getBytes() {
        return this.bytes;
    }

    /**
     * Read a file of a given type
     *
     * @param   filename    name of file to load from current image
     * @param   fileType    string representation of the file type, e.g. PRG
     * @return  array containing the file data, the first two bytes denote the address the program wants to be loaded at
     * @throws  IOException if the file cannot be found
     */
    public byte[] readFile(final String filename, final String fileType) throws IOException {
        // determine file to read
        C64FileEntry entry = null;
        boolean matchFound = false;

        for (final Enumeration en = directoryElements(); !matchFound && en.hasMoreElements();) {
            entry = (C64FileEntry) en.nextElement();
            matchFound = matches(entry, filename, fileType);
        }

        // we have the given file?
        byte[] result = null;

        if (!matchFound) {
            // should the directory be read?
            if ("$".equals(filename.trim())) {
                result = this.readDirectory();
            } else {
                throw new IOException("File not found: '" + filename.trim() + "'!");
            }
        } else {
            result = this.readFile(entry);
        }

        return result;
    }

    /**
     * Read a file
     *
     * @param   filename    name of file to load from current image
     * @return  array containing the file data, the first two bytes denote the address the program wants to be loaded at
     * @throws  IOException if the file cannot be found
     */
    public byte[] readFile(final String filename) throws IOException {
        return readFile(filename, null);
    }

    /**
     * Check if an entry matches a given filename, which might contain the joker characters '*' and '?'
     *
     * @param   entry   file entry to check
     * @param   filename    filename to match
     * @return  true if the entry matches the filename, otherwise false
     */
    public static boolean matches(final C64FileEntry entry, final String filename, final String fileType) {
        // does the file-type match
        final boolean isFileTypeOK = null == fileType || entry.getFileTypeName().equalsIgnoreCase(fileType);

        // the filename to match does not contain any joker characters?
        if (filename.indexOf('*') < 0 && filename.indexOf('?') < 0) {
            // then we simply compare the file names and check the type
            return entry.filename.trim().equals(filename.trim()) && isFileTypeOK;
        } else {
            // the '*' joker determines the number of characters to match as all following characters are ignored
            final int compareLength = filename.indexOf('*') < 0 ? entry.filename.length() : filename.indexOf('*');
            final StringBuffer entryFN = new StringBuffer(entry.filename.substring(0, compareLength));
            // this is the result, we assume a matching name
            boolean matches = isFileTypeOK;

            // compare all character up to the maximum length
            for (int i = 0; matches && i < entryFN.length(); ++i) {
                // a character matches if both are equal or if a '?' joker is at the given position
                matches &= entryFN.charAt(i) == filename.charAt(i) || filename.charAt(i) == '?';
            }

            return matches;
        }
    }

    /**
     * Create the delta between the current image bytes and a given image.
     * A delta file contains multiple entries for modifications, each looking as follows:<br>
     * [offset to last modification, 1-3 bytes][number of modified bytes, 1-3 bytes][modified bytes].
     *
     * @param   in  stream with bytes to compare the current image with
     * @return  array containing the delta
     * @throws  IOException if the inputstream cannot be read
     * @see de.joergjahnke.c64.drive.DriveHandler#applyDelta
     */
    public byte[] createDelta(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        for (int i = 0,  lastIndex = 0,  b; i < this.bytes.length && (b = in.read()) >= 0; ++i) {
            if ((byte) b != this.bytes[i]) {
                // write the index where the diff must be applied
                final int offset = i - lastIndex;

                out.write(offset % 0x80 | (offset >= 0x80 ? 0x80 : 0));
                if (offset >= 0x80) {
                    out.write((offset >> 7) % 0x80 | (offset >= 0x4000 ? 0x80 : 0));
                    if (offset >= 0x4000) {
                        out.write((offset >> 14) % 0x100);
                    }
                }

                lastIndex = i;

                // the current byte is the first to apply
                final ByteArrayOutputStream newBytes = new ByteArrayOutputStream();

                newBytes.write(this.bytes[i]);

                // determine the bytes we include into this diff section
                for (++i; i < this.bytes.length && (b = in.read()) >= 0 && (byte) b != this.bytes[i]; ++i) {
                    newBytes.write(this.bytes[i]);
                }

                // write the number of bytes
                final int n = newBytes.size();

                out.write(n % 0x80 | (n >= 0x80 ? 0x80 : 0));
                if (n >= 0x80) {
                    out.write((n >> 7) % 0x80 | (n >= 0x4000 ? 0x80 : 0));
                    if (n >= 0x4000) {
                        out.write((n >> 14) % 0x100);
                    }
                }

                // write the differing bytes
                out.write(newBytes.toByteArray(), 0, n);
            }
        }

        return out.toByteArray();
    }

    /**
     * Apply a delta and modify the current image 
     *
     * @param   in  inputstream with data to apply
     * @see de.joergjahnke.c64.drive.DriveHandler#createDelta
     * @throws IOException if the stream cannot be read from
     */
    public void applyDelta(final InputStream in) throws IOException {
        for (int lastIndex = 0,  b; (b = in.read()) >= 0;) {
            // retrieve the index where to apply the diff
            int offset = b;

            if (offset >= 0x80) {
                offset &= 0x7f;
                offset += (in.read() << 7);
                if (offset >= 0x4000) {
                    offset &= 0x3fff;
                    offset += (in.read() << 14);
                }
            }

            final int index = lastIndex + offset;

            lastIndex = index;

            // retrieve number of bytes
            int n = in.read();

            if (n >= 0x80) {
                n &= 0x7f;
                n += (in.read() << 7);
                if (n >= 0x4000) {
                    n &= 0x3fff;
                    n += (in.read() << 14);
                }
            }

            // read the given number of bytes and apply them
            for (int i = 0; i < n; ++i) {
                this.bytes[index + i] = (byte) in.read();
            }
        }
    }

    /**
     * Write to a file with a given filename
     * 
     * @param   filename    name of file to write to
     * @param   bytes   data to write
     * @throws  IOException if the data cannot be written to the medium
     */
    public void writeFile(final String filename, final byte[] bytes) throws IOException {
        throw new RuntimeException("Writing to files not yet implemented for " + this.getClass().getName());
    }

    /**
     * Delete a file from the medium
     * 
     * @param   filename    name of file to delete
     * @throws  IOException if the deletion cannot be executed
     */
    public void deleteFile(final String filename) throws IOException {
        throw new IOException("Deleting files not yet implemented for " + this.getClass().getName());
    }

    /**
     * Rename a file on the medium
     * 
     * @param   oldFilename previous file name
     * @param   newFilename new file name
     * @throws  IOException if the renaming cannot be executed
     */
    public void renameFile(final String oldFilename, final String newFilename) throws IOException {
        throw new IOException("Renaming files not yet implemented for " + this.getClass().getName());
    }

    /**
     * Read the directory content for a LOAD "$" command
     *
     * @return  array containing the directory data, the first two bytes denote the address the program wants to be loaded at
     */
    public byte[] readDirectory() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        // write load address 0x0401
        out.write(0x01);
        out.write(0x04);

        // write disk name
        out.write(0x01);
        out.write(0x01);
        out.write(0);
        out.write(0);
        out.write(0x12);
        out.write('\"');
        for (int i = 0; i < this.label.length() && i < 23; ++i) {
            out.write(this.label.charAt(i));
        }
        for (int i = this.label.length(); i < 23; ++i) {
            out.write(' ');
        }
        out.write('\"');
        out.write(0);

        // write all directory entries
        for (Enumeration en = directoryElements(); en.hasMoreElements();) {
            // get directory entry
            final C64FileEntry entry = (C64FileEntry) en.nextElement();
            final String name = entry.filename.trim();
            // dummy line link
            out.write(0x01);
            out.write(0x01);
            // write no. of blocks
            final int blocks = entry.blocks;

            out.write(blocks & 0xff);
            out.write(blocks >> 8);
            // insert spaces so that filename starts always at the same column
            out.write(' ');
            if (blocks < 100) {
                out.write(' ');
            }
            if (blocks < 10) {
                out.write(' ');
            }
            // write filename
            out.write('\"');
            for (int j = 0; j < name.length(); ++j) {
                out.write(name.charAt(j));
            }
            out.write('\"');
            for (int j = name.length(); j < 16; ++j) {
                out.write(' ');
            }
            // mark open files
            out.write((entry.fileType & 0x80) != 0 ? ' ' : '*');
            // write file type name
            final String fileTypeName = entry.getFileTypeName();

            out.write(fileTypeName.charAt(0));
            out.write(fileTypeName.charAt(1));
            out.write(fileTypeName.charAt(2));
            // mark protected files
            out.write((entry.fileType & 0x40) != 0 ? '<' : ' ');
            // denotes the end of the entry
            out.write(0);
        }

        // write number of free blocks
        final String blocksFree = "BLOCKS FREE.             ";

        out.write(0x01);
        out.write(0x01);
        out.write(this.freeBlocks & 0xff);
        out.write(this.freeBlocks / 256);
        for (int i = 0; i < blocksFree.length(); ++i) {
            out.write(blocksFree.charAt(i));
        }
        out.write(0);
        out.write(0);
        out.write(0);

        // convert the bytes from the stream to an array and return this
        return out.toByteArray();
    }

    /**
     * Get a C64 filename from a given position in a buffer.
     * This method will substitute padded 0xa0 (shift-space) with spaces.
     *
     * @param   buffer  buffer to read from
     * @param   offset  offset in the buffer where to start
     * @param   length  number of bytes to read
     * @return  read filename
     */
    protected String readC64Filename(final byte[] buffer, final int offset, final int length) {
        final StringBuffer filename = new StringBuffer();

        for (int i = offset; i < offset + length; ++i) {
            if (buffer[i] == (byte) 0xa0) {
                filename.append(' ');
            } else {
                filename.append((char) buffer[i]);
            }
        }

        return filename.toString();
    }

    /**
     * Write a C64 filename to a given position in a buffer.
     * This method will add 0xa0 (shift-space) at the end of the filename up to the given length.
     *
     * @param   buffer  buffer to read from
     * @param   offset  offset in the buffer where to start
     * @param   length  number of bytes to read
     * @param   filename    name to write
     */
    protected void writeC64Filename(final byte[] buffer, final int offset, final int length, final String filename) {
        for (int i = 0; i < length; ++i) {
            buffer[i + offset] = (byte) (i >= filename.length() ? 0xa0 : filename.charAt(i));
        }
    }

    // methods that need to be implemented by subclasses
    /**
     * Read a file with a given filename
     *
     * @param   entry   contains file information about the file to load
     * @return  byte array with file data
     */
    public abstract byte[] readFile(final C64FileEntry entry);

    /**
     * Assign image data for the drive handler
     *
     * @param   bytes   byte array with image data
     */
    public abstract void mount(final byte[] bytes);

    /**
     * Get the directory entries of the image
     * 
     * @return  enumeration of C64FileEntries
     */
    public abstract Enumeration directoryElements();
}
