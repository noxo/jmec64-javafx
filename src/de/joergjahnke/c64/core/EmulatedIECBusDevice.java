/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.common.vmabstraction.ResourceLoader;

/**
 * Extends the EmulatedDevice class with methods for devices connected to the IEC bus
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public abstract class EmulatedIECBusDevice extends EmulatedDevice {

    /**
     * The CPU cycle when the device was last active
     */
    protected long lastActiveCycle = 0;
    /**
     * Is the device currently sleeping?
     */
    protected boolean isSleeping = false;
    /**
     * the next update if we synchronize with the CPU
     */
    protected long nextUpdate = Long.MAX_VALUE;

    /**
     * Create a new emulated IEC bus device
     *
     * @param   name    device name, used for log file messages
     * @param   resourceLoader  ResourceLoader used when loading system resource for the device
     */
    public EmulatedIECBusDevice(final String name, final ResourceLoader resourceLoader) {
        super(name, resourceLoader);
    }

    /**
     * We stop without issueing a log message and check whether we can put the device to sleep
     */
    public void stop() {
        if (this.isRunning) {
            this.isRunning = false;

            // the device was inactive for too long?
            if (this.cpu.getCycles() - this.lastActiveCycle > getDeactivationPeriod()) {
                // then we won't request another update...
                this.nextUpdate = Long.MAX_VALUE;
                // ...and sleep instead
                getLogger().info(getName() + " sleeping");
                this.isSleeping = true;
            }
        }
    }

    /**
     * Synchronize the floppy with another device.
     * This method sets the floppy cycle count to that of the other device.
     *
     * @param   device  device to synchronize with
     */
    public void synchronizeWithDevice(final EmulatedDevice device) {
        if (this.isSleeping) {
            getLogger().info(getName() + " waking up");
            this.isSleeping = false;
        }
        this.cpu.setCycles(device.getCPU().getCycles());
        this.nextUpdate = device.getCPU().getCycles();
        markActive();
    }

    /**
     * Mark that the device was active at a given CPU cycle count.
     * This information can be used to let the device sleep when it was inactive for some time.
     */
    public void markActive() {
        this.lastActiveCycle = this.cpu.getCycles();
    }

    // abstract methods to be implemented by subclasses
    /**
     * Get the number of CPU cycles that have to pass inactive until we put the device to sleep
     * 
     * @return  number of cycles after which the device may sleep
     */
    protected abstract long getDeactivationPeriod();
}
