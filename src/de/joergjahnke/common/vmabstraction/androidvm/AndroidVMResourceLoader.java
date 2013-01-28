/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.vmabstraction.androidvm;

import de.joergjahnke.common.vmabstraction.ResourceLoader;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the ResourceLoader interface for the Android Java VMs
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class AndroidVMResourceLoader implements ResourceLoader {

    /**
     * Here we store all added resources and retrieve them later via the getResource method
     */
    private final Map<String, InputStream> resourcesMap = new HashMap<String, InputStream>();

    /**
     * Add a new resource to the list of known resources
     * 
     * @param name	name of the resource
     * @param content	stream with resource content
     */
    public void addResource(final String name, final InputStream content) {
        this.resourcesMap.put(name, content);
    }

    public InputStream getResource(final String name) {
        return this.resourcesMap.get(name);
    }
}
