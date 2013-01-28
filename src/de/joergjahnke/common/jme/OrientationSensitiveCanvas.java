/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.jme;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.midlet.MIDlet;

/**
 * Canvas that listens to event from a built-in orientation sensor
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class OrientationSensitiveCanvas extends GameCanvas {

    /**
     * Display of the parent Midlet
     */
    protected final Display display;
    /**
     * automatically change the screen orientation according to the orientation sensor?
     */
    private boolean isAutoChangeOrientation = false;
    /**
     * transformation that needs to be applied when automatically changing the screen orientation
     */
    protected int transform = Sprite.TRANS_NONE;
    /**
     * do we use an accelerometer as input device for controlling the joypad direction?
     */
    private boolean useAccelerometer = false;
    /**
     * reference to the OrientationSensitiveCanvasHelper instance we might use
     */
    private Object sensorUtils = null;

    /**
     * Create a new OrientationSensitiveCanvas for a given MIDlet
     *
     * @param   midlet  MIDlet we work for
     */
    protected OrientationSensitiveCanvas(final MIDlet midlet) {
        super(false);
        this.display = Display.getDisplay(midlet);
    }

    /**
     * Should the screen orientation be automatically adjusted when an orientation sensor reports a change?
     *
     * @return  true if the screen gets rotated, false if it should remain in position as before
     */
    public boolean isAutoChangeOrientation() {
        return this.isAutoChangeOrientation;
    }

    /**
     * Do we use the accelerometer as input device for controlling the joypad direction?
     *
     * @return  true if the accelerometer is being used, otherwise false
     */
    public boolean isUseAccelerometer() {
        return this.useAccelerometer;
    }

    /**
     * Get a OrientationSensitiveCanvasHelper instance
     *
     * @return  a new instance of none was yet created, the existing old one if it has been created before
     */
    public Object getSensorUtils() {
        if (null == this.sensorUtils) {
            this.sensorUtils = new OrientationSensitiveCanvasHelper(this);
        }
        return this.sensorUtils;
    }

    /**
     * Set whether the screen should automatically rotate if an orientation sensor reports a rotation?
     *
     * @param isAutoChangeOrientation   true to let the screen rotate automatically, false to have it remain as before
     */
    public void setAutoChangeOrientation(final boolean isAutoChangeOrientation) {
        if (isAutoChangeOrientation != this.isAutoChangeOrientation) {
            this.isAutoChangeOrientation = isAutoChangeOrientation;
            // switch on the accelerometer support if needed or switch it off if no service needs it
            if (isAutoChangeOrientation && !this.useAccelerometer) {
                activateAccelerometer();
            } else if (!this.useAccelerometer) {
                ((OrientationSensitiveCanvasHelper) getSensorUtils()).deactivateAccelerometer();
            }
        }
    }

    /**
     * Set whether we should use the accelerometer as input device for controlling the joypad direction
     *
     * @param useAccelerometer  true to set the accelerometer as input device, false to ignore its data
     */
    public void setUseAccelerometer(final boolean useAccelerometer) {
        if (useAccelerometer != this.useAccelerometer) {
            this.useAccelerometer = useAccelerometer;
            // switch on the accelerometer support if needed or switch it off if no service needs it
            if (useAccelerometer && !this.isAutoChangeOrientation) {
                activateAccelerometer();
            } else if (!this.isAutoChangeOrientation) {
                ((OrientationSensitiveCanvasHelper) getSensorUtils()).deactivateAccelerometer();
            }
        }
    }

    /**
     * Activate the accelerometer, reporting any occuring errors
     */
    private void activateAccelerometer() {
        try {
            ((OrientationSensitiveCanvasHelper) getSensorUtils()).activateAccelerometer();
        } catch (Exception e) {
            // this might happen e.g. if the interface is not supported
            // without accelerometer data auto-changing the orientation as well as joypad via accelerometer will not work
            this.useAccelerometer = this.isAutoChangeOrientation = false;
            this.display.setCurrent(new Alert(LocalizationSupport.getMessage("CouldNotActivateAccelerometer"), LocalizationSupport.getMessage("ErrorWas") + e.getMessage(), null, AlertType.WARNING));
        }
    }

    /**
     * The mobile device was rotated and the device should react to rotation events.
     * The default implementation does nothing.
     */
    public void onDeviceRotated() {
    }

    /**
     * Gets called when the accelerometer sends new data and accelerometer usage is activated.
     *
     * @param   x   x value from accelerometer
     * @param   y   y value from accelerometer
     * @param   z   z value from accelerometer
     */
    public void onAccelerometerChange(final double x, final double y, final double z) {
    }
}
