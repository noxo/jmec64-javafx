/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.jme;

import javax.microedition.io.Connection;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.game.Sprite;

/**
 * Helper class where the access to the sensors API is encapsulated
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class OrientationSensitiveCanvasHelper {

    /**
     * SensorConnection we might have opened to get accelerometer data
     */
    private Connection con = null;
    /**
     * Canvas that is using this instance
     */
    private final OrientationSensitiveCanvas canvas;

    /**
     * Create a new OrientationSensitiveCanvasHelper instance working for a given canvas
     * 
     * @param canvas
     */
    public OrientationSensitiveCanvasHelper(final OrientationSensitiveCanvas canvas) {
        this.canvas = canvas;
    }

    /**
     * Activate the accelerometer
     */
    public void activateAccelerometer() {
        try {
            final javax.microedition.sensor.SensorInfo[] si = javax.microedition.sensor.SensorManager.findSensors("acceleration", javax.microedition.sensor.SensorInfo.CONTEXT_TYPE_USER);
            final String url = si[0].getUrl();

            this.con = Connector.open(url);

            final javax.microedition.sensor.SensorConnection sensor = (javax.microedition.sensor.SensorConnection) this.con;

            sensor.setDataListener(new javax.microedition.sensor.DataListener() {

                public void dataReceived(final javax.microedition.sensor.SensorConnection sensor, final javax.microedition.sensor.Data[] data, final boolean isDataLost) {
                    try {
                        double x = 0;
                        double y = 0;
                        double z = 0;
                        switch (data[0].getChannelInfo().getDataType()) {
                            case javax.microedition.sensor.ChannelInfo.TYPE_INT:
                                x = (double) data[0].getIntValues()[0];
                                break;
                            case javax.microedition.sensor.ChannelInfo.TYPE_DOUBLE:
                                x = data[0].getDoubleValues()[0];
                                break;
                        }
                        switch (data[1].getChannelInfo().getDataType()) {
                            case javax.microedition.sensor.ChannelInfo.TYPE_INT:
                                y = (double) data[1].getIntValues()[0];
                                break;
                            case javax.microedition.sensor.ChannelInfo.TYPE_DOUBLE:
                                y = data[1].getDoubleValues()[0];
                                break;
                        }
                        switch (data[2].getChannelInfo().getDataType()) {
                            case javax.microedition.sensor.ChannelInfo.TYPE_INT:
                                z = (double) data[2].getIntValues()[0];
                                break;
                            case javax.microedition.sensor.ChannelInfo.TYPE_DOUBLE:
                                z = data[2].getDoubleValues()[0];
                                break;
                        }
                        // we automatically rotate the screen?
                        if (canvas.isAutoChangeOrientation()) {
                            final int oldTransform = canvas.transform;
                            if (x < -500) {
                                canvas.transform = Sprite.TRANS_ROT270;
                            } else if (x > 500) {
                                canvas.transform = Sprite.TRANS_ROT90;
                            } else if (y < -500) {
                                canvas.transform = Sprite.TRANS_ROT180;
                            } else if (y > 500) {
                                canvas.transform = Sprite.TRANS_NONE;
                            }
                            if (oldTransform != canvas.transform) {
                                canvas.onDeviceRotated();
                            }
                        }
                        // we use the accelerometer as input device?
                        if (canvas.isUseAccelerometer()) {
                            canvas.onAccelerometerChange(x, y, z);
                        }
                    } catch (Exception e) {
                    }
                }
            }, 1);
        } catch (Throwable t) {
            throw new RuntimeException(t.getMessage());
        }
    }

    /**
     * Deactivate the accelerometer
     */
    public void deactivateAccelerometer() {
        try {
            ((javax.microedition.sensor.SensorConnection) this.con).removeDataListener();
            this.con.close();
        } catch (Throwable t) {
        }
    }

    /**
     * Check if the Sensor API is supported by the device
     *
     * @return  true if the API is supported
     */
    public static boolean supportsSensorAPI() {
        return null != System.getProperty("microedition.sensor.version");
    }

    /**
     * Check if the device supports an accelerometer
     *
     * @return  true if an accelerometer is supported
     */
    public static boolean supportsAccelerometer() {
        if (supportsSensorAPI()) {
            try {
                final javax.microedition.sensor.SensorInfo[] si = javax.microedition.sensor.SensorManager.findSensors("acceleration", javax.microedition.sensor.SensorInfo.CONTEXT_TYPE_USER);

                return si[0].getChannelInfos().length == 3;
            } catch (Throwable t) {
                // the API is not supported, no problem, we report No
                return false;
            }
        } else {
            return false;
        }
    }
}
