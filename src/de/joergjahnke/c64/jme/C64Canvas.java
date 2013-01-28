/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.jme;

import de.joergjahnke.common.jme.OrientationSensitiveCanvas;
import de.joergjahnke.c64.smalldisplays.SmoothingScalableVIC6569;
import de.joergjahnke.c64.smalldisplays.ScalableVIC6569;
import de.joergjahnke.common.ui.Color;
import de.joergjahnke.common.util.Observer;
import de.joergjahnke.c64.core.C64;
import de.joergjahnke.c64.core.Joystick;
import de.joergjahnke.c64.core.VIC6569;
import de.joergjahnke.common.jme.ButtonAssignmentCanvas;
import de.joergjahnke.common.jme.LocalizationSupport;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;

/**
 * The actual MIDP canvas that shows the C64 Screen.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class C64Canvas extends OrientationSensitiveCanvas implements Observer {

    /**
     * Denotes that the pointer is not used
     */
    private final static int POINTER_NO_USAGE = 0;
    /**
     * Denotes that the pointer is used to emulate the joystick button
     */
    private final static int POINTER_AS_FIRE_BUTTON = 1;
    /**
     * Denotes that a virtual joystick is displayed that can be controlled via the pointer
     */
    private final static int POINTER_FOR_VIRTUAL_JOYSTICK = 2;
    /**
     * Denotes that the pointer should be used to emulate the joystick stick
     */
    private final static int POINTER_FOR_JOYSTICK_MOVEMENT = 3;
    /**
     * the minimum percentage in relation to the screen size for accepting a pointer drag event as pointer movement
     */
    private final static int MIN_PERCENTAGE_POINTER_MOVEMENT = 10;
    /**
     * Setting name for frame-skip rate
     */
    protected final static String SETTING_FRAMESKIP = "FrameSkip";
    /**
     * Setting name for smooth scaling
     */
    protected final static String SETTING_SMOOTH_SCALING = "SmoothScaling";
    /**
     * Setting name for pointer usage
     */
    protected final static String SETTING_POINTER_USAGE = "PointerUsage";
    /**
     * Setting name for phone keyboard
     */
    protected final static String SETTING_PHONE_KEYBOARD = "PhoneKeyboard";
    /**
     * Setting name for the drive mode
     */
    protected final static String SETTING_DRIVEMODE = "DriveMode";
    /**
     * number of milliseconds when we automatically release a pressed key if keyRelease is not fired for the key
     */
    private final static long AUTOMATIC_KEY_RELEASE_TIME = 200;
    /**
     * Midlet the canvas belongs to
     */
    protected final MEC64MIDlet midlet;
    /**
     * C64-screen to display in the canvas
     */
    protected C64 c64;
    /**
     * do we display emulator errors? We do this only once until a reset
     */
    protected boolean showEmulatorExceptions = true;
    /**
     * x-coordinate where to paint the screen
     */
    private int x;
    /**
     * x-coordinate where to paint the screen
     */
    private int y;
    /**
     * x-coordinate where to start painting the border
     */
    private int bx;
    /**
     * y-coordinate where to start painting the border
     */
    private int by;
    /**
     * offset to start printing of C64Screen RGB data
     */
    private int offset;
    /**
     * we pre-calculate the offset position inside the image to paint (in case of rotated screen)
     */
    private int ox,  oy;
    /**
     * J64 logo
     */
    private Sprite logo;
    /**
     * border color
     */
    private Color borderColor = new Color(0);
    /**
     * do we have a phone keyboard and not a full keyboard
     */
    protected boolean isPhoneKeyboard;
    /**
     * we pre-calculate the dimensions to paint
     */
    private int paintWidth,  paintHeight;
    /**
     * class handling the virtual joystick
     */
    private VirtualJoystick virtualJoystick;
    /**
     * defines how the pointer is used, default is to use pointer clicks as joystick clicks
     */
    private int pointerUsage = POINTER_NO_USAGE;
    /**
     * cache the Graphics object for better performance
     */
    private final Graphics graphics;
    /**
     * pointer starting position, used when the pointer is dragged
     */
    private int pStartX = -1,  pStartY = -1;
    /**
     * if we have a custom button assignment then this value is not null
     */
    private Hashtable buttonAssignments = new Hashtable();
    /**
     * we use these timers to automatically release buttons after some time
     */
    private Hashtable buttonReleaseTimers = new Hashtable();

    /**
     * Create a new C64 canvas
     *
     * @param   midlet  MIDlet this canvas is displayed in
     */
    public C64Canvas(final MEC64MIDlet midlet) {
        super(midlet);

        this.midlet = midlet;

        // load some settings
        this.pointerUsage = this.midlet.getSettings().getInteger(SETTING_POINTER_USAGE, 1);
        this.isPhoneKeyboard = this.midlet.getSettings().getBoolean(SETTING_PHONE_KEYBOARD, true);

        // switch to full screen mode, we need as much of the screen as possible
        setFullScreenMode(true);

        // load logo
        try {
            this.logo = new Sprite(Image.createImage("/res/jme/J64.png"));
            this.logo.defineReferencePixel(this.logo.getWidth() / 2, this.logo.getHeight() / 2);
            this.logo.setRefPixelPosition(getWidth() / 2, getHeight() / 2);
        } catch (IOException e) {
            // image could not be copied, this can be ignored
        }

        // load on-screen joystick image if a pointer can be used
        if (hasPointerEvents() || hasPointerMotionEvents()) {
            try {
                this.virtualJoystick = new VirtualJoystick();
            } catch (IOException e) {
                // image could not be loaded, this can be ignored, we can work without the on-screen joystick
            }
        }

        // cache the graphics object for better performance
        this.graphics = getGraphics();
    }

    /**
     * Get the C64 instance being displayed
     *
     * @return the C64 instance shown in this canvas
     */
    public final C64 getC64() {
        return this.c64;
    }

    /**
     * Set the C64 instance being displayed
     *
     * @param   c64 the C64 instance to be shown in this canvas
     */
    public void setC64(final C64 c64) {
        // store instance
        this.c64 = c64;

        // set the drive mode
        try {
            final int level = this.midlet.getSettings().getInteger(SETTING_DRIVEMODE);

            for (int i = 0; i < this.c64.getDriveCount(); ++i) {
                c64.getDrive(i).setEmulationLevel(level);
            }
        } catch (Exception e) {
        }
    }

    /**
     * Get the currently defined pointer usage
     * 
     * @return  usage constant, e.g. POINTER_AS_FIRE_BUTTON
     */
    public int getPointerUsage() {
        return this.pointerUsage;
    }

    /**
     * Set the pointer usage
     * 
     * @param   usage   new pointer usage, e.g. POINTER_AS_FIRE_BUTTON
     */
    public void setPointerUsage(final int usage) {
        this.pointerUsage = usage;
        try {
            this.midlet.getSettings().setInteger(SETTING_POINTER_USAGE, usage);
        } catch (Exception e) {
            // we couldn't store the setting, that's OK
        }
    }

    /**
     * Get the currently set custom buttons
     * 
     * @return  map of key codes and joypad button names
     */
    public Hashtable getButtonAssignments() {
        return this.buttonAssignments;
    }

    /**
     * Set custom button assignment
     * 
     * @param buttonAssignments map of key codes and joypad button names
     */
    public void setButtonAssignments(final Hashtable buttonAssignments) {
        this.buttonAssignments = buttonAssignments;
    }

    /**
     * Paint the C64 screen and on-screen joystick
     */
    private void paint() {
        final VIC6569 vic = this.c64.getVIC();

        // clear the screen
        this.graphics.setColor(0, 0, 0);
        this.graphics.fillRect(0, 0, getWidth(), getHeight());

        // paint the border if necessary
        if (vic.isSmallScreen()) {
            this.graphics.setColor(this.borderColor.getRGB());
            if (!isAutoChangeOrientation() || this.transform == Sprite.TRANS_NONE || this.transform == Sprite.TRANS_ROT180) {
                this.graphics.fillRect(this.bx, this.by, vic.getBorderWidth(), vic.getBorderHeight());
            } else {
                this.graphics.fillRect(this.bx, this.by, vic.getBorderHeight(), vic.getBorderWidth());
            }
        }

        // draw the C64 display
        if (!isAutoChangeOrientation() || this.transform == Sprite.TRANS_NONE) {
            // this is the fast and simple way of drawing
            this.graphics.drawRGB(vic.getRGBData(), this.offset, vic.getBorderWidth(), this.x, this.y, this.paintWidth, this.paintHeight, false);
        } else {
            // creating an image for every repaint it quite costly, but it allows us to apply the rotation transformations
            Image image = Image.createRGBImage(vic.getRGBData(), vic.getBorderWidth(), vic.getBorderHeight(), false);

            this.graphics.drawRegion(image, this.ox, this.oy, this.paintWidth, this.paintHeight, this.transform, this.x, this.y, Graphics.TOP | Graphics.LEFT);

            image = null;
            System.gc();
        }

        // show logo while the C64 is not yet ready
        if (!this.c64.isReady()) {
            this.logo.paint(this.graphics);
        }

        // show on-screen joystick if necessary
        if (this.pointerUsage == POINTER_FOR_VIRTUAL_JOYSTICK && null != this.virtualJoystick && this.c64.isReady()) {
            this.virtualJoystick.getImage().paint(this.graphics);
        }

        flushGraphics();
    }

    /**
     * Calculate the screen size of the C64 screen
     *
     * @throws NullPointerException if the C64 instance has not yet been set
     */
    public void calculateScreenSize() {
        // pause emulator if necessary
        final boolean wasPaused = this.c64.isPaused();

        this.c64.pause();
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
        }

        // determine screen rotation
        final boolean isRotateScreen = this.transform != Sprite.TRANS_NONE && this.transform != Sprite.TRANS_ROT180;

        // determine screen scaling factor
        final int swidth = isRotateScreen ? getHeight() : getWidth(),  sheight = isRotateScreen ? getWidth() : getHeight();
        double scaling = Math.min(swidth * 1.0 / VIC6569.INNER_WIDTH, sheight * 1.0 / VIC6569.INNER_HEIGHT);

        // determine smooth scaling
        final boolean isSmoothScaling = this.midlet.getSettings().getBoolean(SETTING_SMOOTH_SCALING, scaling < 0.8) && scaling < 1.0;

        // initialize screen settings depending on size of the display and selected settings
        final VIC6569Factory factory = new VIC6569Factory();
        VIC6569 vic = factory.create(scaling, isSmoothScaling);

        // copy data from the old VIC
        vic.copy(this.c64.getVIC());

        // inform the C64 about the new VIC and register observers
        this.c64.getVIC().deleteObserver(this);
        this.c64.getVIC().destroy();
        this.c64.setVIC(vic);
        vic.addObserver(this);

        // initialize the screen memory
        System.gc();
        try {
            vic.initScreenMemory();
        } catch (OutOfMemoryError e) {
            // we ran out of memory, so we use the ScalableVIC without smoothing, which is the one using the least memory
            this.c64.getVIC().destroy();
            System.gc();
            vic = new ScalableVIC6569(this.c64, scaling);
            this.c64.getVIC().deleteObserver(this);
            this.c64.setVIC(vic);
            vic.addObserver(this);
            System.gc();
            vic.initScreenMemory();
            this.c64.getLogger().warning(LocalizationSupport.getMessage("OutOfVideoMemory"));
        } catch (IllegalStateException e) {
            // initialization failed, we use a standard VIC and report the problem
            vic = new VIC6569(this.c64);
            vic.initScreenMemory();
            this.display.setCurrent(new Alert(LocalizationSupport.getMessage("AnErrorHasOccurred"), LocalizationSupport.getMessage("ErrorWas") + e.getMessage() + LocalizationSupport.getMessage("CorrectVideoSettings"), null, AlertType.WARNING));
        }

        // the VIC might have slightly modified the scaling, so we fetch the current value
        if (vic instanceof ScalableVIC6569) {
            scaling = ((ScalableVIC6569) vic).getScaling();
        }

        // set frameskip value
        try {
            vic.setFrameSkip(this.midlet.getSettings().getInteger(SETTING_FRAMESKIP));
            this.c64.setDoAutoAdjustFrameskip(false);
        } catch (Exception e) {
            vic.setFrameSkip(4);
            this.c64.setDoAutoAdjustFrameskip(true);
        }

        // use small-screen feature
        vic.setSmallScreen(true);

        // determine position to start painting the border
        this.bx = Math.max(0, (getWidth() - (isRotateScreen ? vic.getBorderHeight() : vic.getBorderWidth())) >> 1);
        this.by = Math.max(0, (getHeight() - (isRotateScreen ? vic.getBorderWidth() : vic.getBorderHeight())) >> 1);

        // determine size of the paintable area
        final int width = vic.isSmallScreen() ? vic.getDisplayWidth() : vic.getBorderWidth();
        final int height = vic.isSmallScreen() ? vic.getDisplayHeight() : vic.getBorderHeight();

        // determine position to start painting the screen
        final int sx = (getWidth() - (isRotateScreen ? height : width)) >> 1;
        final int sy = (getHeight() - (isRotateScreen ? width : height)) >> 1;

        this.x = Math.max(0, sx);
        this.y = Math.max(0, sy);

        // calculate the area we have to repaint
        this.paintWidth = Math.min(swidth, width);
        this.paintHeight = Math.min(sheight, height);

        // set offset for display according to screen size
        if (vic.isSmallScreen()) {
            vic.gotoPixel(VIC6569.BORDER_WIDTH, VIC6569.BORDER_HEIGHT);
            this.ox = (int) (VIC6569.BORDER_WIDTH * scaling);
            this.oy = (int) (VIC6569.BORDER_HEIGHT * scaling);
        } else {
            vic.gotoPixel((int) Math.max(-sx / scaling, 0), (int) Math.max(-sy / scaling, 0));
            this.ox = this.oy = 0;
        }
        this.offset = vic.getNextPixel();

        // determine position of virtual joystick image
        if (null != this.virtualJoystick) {
            this.virtualJoystick.getImage().setPosition(getWidth() - this.virtualJoystick.getWidth(), getHeight() - this.virtualJoystick.getHeight());
        }

        // adjust logo position and orientation
        this.logo.setTransform(this.transform);

        if (!wasPaused) {
            this.c64.resume();
        }
    }

    /**
     * Adjust joystick direction according to the current screen rotation
     *
     * @param   direction   normal joystick direction
     * @return  adjusted joystick direction
     */
    private int adjustJoystickDirection(final int direction) {
        switch (this.transform) {
            case Sprite.TRANS_ROT90:
                return direction < Joystick.LEFT ? direction << 2 : direction == Joystick.LEFT ? Joystick.DOWN : Joystick.UP;
            case Sprite.TRANS_ROT180:
                return direction < Joystick.LEFT ? direction ^ (Joystick.UP | Joystick.DOWN) : direction ^ (Joystick.LEFT | Joystick.RIGHT);
            case Sprite.TRANS_ROT270:
                return direction > Joystick.DOWN ? direction >> 2 : direction == Joystick.UP ? Joystick.RIGHT : Joystick.LEFT;
            default:
                return direction;
        }
    }

    /**
     * Determine key that was pressed or released
     *
     * @param   keyCode keycode of the key that was modified
     * @return  key definition string that can be passed to Keyboard.keyPressed
     *          or Keyboard.keyReleased
     * @see de.joergjahnke.jac.Keyboard#keyPressed
     * @see de.joergjahnke.jac.Keyboard#keyReleased
     */
    private String getKeySelection(final int keyCode) {
        // determine key that was triggered
        String key = getKeyName(keyCode);

        // re-map some keys for mobile phones, which usually do not have a full keyboard
        if (this.isPhoneKeyboard) {
            if (Canvas.KEY_STAR == keyCode) {
                key = "ENTER";
            } else if (Canvas.KEY_POUND == keyCode) {
                key = "SPACE";
            }
        }

        return key;
    }

    /**
     * Get joystick changes depending on the pressed or released key
     * 
     * @param keyCode   pressed or released key
     * @return  affected joystick directions + affected joystick buttons << 16
     */
    private int getJoystickChanges(final int keyCode) {
        int pressedDirections = 0, pressedButtons = 0;
        int repeatMask = 0;
        final int gameAction = this.buttonAssignments.contains("Up") ? 0 : getGameAction(keyCode);
        String buttonName = "";

        if (this.buttonAssignments.containsKey(new Integer(keyCode))) {
            buttonName = this.buttonAssignments.get(new Integer(keyCode)).toString();
        } else if (this.buttonAssignments.containsKey(new Integer(keyCode + ButtonAssignmentCanvas.MASK_REPEAT_KEY))) {
            buttonName = this.buttonAssignments.get(new Integer(keyCode + ButtonAssignmentCanvas.MASK_REPEAT_KEY)).toString();
            repeatMask = ButtonAssignmentCanvas.MASK_REPEAT_KEY;
        }

        if (buttonName.indexOf("Up") >= 0 || gameAction == UP) {
            pressedDirections |= adjustJoystickDirection(Joystick.UP);
        }
        if (buttonName.indexOf("Down") >= 0 || gameAction == DOWN) {
            pressedDirections |= adjustJoystickDirection(Joystick.DOWN);
        }
        if (buttonName.indexOf("Left") >= 0 || gameAction == LEFT) {
            pressedDirections |= adjustJoystickDirection(Joystick.LEFT);
        }
        if (buttonName.indexOf("Right") >= 0 || gameAction == RIGHT) {
            pressedDirections |= adjustJoystickDirection(Joystick.RIGHT);
        }
        if ("Fire".equals(buttonName) || gameAction == FIRE) {
            pressedButtons |= Joystick.FIRE;
        }

        return pressedDirections + (pressedButtons << 16) + repeatMask;
    }

    /**
     * Pass key events to Keyboard class or Joystick after converting the key code
     */
    protected void keyPressed(final int keyCode) {
        // start joystick movement if cursor keys or button keys are released
        final int changes = getJoystickChanges(keyCode);

        if (changes == 0) {
            final String key = getKeySelection(keyCode);

            this.c64.getKeyboard().keyTyped(key);
        } else {
            final int pressedDirections = changes & 0x0000ffff;
            final boolean wasFirePressed = ((changes & (0xffff0000 - ButtonAssignmentCanvas.MASK_REPEAT_KEY)) >> 16) != 0;
            final Joystick joystick = this.c64.getJoystick(this.c64.getActiveJoystick());

            joystick.setDirection(joystick.getDirection() | pressedDirections);
            joystick.setFiring(joystick.isFiring() | wasFirePressed);

            // install a timer that releases the key after some time, if necessary
            final boolean needReleaseTimer = (changes & ButtonAssignmentCanvas.MASK_REPEAT_KEY) != 0;

            if (needReleaseTimer) {
                final Integer key = new Integer(keyCode);
                Timer buttonReleaseTimer = (Timer) this.buttonReleaseTimers.get(key);

                if (buttonReleaseTimer != null) {
                    buttonReleaseTimer.cancel();
                }
                buttonReleaseTimer = new Timer();
                buttonReleaseTimer.schedule(
                        new TimerTask() {

                            public void run() {
                                keyReleased(keyCode);
                            }
                        }, AUTOMATIC_KEY_RELEASE_TIME);
                this.buttonReleaseTimers.put(key, buttonReleaseTimer);
            }
        }
    }

    /**
     * Pass key events to Joystick class after converting the key code
     */
    protected void keyReleased(final int keyCode) {
        // end joystick movement if cursor keys or button keys are released
        final int changes = getJoystickChanges(keyCode);
        final int pressedDirections = changes & 0x0000ffff;
        final boolean wasFireReleased = ((changes & (0xffff0000 - ButtonAssignmentCanvas.MASK_REPEAT_KEY)) >> 16) != 0;
        final Joystick joystick = this.c64.getJoystick(this.c64.getActiveJoystick());

        joystick.setDirection(joystick.getDirection() & (Joystick.DIRECTIONS - pressedDirections));
        joystick.setFiring(joystick.isFiring() & !wasFireReleased);
    }

    /**
     * A repeated key gets continuously pressed
     */
    protected void keyRepeated(final int keyCode) {
        keyPressed(keyCode);
    }

    // here we handle events of the pointing device
    /**
     * React to pointer pressed event depending on the selected pointer option.<br>
     * When displaying a virtual joystick the point on the virtual joystick will be evaluated and
     * sent to the Joystick object. When the pointer is used as fire button we will active the
     * fire button on the Joystick object. When the pointer is used for joystick movement we
     * record the start of a pointer drag operation. Otherwise we do nothing.
     * 
     * @param   x   x-position of the event
     * @param   y   y-position of the event
     */
    protected void pointerPressed(final int x, final int y) {
        switch (this.pointerUsage) {
            case POINTER_FOR_VIRTUAL_JOYSTICK: {
                final int value = this.virtualJoystick.getValue(x - this.virtualJoystick.getImage().getX(), y - this.virtualJoystick.getImage().getY());
                final Joystick joystick = this.c64.getJoystick(this.c64.getActiveJoystick());

                joystick.setDirection(adjustJoystickDirection(value & Joystick.DIRECTIONS));
                joystick.setFiring((value & Joystick.FIRE) != 0);
                break;
            }
            case POINTER_AS_FIRE_BUTTON:
                this.c64.getJoystick(this.c64.getActiveJoystick()).setFiring(true);
                break;
            case POINTER_FOR_JOYSTICK_MOVEMENT:
                this.pStartX = x;
                this.pStartY = y;
                break;
            default:
                ;
        }
    }

    /**
     * When the pointer is used we will release the fire button. When the virtual joystick is used
     * we will also set the joystick direction back to null. When a pointer drag operation was
     * running we end this operation.
     * 
     * @param   x   x-position of the event
     * @param   y   y-position of the event
     */
    protected void pointerReleased(final int x, final int y) {
        switch (this.pointerUsage) {
            case POINTER_FOR_VIRTUAL_JOYSTICK: {
                Joystick joystick = this.c64.getJoystick(this.c64.getActiveJoystick());

                joystick.setFiring(false);
                joystick.setDirection(0);
                break;
            }
            case POINTER_AS_FIRE_BUTTON:
                this.c64.getJoystick(this.c64.getActiveJoystick()).setFiring(false);
                break;
            case POINTER_FOR_JOYSTICK_MOVEMENT:
                this.c64.getJoystick(this.c64.getActiveJoystick()).setDirection(0);
                this.pStartX = this.pStartY = -1;
                break;
            default:
                ;
        }
    }

    /**
     * Check if pointer movement is used to simulate joystick input
     * 
     * @param   x   x-position of the event
     * @param   y   y-position of the event
     */
    protected void pointerDragged(final int x, final int y) {
        switch (this.pointerUsage) {
            case POINTER_FOR_JOYSTICK_MOVEMENT: {
                // the event is within the gameboy screen area?
                if (x >= this.x && x < this.x + this.paintWidth && y >= this.y && y < this.y + this.paintHeight) {
                    // a pointer drag operation is running?
                    if (this.pStartX > 0) {
                        // check distance to last movement
                        final int distX = x - this.pStartX;
                        final int distY = y - this.pStartY;

                        // the direction with more movement is the one we consider
                        // a minimum of 5% of the screen must have been moved, then the joypad is triggered accordingly
                        if (Math.abs(distX) > Math.abs(distY)) {
                            if (Math.abs(distX) > this.paintWidth * MIN_PERCENTAGE_POINTER_MOVEMENT / 100) {
                                this.c64.getJoystick(this.c64.getActiveJoystick()).setDirection(adjustJoystickDirection(distX < 0 ? Joystick.LEFT : Joystick.RIGHT));
                            }
                        } else {
                            if (Math.abs(distY) > this.paintHeight * MIN_PERCENTAGE_POINTER_MOVEMENT / 100) {
                                this.c64.getJoystick(this.c64.getActiveJoystick()).setDirection(adjustJoystickDirection(distY < 0 ? Joystick.UP : Joystick.DOWN));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * We recalculate the screen size when the device gets rotated
     */
    public void onDeviceRotated() {
        calculateScreenSize();
    }

    /**
     * Set joystick according to accelerometer changes
     */
    public void onAccelerometerChange(final double x, final double y, final double z) {
        final Joystick joystick = c64.getJoystick(c64.getActiveJoystick());

        if (x > 200) {
            joystick.setDirection(joystick.getDirection() | Joystick.LEFT);
        } else if (x < -200) {
            joystick.setDirection(joystick.getDirection() | Joystick.RIGHT);
        } else {
            joystick.setDirection(joystick.getDirection() & (Joystick.DIRECTIONS - Joystick.LEFT - Joystick.RIGHT));
        }
        if (y < -200) {
            joystick.setDirection(joystick.getDirection() | Joystick.UP);
        } else if (y > 200) {
            joystick.setDirection(joystick.getDirection() | Joystick.DOWN);
        } else {
            joystick.setDirection(joystick.getDirection() & (Joystick.DIRECTIONS - Joystick.UP - Joystick.DOWN));
        }

        // activate the backlight because with accelerometer usage the keys don't get pressed often, so that many device switch off the backlight
        try {
            de.joergjahnke.common.jme.Backlight.setLevel(75);
        } catch (Throwable t) {
            // the API required for this method might not be available
        }
    }

    // implementation of the Observer interface
    /**
     * Initialize screen update
     */
    public void update(final Object observable, final Object event) {
        // the notification comes from a C64Screen?
        if (observable instanceof VIC6569) {
            // the border color was changed?
            if (event instanceof Color) {
                // update the border color, it will be painted with the next repaint
                this.borderColor = (Color) event;
            } else {
                // no, the screen was updated, so we paint the new screen
                paint();
            }
        // the emulator encountered an exception?
        } else if (event instanceof Throwable && this.showEmulatorExceptions) {
            // we show the first of such exceptions on the screen, all are in the log anyway
            this.showEmulatorExceptions = false;
            this.display.setCurrent(new Alert(LocalizationSupport.getMessage("AnErrorHasOccurred"), LocalizationSupport.getMessage("ErrorWas") + ((Throwable) event).getMessage() + LocalizationSupport.getMessage("NoFurtherMessages"), null, AlertType.WARNING));
        }
    }

    // inner class for creating the suitable VIC (sub-)class
    class VIC6569Factory {

        /**
         * Create a suitable VIC instance
         *
         * @param   scaling desired scaling factor
         * @param   isRotateScreen  should the screen be rotated 90°?
         * @return  suitable VIC6569 (sub-)class instance
         */
        public VIC6569 create(final double scaling, final boolean isSmoothScaling) {
            return scaling != 1.0 ? isSmoothScaling ? new SmoothingScalableVIC6569(c64, scaling) : new ScalableVIC6569(c64, scaling) : new VIC6569(c64);
        }

        /**
         * Get the VIC type we would create based on given parameters
         *
         * @param   scaling desired scaling factor
         * @param   isRotateScreen  should the screen be rotated 90°?
         * @return  suitable VIC6569 (sub-)class
         */
        public Class getDesiredType(final double scaling, final boolean isSmoothScaling) {
            return scaling != 1.0 ? isSmoothScaling ? SmoothingScalableVIC6569.class : ScalableVIC6569.class : VIC6569.class;
        }
    }
}
