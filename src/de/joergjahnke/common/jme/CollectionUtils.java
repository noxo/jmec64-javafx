/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.jme;


import java.util.Enumeration;
import java.util.Hashtable;


/**
 * Utility classes for Java collection classes like Hashtable and Vector
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class CollectionUtils {
    /**
     * Adds all elements of one map to the content of another map
     * 
     * @param   map map to extend
     * @param   add map containing the new elements
     */
    public static void putAll( final Hashtable map, final Hashtable add ) {
        for( Enumeration en = add.keys() ; en.hasMoreElements() ; ) {
            final Object key = en.nextElement();

            map.put( key, add.get( key ) );
        }
    }
    
    /**
     * Removes all elements of one map from the content of another map
     * 
     * @param   map map to shrink
     * @param   remove  map containing the keys to remove
     */
    public static void removeAll( final Hashtable map, final Hashtable remove ) {
        for( Enumeration en = remove.keys() ; en.hasMoreElements() ; ) {
            map.remove( en.nextElement() );
        }
    }
}
