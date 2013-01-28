/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Classes implementing this interface are enabled to store and load their current state to and from an IO stream
 *
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public interface Serializable {

    /**
     * Write the object to the stream
     * 
     * @param out   stream to write to
     * @throws java.io.IOException if the data cannot be written to the stream
     */
    void serialize(DataOutputStream out) throws IOException;

    /**
     * Read the object from the stream
     * 
     * @param in    stream to read from
     * @throws java.io.IOException
     */
    void deserialize(DataInputStream in) throws IOException;
}
