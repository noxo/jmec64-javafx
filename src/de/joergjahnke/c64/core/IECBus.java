/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.util.DefaultObservable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

/**
 * Implements IEC bus routines<br>
 * <br>
 * For information on the C64's serial but see <a href='http://www.zimmers.net/anonftp/pub/cbm/programming/serial-bus.txt'>http://www.zimmers.net/anonftp/pub/cbm/programming/serial-bus.txt</a>,
 * <a href='http://www.retrograde.dk/twiki/bin/view/IEC/SerialBusProtocol'>http://www.retrograde.dk/twiki/bin/view/IEC/SerialBusProtocol</a>
 * or <a href='http://www.viceteam.org/plain/serial.txt'>http://www.viceteam.org/plain/serial.txt</a>.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class IECBus extends DefaultObservable implements Serializable {
    // do we print debug info?

    private final static boolean DEBUG = false;
    /**
     * ATN flag
     */
    public final static Integer ATN = new Integer(1);
    /**
     * CLK flag
     */
    public final static Integer CLK = new Integer(2);
    /**
     * DATA flag
     */
    public final static Integer DATA = new Integer(3);
    /**
     * this is the signal we send to observers when ATN is set
     */
    public final static Integer SIGNAL_ATN = new Integer(1);
    /**
     * this is the signal we send to observer when any flag was changed by the controller
     */
    public final static Integer SIGNAL_CONTROLLER_FLAG_MODIFIED = new Integer(2);
    /**
     * the device controlling the bus
     */
    private final EmulatedDevice controller;
    /**
     * the devices connected to the bus
     */
    private final Vector devices = new Vector();
    /**
     * the corresponding device states for the connected devices
     */
    private final Vector deviceStates = new Vector();
    /**
     * the state of the bus controller
     */
    private final DeviceState controllerState = new DeviceState();

    /**
     * Creates a new instance of IECBus
     *
     * @param   controller  the C64 the IEC bus works for i.e. the bus controller
     */
    public IECBus(final EmulatedDevice controller) {
        this.controller = controller;
    }

    /**
     * Reset the bus
     */
    public void reset() {
        // set all signals to false on all devices and for the controller
        final Integer[] signals = {ATN, CLK, DATA};

        for (int i = 0; i < signals.length; ++i) {
            this.controllerState.setSignal(signals[i], false);
            for (int j = 0; j < this.deviceStates.size(); ++j) {
                final DeviceState deviceState = (DeviceState) this.deviceStates.elementAt(j);

                deviceState.setSignal(signals[j], false);
            }
        }
    }

    /**
     * Get the bus controller
     * 
     * @return  device that is controlling the bus
     */
    public EmulatedDevice getController() {
        return this.controller;
    }

    /**
     * Connect a new device to the bus
     * 
     * @param   device  device to connect
     */
    public void addDevice(final EmulatedIECBusDevice device) {
        this.devices.addElement(device);
        this.deviceStates.addElement(new DeviceState());
    }

    /**
     * Remove a device from the bus
     * 
     * @param   device  device to disconnect
     */
    public void removeDevice(final EmulatedIECBusDevice device) {
        final int index = this.devices.indexOf(device);

        this.devices.removeElementAt(index);
        this.deviceStates.removeElementAt(index);
    }

    /**
     * Get a bus flag from all devices or the controller
     * 
     * @param   flag    flag, e.g. DATA
     * @return  true if the flag is set by any one of the connected devices, including the controller
     */
    public boolean getSignal(final Object flag) {
        return getDevicesSignal(flag) | this.controllerState.getSignal(flag);
    }

    /**
     * Get a bus flag from all devices but not the controller
     * 
     * @param   flag    flag, e.g. DATA
     * @return  true if the flag is set by any one of the connected devices
     */
    private boolean getDevicesSignal(final Object flag) {
        // we check all devices and return true if at least one has the flag set
        final Vector deviceStates_ = this.deviceStates;

        for (int i = 0, to = deviceStates_.size(); i < to; ++i) {
            if (((DeviceState) deviceStates_.elementAt(i)).getSignal(flag)) {
                return true;
            }
        }

        // no device had the flag set, so we return false
        return false;
    }

    /**
     * Get the device state of a given device
     *
     * @param   device  device to check
     * @return  device's state
     */
    private DeviceState getState(final EmulatedDevice device) {
        return device == this.controller ? this.controllerState : (DeviceState) this.deviceStates.elementAt(this.devices.indexOf(device));
    }

    /**
     * Get a flag from a connected device or the controller
     * 
     * @param   device  device to check, can be the controller or a connected device
     * @param   flag    flag, e.g. DATA
     * @return  true if the flag is set on the devices
     */
    public boolean getSignal(final EmulatedDevice device, final Object flag) {
        final DeviceState deviceState = getState(device);

        return deviceState.getSignal(flag);
    }

    /**
     * Set a flag on a connected device or the controller
     * 
     * @param   device  device to modify, can be the controller or a connected device
     * @param   flag    flag, e.g. DATA
     * @param   state   true if the flag is to be set on the devices
     * @return  true if the flag was modified, false if it remained in the same state as before
     */
    public boolean setSignal(final EmulatedDevice device, final Object flag, final boolean state) {
        final DeviceState deviceState = getState(device);
        final boolean oldState = getSignal(device, flag);

        deviceState.setSignal(flag, state);

        if (DEBUG && state != oldState && (flag != ATN || device == controller)) {
            System.out.print("ATNo: " + getSignal(this.controller, ATN) + ", CLKo: " + getSignal(this.controller, CLK) + ", DATAo: " + getSignal(this.controller, DATA) + ", CLKi: " + getDevicesSignal(CLK) + ", DATAi: " + getDevicesSignal(DATA));
            System.out.print(";  " + device.getName() + "-CPU before $" + Integer.toHexString(device.getCPU().pc));
            System.out.print("; call-stack:");

            final Vector stack = device.getCPU().getStackTrace();

            for (int i = 0; i < stack.size(); ++i) {
                System.out.print(" $" + Integer.toHexString(((Integer) stack.elementAt(i)).intValue()));
            }

            System.out.println();
        }

        // the devices should immediately react if the controller changes a flag
        if (state != oldState) {
            if (device == this.controller) {
                // special handling if ATN is set by the controller
                if (flag == ATN && state) {
                    // start attached devices if necessary
                    for (int i = 0, to = this.devices.size(); i < to; ++i) {
                        final EmulatedIECBusDevice attachedDevice = (EmulatedIECBusDevice) this.devices.elementAt(i);

                        // the device is not yet running?
                        if (attachedDevice.getCPU().getCycles() == 0) {
                            // start the device, it will run until it enters the wait loop
                            attachedDevice.run();
                            // we synchronize device and C64
                            attachedDevice.synchronizeWithDevice(this.controller);
                        } else if (!attachedDevice.isRunning()) {
                            // we synchronize device and C64
                            attachedDevice.synchronizeWithDevice(this.controller);
                        }
                    }

                    // notify observers about ATN from the controller
                    setChanged(true);
                    notifyObservers(SIGNAL_ATN);
                } else {
                    // notify observers about the signal change from the controller
                    setChanged(true);
                    notifyObservers(SIGNAL_CONTROLLER_FLAG_MODIFIED);
                }
            } else {
                // mark the device as currently active
                if (device instanceof EmulatedIECBusDevice) {
                    ((EmulatedIECBusDevice) device).markActive();
                }
            }

            return true;
        } else {
            return false;
        }
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        out.writeInt(this.devices.size());
        for (int i = 0; i < this.devices.size(); ++i) {
            final EmulatedDevice device = (EmulatedDevice) this.devices.elementAt(i);
            final DeviceState state = (DeviceState) this.deviceStates.elementAt(i);

            out.writeUTF(device.getName());
            state.serialize(out);
        }
        this.controllerState.serialize(out);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        final int size = in.readInt();

        for (int i = 0; i < size; ++i) {
            final String name = in.readUTF();
            boolean found = false;

            for (int j = 0; !found && j < this.devices.size(); ++j) {
                final EmulatedDevice device = (EmulatedDevice) this.devices.elementAt(j);
                final DeviceState state = new DeviceState();

                state.deserialize(in);
                if (device.getName().equals(name)) {
                    this.deviceStates.setElementAt(state, j);
                    found = true;
                }
            }

            if (!found) {
                throw new IOException("Could not read " + getClass().getName() + " from stream!");
            }
        }
        this.controllerState.deserialize(in);
    }

    // data structure for the state of a device connected to the bus
    class DeviceState implements Serializable {
        // contains the set flags

        public final Vector states = new Vector();

        /**
         * Check whether a given flag is set
         *
         * @param   flag    flag to check, e.g. ATN
         * @return  true if the flag is set
         */
        public boolean getSignal(final Object flag) {
            return this.states.contains(flag);
        }

        /**
         * Set or clear a given flag
         *
         * @param   flag    flag to modify, e.g. ATN
         * @param   state   true to set the flag, false to clear
         */
        public void setSignal(final Object flag, final boolean state) {
            if (state) {
                if (!getSignal(flag)) {
                    this.states.addElement(flag);
                }
            } else {
                this.states.removeElement(flag);
            }
        }

        // implementation of the Serializable interface
        public void serialize(final DataOutputStream out) throws IOException {
            out.writeInt(this.states.size());
            for (int i = 0; i < this.states.size(); ++i) {
                out.writeInt(((Integer) this.states.elementAt(i)).intValue());
            }
        }

        public void deserialize(final DataInputStream in) throws IOException {
            final int size = in.readInt();

            this.states.removeAllElements();
            for (int i = 0; i < size; ++i) {
                final int state = in.readInt();

                if (state == ATN.intValue()) {
                    this.states.addElement(ATN);
                } else if (state == CLK.intValue()) {
                    this.states.addElement(CLK);
                } else if (state == DATA.intValue()) {
                    this.states.addElement(DATA);
                } else {
                    throw new IOException("Could not read " + getClass().getName() + " from stream!");
                }
            }
        }
    }
}
