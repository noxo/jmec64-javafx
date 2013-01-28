/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.io.SerializationUtils;
import de.joergjahnke.common.util.DefaultObservable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Implements the C64's <a href='http://en.wikipedia.org/wiki/MOS_Technology_CIA'>6526 Complex Interface Adapter (CIA)</a> chip.<br>
 * <br>
 * For a (German) documentation on the CIA registers see <a href='http://www.infinite-loop.at/Power64/Documentation/Power64-LiesMich/AD-Spezialbausteine.html#Section%20D.3.'>http://www.infinite-loop.at/Power64/Documentation/Power64-LiesMich/AD-Spezialbausteine.html#Section%20D.3</a>.<br>
 * Some more documentation can be found at <a href='http://cbmmuseum.kuto.de/zusatz_6526_cia.html'>http://cbmmuseum.kuto.de/zusatz_6526_cia.html</a>
 * or at <a href='http://archive.6502.org/datasheets/mos_6526_cia.pdf'>http://archive.6502.org/datasheets/mos_6526_cia.pdf</a>
 * or at <a href='http://www.c64-wiki.de/index.php/CIA'>http://www.c64-wiki.de/index.php/CIA</a>.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public abstract class CIA6526 extends DefaultObservable implements IOChip, Serializable {
    // debug flag for the timer
    private final static boolean TIMER_DEBUG = false;
    /**
     * Number of cycles after which we want a new CIA chip update
     */
    private final static int UPDATE_CYCLES = 37;
    /**
     * Delay when we start a new timer
     */
    private final static int START_DELAY = 2;
    /**
     * Delay when we restart a timer
     */
    private final static int RESTART_DELAY = 1;
    /**
     * peripheral data register A
     */
    public final static int PRA = 0x00;
    /**
     * peripheral data register B
     */
    public final static int PRB = 0x01;
    /**
     * data direction register A
     */
    public final static int DDRA = 0x02;
    /**
     * data direction register B
     */
    public final static int DDRB = 0x03;
    /**
     * timer A low
     */
    private final static int TIMER_A_LOW = 0x04;
    /**
     * timer A high
     */
    private final static int TIMER_A_HIGH = 0x05;
    /**
     * timer B low
     */
    private final static int TIMER_B_LOW = 0x06;
    /**
     * timer B high
     */
    private final static int TIMER_B_HIGH = 0x07;
    /**
     * day time counter 1/10 seconds
     */
    private final static int TIMEOFDAY_TEN = 0x08;
    /**
     * day time counter seconds
     */
    private final static int TIMEOFDAY_SEC = 0x09;
    /**
     * day time counter minutes
     */
    private final static int TIMEOFDAY_MIN = 0x0a;
    /**
     * day time counter hours
     */
    private final static int TIMEOFDAY_HRS = 0x0b;
    /**
     * serial data register
     */
    private final static int SDR = 0x0c;
    /**
     * Interrupt Control Register
     */
    private final static int ICR = 0x0d;
    /**
     * control register A
     */
    private final static int CRA = 0x0e;
    /**
     * control register B
     */
    private final static int CRB = 0x0f;
    /**
     * Timer A
     */
    private final Timer timerA = new Timer(getClass().getName() + " (Timer A)");
    /**
     * Timer B
     */
    private final Timer timerB = new Timer(getClass().getName() + " (Timer B)");
    /**
     * IRQ control register value
     */
    private int icr;
    /**
     * Control register B value
     */
    private int crb = 0;
    /**
     * IRQ mask
     */
    protected int irqMask = 0;
    /**
     * Alarm time 1/10 seconds
     */
    private int alarmTen = 0;
    /**
     * Alarm time seconds
     */
    private int alarmSec = 0;
    /**
     * Alarm time minutes
     */
    private int alarmMin = 0;
    /**
     * Alarm time hours
     */
    private int alarmHrs = 0;
    /**
     * Time of day time 1/10 seconds
     */
    private int todTen = 0;
    /**
     * Time of day time seconds
     */
    private int todSec = 0;
    /**
     * Time of day time minutes
     */
    private int todMin = 0;
    /**
     * Time of day time hours
     */
    private int todHrs = 0;
    /**
     * CPU cycle when we have to update the day time the next time
     */
    private long nextDayTimeUpdate = 0;
    /**
     * CPU cycles for the next update of the CIA
     */
    private long nextUpdate = 0;
    /**
     * offset of CIA memory in CPU memory ($dc00 or $dd00)
     */
    private int offset;
    /**
     * the C64 instance we belong to
     */
    protected final C64 c64;
    /**
     * C64 CPU
     */
    protected final CPU6502 cpu;
    /**
     * memory for chip registers
     */
    protected final int[] registers = new int[0x10];

    /**
     * Creates a new CIA instance.
     *
     * @param   c64 the C64 we are attached to
     * @param   offset  CIA's memory offset
     */
    public CIA6526(final C64 c64, final int offset) {
        this.c64 = c64;
        this.cpu = c64.getCPU();
        this.offset = offset;
    }

    /**
     * Get the CIA base address
     *
     * @return  0xdc00 or 0xdd00
     */
    public int getOffset() {
        return this.offset;
    }

    /**
     * Reset the CIA
     */
    public void reset() {
        this.icr = 0;
        this.irqMask = 0;
        this.crb = 0;
        this.alarmTen = this.alarmSec = this.alarmMin = this.alarmHrs = 0;
        this.timerA.reset();
        this.timerB.reset();
    }

    /**
     * Trigger an interrupt if the corresponding IRQ mask bit is set
     *
     * @param   bit bit to set in the ICR register and to check in the mask
     */
    protected void setInterrupt(final int bit) {
        this.icr |= bit;
        if ((this.irqMask & bit) != 0) {
            this.icr |= 0x80;
            triggerInterrupt();

            if (TIMER_DEBUG && this.c64.getLogger() != null) {
                this.c64.getLogger().info(getClass().getName() + ": Timer interrupt on " + this.cpu.getCycles());
            }
        }
    }

    // implementation of the IOChip interface    
    public int readRegister(final int register) {
        switch (register) {
            case PRA:
                return this.registers[register] | ~this.registers[DDRA];
            case PRB:
                return this.registers[register] | ~this.registers[DDRB];

            case TIMER_A_LOW:
                return (int) ((this.timerA.isActive ? Math.max(0, this.timerA.nextTrigger - this.cpu.getCycles()) : this.timerA.timeLeft) & 0xff);
            case TIMER_A_HIGH:
                return (int) ((this.timerA.isActive ? Math.max(0, this.timerA.nextTrigger - this.cpu.getCycles()) : this.timerA.timeLeft) >> 8);
            case TIMER_B_LOW:
                return (int) ((this.timerB.isActive ? Math.max(0, this.timerB.nextTrigger - this.cpu.getCycles()) : this.timerB.timeLeft) & 0xff);
            case TIMER_B_HIGH:
                return (int) ((this.timerB.isActive ? Math.max(0, this.timerB.nextTrigger - this.cpu.getCycles()) : this.timerB.timeLeft) >> 8);

            case TIMEOFDAY_TEN:
                return this.todTen;
            case TIMEOFDAY_SEC:
                return this.todSec;
            case TIMEOFDAY_MIN:
                return this.todMin;
            case TIMEOFDAY_HRS:
                return this.todHrs;

            case ICR: {
                // read and clear ICR and triggered IRQs
                final int result = this.icr;

                this.icr = 0;
                clearInterrupt();

                if (TIMER_DEBUG && this.c64.getLogger() != null) {
                    this.c64.getLogger().info(getClass().getName() + ": Read ICR: " + Integer.toHexString(result));
                }

                return result;
            }

            // otherwise read result from memory
            default:
                return this.registers[register];
        }
    }

    public void writeRegister(final int register, final int data) {
        switch (register) {
            // only write to bits allowed by the data direction registers
            case PRA:
                this.registers[register] = (this.registers[register] & ~this.registers[DDRA]) | (data & this.registers[DDRA]);
                break;
            case PRB:
                this.registers[register] = (this.registers[register] & ~this.registers[DDRB]) | (data & this.registers[DDRB]);
                break;

            // update timer latch value low/high
            case TIMER_A_LOW:
                this.timerA.latch &= 0xff00;
                this.timerA.latch |= data;
                break;
            case TIMER_A_HIGH:
                this.timerA.latch &= 0xff;
                this.timerA.latch |= (data << 8);
                break;
            case TIMER_B_LOW:
                this.timerB.latch &= 0xff00;
                this.timerB.latch |= data;
                break;
            case TIMER_B_HIGH:
                this.timerB.latch &= 0xff;
                this.timerB.latch |= (data << 8);
                break;

            // update alarm or time of day, depending on the value of CRB bit 7
            case TIMEOFDAY_TEN:
                if ((this.crb & 0x80) != 0) {
                    this.alarmTen = data;
                } else {
                    this.todTen = data;
                }
                break;
            case TIMEOFDAY_SEC:
                if ((this.crb & 0x80) != 0) {
                    this.alarmSec = data;
                } else {
                    this.todSec = data;
                }
                break;
            case TIMEOFDAY_MIN:
                if ((this.crb & 0x80) != 0) {
                    this.alarmMin = data;
                } else {
                    this.todMin = data;
                }
                break;
            case TIMEOFDAY_HRS:
                if ((this.crb & 0x80) != 0) {
                    this.alarmHrs = data;
                } else {
                    this.todHrs = data;
                }
                break;

            // trigger SDR interrupt
            case SDR:
                setInterrupt(1 << 3);
                this.registers[register] = data;
                break;

            // CIA Interrupt Control Register
            case ICR: {
                // bit 7 defines whether IRQs should be triggered or not
                final boolean doesTriggerIRQs = (data & 0x80) != 0;

                // set the IRQ mask according to the given data
                if (doesTriggerIRQs) {
                    this.irqMask |= (data & 0x7f);
                } else {
                    this.irqMask &= (~data);
                }

                // trigger IRQ if necessary
                setInterrupt(this.irqMask & this.icr);
                break;
            }

            // control register A controls timer A
            case CRA:
                this.timerA.applyControlRegister(data);
                this.registers[register] = data;
                break;
            // control register B controls timer B
            case CRB:
                this.crb = data;
                this.timerB.applyControlRegister(data);
                this.registers[register] = data;
                break;

            // otherwise do nothing
            default:
                this.registers[register] = data;
                break;
        }
    }

    public final long getNextUpdate() {
        return this.nextUpdate;
    }

    public void update(final long cycles) {
        this.nextUpdate += UPDATE_CYCLES;

        // Timer A triggers?
        if (this.timerA.isActive && cycles >= this.timerA.nextTrigger) {
            // Timer A corresponds to bit 0 in the IRQ mask
            setInterrupt(1 << 0);

            // we trigger more than once?
            if (!this.timerA.isOneShot) {
                // yes, set new trigger time
                this.timerA.nextTrigger += this.timerA.latch + RESTART_DELAY;
            } else {
                // no, this was a one-shot timer
                this.timerA.isActive = false;
                // clear bit 0 of CRA to indicate stop
                this.registers[CRA] &= 0xfe;
            }
        }

        // Timer B triggers?
        if (this.timerB.isActive && cycles >= this.timerB.nextTrigger) {
            // Timer B corresponds to bit 0 in the IRQ mask
            setInterrupt(1 << 1);

            // we trigger more than once?
            if (!this.timerB.isOneShot) {
                // yes, set new trigger time
                // count only on timer A triggers?
                if ((this.crb & 0x60) == 0x40) {
                    this.timerB.nextTrigger += this.timerB.latch * this.timerA.latch;
                } else {
                    this.timerB.nextTrigger += this.timerB.latch;
                }
                // also apply restart delay
                this.timerB.nextTrigger += RESTART_DELAY;
            } else {
                this.timerB.isActive = false;
                // clear bit 0 of CRB to indicate stop
                this.crb = this.registers[CRB] &= 0xfe;
            }
        }

        // update day time timer?
        if (cycles >= this.nextDayTimeUpdate) {
            this.nextDayTimeUpdate += 100000;

            // increase 1/10th seconds counter
            this.todTen &= 0x0f;
            ++this.todTen;
            // another second has passed?
            if (this.todTen > 9) {
                this.todTen %= 10;
                // increase second counter (BCD)
                this.todSec &= 0x7f;
                if ((++this.todSec & 0x0f) > 9) {
                    this.todSec += 6;
                }
                // another minute has passed?
                if (this.todSec >= 0x60) {
                    this.todSec %= 0x60;
                    // increase minute counter (BCD)
                    this.todMin &= 0x7f;
                    if ((++this.todMin & 0x0f) > 9) {
                        this.todMin += 6;
                    }
                    // another hour has passed?
                    if (this.todMin >= 0x60) {
                        this.todMin %= 0x60;
                        // increase hour counter (BCD, 0x00-0x11 + am/pm in bit 7)
                        this.todHrs &= 0x9f;

                        if ((++this.todHrs & 0x0f) > 9) {
                            this.todHrs += 6;
                            if ((this.todHrs & 0x7f) >= 0x12) {
                                this.todHrs ^= 0x80;
                                this.todHrs &= 0x80;
                            }
                        }
                    }
                }
            }

            // check if we raise the alarm...
            if (this.alarmTen == this.todTen && this.alarmSec == this.todSec && this.alarmMin == this.todMin && this.alarmHrs == this.todHrs) {
                // ...which corresponds to bit 2 in the IRQ mask
                setInterrupt(1 << 2);
            }
        }

        // determine next update time
        if (this.timerA.isActive && this.timerA.nextTrigger < this.nextUpdate) {
            this.nextUpdate = this.timerA.nextTrigger;
        }
        if (this.timerB.isActive && this.timerB.nextTrigger < this.nextUpdate) {
            this.nextUpdate = this.timerB.nextTrigger;
        }
        if (this.nextDayTimeUpdate < this.nextUpdate) {
            this.nextUpdate = this.nextDayTimeUpdate;
        }
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        out.writeInt(this.alarmHrs);
        out.writeInt(this.alarmMin);
        out.writeInt(this.alarmSec);
        out.writeInt(this.alarmTen);
        out.writeInt(this.crb);
        out.writeInt(this.icr);
        out.writeInt(this.irqMask);
        out.writeLong(this.nextDayTimeUpdate);
        out.writeLong(this.nextUpdate);
        out.writeInt(this.offset);
        out.writeInt(this.todHrs);
        out.writeInt(this.todMin);
        out.writeInt(this.todSec);
        out.writeInt(this.todTen);
        SerializationUtils.serialize(out, this.registers);
        this.timerA.serialize(out);
        this.timerB.serialize(out);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        this.alarmHrs = in.readInt();
        this.alarmMin = in.readInt();
        this.alarmSec = in.readInt();
        this.alarmTen = in.readInt();
        this.crb = in.readInt();
        this.icr = in.readInt();
        this.irqMask = in.readInt();
        this.nextDayTimeUpdate = in.readLong();
        this.nextUpdate = in.readLong();
        this.offset = in.readInt();
        this.todHrs = in.readInt();
        this.todMin = in.readInt();
        this.todSec = in.readInt();
        this.todTen = in.readInt();
        SerializationUtils.deserialize(in, this.registers);
        this.timerA.deserialize(in);
        this.timerB.deserialize(in);
    }

    // abstract methods subclasses have to implement
    /**
     * Clear the corresponding interrupt of the CPU where this CIA is connected to
     */
    protected abstract void clearInterrupt();

    /**
     * Set the corresponding interrupt of the CPU where this CIA is connected to
     */
    protected abstract void triggerInterrupt();

    // inner class implementing a timer
    class Timer implements Serializable {

        /**
         * Timer name
         */
        private final String name;
        /**
         * Is the timer active
         */
        public boolean isActive = false;
        /**
         * Time left to run
         */
        public int timeLeft = 0;
        /**
         * Cycle when the timer triggers the next time
         */
        public long nextTrigger = 0;
        /**
         * Value to fill timer with
         */
        public int latch = 0;
        /**
         * Only trigger an IRQ once?
         */
        public boolean isOneShot = false;
        /**
         * Signal timer underflow on PB
         */
        public boolean isSignalUnderflowOnPB = false;
        /**
         * If timer underflow is signaled on PB, does this toggle every cycle?
         */
        public boolean isPBSignalToggle = false;

        /**
         * Create a new timer with a given name
         *
         * @param   name    timer name
         */
        public Timer(final String name) {
            this.name = name;
        }

        /**
         * Reset the timer
         */
        public void reset() {
            this.isActive = false;
        }

        /**
         * Start the timer
         */
        public void start() {
            this.isActive = true;
            this.nextTrigger = cpu.getCycles() + this.latch + START_DELAY;

            if (TIMER_DEBUG && c64.getLogger() != null) {
                c64.getLogger().info(this.name + ": Starting timer: " + this.latch + ", isOneShot: " + this.isOneShot + ", triggered on: " + this.nextTrigger);
            }
        }

        /**
         * Stop the timer
         */
        public void stop() {
            this.isActive = false;
            this.timeLeft = Math.max(0, (int) (this.nextTrigger - cpu.getCycles()));

            if (TIMER_DEBUG && c64.getLogger() != null) {
                c64.getLogger().info(this.name + ": Stopping timer");
            }
        }

        /**
         * Apply the new value of the control register to the timer
         *
         * @param   data    control register value
         */
        public void applyControlRegister(final int data) {
            // bit 3 = trigger an IRQ only once?
            this.isOneShot = ((data & 0x08) > 0);

            // bit 0 = start/stop of the timer
            if ((data & 0x01) != 0) {
                // starting timer A
                start();
            } else {
                // stopping timer A
                stop();
            }

            // bit 1 = signal underflow of timer A on PB6
            this.isSignalUnderflowOnPB = (data & 0x02) != 0;

            // bit 2 = mode how underflow of timer A is shown on PB6
            this.isPBSignalToggle = (data & 0x04) != 0;

            // bit 4 = force load latch value
            if ((data & 0x10) != 0) {
                this.nextTrigger = cpu.getCycles() + this.latch + START_DELAY;
            }
        }

        // implementation of the Serializable interface
        public void serialize(final DataOutputStream out) throws IOException {
            out.writeBoolean(this.isActive);
            out.writeBoolean(this.isOneShot);
            out.writeBoolean(this.isPBSignalToggle);
            out.writeBoolean(this.isSignalUnderflowOnPB);
            out.writeInt(this.latch);
            out.writeInt(this.timeLeft);
            out.writeLong(this.nextTrigger);
        }

        public void deserialize(final DataInputStream in) throws IOException {
            this.isActive = in.readBoolean();
            this.isOneShot = in.readBoolean();
            this.isPBSignalToggle = in.readBoolean();
            this.isSignalUnderflowOnPB = in.readBoolean();
            this.latch = in.readInt();
            this.timeLeft = in.readInt();
            this.nextTrigger = in.readLong();
        }
    }
}
