/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import java.util.Vector;

/**
 * Extension of the CIA6526 class for the CIA 2
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class CIA6526_2 extends CIA6526 {
    // serial bus pins
    /**
     * Serial attention out
     */
    public final static int SB_ATN_OUT = 1 << 3;
    /**
     * Serial clock out
     */
    public final static int SB_CLK_OUT = 1 << 4;
    /**
     * Serial data out
     */
    public final static int SB_DATA_OUT = 1 << 5;
    /**
     * Serial clock in
     */
    public final static int SB_CLK_IN = 1 << 6;
    /**
     * Serial data in
     */
    public final static int SB_DATA_IN = 1 << 7;
    /**
     * CIA2 PRA provides two additional address bits for the VIC
     */
    public final static Integer ADDRESS_PRA = new Integer(0xdd00);
    /**
     * signals we sent to observers
     * this little set of signals saves us the creation of a new Integer object each time a signal is sent
     */
    private final Vector signals = new Vector();

    /**
     * Creates a new instance of CIA6526_2
     *
     * @param   c64 the C64 we are attached to
     */
    public CIA6526_2(final C64 c64) {
        super(c64, 0xdd00);
        initDataDirectionRegisters();
        // initialize signals we may send
        for(int i = 0 ; i < this.registers.length ; ++i) {
            this.signals.addElement(new Integer(getOffset() + i));
        }
    }

    /**
     * Initialize the data direction registers
     */
    private void initDataDirectionRegisters() {
        this.registers[DDRA] = 0x3f;
        this.registers[DDRB] = 0;
    }

    public void reset() {
        super.reset();
        initDataDirectionRegisters();
    }

    /**
     * 
     */
    public int readRegister(final int register) {
        switch (register) {
            case PRA:
                return (super.readRegister(register) & 0x3f) | (this.c64.getIECBus().getSignal(IECBus.CLK) ? 0 : SB_CLK_IN) | (this.c64.getIECBus().getSignal(IECBus.DATA) ? 0 : SB_DATA_IN);

            default:
                return super.readRegister(register);
        }
    }

    /**
     * Also trigger IECBus and VIC if it is written to $dd00
     */
    public void writeRegister(final int register, final int data) {
        super.writeRegister(register, data);

        switch (register) {
            // Data Port A (Serial Bus, RS-232, VIC Memory Control)
            case PRA:
                // write data to IEC bus, a bit is high when it is set in PRA
                this.c64.getIECBus().setSignal(this.c64, IECBus.CLK, (data & SB_CLK_OUT) != 0);
                this.c64.getIECBus().setSignal(this.c64, IECBus.DATA, (data & SB_DATA_OUT) != 0);
                this.c64.getIECBus().setSignal(this.c64, IECBus.ATN, (data & SB_ATN_OUT) != 0);
                // notify other observers i.e. the VIC
                setChanged(true);
                notifyObservers(this.signals.elementAt(register));
                break;

            // otherwise do nothing
            default:
                ;
        }
    }

    // implementation of abstract methods of class CIA6526
    protected final void clearInterrupt() {
        this.cpu.setNMI(this, false);
    }

    protected final void triggerInterrupt() {
        this.cpu.setNMI(this, true);
    }
}
