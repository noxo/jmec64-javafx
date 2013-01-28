/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.joergjahnke.common.emulation;

/**
 * Defines methods a CPU has to implement to be able to work with the PerformanceMeter class
 *
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public interface ThrottleableCPU {

    /**
     * Set the time the CPU should be throttled to limit it to the normal C64 speed
     *
     * @param   ms  number of milliseconds to pause
     */
    void throttle(final long ms);

    /**
     * Get the time the CPU was throttled since the last reset of the throttle counter.
     * This time is automatically increase with every call to throttle.
     *
     * @return  number of milliseconds the CPU was paused to limit it to the normal CPU speed
     */
    long getThrottledTime();

    /**
     * Reset the idle time counter to 0
     */
    void resetThrottleTime();
}
