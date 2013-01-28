/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.emulation;

import de.joergjahnke.common.util.DefaultObservable;

/**
 * Class for a device that can be started in a separate thread
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public abstract class RunnableDevice extends DefaultObservable implements Runnable {

    /**
     * is the device currently running?
     */
    protected boolean isRunning = false;
    /**
     * are we suspended and pause the device?
     */
    protected boolean isPaused = false;

    /**
     * Has the device been started and not yet stopped?
     *
     * @return true if the device is running
     */
    public final boolean isRunning() {
        return this.isRunning;
    }

    /**
     * Stop the device
     */
    public void stop() {
        pause();
        this.isRunning = false;
    }

    /**
     * Is the device paused?
     *
     * @return true if the device is paused
     * @see de.joergjahnke.common.emulation.RunnableDevice#pause
     * @see de.joergjahnke.common.emulation.RunnableDevice#resume
     */
    public boolean isPaused() {
        return this.isPaused;
    }

    /**
     * Pause the emulation
     *
     * @see de.joergjahnke.common.emulation.RunnableDevice#resume
     * @see de.joergjahnke.common.emulation.RunnableDevice#isPaused
     */
    public void pause() {
        this.isPaused = true;
    }

    /**
     * Continue the emulation
     *
     * @see de.joergjahnke.common.emulation.RunnableDevice#pause
     * @see de.joergjahnke.common.emulation.RunnableDevice#isPaused
     */
    public void resume() {
        this.isPaused = false;
    }

    /**
     * The default implementation only marks the device as started
     */
    public void run() {
        this.isPaused = false;
        this.isRunning = true;
    }
}
