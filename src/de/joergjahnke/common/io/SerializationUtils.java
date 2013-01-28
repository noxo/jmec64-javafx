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
 * Utility methods for object serialization
 *
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class SerializationUtils {

    /**
     * Write a serialization marked to the stream
     * 
     * @param out   stream to write to
     * @throws java.io.IOException if the marker cannot be written to the stream
     */
    public static void setMarker(final DataOutputStream out) throws IOException {
        out.writeInt(Integer.MAX_VALUE);
    }

    /**
     * Verify the serialization marked
     * 
     * @param in    stream to read from
     * @throws java.io.IOException if the marked was not found
     */
    public static void verifyMarker(final DataInputStream in) throws IOException {
        if (in.readInt() != Integer.MAX_VALUE) {
            throw new IOException("Serialization marked not found!");
        }
    }

    /**
     * Serialize an array of bytes
     * 
     * @param out   stream to write to
     * @param data  data to serialize
     * @throws java.io.IOException if the data cannot be written to the stream
     */
    public static void serialize(final DataOutputStream out, final byte[] data) throws IOException {
        out.writeInt(data.length);
        for (int i = 0; i < data.length; ++i) {
            out.writeByte(data[i]);
        }
    }

    /**
     * Deserialize an array of bytes
     * 
     * @param in    stream to read from
     * @param data  target of the deserialization
     * @throws java.io.IOException if the data cannot be read from the stream
     */
    public static void deserialize(final DataInputStream in, final byte[] data) throws IOException {
        final int size = in.readInt();

        for (int i = 0; i < size; ++i) {
            data[i] = in.readByte();
        }
    }

    /**
     * Serialize an array of shorts
     * 
     * @param out   stream to write to
     * @param data  data to serialize
     * @throws java.io.IOException if the data cannot be written to the stream
     */
    public static void serialize(final DataOutputStream out, final short[] data) throws IOException {
        out.writeInt(data.length);
        for (int i = 0; i < data.length; ++i) {
            out.writeShort(data[i]);
        }
    }

    /**
     * Deserialize an array of shorts
     * 
     * @param in    stream to read from
     * @param data  target of the deserialization
     * @throws java.io.IOException if the data cannot be read from the stream
     */
    public static void deserialize(final DataInputStream in, final short[] data) throws IOException {
        final int size = in.readInt();

        for (int i = 0; i < size; ++i) {
            data[i] = in.readShort();
        }
    }

    /**
     * Serialize an array of integers
     * 
     * @param out   stream to write to
     * @param data  data to serialize
     * @throws java.io.IOException if the data cannot be written to the stream
     */
    public static void serialize(final DataOutputStream out, final int[] data) throws IOException {
        out.writeInt(data.length);
        for (int i = 0; i < data.length; ++i) {
            out.writeInt(data[i]);
        }
    }

    /**
     * Deserialize an array of integers
     * 
     * @param in    stream to read from
     * @param data  target of the deserialization
     * @throws java.io.IOException if the data cannot be read from the stream
     */
    public static void deserialize(final DataInputStream in, final int[] data) throws IOException {
        final int size = in.readInt();

        for (int i = 0; i < size; ++i) {
            data[i] = in.readInt();
        }
    }

    /**
     * Serialize an array of long values
     * 
     * @param out   stream to write to
     * @param data  data to serialize
     * @throws java.io.IOException if the data cannot be written to the stream
     */
    public static void serialize(final DataOutputStream out, final long[] data) throws IOException {
        out.writeInt(data.length);
        for (int i = 0; i < data.length; ++i) {
            out.writeLong(data[i]);
        }
    }

    /**
     * Deserialize an array of long values
     * 
     * @param in    stream to read from
     * @param data  target of the deserialization
     * @throws java.io.IOException if the data cannot be read from the stream
     */
    public static void deserialize(final DataInputStream in, final long[] data) throws IOException {
        final int size = in.readInt();

        for (int i = 0; i < size; ++i) {
            data[i] = in.readLong();
        }
    }

    /**
     * Serialize an array of boolean values
     * 
     * @param out   stream to write to
     * @param data  data to serialize
     * @throws java.io.IOException if the data cannot be written to the stream
     */
    public static void serialize(final DataOutputStream out, final boolean[] data) throws IOException {
        out.writeInt(data.length);
        for (int i = 0; i < data.length; ++i) {
            out.writeBoolean(data[i]);
        }
    }

    /**
     * Deserialize an array of boolean values
     * 
     * @param in    stream to read from
     * @param data  target of the deserialization
     * @throws java.io.IOException if the data cannot be read from the stream
     */
    public static void deserialize(final DataInputStream in, final boolean[] data) throws IOException {
        final int size = in.readInt();

        for (int i = 0; i < size; ++i) {
            data[i] = in.readBoolean();
        }
    }

    /**
     * Serialize an array of strings
     * 
     * @param out   stream to write to
     * @param data  data to serialize
     * @throws java.io.IOException if the data cannot be written to the stream
     */
    public static void serialize(final DataOutputStream out, final String[] data) throws IOException {
        out.writeInt(data.length);
        for (int i = 0; i < data.length; ++i) {
            out.writeUTF(data[i]);
        }
    }

    /**
     * Deserialize an array of strings
     * 
     * @param in    stream to read from
     * @param data  target of the deserialization
     * @throws java.io.IOException if the data cannot be read from the stream
     */
    public static void deserialize(final DataInputStream in, final String[] data) throws IOException {
        final int size = in.readInt();

        for (int i = 0; i < size; ++i) {
            data[i] = in.readUTF();
        }
    }

    /**
     * Serialize an array of serializable objects
     * 
     * @param out   stream to write to
     * @param data  data to serialize
     * @throws java.io.IOException if the data cannot be written to the stream
     */
    public static void serialize(final DataOutputStream out, final Serializable[] data) throws IOException {
        out.writeInt(data.length);
        for (int i = 0; i < data.length; ++i) {
            data[i].serialize(out);
        }
    }

    /**
     * Deserialize an array of serializable objects
     * 
     * @param in    stream to read from
     * @param data  target of the deserialization
     * @throws java.io.IOException if the data cannot be read from the stream
     */
    public static void deserialize(final DataInputStream in, final Serializable[] data) throws IOException {
        final int size = in.readInt();

        for (int i = 0; i < size; ++i) {
            data[i].deserialize(in);
        }
    }
}
