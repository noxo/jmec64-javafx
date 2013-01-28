/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.swing;

import de.joergjahnke.c64.core.C64;
import de.joergjahnke.c64.core.VIC6569;
import de.joergjahnke.common.util.Observer;
import de.joergjahnke.c64.core.Joystick;
import de.joergjahnke.c64.core.Keyboard;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Hashtable;
import javax.swing.JPanel;

/**
 * The actual Swing canvas that shows the C64 Screen.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class C64Canvas extends JPanel implements Observer, KeyListener, MouseListener {

    /**
     * Denotes that the mouse is not used
     */
    public final static int MOUSE_NO_USAGE = 0;
    /**
     * Denotes that the mouse button is used to emulate the joystick button
     */
    public final static int MOUSE_AS_FIRE_BUTTON = 1;
    /**
     * Denotes that a virtual joystick is displayed that can be controlled via the mouse
     */
    public final static int MOUSE_FOR_VIRTUAL_JOYSTICK = 2;
    // maps key codes from key events to keys of the C64
    private final static Hashtable keycodeKeyMap = new Hashtable();


    static {
        keycodeKeyMap.put(Integer.valueOf(KeyEvent.VK_BACK_SPACE), "BACKSPACE");
        keycodeKeyMap.put(Integer.valueOf(KeyEvent.VK_ESCAPE), "ARROW_LEFT");
        keycodeKeyMap.put(Integer.valueOf(KeyEvent.VK_SHIFT), "SHIFT");
        keycodeKeyMap.put(Integer.valueOf(KeyEvent.VK_ENTER), "ENTER");
        keycodeKeyMap.put(Integer.valueOf(KeyEvent.VK_HOME), "HOME");
        keycodeKeyMap.put(Integer.valueOf(KeyEvent.VK_F1), "F1");
        keycodeKeyMap.put(Integer.valueOf(KeyEvent.VK_F3), "F3");
        keycodeKeyMap.put(Integer.valueOf(KeyEvent.VK_F5), "F5");
        keycodeKeyMap.put(Integer.valueOf(KeyEvent.VK_F7), "F7");
        keycodeKeyMap.put(Integer.valueOf(KeyEvent.VK_LEFT), "LEFT");
        keycodeKeyMap.put(Integer.valueOf(KeyEvent.VK_RIGHT), "RIGHT");
        keycodeKeyMap.put(Integer.valueOf(KeyEvent.VK_UP), "UP");
        keycodeKeyMap.put(Integer.valueOf(KeyEvent.VK_DOWN), "DOWN");
    }
    // maps key codes from key events to joystick movements of the C64
    private final static Hashtable keycodeJoystickMap = new Hashtable();


    static {
        keycodeJoystickMap.put(Integer.valueOf(KeyEvent.VK_LEFT), Integer.valueOf(Joystick.LEFT));
        keycodeJoystickMap.put(Integer.valueOf(KeyEvent.VK_RIGHT), Integer.valueOf(Joystick.RIGHT));
        keycodeJoystickMap.put(Integer.valueOf(KeyEvent.VK_UP), Integer.valueOf(Joystick.UP));
        keycodeJoystickMap.put(Integer.valueOf(KeyEvent.VK_DOWN), Integer.valueOf(Joystick.DOWN));
        keycodeJoystickMap.put(Integer.valueOf(KeyEvent.VK_F12), Integer.valueOf(Joystick.FIRE));
    }
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
    // border color
    private final Color borderColor = new Color(0);
    // class handling the virtual joystick
    private VirtualJoystick virtualJoystick;
    // location of joypad image on the screen
    private int joypadX,  joypadY;
    // denotes the mouse usage
    private int mouseUsage = MOUSE_AS_FIRE_BUTTON;

    /**
     * Create a new C64 canvas.
     * The canvas has no C64 screen attached. It contains some basic menu items.
     */
    public C64Canvas() {
        addKeyListener(this);
        addMouseListener(this);
        setFocusable(true);

        try {
            this.virtualJoystick = new VirtualJoystick();
        } catch (IOException e) {
            // image could not be loaded, this can be ignored, we can work without the on-screen joystick
        }
    }

    /**
     * We'd like to have the size of the C64 Screen
     */
    @Override
    public Dimension getPreferredSize() {
        return this.preferredSize;
    }

    @Override
    public void setPreferredSize(final Dimension size) {
        // the original C64 size is the minimum
        final VIC6569 vic = this.c64.getVIC();

        if (size.getHeight() < vic.getBorderHeight()) {
            size.width = vic.getBorderWidth();
            size.height = vic.getBorderHeight();
        }

        // set new size
        this.preferredSize = size;

        // set new size and position for the virtual joystick
        this.virtualJoystick.setWidth(size.width / 5);
        this.virtualJoystick.setHeight(size.width / 5);
        this.joypadX = size.width - this.virtualJoystick.getWidth();
        this.joypadY = size.height - this.virtualJoystick.getHeight();
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
    public void setC64(final C64 instance) {
        // store instance
        this.c64 = instance;

        // register as observer for screen refresh
        final VIC6569 vic = instance.getVIC();

        vic.addObserver(this);

        // initiaize screen settings depending on size of the display
        vic.initScreenMemory();

        // create image where the C64 screen content is copied to
        this.screenImage = new BufferedImage(vic.getBorderWidth(), vic.getBorderHeight(), BufferedImage.TYPE_INT_ARGB);

        // set the preferred size
        setScaling(1);
    }

    /**
     * Get the currently defined mouse usage
     * 
     * @return  usage constant, e.g. MOUSE_AS_FIRE_BUTTON
     */
    public int getMouseUsage() {
        return this.mouseUsage;
    }

    /**
     * Set the mouse usage
     * 
     * @param   usage   new mouse usage, e.g. MOUSE_AS_FIRE_BUTTON
     */
    public void setMouseUsage(final int usage) {
        this.mouseUsage = usage;
    }

    /**
     * Repaint the screen.
     * First the background is cleared. Then status messages are displayed and
     * lastly the contents of the C64 screen are painted.
     *
     * @param   g   graphics context to use
     */
    @Override
    public void paint(final Graphics g) {
        if (this.c64 != null) {
            // show border
            final VIC6569 vic = this.c64.getVIC();

            g.setColor(new Color(this.borderColor.getRGB()));
            g.fillRect(0, 0, getWidth(), getHeight());

            // show C64 screen
            this.screenImage.setRGB(0, 0, vic.getBorderWidth(), vic.getBorderHeight(), vic.getRGBData(), 0, vic.getBorderWidth());
            g.drawImage(this.screenImage, this.sx, this.sy, this.swidth, this.sheight, this);
        }

        if (this.mouseUsage == MOUSE_FOR_VIRTUAL_JOYSTICK && null != this.virtualJoystick && this.c64.isReady()) {
            g.drawImage(this.virtualJoystick.getImage(), this.joypadX, this.joypadY, this.virtualJoystick.getWidth(), this.virtualJoystick.getHeight(), this);
        }
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
        Object result = null;

        // convert some special key+modifier combinations
        if (keyCode == KeyEvent.CHAR_UNDEFINED && event.isShiftDown()) {
            result = "SHIFT";
        } else if (keyCode == KeyEvent.VK_C && event.isControlDown()) {
            result = "BREAK";
        } else if (keyCode == KeyEvent.VK_R && event.isControlDown()) {
            result = "RUN";
        // if we have no modifiers then we might use cursor keys for joystick movement
        } else if (event.getModifiers() == 0 && keycodeJoystickMap.containsKey(Integer.valueOf(keyCode))) {
            result = keycodeJoystickMap.get(Integer.valueOf(keyCode));
        // we might have some special keys we need to convert to C64 keys
        } else if (keycodeKeyMap.containsKey(Integer.valueOf(keyCode))) {
            result = keycodeKeyMap.get(Integer.valueOf(keyCode)).toString();
        }

        return result;
    }

    // implementation of the KeyListener interface
    /**
     * Convert key event to key selection suitable for the Keyboard class and pass it to that class
     */
    @Override
    public void keyPressed(final KeyEvent event) {
        // allow pasting from the clipboard
        if (event.getKeyCode() == KeyEvent.VK_V && event.getModifiers() == KeyEvent.CTRL_MASK) {
            final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            final Transferable contents = clipboard.getContents(null);

            if ((contents != null) && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                try {
                    this.c64.getKeyboard().textTyped((String) contents.getTransferData(DataFlavor.stringFlavor));
                } catch (Exception e) {
                    // can't paste from clipboard, that's a pity but we can live with this
                }
            }
        } else {
            // process C64 key events
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
    }

    /**
     * Convert key event to key selection suitable for the Keyboard class and pass it to that class
     */
    @Override
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
    @Override
    public void keyTyped(final KeyEvent event) {
        if (event.getKeyChar() != KeyEvent.CHAR_UNDEFINED && event.getModifiers() == 0) {
            // this has already been processed by keyPressed and keyReleased
        } else if (!event.isAltDown() && !event.isControlDown()) {
            final Keyboard keyboard = this.c64.getKeyboard();
            final String key = Character.toString(event.getKeyChar());

            // some keys can only be reached on the PC keyboard via the shift key but are unshifted on the C64 keyboard
            // do we have such a special case?
            if (event.isShiftDown() && keyboard.hasShiftedVariant(key)) {
                // then temporarily disable the shift key and then type
                keyboard.keyReleased("SHIFT");
                keyboard.textTyped(key);
                keyboard.keyTyped("SHIFT");
            } else {
                // otherwise type normally
                keyboard.textTyped(key);
            }
        }
    }

    // implementation of the Observer interface
    /**
     * Initialize screen update
     */
    @Override
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

    // implementation of the MouseListener interface
    @Override
    public void mouseClicked(final MouseEvent e) {
    }

    @Override
    public void mousePressed(final MouseEvent e) {
        // in case we lost the focus we grab it again
        grabFocus();

        // translate mouse clicks to C64 joystick clicks/movements
        if (e.getButton() == MouseEvent.BUTTON1) {
            switch (this.mouseUsage) {
                case MOUSE_FOR_VIRTUAL_JOYSTICK: {
                    final int value = this.virtualJoystick.getValue(e.getX() - this.joypadX, e.getY() - this.joypadY);
                    final Joystick joystick = this.c64.getJoystick(this.c64.getActiveJoystick());

                    joystick.setDirection(value & Joystick.DIRECTIONS);
                    joystick.setFiring((value & Joystick.FIRE) != 0);
                    break;
                }
                case MOUSE_AS_FIRE_BUTTON:
                    this.c64.getJoystick(this.c64.getActiveJoystick()).setFiring(true);
                    break;
                default:
                    ;
            }
        }
    }

    @Override
    public void mouseReleased(final MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            switch (this.mouseUsage) {
                case MOUSE_FOR_VIRTUAL_JOYSTICK: {
                    Joystick joystick = this.c64.getJoystick(this.c64.getActiveJoystick());

                    joystick.setFiring(false);
                    joystick.setDirection(0);
                    break;
                }
                case MOUSE_AS_FIRE_BUTTON:
                    this.c64.getJoystick(this.c64.getActiveJoystick()).setFiring(false);
                    break;
                default:
                    ;
            }
        }
    }

    @Override
    public void mouseEntered(final MouseEvent e) {
    }

    @Override
    public void mouseExited(final MouseEvent e) {
    }
}

