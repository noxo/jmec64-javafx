/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.drive;

import de.joergjahnke.c64.core.C64FileEntry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * class for reading/writing information from/to a tape file
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public abstract class TapeDriveHandler extends DriveHandler {

    /**
     * used when reading from tape, denotes the current position on the tape
     */
    protected int tapePosition = 0;

    /**
     * Read a given number of bytes of tape
     *
     * @param   size    number of bytes to read
     * @return  array containing data from the tape
     * @throws  RuntimeException if not all bytes could be read
     */
    protected byte[] readTapeData(final int size) {
        final byte[] tapeBytes = new byte[size];
        final int first = this.tapePosition;
        final int last = first + size;

        if (last > this.bytes.length) {
            throw new RuntimeException("Preliminary end of tape. Could not tape data!");
        }

        System.arraycopy(bytes, first, tapeBytes, 0, size);

        this.tapePosition = last;

        return tapeBytes;
    }

    /**
     * Go to a given position in a tape file
     *
     * @param   position    position to reach
     */
    protected void gotoTapePosition(final int position) {
        this.tapePosition = position;
    }

    /**
     * Read a tape file
     *
     * @param   fileEntry   contains file information about the file to load
     * @return  array containing the file data, the first two bytes denote the address the program wants to be loaded at
     */
    public byte[] readFile(final C64FileEntry fileEntry) {
        // determine number of bytes to read
        final int fileLen = fileEntry.fileLength >= 0 ? fileEntry.fileLength : this.bytes.length - this.tapePosition;
        // the result stream to write to
        final ByteArrayOutputStream out = new ByteArrayOutputStream(fileLen);

        // write the file entries' start address to the stream
        out.write(fileEntry.startAddress & 0xff);
        out.write((fileEntry.startAddress & 0xff00) >> 8);

        // goto desired position on the tape
        gotoTapePosition(fileEntry.offset);

        // read content of the file
        try {
            out.write(readTapeData(fileLen));
        } catch (IOException e) {
        // we do nothing but return an almost empty stream
        }

        // convert the bytes from the stream to an array and return this
        return out.toByteArray();
    }
}    
