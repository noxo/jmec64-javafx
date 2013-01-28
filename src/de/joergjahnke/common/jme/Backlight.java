/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.jme;

/**
 * This class offers a method to control the devices backlight.
 * Uses the com.nokia.mid.ui.DeviceControl class which must be available on the device.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class Backlight {
    /**
     * Set the backlight
     *
     * @param level 0 for off, 100 for maximum
     */
    public static void setLevel(final int level) {
        com.nokia.mid.ui.DeviceControl.setLights(0, level);
    }
}
