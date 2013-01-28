/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

/**
 * Part implementation for an on-screen joystick that can be used via the mouse or a pointer device.
 * This joystick emulation is supposed to be a small image containing 8 fields for the joystick
 * directions and one field in the center for the joystick button. The 8 fields are further separated
 * in a part indicating that the joystick is e.g. moved upwards and a part that also includes the fire
 * button i.e. the joystick is moved upwards <b>and</b> the fire-button is pressed.<br>
 * <br>
 * As creating and displaying cannot (at least not easily) be done in a platform- (J2ME vs. J2SE)
 * independant manner, this class only offers some utility methods but does not take over displaying the
 * on-screen joystick. Sub-classes need to implement the rest.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public abstract class AbstractVirtualJoystick {

    /**
     * Translate a click into the on-screen joystick image into the value to pass to the C64
     * joystick class
     * 
     * @param   x   x-coordinate of the selected point in the image
     * @param   y   y-coordinate of the selected point in the image
     * @return  subset of Joystick.DIRECTIONS | Joystick.FIRING
     */
    public int getValue(final int x, final int y) {
        int result = 0;

        // we must be within the boundary of the joystick image
        if (x >= 0 && x < getWidth() && y >= 0 && y < getHeight()) {
            // roughly the left third of the image is for moving the joystick left
            // while the right third is for moving it right. The outer third of these
            // parts again denotes the area where also the joystick button is pressed.
            if (x < getWidth() / 3) {
                result |= Joystick.LEFT;
                if (x < getWidth() / 9) {
                    result |= Joystick.FIRE;
                }
            } else if (x > getWidth() * 2 / 3) {
                result |= Joystick.RIGHT;
                if (x > getWidth() * 8 / 9) {
                    result |= Joystick.FIRE;
                }
            }
            // roughly the upper third of the image is for moving the joystick up
            // while the lower third is for moving it down. The outer third of these
            // parts again denotes the area where also the joystick button is pressed.
            if (y < getHeight() / 3) {
                result |= Joystick.UP;
                if (y < getHeight() / 9) {
                    result |= Joystick.FIRE;
                }
            } else if (y > getWidth() * 2 / 3) {
                result |= Joystick.DOWN;
                if (y > getHeight() * 8 / 9) {
                    result |= Joystick.FIRE;
                }
            }
            // check whether the button in the middle of the image was pressed
            // first we check whether a point the in middle square was pressed
            if (x > getWidth() / 3 && x < getWidth() * 2 / 3 && y > getHeight() / 3 && y < getHeight() * 2 / 3) {
                // the button in the middle is a circle, we check if only the colorized part was clicked
                final int middleX = getWidth() / 2;
                final int middleY = getHeight() / 2;
                final int diffX = x - middleX;
                final int diffY = y - middleY;
                final int radius = (int) (getWidth() / 3 / 2 * 0.75);

                if (diffX * diffX + diffY * diffY < radius * radius) {
                    result |= Joystick.FIRE;
                }
            }
        }

        return result;
    }

    // abstract methods to be implemented by subclasses
    /**
     * Get the width of the on-screen image
     * 
     * @return  width in pixels
     */
    public abstract int getWidth();

    /**
     * Get the height of the on-screen image
     * 
     * @return  height in pixels
     */
    public abstract int getHeight();
}
