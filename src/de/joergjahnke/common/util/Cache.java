/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.util;


import java.util.Enumeration;


/**
 * Interface for a cache holding a defined number of objects.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public interface Cache {

    /**
     * Get the cache's maximum capacity
     *
     * @return  number of objects that the cache can hold
     */
    int capacity();

    /**
     * Remove all elements from the cache
     */
    void clear();

    /**
     * Check whether a given object is contained inside the cache
     *
     * @param   obj object to search
     * @return  true if the object is inside the cache
     */
    boolean contains(final Object obj);

    /**
     * Check whether an object with a given key is contained in the cache
     *
     * @param   key object key
     * @return  true if the object is inside the cache
     */
    boolean containsKey(final Object key);

    /**
     * Get an enumeration of the objects inside the cache
     *
     * @return  enumeration of objects
     */
    Enumeration elements();

    /**
     * Retrieve an object from the cache
     *
     * @param   key object's key
     * @return  null if the cache does not contain the object identified by the given key, otherwise the cached object
     */
    Object get(final Object key);

    /**
     * Get an enumeration of the keys inside the cache
     *
     * @return  enumeration of keys
     */
    Enumeration keys();

    /**
     * Put an object into the cache
     *
     * @param   key object's key for later retrieval
     * @param   obj object to store
     */
    void put(final Object key, final Object obj);
    
    /**
     * Remove an object from the cache
     * 
     * @param   key key of the object to remove
     */
    void remove(final Object key);

    /**
     * Get the number of objects that are currently stored inside the cache
     *
     * @return  number of objects
     */
    int size();
}
