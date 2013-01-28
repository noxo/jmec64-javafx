/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.emulation;

import de.joergjahnke.common.util.DefaultObservable;

/**
 * Class for measuring and, if necessary, throttling the performance of the emulator
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class PerformanceMeter extends DefaultObservable {

    /**
     * percentage of original CPU speed we try to reach each test invervall
     */
    private final static int SPEED_TARGET_PERCENTAGE = 100;
    /**
     * minimum time for throttling the CPU
     * the CPU timers are not too accurate and sleep intervals might have a minimum
     */
    private final static int MINIMUM_WAIT_TIME = 30;
    /**
     * performance measurement interval in ms
     */
    private final static long PERFORMANCE_MEASUREMENT_INTERVAL_SECONDS = 10 * 1000;
    /**
     * CPU to throttle
     */
    private final ThrottleableCPU cpu;
    /**
     * the target speed of the emulation in CPU cycles per second
     */
    private int targetSpeed;
    /**
     * percentage of time the CPU was throttled to limit it to the speed of the original CPU
     */
    private int lastThrottle = 0;
    /**
     * used for correcting the emulator speed if we are too fast
     */
    private long lastCorrectionTime = 0;
    private long lastCorrectionCycles = 0;
    /**
     * the time when we next measure the CPU performance
     */
    private long nextPerformanceMeasurementTime;
    /**
     * the time when we initialized the last performance measurement
     */
    private long started;
    /**
     * last performance in % of the original CPU speed
     */
    private int lastPerformance = 0;
    /**
     * do we throttle the emulator to limit it to 100% of the original CPU speed?
     */
    private boolean doThrottling = true;

    /**
     * Create a new performance meter
     * 
     * @param   cpu CPU to measure and throttle
     * @param   targetSpeed target CPU speed in cycles per second
     */
    public PerformanceMeter(final ThrottleableCPU cpu, final int targetSpeed) {
        this.cpu = cpu;
        this.targetSpeed = targetSpeed;
    }

    /**
     * Get the current target speed
     * 
     * @return  target speed in Hz
     */
    public int getTargetSpeed() {
        return this.targetSpeed;
    }

    /**
     * Set a new target speed
     * 
     * @param   targetSpeed target CPU speed in Hz
     */
    public void setTargetSpeed(final int targetSpeed) {
        this.targetSpeed = targetSpeed;
    }

    /**
     * Throttle the CPU to a maximum of 100% of the original CPU speed, if wanted, and
     * measure the speed of the emulation.
     * 
     * @param   cycles  current CPU cycles
     */
    public void measure(final long cycles) {
        if (this.lastCorrectionTime == 0) {
            setupNextMeasurement(cycles);
        } else {

            if (isDoThrottling()) {
                // calculate time and CPU cycles difference from last measurement
                final long timeDiff = System.currentTimeMillis() - this.lastCorrectionTime;
                final long cyclesDiff = cycles - this.lastCorrectionCycles;
                // this is what we would have expected for out target CPU speed
                final long expectedCycles = timeDiff * getTargetSpeed() / 1000 * SPEED_TARGET_PERCENTAGE / 100;

                // we have more cycles processed?
                if (cyclesDiff > expectedCycles) {
                    // then calculate how long we should wait
                    final long waitTime = 1000 * (cyclesDiff - expectedCycles) / getTargetSpeed();

                    // this is at least the minimum wait time?
                    if (waitTime >= MINIMUM_WAIT_TIME) {
                        // then wait for the calculated time
                        this.cpu.throttle(waitTime);
                    }
                }
            }

            // time for the next performance measurement?
            if (System.currentTimeMillis() > this.nextPerformanceMeasurementTime) {
                // get the new cycle count
                final long executed = cycles - this.started;

                // calculate the percentage of time the CPU was set to idle to limit it to CPU speed
                this.lastThrottle = (int) (100.0 * this.cpu.getThrottledTime() / PERFORMANCE_MEASUREMENT_INTERVAL_SECONDS);
                this.cpu.resetThrottleTime();
                // compare this with the expected CPU cycles count for this interval and log this
                this.lastPerformance = (int) (100.0 * executed / this.getTargetSpeed() / (PERFORMANCE_MEASUREMENT_INTERVAL_SECONDS / 1000));
                // notify observers about the new measurements
                setChanged(true);
                notifyObservers("Emulator working at " + this.lastPerformance + "% of original CPU performance" + (this.lastThrottle > 0 ? ", " + this.lastThrottle + "% idle time" : ""));

                setupNextMeasurement(cycles);
            }
        }
    }

    /**
     * Get the last measured performance
     * 
     * @return  performance in % of the original CPU speed
     */
    public int getLastPerformance() {
        return this.lastPerformance;
    }

    /**
     * Get the result of the last throttle time measurement
     *
     * @return  percentage the CPU was throttled during the last measurement interval
     */
    public int getThrottlePercentage() {
        return this.lastThrottle;
    }

    /**
     * Reset measurements used for CPU throttling
     *
     * @param cycles    the current number of CPU cycles
     */
    public void resetThrottleMeasurement(final long cycles) {
        this.lastCorrectionTime = System.currentTimeMillis();
        this.lastCorrectionCycles = cycles;
    }

    /**
     * Setup the next measurement
     *
     * @param cycles    the current number of CPU cycles
     */
    public void setupNextMeasurement(final long cycles) {
        resetThrottleMeasurement(cycles);
        this.started = cycles;
        this.nextPerformanceMeasurementTime = System.currentTimeMillis() + PERFORMANCE_MEASUREMENT_INTERVAL_SECONDS;
    }

    /**
     * Do we throttle the emulator to limit it to 100% of the original CPU speed?
     * 
     * @return  true if the emulator is throttled to limit its speed, false if it can run as fast as possible
     */
    public boolean isDoThrottling() {
        return this.doThrottling;
    }

    /**
     * Set whether the emulator is allowed to throttle its performance to max. 100% of the original CPU speed
     * 
     * @param   doThrottling    true to throttle the emulator if it runs too fast, false if it should never be throttled
     */
    public void setDoThrottling(final boolean doThrottling) {
        this.doThrottling = doThrottling;
    }
}
