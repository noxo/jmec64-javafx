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
 * class for reading/writing information from/to a .prg file
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class PRGDriveHandler extends TapeDriveHandler {

    public void mount(final byte[] bytes) {
        this.imageType = SEQUENTIAL;
        this.bytes = bytes;
        this.tapePosition = 0;

        this.label = ".PRG FILE";
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
                    gotoTapePosition(0);

                    // the first 2 bytes give us information about the start address
                    final byte[] buffer = readTapeData(2);

                    final int fileType = 0x82;
                    final int startAddress = (buffer[ 0] & 0xff) + (buffer[ 1] & 0xff) * 256;
                    final int offset = tapePosition;
                    final String filename = "FILE";

                    ++this.retrieved;

                    // create and return a file entry
                    return new C64FileEntry(filename, fileType, startAddress, offset);
                }
            }
        };
    }
}
