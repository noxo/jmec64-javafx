/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Extension of the CIA6526 class for the CIA 1
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class CIA6526_1 extends CIA6526 {

    /**
     * Number of reads from PRB
     */
    private int prbRead = 0;

    /**
     * Creates a new instance of CIA6526_1
     *
     * @param   c64 the C64 we are attached to
     */
    public CIA6526_1(final C64 c64) {
        super(c64, 0xdc00);
        initDataDirectionRegisters();
    }

    /**
     * Initialize the data direction registers
     */
    private void initDataDirectionRegisters() {
        this.registers[DDRA] = 0xff;
        this.registers[DDRB] = 0;
    }

    /**
     * Return the number of times register PRB was read.
     * This can be used to determine whether the C64 has finished booting, as approximately 20
     * reads of CIA 1 PRB take place until the C64 has booted after a reset.
     *
     * @return  number of read operations to register PRB
     */
    public int getPRBReads() {
        return this.prbRead;
    }

    public void reset() {
        super.reset();
        this.prbRead = 0;
        initDataDirectionRegisters();
    }

    /**
     * Add values from the keyboard matrix to return values, when reading from PRA and PRB
     */
    public int readRegister(final int register) {
        switch (register) {
            case PRA:
                return super.readRegister(register) & this.c64.getKeyboard().getCIAPRAAdjustment(super.readRegister(PRB) & this.c64.getJoystick(0).getValue()) & this.c64.getJoystick(1).getValue();
            case PRB:
                ++this.prbRead;
                return super.readRegister(register) & this.c64.getKeyboard().getCIAPRBAdjustment(super.readRegister(PRA) & this.c64.getJoystick(1).getValue()) & this.c64.getJoystick(0).getValue();
            default:
                return super.readRegister(register);
        }
    }

    public void serialize(final DataOutputStream out) throws IOException {
        super.serialize(out);
        out.writeInt(this.prbRead);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        super.deserialize(in);
        this.prbRead = in.readInt();
    }

    // implementation of abstract methods of class CIA6526
    protected final void clearInterrupt() {
        this.cpu.setIRQ(this, false);
    }

    protected final void triggerInterrupt() {
        this.cpu.setIRQ(this, true);
    }
}
