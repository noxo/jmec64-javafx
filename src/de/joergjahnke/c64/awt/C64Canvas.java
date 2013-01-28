/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.awt;

import de.joergjahnke.common.awt.OptionPane;
import de.joergjahnke.c64.core.C64;
import de.joergjahnke.c64.core.Joystick;
import de.joergjahnke.c64.core.VIC6569;
import de.joergjahnke.common.util.Observer;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.util.Hashtable;

/**
 * The actual Swing canvas that shows the C64 Screen.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class C64Canvas extends Canvas implements Observer, KeyListener, ComponentListener {
    // maps key codes from key events to keys of the C64
    private final static Hashtable keycodeKeyMap = new Hashtable();

    static {
        keycodeKeyMap.put(new Integer(KeyEvent.VK_BACK_SPACE), "BACKSPACE");
        keycodeKeyMap.put(new Integer(KeyEvent.VK_ESCAPE), "ARROW_LEFT");
        keycodeKeyMap.put(new Integer(KeyEvent.VK_SHIFT), "SHIFT");
        keycodeKeyMap.put(new Integer(KeyEvent.VK_ENTER), "ENTER");
        keycodeKeyMap.put(new Integer(KeyEvent.VK_HOME), "HOME");
        keycodeKeyMap.put(new Integer(KeyEvent.VK_F1), "F1");
        keycodeKeyMap.put(new Integer(KeyEvent.VK_F3), "F3");
        keycodeKeyMap.put(new Integer(KeyEvent.VK_F5), "F5");
        keycodeKeyMap.put(new Integer(KeyEvent.VK_F7), "F7");
    }
    // maps key codes from key events to joystick movements of the C64
    private final static Hashtable keycodeJoystickMap = new Hashtable();

    static {
        keycodeJoystickMap.put(new Integer(KeyEvent.VK_LEFT), new Integer(Joystick.LEFT));
        keycodeJoystickMap.put(new Integer(KeyEvent.VK_RIGHT), new Integer(Joystick.RIGHT));
        keycodeJoystickMap.put(new Integer(KeyEvent.VK_UP), new Integer(Joystick.UP));
        keycodeJoystickMap.put(new Integer(KeyEvent.VK_DOWN), new Integer(Joystick.DOWN));
        keycodeJoystickMap.put(new Integer(KeyEvent.VK_F12), new Integer(Joystick.FIRE));
    }
    // frame where this canvas is displayed
    private final Frame frame;
    // image with C64 screen to display
    private BufferedImage screenImage;
    // C64-screen to display in the canvas
    private C64 c64;
    // position to paint the screen
    private int sx,  sy;
    // size of painted screen
    private int swidth,  sheight;
    // preferred size
    private Dimension preferredSize;
    // time when the canvas was created, used to determine whether to display the logo
    private final long startedOn = new java.util.Date().getTime();
    // border color
    private Color borderColor = new Color(0),  lastBorderColor = borderColor;

    /**
     * Create a new C64 canvas.
     * The canvas has no C64 screen attached. It contains some basic menu items.
     */
    public C64Canvas(final Frame frame) {
        this.frame = frame;
        addComponentListener(this);
        addKeyListener(this);
        setFocusable(true);
    }

    /**
     * We'd like to have the size of the C64 Screen
     */
    public Dimension getPreferredSize() {
        return this.preferredSize;
    }

    public void setPreferredSize(final Dimension size) {
        // the original C64 size is the minimum
        final VIC6569 vic = this.c64.getVIC();

        if (size.getHeight() < vic.getBorderHeight()) {
            size.width = vic.getBorderWidth();
            size.height = vic.getBorderHeight();
        }

        // set new size
        this.preferredSize = size;
    }

    /**
     * Scale the screen size with a given factor
     *
     * @param   scaling scaling factor, 1 equals 320x200 pixels i.e. the C64's screen size
     */
    public void setScaling(final int scaling) {
        // set component size to fit new desired C64 screen size
        final VIC6569 vic = this.c64.getVIC();
        final Dimension newSize = new Dimension(vic.getBorderWidth() * scaling, vic.getBorderHeight() * scaling);

        setPreferredSize(newSize);
        setSize(getPreferredSize());

        // determine screen rectangle
        this.swidth = vic.getBorderWidth() * scaling;
        this.sheight = vic.getBorderHeight() * scaling;
        this.sx = 0;
        this.sy = 0;
    }

    /**
     * Get the instance being displayed
     */
    public final C64 getC64() {
        return this.c64;
    }

    /**
     * Set the instance being displayed
     */
    public void setC64(final C64 c64) {
        // store instance
        this.c64 = c64;

        // register as observer for screen refresh
        final VIC6569 vic = c64.getVIC();

        vic.addObserver(this);

        // initiaize screen settings depending on size of the display
        vic.initScreenMemory();

        // create image where the C64 screen content is copied to
        this.screenImage = new BufferedImage(vic.getBorderWidth(), vic.getBorderHeight(), BufferedImage.TYPE_INT_ARGB);

        // set the preferred size
        setScaling(1);
    }

    /**
     * Displays an alert message
     *
     * @param   title   message box title
     * @param   message full message
     */
    public void showMessage(final String title, final String message, final int type) {
        OptionPane.showMessageDialog(null, message, title, type);
    }

    /**
     * Repaint the screen.
     * First the background is cleared. Then status messages are displayed and
     * lastly the contents of the C64 screen are painted.
     *
     * @param   g   graphics context to use
     */
    public void paint(final Graphics g) {
        final long time = System.currentTimeMillis();

        if (this.c64 != null) {
            final VIC6569 vic = this.c64.getVIC();

            this.screenImage.setRGB(0, 0, vic.getBorderWidth(), vic.getBorderHeight(), vic.getRGBData(), 0, vic.getBorderWidth());
            g.drawImage(this.screenImage, this.sx, this.sy, this.swidth, this.sheight, this);
        }

        if (this.c64.getLogger() != null && this.c64.getLogger().isVerbose()) {
            this.c64.getLogger().info("Repaint took " + (System.currentTimeMillis() - time));
        }
    }

    /**
     * Directly call paint to avoid clearing the screen
     */
    public void update(final Graphics g) {
        paint(g);
    }

    /**
     * Determine key that was pressed or released
     *
     * @param   event   KeyEvent holding the information about the key that was modified
     * @return  key definition string that can be passed to Keyboard.keyPressed
     *          or Keyboard.keyReleased or a joystick movement
     * @see de.joergjahnke.jac.Keyboard#keyPressed
     * @see de.joergjahnke.jac.Keyboard#keyReleased
     */
    private Object getKeySelection(final KeyEvent event) {
        final int keyCode = event.getKeyCode();
        // determine key that was triggered
        Object result = null;

        if (keycodeKeyMap.containsKey(new Integer(keyCode))) {
            result = keycodeKeyMap.get(new Integer(keyCode)).toString();
        } else if (keycodeJoystickMap.containsKey(new Integer(keyCode))) {
            result = keycodeJoystickMap.get(new Integer(keyCode));
        } else if (keyCode == KeyEvent.CHAR_UNDEFINED && event.isShiftDown()) {
            result = "SHIFT";
        } else if (keyCode == KeyEvent.VK_C && event.isControlDown()) {
            result = "BREAK";
        } else if (keyCode == KeyEvent.VK_R && event.isControlDown()) {
            result = "RUN";
        }

        return result;
    }

    // implementation of the ComponentListener interface
    /**
     * Change the screen size so that we always have a 16:10 ratio like the original C64
     */
    public void componentResized(final ComponentEvent event) {
        frame.pack();
    }

    public void componentMoved(final ComponentEvent event) {
    }

    public void componentShown(final ComponentEvent event) {
    }

    public void componentHidden(final ComponentEvent event) {
    }

    // implementation of the KeyListener interface
    /**
     * Convert key event to key selection suitable for the Keyboard class and pass it to that class
     */
    public void keyPressed(final KeyEvent event) {
        final Object key = getKeySelection(event);

        if (null != key) {
            if (key instanceof String) {
                this.c64.getKeyboard().keyPressed((String) key);
            } else {
                final Joystick joystick = this.c64.getJoystick(this.c64.getActiveJoystick());
                final int value = ((Integer) key).intValue();

                joystick.setDirection(joystick.getDirection() | (value & Joystick.DIRECTIONS));
                if ((value & Joystick.FIRE) != 0) {
                    joystick.setFiring(true);
                }
            }
        } else {
            if (event.getKeyChar() != KeyEvent.CHAR_UNDEFINED && event.getModifiers() == 0) {
                this.c64.getKeyboard().keyPressed(Character.toString(event.getKeyChar()));
            }
        }
    }

    /**
     * Convert key event to key selection suitable for the Keyboard class and pass it to that class
     */
    public void keyReleased(final KeyEvent event) {
        final Object key = getKeySelection(event);

        if (null != key) {
            if (key instanceof String) {
                this.c64.getKeyboard().keyReleased((String) key);
            } else {
                final Joystick joystick = this.c64.getJoystick(this.c64.getActiveJoystick());
                final int value = ((Integer) key).intValue();

                joystick.setDirection(joystick.getDirection() & ~(value & Joystick.DIRECTIONS));
                if ((value & Joystick.FIRE) != 0) {
                    joystick.setFiring(false);
                }
            }
        } else {
            if (event.getKeyChar() != KeyEvent.CHAR_UNDEFINED && event.getModifiers() == 0) {
                this.c64.getKeyboard().keyReleased(Character.toString(event.getKeyChar()));
            }
        }
    }

    /**
     * Pass on typed keys to the emulator keyboard's textTyped method
     */
    public void keyTyped(final KeyEvent event) {
        if (event.getKeyChar() != KeyEvent.CHAR_UNDEFINED && event.getModifiers() == 0) {
        // this has already been processed by keyPressed and keyReleased
        } else if (!event.isAltDown() && !event.isControlDown()) {
            this.c64.getKeyboard().textTyped(Character.toString(event.getKeyChar()));
        }
    }

    // implementation of the Observer interface
    /**
     * Initialize screen update
     */
    public final void update(final Object observable, final Object event) {
        // the notification comes from a VIC6569?
        if (observable instanceof VIC6569) {
            // the border color was changed?
            if (event instanceof de.joergjahnke.common.ui.Color) {
            // ignore this as this is covered in the normal screen paint operations
            } else {
                // repaint the screen;
                repaint();
            }
        }
    }
}
