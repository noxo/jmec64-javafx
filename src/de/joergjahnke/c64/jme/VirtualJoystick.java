/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.jme;

import de.joergjahnke.c64.core.AbstractVirtualJoystick;
import java.io.IOException;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;

/**
 * Implements the virtual joystick for J2ME devices
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class VirtualJoystick extends AbstractVirtualJoystick {
    // image data for the on-screen joystick
    private Sprite image;

    /**
     * Creates a new virtual joystick with the default on-screen joystick image
     * 
     * @throws IOException  if the image cannot be loaded
     */
    public VirtualJoystick() throws IOException {
        this.image = new Sprite(Image.createImage("/res/drawable/joypad_small.png"));
    }

    /**
     * Get the on-screen image
     * 
     * @return  image object
     */
    public Sprite getImage() {
        return this.image;
    }

    public int getWidth() {
        return this.image.getWidth();
    }

    public int getHeight() {
        return this.image.getHeight();
    }
}
