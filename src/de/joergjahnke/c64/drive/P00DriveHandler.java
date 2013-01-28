/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.drive;

import de.joergjahnke.c64.core.C64FileEntry;
import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * class for reading/writing the directory information of a .p00 file
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class P00DriveHandler extends TapeDriveHandler {

    public void mount(final byte[] bytes) {
        this.imageType = SEQUENTIAL;
        this.bytes = bytes;
        this.tapePosition = 0;

        // the first 7 bytes usually start with "C64File",
        // starting with "C64" is mandatory
        final byte[] buffer = readTapeData(8);

        if (!new String(buffer).startsWith("C64")) {
            throw new RuntimeException("Not a valid .p00 file!");
        }

        this.label = ".P00 FILE";
    }

    public Enumeration directoryElements() {
        return new Enumeration() {

            private int retrieved = 0;

            public boolean hasMoreElements() {
                return this.retrieved < 1;
            }

            public Object nextElement() {
                if (this.retrieved > 0) {
                    throw new NoSuchElementException("Tried to access past end of directory");
                } else {
                    gotoTapePosition(8);

                    // the next 18 bytes give us information about the file name and the record length
                    final byte[] buffer = readTapeData(20);

                    final int fileType = 0x82;
                    final int startAddress = (buffer[ 18] & 0xff) + (buffer[ 19] & 0xff) * 256;
                    final int offset = tapePosition;
                    final String filename = readC64Filename(buffer, 0, 16);

                    ++this.retrieved;

                    // create and return a file entry
                    return new C64FileEntry(filename, fileType, startAddress, offset);
                }
            }
        };
    }
}
