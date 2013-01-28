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
 * Class for reading/writing information from/to a .t64 file.<br>
 * <br>
 * For documentation on the .t64 file format see <a href='http://mediasrv.ns.ac.yu/extra/fileformat/emulator/t64/t64.txt'>http://mediasrv.ns.ac.yu/extra/fileformat/emulator/t64/t64.txt</a>.<br>
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class T64DriveHandler extends TapeDriveHandler {

    private int entries = 0;

    public void mount(final byte[] bytes) {
        this.imageType = SEQUENTIAL;
        this.bytes = bytes;
        this.tapePosition = 0;

        // buffer for reading directory
        byte[] buffer = null;

        // the first 32 bytes usually start with C64S.tape.image.file,
        buffer = readTapeData(32);
        // - starting with "C64" is mandatory
        if (!new String(buffer).startsWith("C64")) {
            throw new RuntimeException("Not a valid .t64 file!");
        }

        // the next 32 bytes give us information about the tape label and the number of entries in the file
        buffer = readTapeData(32);
        // - first we check whether the tape version number is $0101 or $0100
        if (!(buffer[ 0] == 0 && buffer[ 1] == 1) && !(buffer[ 0] == 1 && buffer[ 1] == 1)) {
            throw new RuntimeException("Not a valid .t64 file!");
        }

        this.entries = Math.max(1, (buffer[ 4] & 0xff) + (buffer[ 5] & 0xff) * 256);

        this.label = readC64Filename(buffer, 8, 24);
    }

    public Enumeration directoryElements() {
        return new Enumeration() {

            private int retrieved = 0;

            public boolean hasMoreElements() {
                return this.retrieved < entries;
            }

            public Object nextElement() {
                if (this.retrieved >= entries) {
                    throw new NoSuchElementException("Tried to access past end of directory");
                } else {
                    // go to the current directory entry
                    gotoTapePosition(64 + this.retrieved * 32);

                    // read the entry
                    final byte[] buffer = readTapeData(32);

                    final int tapeType = buffer[ 0] & 0xff;
                    final int fileType = buffer[ 1] & 0xff;
                    final int startAddress = (buffer[ 2] & 0xff) + (buffer[ 3] & 0xff) * 256;
                    final int endAddress = (buffer[ 4] & 0xff) + (buffer[ 5] & 0xff) * 256;
                    final int offset = (buffer[ 8] & 0xff) + (buffer[ 9] & 0xff) * 256 + ((buffer[ 10] & 0xff) << 16) + ((buffer[ 11] & 0xff) << 24);
                    final String filename = readC64Filename(buffer, 16, 16);

                    ++this.retrieved;

                    // the end address might point beyond the end of the file, we need to correct this
                    final int fileLength = endAddress - startAddress;
                    final int correctedEndAddress = fileLength + offset > bytes.length ? bytes.length - offset + startAddress : endAddress;

                    // store this in the directory structure
                    return new C64FileEntry(filename, fileType, tapeType, startAddress, correctedEndAddress, offset);
                }
            }
        };
    }
}
