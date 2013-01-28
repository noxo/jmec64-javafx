/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.drive;

import de.joergjahnke.c64.core.C1541;
import java.io.IOException;

/**
 * class for a DriveChannel used for writing to a file
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class FileWriteDriveChannel extends ByteArrayDriveChannel {
    // name of file to write
    private String filename = null;

    /**
     * Creates a new c1541 channel for writing to a file
     * 
     * @param   c1541   c1541 where this channel is attached to
     */
    public FileWriteDriveChannel(final C1541 c1541) {
        super(c1541);
    }

    public void commit() throws IOException {
        // no filename specified yet?
        if (null == this.filename) {
            // we get the filename first, read this from output data
            this.filename = this.out.toString();
        } else {
            // save file data to specified file
            this.c1541.getDriveHandler().writeFile(this.filename, this.out.toByteArray());
        }
        this.out.reset();
    }
}
