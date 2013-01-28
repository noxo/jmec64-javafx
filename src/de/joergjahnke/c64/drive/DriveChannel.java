/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.drive;

import java.io.IOException;

/**
 * Defines a logical channel for a C64 drive
 * 
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public interface DriveChannel {

    /**
     * Read the next available byte from the channel
     *
     * @return  -1 if no further bytes are available, otherwise the next byte
     * @throws IOException if the channel cannot be read from
     */
    int read() throws IOException;

    /**
     * Write a byte to the channel
     *
     * @param   b   the byte to write
     * @throws IOException if the channel cannot be written to
     */
    void write(int b) throws IOException;

    /**
     * Write an array of bytes to the channel
     *
     * @param   bytes   array with data to write
     * @param   offset  index of first byte to write
     * @param   len number of bytes to write
     * @throws IOException if the channel cannot be written to
     */
    void write(byte[] bytes, int offset, int len) throws IOException;

    /**
     * Get the number of available bytes in the channel
     *
     * @return  number of bytes that can be read from the channel
     * @throws IOException if the number of bytes available cannot be detected
     */
    int available() throws IOException;

    /**
     * Commit the current transaction.
     * When opening a file the commit will cause the file to be opened, when saving a file
     * the data will be stored on the disk.
     *
     * @throws IOException if the channel cannot be written to
     */
    void commit() throws IOException;

    /**
     * Close the channel and free resources
     *
     * @throws IOException if the channel cannot be closed correctly
     */
    void close() throws IOException;
}
