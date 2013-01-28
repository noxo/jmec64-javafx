/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.common.emulation.RunnableDevice;
import de.joergjahnke.common.util.DefaultLogger;
import de.joergjahnke.common.vmabstraction.ResourceLoader;

/**
 * Class for an emulated device like the C64 or the C1541
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public abstract class EmulatedDevice extends RunnableDevice implements Runnable {

    /**
     * the devices name
     */
    private final String name;
    /**
     * the CPU of this device
     */
    protected final CPU6502 cpu;
    /**
     * used when loading system resource for the device
     */
    protected final ResourceLoader resourceLoader;
    /**
     * logs messages
     */
    private DefaultLogger logger = null;
    /**
     * was the CPU paused during the last performance measurement?
     */
    protected boolean wasPaused = false;

    /**
     * Create a new emulated device
     *
     * @param   name    device name, used for log file messages
     * @param   resourceLoader  ResourceLoader used when loading system resource for the device
     */
    public EmulatedDevice(final String name, final ResourceLoader resourceLoader) {
        this.name = name;
        this.resourceLoader = resourceLoader;

        // create the CPU
        this.cpu = createCPU();
    }

    /**
     * Get the name of the emulated device
     *
     * @return  device name given in the ctor
     */
    public final String getName() {
        return this.name;
    }

    /**
     * Get the CPU
     *
     * @return  device's CPU
     */
    public final CPU6502 getCPU() {
        return this.cpu;
    }

    /**
     * Get the logger of this instance
     *
     * @return  the central logger
     */
    public final DefaultLogger getLogger() {
        return this.logger;
    }

    /**
     * Set logger for this device
     *
     * @param logger    the logger to use
     */
    public final void setLogger(final DefaultLogger logger) {
        this.logger = logger;
        if (null != this.cpu) {
            this.cpu.setLogger(logger);
        }
    }

    /**
     * Stop the CPU
     */
    public void stop() {
        if (this.isRunning) {
            this.logger.info(this.name + " stopping");
            super.stop();
        }
    }

    /**
     * Pause the emulation
     *
     * @see de.joergjahnke.c64.EmulatedDevice#resume
     * @see de.joergjahnke.c64.EmulatedDevice#isPaused
     */
    public void pause() {
        if (isRunning() && !isPaused()) {
            super.pause();
            this.wasPaused = true;
            this.logger.info(this.name + " paused");
        }
    }

    /**
     * Continue the emulation
     *
     * @see de.joergjahnke.c64.EmulatedDevice#pause
     * @see de.joergjahnke.c64.EmulatedDevice#isPaused
     */
    public void resume() {
        if (isRunning() && isPaused()) {
            super.resume();
            this.logger.info(this.name + " resumed");
        }
    }

    /**
     * Reset the C64
     */
    public void reset() {
        this.logger.info(this.name + " resetting");

        // pause the emulator
        pause();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
        }

        this.cpu.reset();
        resetIOChips();

        // resume work again
        resume();

        this.logger.info(this.name + " reset done");
    }

    // implementation of the Runnable interface
    /**
     * The default implementation only reports that the device is now started
     */
    public void run() {
        this.logger.info("Starting " + name);
        super.run();
    }

    // abstract methods to be implemented by subclasses
    /**
     * Create the CPU instance
     *
     * @return  CPU of this device
     */
    protected abstract CPU6502 createCPU();

    /**
     * Reset all IO chips
     */
    protected abstract void resetIOChips();
}
