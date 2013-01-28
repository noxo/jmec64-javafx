/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.drive;

import de.joergjahnke.c64.core.C1541;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * class for a DriveChannel implementation that allows a byte array to be read from the channel
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class ByteArrayDriveChannel implements DriveChannel {

    /**
     * data that can be read from the channel
     */
    protected ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
    /**
     * data that was written to the channel
     */
    protected ByteArrayOutputStream out = new ByteArrayOutputStream();
    /**
     * c1541 this channel belongs to
     */
    protected final C1541 c1541;

    /**
     * Create a new channel that contains no data
     * 
     * @param   c1541   c1541 where this channel is attached to
     */
    public ByteArrayDriveChannel(final C1541 c1541) {
        this.c1541 = c1541;
    }

    /**
     * Fill the channel with a given array of bytes that can afterwards be read from it
     *
     * @param   bytes   bytes to fill the channel with
     * @param   offset  index where to start reading the bytes from the array
     * @param   len number of bytes to read
     */
    protected void fill(final byte[] bytes, final int offset, final int len) {
        this.in = new ByteArrayInputStream(bytes, offset, len);
    }

    /**
     * Skip a given number of bytes which are read and discarded
     *
     * @param   numBytes    number of bytes to skip
     * @return  the skipped bytes
     */
    protected byte[] skip(final int numBytes) {
        final ByteArrayOutputStream skipped = new ByteArrayOutputStream();

        for (int i = 0; i < numBytes; ++i) {
            skipped.write(this.in.read());
        }

        return skipped.toByteArray();
    }

    /**
     * Write data to the channel and inform observers about the write operation
     * 
     * @throws  IOException if the byte cannot be written
     */
    public void write(final int b) throws IOException {
        this.c1541.setChanged(true);
        this.c1541.notifyObservers(C1541.WRITING);

        this.out.write(b);
    }

    /**
     * Write data to the channel and inform observers about the write operation
     */
    public void write(final byte[] bytes, final int offset, final int len) {
        this.c1541.setChanged(true);
        this.c1541.notifyObservers(C1541.WRITING);

        this.out.write(bytes, offset, len);
    }

    /**
     * Read data from the channel and inform observers about the read operation
     */
    public int read() {
        this.c1541.setChanged(true);
        this.c1541.notifyObservers(C1541.READING);

        return this.in.read();
    }

    public int available() throws IOException {
        return this.in.available();
    }

    public void commit() throws IOException {
    // we do nothing
    }

    public void close() throws IOException {
        this.in.close();
        this.out.close();
    }
}
