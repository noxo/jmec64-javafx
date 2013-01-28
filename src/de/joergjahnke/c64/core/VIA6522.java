/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.io.SerializationUtils;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Implements the C1541's <a href='http://en.wikipedia.org/wiki/6522'>6522 Versatile Interface Adapter (VIA)</a> chip.<br>
 * <br>
 * For a (German) documentation on the VIA see <a href='http://www.htu.tugraz.at/~herwig/c64/via.php'>http://www.htu.tugraz.at/~herwig/c64/via.php</a>
 * or <a href='http://cbmmuseum.kuto.de/zusatz_6522_via.html'>http://cbmmuseum.kuto.de/zusatz_6522_via.html</a>.<br>
 * English documentation can be found at <a href='http://archive.6502.org/datasheets/mos_6522.pdf'>http://archive.6502.org/datasheets/mos_6522.pdf</a>.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 * @todo    implement shift register
 * @todo    implement PCR handling
 */
public class VIA6522 implements IOChip, Serializable {
    // do we print debug info?
    private final static boolean DEBUG = false;
    /**
     * Number of cycles after which we want a new VIA chip update
     */
    private final static int UPDATE_CYCLES = 20;
    /**
     * peripheral data register B
     */
    public final static int PRB = 0x00;
    /**
     * peripheral data register A
     */
    public final static int PRA = 0x01;
    /**
     * data direction register B
     */
    public final static int DDRB = 0x02;
    /**
     * data direction register A
     */
    public final static int DDRA = 0x03;
    /**
     * timer A low
     */
    public final static int TIMER_A_LOW = 0x04;
    /**
     * timer A high
     */
    public final static int TIMER_A_HIGH = 0x05;
    /**
     * timer A latch low
     */
    public final static int TIMER_A_LATCH_LOW = 0x06;
    /**
     * timer A latch high
     */
    public final static int TIMER_A_LATCH_HIGH = 0x07;
    /**
     * timer B low
     */
    public final static int TIMER_B_LOW = 0x08;
    /**
     * timer B high
     */
    public final static int TIMER_B_HIGH = 0x09;
    /**
     * shift register
     */
    public final static int SHIFT = 0x0a;
    /**
     * auxiliary control register
     */
    public final static int ACR = 0x0b;
    /**
     * peripheral control register
     */
    public final static int PCR = 0x0c;
    /**
     * interrupt flag register
     */
    public final static int IFR = 0x0d;
    /**
     * interrupt enable register
     */
    public final static int IER = 0x0e;
    /**
     * register A without handshake
     */
    public final static int PRA2 = 0x0f;
    // IRQ signals
    /**
     * IRQ line for a transition on CA2
     */
    protected final static int IRQ_TRANSITION_CA2 = 0x01;
    /**
     * IRQ line for a transition on CA1
     */
    protected final static int IRQ_TRANSITION_CA1 = 0x02;
    /**
     * IRQ line for a completion of eight shifts
     */
    protected final static int IRQ_SHIFT_COMPLETION = 0x04;
    /**
     * IRQ line for a transition on CB2
     */
    protected final static int IRQ_TRANSITION_CB2 = 0x08;
    /**
     * IRQ line for a transition on CB1
     */
    protected final static int IRQ_TRANSITION_CB1 = 0x10;
    /**
     * IRQ line for a timeout of timer B
     */
    protected final static int IRQ_TIMEOUT_TIMER_B = 0x20;
    /**
     * IRQ line for a timeout of timer A
     */
    protected final static int IRQ_TIMEOUT_TIMER_A = 0x40;
    /**
     * CPU cycles for the next update of the VIA
     */
    protected long nextUpdate = 0;
    /**
     * CPU cycles when the VIA was last updated
     */
    protected long lastUpdate = 0;
    /**
     * the C1541 instance we belong to
     */
    protected final C1541 c1541;
    /**
     * C1541 CPU
     */
    protected final CPU6502 cpu;
    /**
     * memory for chip registers
     */
    protected final int[] registers = new int[0x10];

    /**
     * Creates a new VIA instance.
     *
     * @param   c1541   the C1541 we are attached to
     */
    public VIA6522(final C1541 c1541) {
        this.c1541 = c1541;
        this.cpu = c1541.getCPU();
    }

    /**
     * Reset the VIA
     */
    public void reset() {
        for (int i = 0; i < registers.length; ++i) {
            this.registers[i] = 0;
        }
        this.lastUpdate = this.cpu.getCycles();
    }

    /**
     * Check whether to trigger or clear interrupt lines
     */
    protected void checkInterrupts() {
        if ((this.registers[IER] & this.registers[IFR]) != 0) {
            triggerInterrupt();
        } else {
            clearInterrupt();
        }
    }

    /**
     * Clear interrupt line
     */
    protected void clearInterrupt() {
        if (DEBUG && this.cpu.irqs.contains(this)) {
            System.out.println(this.getClass().getName() + ": clear IRQ");
        }
        this.cpu.setIRQ(this, false);
    }

    /**
     * Set interrupt line
     */
    protected void triggerInterrupt() {
        if (DEBUG && !this.cpu.irqs.contains(this)) {
            System.out.println(this.getClass().getName() + ": trigger IRQ " + Integer.toBinaryString(this.registers[IER] & this.registers[IFR]));
        }
        this.cpu.setIRQ(this, true);
    }

    /**
     * Synchronize the VIAs CPU-cycle-based counters with another device.
     *
     * @param   device  device to synchronize with
     */
    public void synchronizeWithDevice(final EmulatedDevice device) {
        this.nextUpdate = this.lastUpdate = device.getCPU().getCycles();
    }

    // implementation of the IOChip interface    
    public int readRegister(final int register) {
        switch (register) {
            // check interrupts and account for data direction registers when reading from data ports
            case PRA:
                this.registers[IFR] &= (0xff - IRQ_TRANSITION_CA2 - IRQ_TRANSITION_CA1);
                checkInterrupts();
                return this.registers[register] | ~this.registers[DDRA];
            case PRB:
                this.registers[IFR] &= (0xff - IRQ_TRANSITION_CB2 - IRQ_TRANSITION_CB1);
                checkInterrupts();
                return this.registers[register] | ~this.registers[DDRB];

            // when reading timer low values deactivate the corresponding timer IRQ flag
            case TIMER_A_LOW:
                this.registers[IFR] &= (0xff - IRQ_TIMEOUT_TIMER_A);
                checkInterrupts();
                return this.registers[register];
            case TIMER_B_LOW:
                this.registers[IFR] &= (0xff - IRQ_TIMEOUT_TIMER_B);
                checkInterrupts();
                return this.registers[register];

            case SHIFT:
                this.registers[IFR] &= (0xff - IRQ_SHIFT_COMPLETION);
                checkInterrupts();
                return this.registers[register];

            case IFR:
                return this.registers[IFR] | ((this.registers[IFR] & this.registers[IER]) != 0 ? 0x80 : 0);

            case IER:
                return this.registers[IER] | 0x80;

            case PRA2:
                return this.registers[PRA];

            // otherwise read result from register memory
            default:
                return this.registers[register];
        }
    }

    public void writeRegister(final int register, final int data) {
        switch (register) {
            // only write to bits allowed by the data direction registers
            case PRA:
                this.registers[IFR] &= (0xff - IRQ_TRANSITION_CA2 - IRQ_TRANSITION_CA1);
                this.registers[register] = (this.registers[register] & ~this.registers[DDRA]) | (data & this.registers[DDRA]);
                checkInterrupts();
                break;
            case PRB:
                this.registers[IFR] &= (0xff - IRQ_TRANSITION_CB2 - IRQ_TRANSITION_CB1);
                this.registers[register] = (this.registers[register] & ~this.registers[DDRB]) | (data & this.registers[DDRB]);
                break;

            // write to latch when writing to timer A low counter
            case TIMER_A_LOW:
                this.registers[TIMER_A_LATCH_LOW] = data;
                break;
            // writing to timer A high counter resets the timer
            case TIMER_A_HIGH:
                // write new value to latch and copy latch to timer
                this.registers[register] = this.registers[TIMER_A_LATCH_HIGH] = data;
                this.registers[TIMER_A_LOW] = this.registers[TIMER_A_LATCH_LOW];
                // clear interrupt for timer A
                this.registers[IFR] &= (0xff - IRQ_TIMEOUT_TIMER_A);
                checkInterrupts();
                break;
            // writing to timer B high counter resets the timer
            case TIMER_B_HIGH:
                this.registers[register] = data;
                this.registers[IFR] &= (0xff - IRQ_TIMEOUT_TIMER_B);
                checkInterrupts();
                break;

            case SHIFT:
                this.registers[register] = data;
                this.registers[IFR] &= (0xff - IRQ_SHIFT_COMPLETION);
                checkInterrupts();
                break;

            case IFR:
                this.registers[IFR] &= (~data);
                checkInterrupts();
                break;

            case IER:
                if (data >= 0x80) {
                    this.registers[IER] |= (data & 0x7f);
                } else {
                    this.registers[IER] &= (~data);
                }
                checkInterrupts();
                break;

            case PRA2:
                this.registers[PRA] = data;
                break;

            // otherwise simply store the data
            default:
                this.registers[register] = data;
                break;
        }
    }

    public final long getNextUpdate() {
        return this.nextUpdate;
    }

    public void update(final long cycles) {
        // update timers
        final int passed = (int) (cycles - this.lastUpdate);
        int ta = this.registers[TIMER_A_LOW] + (this.registers[TIMER_A_HIGH] << 8);

        // - subtract passed cycles from timer value
        ta -= passed;
        // - timer goes off?
        if (ta <= 0) {
            // continuous interrupt?
            if ((this.registers[ACR] & 0x40) == 0) {
                // then reload with latch value
                ta += (this.registers[TIMER_A_LATCH_LOW] + (this.registers[TIMER_A_LATCH_HIGH] << 8));
            }
            // set interrupt line
            this.registers[IFR] |= IRQ_TIMEOUT_TIMER_A;
            checkInterrupts();

            ta &= 0xffff;
        }
        this.registers[TIMER_A_LOW] = ta & 0xff;
        this.registers[TIMER_A_HIGH] = ta >> 8;
        // - do we have to update timer B?
        if ((this.registers[ACR] & 0x20) == 0) {
            int tb = this.registers[TIMER_B_LOW] + (this.registers[TIMER_B_HIGH] << 8);

            tb -= passed;
            // timer goes off?
            if (tb <= 0) {
                // set interrupt line
                this.registers[IFR] |= IRQ_TIMEOUT_TIMER_B;
                checkInterrupts();

                tb &= 0xffff;
            }
            this.registers[TIMER_B_LOW] = tb & 0xff;
            this.registers[TIMER_B_HIGH] = tb >> 8;
        }

        // prepare for next update
        this.nextUpdate += UPDATE_CYCLES;
        this.lastUpdate = cycles;
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        out.writeLong(this.lastUpdate);
        out.writeLong(this.nextUpdate);
        SerializationUtils.serialize(out, this.registers);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        this.lastUpdate = in.readLong();
        this.nextUpdate = in.readLong();
        SerializationUtils.deserialize(in, this.registers);
    }
}
