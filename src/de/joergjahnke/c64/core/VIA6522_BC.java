/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.common.util.Observer;

/**
 * Extension of the VIA6522 class for the Floppy Bus Controller
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class VIA6522_BC extends VIA6522 implements Observer {
    // serial bus pins
    /**
     * Serial data in
     */
    private final static int SB_DATA_IN = 1 << 0;
    /**
     * Serial data out
     */
    public final static int SB_DATA_OUT = 1 << 1;
    /**
     * Serial clock in
     */
    private final static int SB_CLK_IN = 1 << 2;
    /**
     * Serial clock out
     */
    public final static int SB_CLK_OUT = 1 << 3;
    /**
     * Serial attention out
     */
    public final static int SB_ATN_OUT = 1 << 4;
    /**
     * Serial attention in
     */
    public final static int SB_ATN_IN = 1 << 7;
    // the IECBus the VIA is connected to
    private final IECBus iecBus;

    /**
     * Creates a new instance of VIA6522_BC
     *
     * @param   c1541   the C1541 we are attached to
     * @param   iecBus  IECBus connected to the VIA chip
     */
    public VIA6522_BC(final C1541 c1541, final IECBus iecBus) {
        super(c1541);
        this.iecBus = iecBus;
    }

    private void updateDataLine() {
        final EmulatedDevice controller = this.iecBus.getController();
        boolean isData = this.iecBus.getSignal(this.c1541, IECBus.DATA);

        isData |= (this.iecBus.getSignal(controller, IECBus.ATN) ^ (super.readRegister(PRB) & SB_ATN_OUT) != 0);

        this.iecBus.setSignal(this.c1541, IECBus.DATA, isData);
    }

    /**
     * Include bits from IECBus when reading from PRB
     */
    public int readRegister(final int register) {
        switch (register) {
            case PRB:
                return (super.readRegister(register) & 0x1a) | this.c1541.getID() << 5 | (this.iecBus.getSignal(IECBus.ATN) ? SB_ATN_IN : 0) | (this.iecBus.getSignal(IECBus.CLK) ? SB_CLK_IN : 0) | (this.iecBus.getSignal(IECBus.DATA) ? SB_DATA_IN : 0);

            case PRA2:
                return 0xff;

            default:
                return super.readRegister(register);
        }
    }

    /**
     * Also update IECBus when PRB or DDRB gets modified
     */
    public void writeRegister(final int register, final int data) {
        super.writeRegister(register, data);

        switch (register) {
            case PRB:
            case DDRB:
                // we set the flags for CLK and DATA on the IEC bus, a bit on the bus is true (low) when it is set in PRB
                this.iecBus.setSignal(this.c1541, IECBus.CLK, (super.readRegister(PRB) & SB_CLK_OUT) != 0);
                this.iecBus.setSignal(this.c1541, IECBus.DATA, (super.readRegister(PRB) & SB_DATA_OUT) != 0);
                updateDataLine();
                break;

            // otherwise do nothing
            default:
                ;
        }
    }

    // implementation of the Observer interface
    /**
     * raise IRQ if the IEC buses ATN signal was set to high
     */
    public void update(final Object observed, final Object arg) {
        // a signal from the IEC bus?
        if (observed == this.iecBus) {
            // ATN was set high?
            if (IECBus.SIGNAL_ATN == arg) {
                // then set the corresponding interrupt flag, activate the data line and check interrupts
                this.registers[IFR] |= IRQ_TRANSITION_CA1;
                this.registers[PRB] |= SB_DATA_OUT;
                checkInterrupts();
            }
            // a flag was modified?
            if (IECBus.SIGNAL_ATN == arg || IECBus.SIGNAL_CONTROLLER_FLAG_MODIFIED == arg) {
                updateDataLine();
            }
        }
    }
}
