/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.jme;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreFullException;
import javax.microedition.rms.RecordStoreNotFoundException;
import javax.microedition.rms.RecordStoreNotOpenException;

/**
 * Persistently stores settings for a MIDP application
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class Settings {

    // RecordStore which stores the data on the mobile device
    final RecordStore db;
    // map of settings hashcodes to record ids in the storage
    final Hashtable nameRecordIDMap = new Hashtable();

    /**
     * Creates a new instance of Settings
     *
     * @param   dbName  name of the internal storage to access
     * @throws RecordStoreException, RecordStoreFullException or RecordStoreNotFoundException if the settings cannot be read
     */
    public Settings(final String dbName) throws RecordStoreException, RecordStoreFullException, RecordStoreNotFoundException {
        // open record store
        this.db = RecordStore.openRecordStore(dbName, true);

        // read all keys
        final Vector ids = new Vector();

        for (final RecordEnumeration en = this.db.enumerateRecords(null, null, false); en.hasNextElement();) {
            ids.addElement(new Integer(en.nextRecordId()));
        }

        for (int i = 0; i < ids.size(); ++i) {
            final int id = ((Integer) ids.elementAt(i)).intValue();
            final byte[] bytes = this.db.getRecord(id);
            final int hashCode = toInt(bytes);

            this.nameRecordIDMap.put(new Integer(hashCode), new Integer(id));
        }
    }

    /**
     * Close the settings object and release resources.
     * The object may no longer be used afterwards.
     *
     * @throws RecordStoreNotOpenException or RecordStoreException if the underlying record store cannot be closed
     */
    public void close() throws RecordStoreNotOpenException, RecordStoreException {
        this.db.closeRecordStore();
    }

    /**
     * Get the integer denoted by this key
     *
     * @param   key key to access inside the storage
     * @return  integer value associated to the given key
     * @throws  IllegalArgumentException if the key does not exist
     */
    public int getInteger(final String key) {
        return toInt(getRecordRawData(key));
    }

    /**
     * Get the integer value denoted by this key
     *
     * @param   key key to access inside the storage
     * @param   defaultValue    default to return if the key does not exist
     * @return  integer value associated to the given key or the default value if the key does not exist
     */
    public int getInteger(final String key, final int defaultValue) {
        try {
            return getInteger(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Store integer value
     *
     * @param   key key to access the data
     * @param   value   value to store
     * @throws  RecordStoreNotOpenException, RecordStoreException or RecordStoreFullException if the data cannot be written to the underlying record store
     */
    public void setInteger(final String key, final int value) throws RecordStoreNotOpenException, RecordStoreException, RecordStoreFullException {
        setRecordRawData(key, toBytes(value));
    }

    /**
     * Get the boolean value denoted by this key
     *
     * @param   key key to access inside the storage
     * @return  boolean value associated to the given key
     * @throws  IllegalArgumentException if the key does not exist
     */
    public boolean getBoolean(final String key) {
        return getRecordRawData(key)[0] == 1;
    }

    /**
     * Get the boolean value denoted by this key
     *
     * @param   key key to access inside the storage
     * @param   defaultValue    default to return if the key does not exist
     * @return  boolean value associated to the given key or the default value if the key does not exist
     */
    public boolean getBoolean(final String key, final boolean defaultValue) {
        try {
            return getBoolean(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Store boolean value
     *
     * @param   key key to access the data
     * @param   value   value to store
     * @throws  RecordStoreNotOpenException, RecordStoreException or RecordStoreFullException if the data cannot be written to the underlying record store
     */
    public void setBoolean(final String key, final boolean value) throws RecordStoreNotOpenException, RecordStoreException, RecordStoreFullException {
        byte[] bytes = new byte[1];

        bytes[0] = (byte) (value ? 1 : 0);
        setRecordRawData(key, bytes);
    }

    /**
     * Get the string denoted by this key
     *
     * @param   key key to access inside the storage
     * @return  string value associated to the given key
     * @throws  IllegalArgumentException if the key does not exist
     */
    public String getString(final String key) {
        return new String(getRecordRawData(key));
    }

    /**
     * Get the string denoted by this key
     *
     * @param   key key to access inside the storage
     * @param   defaultValue    default to return if the key does not exist
     * @return  string value associated to the given key or the default value if the key does not exist
     */
    public String getString(final String key, final String defaultValue) {
        try {
            return getString(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Store string value
     *
     * @param   key key to access the data
     * @param   value   value to store
     * @throws  RecordStoreNotOpenException, RecordStoreException or RecordStoreFullException if the data cannot be written to the underlying record store
     */
    public void setString(final String key, final String value) throws RecordStoreNotOpenException, RecordStoreException, RecordStoreFullException {
        setRecordRawData(key, value.getBytes());
    }

    /**
     * Check whether a key exists in the storage
     *
     * @param   key key to check
     * @return  true if the key exists, otherwise false
     */
    public boolean exists(final String key) {
        return this.nameRecordIDMap.containsKey(new Integer(key.hashCode()));
    }

    /**
     * Remove a key from the storage
     *
     * @param   key key to remove from storage
     */
    public void remove(final String key) {
        final Integer hashCode = new Integer(key.hashCode());
        final Integer recordId = (Integer) this.nameRecordIDMap.get(hashCode);

        try {
            this.db.deleteRecord(recordId.intValue());
            this.nameRecordIDMap.remove(hashCode);
        } catch (Exception e) {
            throw new IllegalArgumentException("Key '" + key + "' could not be found in storage!\n The original exception and message was " + e.getClass() + ": " + e.getMessage());
        }
    }

    /**
     * Create an integer value from an array of four bytes
     */
    private final int toInt(final byte[] bytes) {
        int result = 0;

        for (int i = 0; i < 4; ++i) {
            result <<= 8;
            result += bytes[i] >= 0 ? bytes[i] : (int) 256 + bytes[i];
        }

        return result;
    }

    /**
     * Convert an integer value to a byte array
     */
    private final byte[] toBytes(final int value) {
        byte[] bytes = new byte[4];

        bytes[0] = (byte) (value >>> 24);
        bytes[1] = (byte) (value >> 16);
        bytes[2] = (byte) (value >> 8);
        bytes[3] = (byte) (value);

        return bytes;
    }

    /**
     * Get raw record data for a given key
     *
     * @param   key name of the key to search
     * @return  bytes denoted by this key
     * @throws  IllegalArgumentException if the key does not exist
     */
    private byte[] getRecordRawData(final String key) {
        final Integer recordId = (Integer) this.nameRecordIDMap.get(new Integer(key.hashCode()));

        try {
            // get the bytes for the record denoted by the key and return everything except for the key contained in the bytes
            final byte[] raw = this.db.getRecord(recordId.intValue());
            final byte[] bytes = new byte[raw.length - 4];

            for (int i = 0; i < bytes.length; ++i) {
                bytes[i] = raw[i + 4];
            }

            return bytes;
        } catch (Exception e) {
            throw new IllegalArgumentException("Key '" + key + "' could not be read from storage!\n The original exception and message was " + e.getClass() + ": " + e.getMessage());
        }
    }

    /**
     * Store raw record data
     *
     * @param   name of the key to store
     * @param   bytes   data to store
     */
    private void setRecordRawData(final String key, final byte[] bytes) throws RecordStoreNotOpenException, RecordStoreException, RecordStoreFullException {
        // create byte array to be stored, which contains the hashCode of the given key plus the value to be stored
        final byte[] raw = new byte[bytes.length + 4];
        final int hashCode = key.hashCode();

        raw[0] = (byte) (hashCode >>> 24);
        raw[1] = (byte) (hashCode >> 16);
        raw[2] = (byte) (hashCode >> 8);
        raw[3] = (byte) (hashCode);

        for (int i = 0; i < bytes.length; ++i) {
            raw[i + 4] = bytes[i];
        }

        // store data in RecordStore
        if (this.nameRecordIDMap.containsKey(new Integer(hashCode))) {
            this.db.setRecord(((Integer) this.nameRecordIDMap.get(new Integer(hashCode))).intValue(), raw, 0, raw.length);
        } else {
            final int id = this.db.addRecord(raw, 0, raw.length);

            this.nameRecordIDMap.put(new Integer(hashCode), new Integer(id));
        }
    }
}
