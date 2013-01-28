/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.vmabstraction.sunvm;

import de.joergjahnke.common.vmabstraction.ResourceLoader;
import java.io.InputStream;

/**
 * Implementation of the ResourceLoader interface for the Sun Java VMs
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class SunVMResourceLoader implements ResourceLoader {

    public InputStream getResource(final String name) {
        return getClass().getResourceAsStream(name);
    }
}
