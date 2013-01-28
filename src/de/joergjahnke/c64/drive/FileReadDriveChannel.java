/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.drive;

import de.joergjahnke.c64.core.C1541;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * class for a DriveChannel used for reading a file
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class FileReadDriveChannel extends ByteArrayDriveChannel {

    /**
     * Creates a new c1541 channel for reading files
     * 
     * @param   c1541   c1541 where this channel is attached to
     */
    public FileReadDriveChannel(final C1541 c1541) {
        super(c1541);
    }

    public void commit() throws IOException {
        final String filename = this.out.toString();

        this.in = new ByteArrayInputStream(this.c1541.readFile(filename));
        this.out.reset();
    }
}
