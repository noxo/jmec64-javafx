/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

/**
 * A structure which holds the data for a 6502 CPU instruction
 *
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class CPU6502Instruction {

    /**
     * Implied address mode i.e. no additional address needs to be read
     */
    public final static int IMPLIED = 0;
    /**
     * Immediate address mode i.e. the operand follows after the instruction code
     */
    public final static int IMMEDIATE = 1;
    /**
     * Accumulator address mode i.e. the accumulator is being modified
     */
    public final static int ACCUMULATOR = 2;
    /**
     * Absolute address mode i.e. the instruction is followed by an address that denotes the memory position to read from
     */
    public final static int ABSOLUTE = 3;
    /**
     * Absolute plus X register address mode.
     * As the Absolute address mode except that the X register value is added to get the final address to read from
     */
    public final static int ABSOLUTE_X = 4;
    /**
     * Absolute plus Y register address mode.
     * As the Absolute address mode except that the Y register value is added to get the final address to read from
     */
    public final static int ABSOLUTE_Y = 5;
    /**
     * Indirect address mode i.e. the instruction is followed by an address that points to another address which points to the memory to read from
     */
    public final static int INDIRECT = 6;
    /**
     * Indirect plus X address mode i.e. the byte following the instruction plus the X register value gives the address in the zero page where the operand can be read
     */
    public final static int INDIRECT_X = 7;
    /**
     * Indirect plus Y address mode i.e. the byte following the instruction code gives an address in the zero page where we can find an address.
     * This address plus the Y register gives the operand address.
     */
    public final static int INDIRECT_Y = 8;
    /**
     * Zero-page address mode i.e. the instruction is followed by a zero-page address that denotes the memory position to read from
     */
    public final static int ZERO = 9;
    /**
     * Zero-page plus X register address mode.
     * As the Zero-page address mode except that the X register value is added to get the final address to read from
     */
    public final static int ZERO_X = 10;
    /**
     * Zero-page plus Y register address mode.
     * As the Zero-page address mode except that the X register value is added to get the final address to read from
     */
    public final static int ZERO_Y = 11;
    /**
     * Relative address mode i.e. the byte value following the instruction code is added to the current value of the program counter to the the operand address
     */
    public final static int RELATIVE = 12;
    /**
     * string identifying the operand when using the toString method
     */
    public final static String OPERAND_ID = "OPERAND";
    /**
     * the instruction name e.g. LDA, ORA
     */
    public final String name;
    /**
     * 6510 op-code for the instruction
     */
    public final int opCode;
    /**
     * 6510 op-code of the general group of operations, e.g. ORA, where this instruction belongs to
     */
    public final int opGroup;
    /**
     * address mode e.g. indirect-x
     */
    public final int addressMode;
    /**
     * number of CPU cycles the operation takes
     */
    public final int cycles;
    /**
     * number of bytes this instruction takes, including the operand/operand address
     */
    public final int size;
    /**
     * indicates that an additional CPU cycle has to be added if the address crosses a page boundary
     */
    public final boolean addPageBoundaryCycle;

    /**
     * Create a new instruction
     *
     * @param   name    name e.g. LDA, ORA
     * @param   opCode  6510 op-code for the instruction
     * @param   opGroup 6510 op-code of the general group of operations, e.g. ORA, where this instruction belongs to
     * @param   addressMode address mode e.g. indirect-x
     * @param   cycles  number of CPU cycles the operation takes
     * @param   addPageBoundaryCycle    indicates that an additional CPU cycle has to be added if the address crosses a page boundary
     */
    public CPU6502Instruction(final String name, final int opCode, final int opGroup, final int addressMode, final int cycles, final boolean addPageBoundaryCycle) {
        this.name = name;
        this.opCode = opCode;
        this.opGroup = opGroup;
        this.addressMode = addressMode;
        this.cycles = cycles;
        this.size = addressMode == IMPLIED || addressMode == ACCUMULATOR ? 1 : addressMode == ABSOLUTE || addressMode == ABSOLUTE_X || addressMode == ABSOLUTE_Y || addressMode == INDIRECT ? 3 : 2;
        this.addPageBoundaryCycle = addPageBoundaryCycle;
    }

    /**
     * Create a new instruction
     *
     * @param   name    name e.g. LDA, ORA
     * @param   opCode  6510 op-code for the instruction
     * @param   opGroup 6510 op-code of the general group of operations, e.g. ORA, where this instruction belongs to
     * @param   addressMode address mode e.g. indirect-x
     * @param   cycles  number of CPU cycles the operation takes
     */
    public CPU6502Instruction(final String name, final int opCode, final int opGroup, final int addressMode, final int cycles) {
        this(name, opCode, opGroup, addressMode, cycles, false);
    }

    /**
     * Create a new instruction from an existing one.
     * All parameters except for the opcode are copied from the given instruction.
     *
     * @param   instruction instruction to copy from
     * @param   opCode  6510 op-code for the instruction
     */
    public CPU6502Instruction(final CPU6502Instruction instruction, final int opCode) {
        this.name = instruction.name;
        this.opCode = opCode;
        this.opGroup = instruction.opGroup;
        this.addressMode = instruction.addressMode;
        this.cycles = instruction.cycles;
        this.size = instruction.size;
        this.addPageBoundaryCycle = instruction.addPageBoundaryCycle;
    }

    /**
     * Simple disassembling of the instruction.
     * Instead of the real operand the string "OPERAND" is inserted
     *
     * @return  assembly language representation of the instruction
     */
    public String toString() {
        final StringBuffer result = new StringBuffer(this.name);

        switch (this.addressMode) {
            case ABSOLUTE:
            case RELATIVE:
                result.append(" $" + OPERAND_ID);
                break;
            case ABSOLUTE_X:
                result.append(" $" + OPERAND_ID + ",X");
                break;
            case ABSOLUTE_Y:
                result.append(" $" + OPERAND_ID + ",Y");
                break;
            case IMMEDIATE:
                result.append(" #$" + OPERAND_ID);
                break;
            case ZERO:
                result.append(" *$" + OPERAND_ID);
                break;
            case ZERO_X:
                result.append(" *$" + OPERAND_ID + ",X");
                break;
            case ZERO_Y:
                result.append(" *$" + OPERAND_ID + ",Y");
                break;
            case INDIRECT:
                result.append(" (" + OPERAND_ID + ")");
                break;
            case INDIRECT_X:
                result.append(" (" + OPERAND_ID + ",X)");
                break;
            case INDIRECT_Y:
                result.append(" (" + OPERAND_ID + "),Y");
                break;
            default:
                break;
        }

        return result.toString();
    }
}
