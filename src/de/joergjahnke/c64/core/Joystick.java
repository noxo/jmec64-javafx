/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.common.io.Serializable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Implements the C64's joystick.<br>
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class Joystick implements Serializable {

    /**
     * Indicates joystick is moved upwards
     */
    public final static int UP = 1;
    /**
     * Indicates joystick is moved downwards
     */
    public final static int DOWN = 2;
    /**
     * Indicates joystick is moved left
     */
    public final static int LEFT = 4;
    /**
     * Indicates joystick is moved right
     */
    public final static int RIGHT = 8;
    /**
     * Indicates the fire button is pressed
     */
    public final static int FIRE = 16;
    /**
     * Combines all joystick directions
     */
    public final static int DIRECTIONS = UP | DOWN | LEFT | RIGHT;
    /**
     * Current direction of the joystick
     */
    private int direction = 0;
    /**
     * Is the joystick firing?
     */
    private boolean isFiring = false;

    /**
     * Get the current joystick direction
     *
     * @return joystick direction value UP, LEFT, RIGHT, DOWN or a combination of these
     */
    public int getDirection() {
        return this.direction;
    }

    /**
     * Set the current joystick direction
     *
     * @param   direction   new joystick direction, e.g. Joystick.UP
     */
    public void setDirection(final int direction) {
        this.direction = direction;
    }

    /**
     * Check whether the joystick fire button is pressed
     *
     * @return true if the joystick fire button is currently pressed
     */
    public boolean isFiring() {
        return this.isFiring;
    }

    /**
     * Set whether the joystick fire button is pressed
     *
     * @param   isFiring    true if the fire button is pressed, otherwise false
     */
    public void setFiring(final boolean isFiring) {
        this.isFiring = isFiring;
    }

    /**
     * Get the value of the joystick port for CIA operations
     *
     * @param joystick port value for CIA operations
     */
    protected int getValue() {
        return 0xff - this.direction - (this.isFiring ? FIRE : 0);
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        out.writeInt(this.direction);
        out.writeBoolean(this.isFiring);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        this.direction = in.readInt();
        this.isFiring = in.readBoolean();
    }
}
