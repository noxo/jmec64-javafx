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
 * class for a DriveChannel that can be used for reading or writing
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class MultiPurposeDriveChannel extends ByteArrayDriveChannel {
    // file types
    private final static int TYPE_SEQUENTIAL = 0;
    private final static int TYPE_USER = 1;
    private final static int TYPE_PROGRAM = 2;
    // access modes
    private final static int MODE_READ = 0;
    private final static int MODE_WRITE = 1;
    private final static int MODE_APPEND = 2;
    // do we work in block mode?
    private boolean isBlockMode = false;
    // are we currently writing data to a file?
    private boolean isWriting = false;
    // file we currently write to
    private String filename = null;

    /**
     * Creates a new c1541 channel for reading and writing data
     * 
     * @param   c1541   c1541 where this channel is attached to
     */
    public MultiPurposeDriveChannel(final C1541 c1541) {
        super(c1541);
    }

    /**
     * Activate or deactivate block mode.
     * In block mode data from the output is written directly to the current block instead of interpreting it as a filename.
     *
     * @param   isBlockMode true to activate the block mode, false to deactivate
     */
    public void setBlockMode(final boolean isBlockMode) {
        this.isBlockMode = isBlockMode;
    }

    public void commit() throws IOException {
        // we have to determine the filename to write to?
        if (!this.isWriting && !isBlockMode) {
            String fn = this.out.toString();
            int type = TYPE_SEQUENTIAL;
            int mode = MODE_READ;

            if (C1541.DEBUG) {
                System.out.println("Processing request '" + fn + "'.");
            }

            // type and mode are specified?
            if (fn.indexOf(',') > 0) {
                // get the file type
                int index = fn.indexOf(',');

                switch (fn.charAt(index + 1)) {
                    case 'S':
                        // we keep the default, which is a sequential file
                        break;
                    case 'P':
                        type = TYPE_PROGRAM;
                        break;
                    case 'U':
                        type = TYPE_USER;
                        break;
                }

                // mode is also specified
                index = fn.indexOf(',', index + 1);
                if (index > 0) {
                    // get the mode
                    switch (fn.charAt(index + 1)) {
                        case 'R':
                            // we keep the default, which is reading from the file
                            break;
                        case 'W':
                            mode = MODE_WRITE;
                            break;
                        case 'A':
                            mode = MODE_APPEND;
                            break;
                    }
                }

                // truncate the filename, so that the access mode is no longer contained
                fn = fn.substring(0, fn.indexOf(','));
            }

            this.out.reset();

            // direct access
            if ("#".equals(fn)) {
            // only allocate the channel i.e. we do nothing
            } else if ((type == TYPE_SEQUENTIAL || type == TYPE_PROGRAM) && mode == MODE_READ) {
                // normal file read
                this.in = new ByteArrayInputStream(this.c1541.getDriveHandler().readFile(fn));
            } else if ((type == TYPE_SEQUENTIAL || type == TYPE_PROGRAM) && mode == MODE_WRITE) {
                // normal file write
                this.filename = fn;
                this.isWriting = true;
            } else if ((type == TYPE_SEQUENTIAL || type == TYPE_PROGRAM) && mode == MODE_APPEND) {
                // append to a file
                final byte[] bytes = this.c1541.getDriveHandler().readFile(fn);

                this.out.write(bytes, 0, bytes.length);
                this.filename = "@:" + fn;
                this.isWriting = true;
            } else {
                // we don't support user-type access and report an error
                final String message = "Illegal access mode or user type for file access '" + fn + "'!";

                if (null != this.c1541.getLogger()) {
                    this.c1541.getLogger().warning(message);
                }
                throw new IOException(message);
            }
        }
    }

    public void close() throws IOException {
        // write collected data to the file
        if (this.isWriting) {
            this.c1541.getDriveHandler().writeFile(this.filename, this.out.toByteArray());
            this.isWriting = false;
        }
        // free other resources
        super.close();
    }
}
