/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.vmabstraction;

import java.io.InputStream;

/**
 * Interface for ResourceLoaders which load resource data for a given VM implementation
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public interface ResourceLoader {

    /**
     * Retrieve a resource with a given name
     *
     * @param   name    name of the resource to load
     * @return  stream with resource data
     */
    InputStream getResource(final String name);
}
