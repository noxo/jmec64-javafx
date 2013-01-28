/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.swing;

import de.joergjahnke.c64.core.AbstractVirtualJoystick;
import java.awt.Image;
import java.io.IOException;
import javax.swing.ImageIcon;

/**
 * Implements the virtual joystick for J2SE devices
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class VirtualJoystick extends AbstractVirtualJoystick {
    // image data for the on-screen joystick
    private final Image image;
    // target image size
    private int width,  height;

    /**
     * Creates a new virtual joystick with the default on-screen joystick image
     * 
     * @throws IOException  if the image cannot be loaded
     */
    public VirtualJoystick() throws IOException {
        this.image = new ImageIcon(getClass().getResource("/res/drawable/joypad.png")).getImage();
    }

    /**
     * Get the on-screen image
     * 
     * @return  image object
     */
    public final Image getImage() {
        return this.image;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    /**
     * Set a new target width for the image
     * 
     * @param   width   new width
     */
    public void setWidth(final int width) {
        this.width = width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    /**
     * Set a new target height for the image
     * 
     * @param   height  new height
     */
    public void setHeight(final int height) {
        this.height = height;
    }
}
