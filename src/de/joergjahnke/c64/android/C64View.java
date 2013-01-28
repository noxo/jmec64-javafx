/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.android;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import de.joergjahnke.c64.core.C64;
import de.joergjahnke.c64.core.Joystick;
import de.joergjahnke.c64.core.VIC6569;
import de.joergjahnke.c64.smalldisplays.SmoothingScalableVIC6569;
import de.joergjahnke.common.ui.Color;
import de.joergjahnke.common.util.Observer;
import java.util.Hashtable;

/**
 * The actual Android view that shows the C64 screen.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class C64View extends SurfaceView implements Observer, SensorEventListener, SurfaceHolder.Callback {

    /**
     * message we send to repaint the screen
     */
    protected final static int MSG_REPAINT = 1;
    /**
     * maps key codes from key events to keys of the C64
     */
    private final static Hashtable<Integer, String> keycodeKeyMap = new Hashtable<Integer, String>();


    static {
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_0), "0");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_1), "1");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_2), "2");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_3), "3");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_4), "4");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_5), "5");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_6), "6");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_7), "7");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_8), "8");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_9), "9");

        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_A), "a");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_B), "b");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_C), "c");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_D), "d");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_E), "e");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_F), "f");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_G), "g");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_H), "h");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_I), "i");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_J), "j");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_K), "k");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_L), "l");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_M), "m");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_N), "n");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_O), "o");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_P), "p");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_Q), "q");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_R), "r");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_S), "s");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_T), "t");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_U), "u");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_V), "v");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_W), "w");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_X), "x");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_Y), "y");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_Z), "z");

        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_DEL), "DELETE");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_SHIFT_LEFT), "SHIFT");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_SHIFT_RIGHT), "SHIFT");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_SPACE), "SPACE");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_COMMA), ",");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_PERIOD), ".");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_ENTER), "ENTER");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_EQUALS), "=");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_SEMICOLON), ";");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_STAR), "*");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_APOSTROPHE), "'");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_AT), "@");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_HOME), "HOME");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_LEFT_BRACKET), "(");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_RIGHT_BRACKET), ")");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_MINUS), "-");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_POUND), "POUND");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_SLASH), "/");

        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_DPAD_LEFT), "LEFT");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_DPAD_RIGHT), "RIGHT");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_DPAD_UP), "UP");
        keycodeKeyMap.put(new Integer(KeyEvent.KEYCODE_DPAD_DOWN), "DOWN");
    }
    /**
     * maps key codes from key events to joystick movements of the C64
     */
    private final static Hashtable<Integer, Integer> keycodeJoystickMap = new Hashtable<Integer, Integer>();


    static {
        keycodeJoystickMap.put(new Integer(KeyEvent.KEYCODE_DPAD_LEFT), new Integer(Joystick.LEFT));
        keycodeJoystickMap.put(new Integer(KeyEvent.KEYCODE_DPAD_RIGHT), new Integer(Joystick.RIGHT));
        keycodeJoystickMap.put(new Integer(KeyEvent.KEYCODE_DPAD_UP), new Integer(Joystick.UP));
        keycodeJoystickMap.put(new Integer(KeyEvent.KEYCODE_DPAD_DOWN), new Integer(Joystick.DOWN));
        keycodeJoystickMap.put(new Integer(KeyEvent.KEYCODE_DPAD_CENTER), new Integer(Joystick.FIRE));
    }
    /**
     * C64-screen to display in the view
     */
    protected C64 c64;
    /**
     * bitmap used when painting the C64 screen 
     */
    protected Bitmap screenBitmap = null;
    /**
     * border color
     */
    protected Color borderColor = new Color(0);
    /**
     * offset to start printing of C64Screen RGB data
     */
    private int offset;
    /**
     * rectangle where we paint the C64 screen
     */
    private Rect paintRect = null;
    /**
     * rectangle where we paint the C64 border
     */
    private Rect borderRect = null;
    /**
     * emulator logo
     */
    private final Bitmap logo;
    /**
     * do we use antialiasing to improve the C64 screen content?
     */
    private boolean useAntialiasing = false;
    /**
     * SurfaceHolder where we paint
     */
    private final SurfaceHolder holder;
    /**
     * do we have a drawing surface?
     */
    private boolean hasSurface = false;

    /**
     * Create a new C64View
     * 
     * @param context	application content
     */
    public C64View(final Context context) {
        super(context);

        // we need to get key events
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);

        // we need to set to transparent pixel format, otherwise the view stays black
        ((Activity) getContext()).getWindow().setFormat(PixelFormat.TRANSPARENT);

        // register our interest in hearing about changes to our surface
        this.holder = getHolder();
        this.holder.addCallback(this);

        // get the emulator logo
        this.logo = BitmapFactory.decodeResource(getResources(), R.drawable.a64);
    }

    /**
     * Get the C64 instance being displayed
     */
    public final C64 getC64() {
        return this.c64;
    }

    /**
     * Set the C64 instance being displayed
     */
    public void setC64(final C64 c64) {
        // store C64 instance
        this.c64 = c64;

        // determine screen scaling factor
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        final double scaling = Math.min(1.0, Math.min(metrics.widthPixels * 1.0 / VIC6569.INNER_WIDTH, metrics.heightPixels * 1.0 / VIC6569.INNER_HEIGHT));

        // initialize screen settings depending on size of the display and selected settings
        VIC6569 vic = scaling < 0.9 ? new SmoothingScalableVIC6569(c64, scaling) : c64.getVIC();

        vic.setSmallScreen(true);
        vic.setFrameSkip(4);
        try {
            vic.initScreenMemory();
        } catch (IllegalStateException e) {
            // initialization failed, we use a standard VIC and report the problem
            vic = new VIC6569(this.c64);
            vic.initScreenMemory();
            ((AndroidC64) getContext()).showAlert("An error has occurred", "The error message was:\n" + e.getMessage() + "\nPlease correct your video settings and restart the emulator.");
        }
        c64.setVIC(vic);

        // register as observer for screen refresh and emulator exceptions
        vic.addObserver(this);
        c64.addObserver(this);

        // create a bitmap used when painting the C64's screen
        this.screenBitmap = Bitmap.createBitmap(vic.getDisplayWidth(), vic.getDisplayHeight(), Bitmap.Config.ARGB_8888);

        // set offset for display according to screen size
        vic.gotoPixel(VIC6569.BORDER_WIDTH, VIC6569.BORDER_HEIGHT);
        this.offset = vic.getNextPixel();
    }

    /**
     * Check whether antialiasing is used for the C64 screen
     *
     * @return	true if antialiasing is used
     */
    public boolean isUseAntialiasing() {
        return this.useAntialiasing;
    }

    /**
     * Set whether to use antialiasing for the C64 screen
     * 
     * @param	useAntialiasing	true to enable antialiasing, false to disable
     */
    public void setUseAntialiasing(boolean useAntialiasing) {
        this.useAntialiasing = useAntialiasing;
    }

    /**
     * Get the optimum size for the emulator screen for the device's display
     */
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        final VIC6569 vic = c64.getVIC();

        // determine size of the paintable area
        final int width = vic.isSmallScreen() ? vic.getDisplayWidth() : vic.getBorderWidth();
        final int height = vic.isSmallScreen() ? vic.getDisplayHeight() : vic.getBorderHeight();

        // determine position and size of the C64 screen
        final double scaling = Math.min(Math.min(1.0, w * 1.0 / width), Math.min(1.0, h * 1.0 / height));
        final int sw = (int) (scaling * width);
        final int sh = (int) (scaling * height);
        final int sx = Math.max(0, (w - sw) >> 1);
        final int sy = Math.max(0, (h - sh) >> 1);

        this.paintRect = new Rect(sx, sy, sx + sw, sy + sh);

        // determine position and size of the C64 border
        final int bw = (int) (vic.getBorderWidth() * scaling);
        final int bh = (int) (vic.getBorderHeight() * scaling);
        final int bx = Math.max(0, (w - bw) >> 1);
        final int by = Math.max(0, (h - bh) >> 1);

        this.borderRect = new Rect(bx, by, bx + bw, by + bh);
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
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN && event.isShiftPressed()) {
            result = "SHIFT";
        } else if (keyCode == KeyEvent.KEYCODE_C && event.isSymPressed()) {
            result = "BREAK";
        } else if (keyCode == KeyEvent.KEYCODE_R && event.isSymPressed()) {
            result = "RUN";
        } else if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_8 && event.isSymPressed()) {
            result = "F" + ('0' + (keyCode - KeyEvent.KEYCODE_0));
        // if we have no modifiers then we might use cursor keys for joystick movement
        } else if (!KeyEvent.isModifierKey(keyCode) && keycodeJoystickMap.containsKey(new Integer(keyCode))) {
            result = keycodeJoystickMap.get(new Integer(keyCode));
        // we might have some special keys we need to convert to C64 keys
        } else if (keycodeKeyMap.containsKey(new Integer(keyCode))) {
            result = keycodeKeyMap.get(new Integer(keyCode)).toString();
        }

        return result;
    }

    /**
     * Paint the C64 screen
     */
    protected void draw() {
        if (this.hasSurface) {
            try {
                // this is where we draw to
                final Canvas canvas = this.holder.lockCanvas();
                // set black as screen background
                final Paint paint = new Paint();

                paint.setColor(0);
                paint.setAlpha(255);
                canvas.drawPaint(paint);

                // paint C64's screen border
                final VIC6569 vic = this.c64.getVIC();

                paint.setColor(this.borderColor.getRGB());
                canvas.drawRect(this.borderRect, paint);

                // show C64 screen
                final Paint paint2 = isUseAntialiasing() ? new Paint(Paint.ANTI_ALIAS_FLAG + Paint.FILTER_BITMAP_FLAG) : null;

                this.screenBitmap.setPixels(vic.getRGBData(), this.offset, vic.getBorderWidth(), 0, 0, this.screenBitmap.getWidth(), this.screenBitmap.getHeight());
                canvas.drawBitmap(this.screenBitmap, null, this.paintRect, paint2);

                // show logo if the emulator is not yet ready
                if (!this.c64.isReady()) {
                    canvas.drawBitmap(this.logo, (getWidth() - this.logo.getWidth()) >> 1, (getHeight() - this.logo.getHeight()) >> 1, null);
                }

                this.holder.unlockCanvasAndPost(canvas);
            } catch (Exception e) {
                // probably the Gameboy instance is not yet initialized, no problem, the next painting might work
            }
        }
    }

    /**
     * Convert key event to key selection suitable for the Keyboard class and pass it to that class
     */
    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
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

            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Convert key event to key selection suitable for the Keyboard class and pass it to that class
     */
    @Override
    public boolean onKeyUp(final int keyCode, final KeyEvent event) {
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

            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        // check whether the joystick button was pressed or released
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            onKeyUp(KeyEvent.KEYCODE_DPAD_CENTER, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        // check whether we had a move event
        } else if (event.getAction() == MotionEvent.ACTION_MOVE && event.getHistorySize() > 0) {
            final int hist = event.getHistorySize();
            final float xmove = event.getX() - event.getHistoricalX(hist - 1);
            final float ymove = event.getY() - event.getHistoricalY(hist - 1);

            // horizontal movement?
            if (Math.abs(xmove) > Math.abs(ymove)) {
                if (xmove < 0) {
                    onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
                } else {
                    onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
                }
            // no, vertical movement
            } else {
                if (ymove < 0) {
                    onKeyDown(KeyEvent.KEYCODE_DPAD_UP, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
                } else {
                    onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
                }
            }

            // stop the event shortly after
            postDelayed(
                    new Runnable() {

                        public void run() {
                            c64.getJoystick(c64.getActiveJoystick()).setDirection(0);
                        }
                    }, 500);
        }

        return true;
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
                // update the border color, it will be painted with the next repaint
                this.borderColor = (Color) event;
            } else {
                // repaint the screen
                draw();
            }
        }
    }


    // implementation of the SensorListener interface
    /**
     * Translate orientation sensor changes into key events
     */
    @Override
    public void onSensorChanged(final SensorEvent event) {
        final float[] sensorValues = event.values;
        final float pitch = sensorValues[1];

        if (pitch < -20) {
            onKeyDown(KeyEvent.KEYCODE_DPAD_UP, null);
        } else if (pitch > 20) {
            onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, null);
        } else {
            onKeyUp(KeyEvent.KEYCODE_DPAD_UP, null);
            onKeyUp(KeyEvent.KEYCODE_DPAD_DOWN, null);
        }

        final float roll = sensorValues[2];

        if (roll < -20) {
            onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, null);
        } else if (roll > 20) {
            onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, null);
        } else {
            onKeyUp(KeyEvent.KEYCODE_DPAD_LEFT, null);
            onKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT, null);
        }
    }

    @Override
    public void onAccuracyChanged(final Sensor sensor, int accuracy) {
        // we do nothing
    }

    // implementation of the SurfaceHolder.Callback interface
    @Override
    public void surfaceChanged(SurfaceHolder holder, int arg1, int arg2, int arg3) {
        // nothing to do
    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
        this.hasSurface = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        this.hasSurface = false;
    }
}
