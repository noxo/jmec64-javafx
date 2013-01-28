/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.common.emulation.ThrottleableCPU;
import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.util.Logger;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

/**
 * Implementation of the core functionality of the 6502 CPU.<br>
 * Subclasses have to implement some methods for reading from and writing to memory and
 * for memory initialization.<br>
 * <br>
 * A good documentation on C64 instructions can be found at <a href='https://mirror1.cvsdude.com/trac/osflash/fc64/wiki/64Doc?format=pdf'>https://mirror1.cvsdude.com/trac/osflash/fc64/wiki/64Doc?format=pdf</a>.<br>
 * For some documentation on the C64 memory see <a href='http://sta.c64.org/cbm64mem.html'>http://sta.c64.org/cbm64mem.html</a>.<br>
 * For documentation on the official 6510 instructions see <a href='http://e-tradition.net/bytes/6502/6502_instruction_set.html'>http://e-tradition.net/bytes/6502/6502_instruction_set.html</a>.<br>
 * For documentation on undocumented 6510 instructions see <a href='http://www.s-direktnet.de/homepages/k_nadj/opcodes.html'>http://www.s-direktnet.de/homepages/k_nadj/opcodes.html</a>.<br>
 * <a href='http://www.oxyron.de/html/opcodes02.html'>http://www.oxyron.de/html/opcodes02.html</a> gives some information on bugs of the 6502 CPU.<br>
 * For documentation on interrupts see <a href='http://www.6502.org/tutorials/interrupts.html'>http://www.6502.org/tutorials/interrupts.html</a>.
 *
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class CPU6502 implements ThrottleableCPU, Serializable {

    /**
     * Do we need debug information about interrupts?
     */
    private final static boolean DEBUG_INTERRUPTS = false;
    /**
     * Do we need debug information about the executed code?
     */
    private final static boolean DEBUG_CODE = false;
    // 6510 CPU instruction groups
    // - official instructions
    public final static int ADC = 0;
    public final static int AND = 1;
    public final static int ASL = 2;
    public final static int BCC = 3;
    public final static int BCS = 4;
    public final static int BEQ = 5;
    public final static int BIT = 6;
    public final static int BMI = 7;
    public final static int BNE = 8;
    public final static int BPL = 9;
    public final static int BRK = 10;
    public final static int BVC = 11;
    public final static int BVS = 12;
    public final static int CLC = 13;
    public final static int CLD = 14;
    public final static int CLI = 15;
    public final static int CLV = 16;
    public final static int CMP = 17;
    public final static int CPX = 18;
    public final static int CPY = 19;
    public final static int DEC = 20;
    public final static int DEX = 21;
    public final static int DEY = 22;
    public final static int EOR = 23;
    public final static int INC = 24;
    public final static int INX = 25;
    public final static int INY = 26;
    public final static int JMP = 27;
    public final static int JSR = 28;
    public final static int LDA = 29;
    public final static int LDX = 30;
    public final static int LDY = 31;
    public final static int LSR = 32;
    public final static int NOP = 33;
    public final static int ORA = 34;
    public final static int PHA = 35;
    public final static int PHP = 36;
    public final static int PLA = 37;
    public final static int PLP = 38;
    public final static int ROL = 39;
    public final static int ROR = 40;
    public final static int RTI = 41;
    public final static int RTS = 42;
    public final static int SBC = 43;
    public final static int SEC = 44;
    public final static int SED = 45;
    public final static int SEI = 46;
    public final static int STA = 47;
    public final static int STX = 48;
    public final static int STY = 49;
    public final static int TAX = 50;
    public final static int TAY = 51;
    public final static int TSX = 52;
    public final static int TXA = 53;
    public final static int TXS = 54;
    public final static int TYA = 55;
    // - undocumented instructions
    public final static int ANC = 100;
    public final static int ALR = 101;
    public final static int ARR = 102;
    public final static int ASO = 103;
    public final static int AXA = 104;
    public final static int AXS = 105;
    public final static int DCM = 106;
    public final static int INS = 107;
    public final static int LAS = 108;
    public final static int LAX = 109;
    public final static int LSE = 110;
    public final static int OAL = 111;
    public final static int RLA = 112;
    public final static int RRA = 113;
    public final static int SAY = 114;
    public final static int SAX = 115;
    public final static int SKB = 116;
    public final static int SKW = 117;
    public final static int TAS = 118;
    public final static int XAA = 119;
    public final static int XAS = 120;
    // here we can read the address where to start the emulation
    private final static int RESET_VECTOR = 0xfffc;
    /**
     * device the CPU works for
     */
    protected final EmulatedDevice device;
    /**
     * RAM and ROM memory
     */
    protected final byte memory[];
    /**
     * used for warnings, errors and information
     */
    protected Logger logger;
    /**
     * a non-maskable interrupt (NMI) is requested?
     */
    protected boolean isNMILow = false;
    /**
     * the last NMI state
     */
    protected boolean lastNMIState = false;
    /**
     * a (maskable) interrupt is requested?
     */
    protected boolean isIRQLow = false;
    /**
     * set of activated IRQs
     */
    protected final Vector irqs = new Vector();
    /**
     * set of activated NMIs
     */
    protected final Vector nmis = new Vector();
    /**
     * if either interrupt is requested we set this flag, used for better performance
     */
    protected boolean isCheckInterrupt = false;
    /**
     * the number of cycles we emulated
     */
    protected long cycles = 0;
    /**
     * number of milliseconds we were throttled since last reset of this counter
     */
    protected long throttledMillis = 0;
    /**
     * the program counter
     */
    protected int pc;
    /**
     * the address of the instruction currently being processed
     */
    protected int currentInstructionAddress;

    // the processor flags
    /**
     * sign flag, set when an expression has bit 7, i.e. the sign, set
     */
    protected boolean signFlag = false;
    /**
     * zero flag, set when an expression equals zero
     */
    protected boolean zeroFlag = false;
    /**
     * overflow flag, set on some operation when bit 6 was set
     */
    protected boolean overflowFlag = false;
    /**
     * carry flag, set e.g. if an add operation produces a result > 255
     */
    protected boolean carryFlag = false;
    /**
     * decimal flag, used to denote that we add or subtract interpreting numbers as binary coded decimals
     */
    protected boolean decimalFlag = false;
    /**
     * break flag, set with a BRK operation
     */
    protected boolean breakFlag = false;
    /**
     * interrupt flag, when set then we don't execute maskable interrupts
     */
    protected boolean interruptFlag = false;

    // CPU registers
    /**
     * Accumulator register
     */
    protected int ac = 0;
    /**
     * X register
     */
    protected int x = 0;
    /**
     * Y register
     */
    protected int y = 0;
    /**
     * stack-pointer
     */
    protected int sp = 0xff;
    /**
     * RAM memory size, used for serialization to avoid saving the ROM contents.
     * This variable is set to the memory size on initialization but can be modified later.
     * During serialization the memory content from byte 0 up to this pointer will be saved and restored.
     */
    protected int ramSize;
    /**
     * definition of the 6510 instructions, we allow 256 normal 6502 instructions the emulator
     */
    protected CPU6502Instruction[] instructions = new CPU6502Instruction[256];

    /**
     * Create a new 6502 CPU
     *
     * @param   device  the device we work for
     * @param   memSize the amount of memory to reserve
     */
    public CPU6502(final EmulatedDevice device, final int memSize) {
        this.device = device;
        this.memory = new byte[memSize];
        this.ramSize = memSize;
        initializeInstructions();
    }

    /**
     * Initialize the CPU instructions
     */
    protected void initializeInstructions() {
        // official instruction
        addInstruction(new CPU6502Instruction("ADC", 0x6d, ADC, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("ADC", 0x69, ADC, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("ADC", 0x61, ADC, CPU6502Instruction.INDIRECT_X, 6));
        addInstruction(new CPU6502Instruction("ADC", 0x71, ADC, CPU6502Instruction.INDIRECT_Y, 5, true));
        addInstruction(new CPU6502Instruction("ADC", 0x7d, ADC, CPU6502Instruction.ABSOLUTE_X, 4, true));
        addInstruction(new CPU6502Instruction("ADC", 0x79, ADC, CPU6502Instruction.ABSOLUTE_Y, 4, true));
        addInstruction(new CPU6502Instruction("ADC", 0x65, ADC, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("ADC", 0x75, ADC, CPU6502Instruction.ZERO_X, 4));
        addInstruction(new CPU6502Instruction("AND", 0x2d, AND, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("AND", 0x29, AND, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("AND", 0x21, AND, CPU6502Instruction.INDIRECT_X, 6));
        addInstruction(new CPU6502Instruction("AND", 0x31, AND, CPU6502Instruction.INDIRECT_Y, 5, true));
        addInstruction(new CPU6502Instruction("AND", 0x3d, AND, CPU6502Instruction.ABSOLUTE_X, 4, true));
        addInstruction(new CPU6502Instruction("AND", 0x39, AND, CPU6502Instruction.ABSOLUTE_Y, 4, true));
        addInstruction(new CPU6502Instruction("AND", 0x25, AND, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("AND", 0x35, AND, CPU6502Instruction.ZERO_X, 4));
        addInstruction(new CPU6502Instruction("ASL", 0x0E, ASL, CPU6502Instruction.ABSOLUTE, 6));
        addInstruction(new CPU6502Instruction("ASL", 0x0A, ASL, CPU6502Instruction.ACCUMULATOR, 2));
        addInstruction(new CPU6502Instruction("ASL", 0x1E, ASL, CPU6502Instruction.ABSOLUTE_X, 7));
        addInstruction(new CPU6502Instruction("ASL", 0x06, ASL, CPU6502Instruction.ZERO, 5));
        addInstruction(new CPU6502Instruction("ASL", 0x16, ASL, CPU6502Instruction.ZERO_X, 6));
        addInstruction(new CPU6502Instruction("BCC", 0x90, BCC, CPU6502Instruction.RELATIVE, 2));
        addInstruction(new CPU6502Instruction("BCS", 0xb0, BCS, CPU6502Instruction.RELATIVE, 2));
        addInstruction(new CPU6502Instruction("BEQ", 0xf0, BEQ, CPU6502Instruction.RELATIVE, 2));
        addInstruction(new CPU6502Instruction("BIT", 0x2c, BIT, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("BIT", 0x24, BIT, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("BMI", 0x30, BMI, CPU6502Instruction.RELATIVE, 2));
        addInstruction(new CPU6502Instruction("BNE", 0xd0, BNE, CPU6502Instruction.RELATIVE, 2));
        addInstruction(new CPU6502Instruction("BPL", 0x10, BPL, CPU6502Instruction.RELATIVE, 2));
        addInstruction(new CPU6502Instruction("BRK", 0x00, BRK, CPU6502Instruction.IMPLIED, 7));
        addInstruction(new CPU6502Instruction("BVC", 0x50, BVC, CPU6502Instruction.RELATIVE, 2));
        addInstruction(new CPU6502Instruction("BVS", 0x70, BVS, CPU6502Instruction.RELATIVE, 2));
        addInstruction(new CPU6502Instruction("CLC", 0x18, CLC, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("CLD", 0xd8, CLD, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("CLI", 0x58, CLI, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("CLV", 0xb8, CLV, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("CMP", 0xcd, CMP, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("CMP", 0xc9, CMP, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("CMP", 0xc1, CMP, CPU6502Instruction.INDIRECT_X, 6));
        addInstruction(new CPU6502Instruction("CMP", 0xd1, CMP, CPU6502Instruction.INDIRECT_Y, 5, true));
        addInstruction(new CPU6502Instruction("CMP", 0xdd, CMP, CPU6502Instruction.ABSOLUTE_X, 4, true));
        addInstruction(new CPU6502Instruction("CMP", 0xd9, CMP, CPU6502Instruction.ABSOLUTE_Y, 4, true));
        addInstruction(new CPU6502Instruction("CMP", 0xc5, CMP, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("CMP", 0xd5, CMP, CPU6502Instruction.ZERO_X, 4));
        addInstruction(new CPU6502Instruction("CPX", 0xec, CPX, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("CPX", 0xe0, CPX, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("CPX", 0xe4, CPX, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("CPY", 0xcc, CPY, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("CPY", 0xc0, CPY, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("CPY", 0xc4, CPY, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("DEC", 0xce, DEC, CPU6502Instruction.ABSOLUTE, 6));
        addInstruction(new CPU6502Instruction("DEC", 0xde, DEC, CPU6502Instruction.ABSOLUTE_X, 7));
        addInstruction(new CPU6502Instruction("DEC", 0xc6, DEC, CPU6502Instruction.ZERO, 5));
        addInstruction(new CPU6502Instruction("DEC", 0xd6, DEC, CPU6502Instruction.ZERO_X, 6));
        addInstruction(new CPU6502Instruction("DEX", 0xca, DEX, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("DEY", 0x88, DEY, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("EOR", 0x4d, EOR, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("EOR", 0x49, EOR, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("EOR", 0x41, EOR, CPU6502Instruction.INDIRECT_X, 6));
        addInstruction(new CPU6502Instruction("EOR", 0x51, EOR, CPU6502Instruction.INDIRECT_Y, 5, true));
        addInstruction(new CPU6502Instruction("EOR", 0x5d, EOR, CPU6502Instruction.ABSOLUTE_X, 4, true));
        addInstruction(new CPU6502Instruction("EOR", 0x59, EOR, CPU6502Instruction.ABSOLUTE_Y, 4, true));
        addInstruction(new CPU6502Instruction("EOR", 0x45, EOR, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("EOR", 0x55, EOR, CPU6502Instruction.ZERO_X, 4));
        addInstruction(new CPU6502Instruction("INC", 0xee, INC, CPU6502Instruction.ABSOLUTE, 6));
        addInstruction(new CPU6502Instruction("INC", 0xfe, INC, CPU6502Instruction.ABSOLUTE_X, 7));
        addInstruction(new CPU6502Instruction("INC", 0xe6, INC, CPU6502Instruction.ZERO, 5));
        addInstruction(new CPU6502Instruction("INC", 0xf6, INC, CPU6502Instruction.ZERO_X, 6));
        addInstruction(new CPU6502Instruction("INX", 0xe8, INX, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("INY", 0xc8, INY, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("JMP", 0x4c, JMP, CPU6502Instruction.ABSOLUTE, 3));
        addInstruction(new CPU6502Instruction("JMP", 0x6c, JMP, CPU6502Instruction.INDIRECT, 5));
        addInstruction(new CPU6502Instruction("JSR", 0x20, JSR, CPU6502Instruction.ABSOLUTE, 6));
        addInstruction(new CPU6502Instruction("LDA", 0xad, LDA, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("LDA", 0xa9, LDA, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("LDA", 0xa1, LDA, CPU6502Instruction.INDIRECT_X, 6));
        addInstruction(new CPU6502Instruction("LDA", 0xb1, LDA, CPU6502Instruction.INDIRECT_Y, 5, true));
        addInstruction(new CPU6502Instruction("LDA", 0xbd, LDA, CPU6502Instruction.ABSOLUTE_X, 4, true));
        addInstruction(new CPU6502Instruction("LDA", 0xb9, LDA, CPU6502Instruction.ABSOLUTE_Y, 4, true));
        addInstruction(new CPU6502Instruction("LDA", 0xa5, LDA, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("LDA", 0xb5, LDA, CPU6502Instruction.ZERO_X, 4));
        addInstruction(new CPU6502Instruction("LDX", 0xae, LDX, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("LDX", 0xa2, LDX, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("LDX", 0xbe, LDX, CPU6502Instruction.ABSOLUTE_Y, 4, true));
        addInstruction(new CPU6502Instruction("LDX", 0xa6, LDX, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("LDX", 0xb6, LDX, CPU6502Instruction.ZERO_Y, 4));
        addInstruction(new CPU6502Instruction("LDY", 0xac, LDY, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("LDY", 0xa0, LDY, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("LDY", 0xbc, LDY, CPU6502Instruction.ABSOLUTE_X, 4, true));
        addInstruction(new CPU6502Instruction("LDY", 0xa4, LDY, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("LDY", 0xb4, LDY, CPU6502Instruction.ZERO_X, 4));
        addInstruction(new CPU6502Instruction("LSR", 0x4e, LSR, CPU6502Instruction.ABSOLUTE, 6));
        addInstruction(new CPU6502Instruction("LSR", 0x4a, LSR, CPU6502Instruction.ACCUMULATOR, 2));
        addInstruction(new CPU6502Instruction("LSR", 0x5e, LSR, CPU6502Instruction.ABSOLUTE_X, 7));
        addInstruction(new CPU6502Instruction("LSR", 0x46, LSR, CPU6502Instruction.ZERO, 5));
        addInstruction(new CPU6502Instruction("LSR", 0x56, LSR, CPU6502Instruction.ZERO_X, 6));
        addInstruction(new CPU6502Instruction("NOP", 0xea, NOP, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("ORA", 0x0D, ORA, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("ORA", 0x09, ORA, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("ORA", 0x01, ORA, CPU6502Instruction.INDIRECT_X, 6));
        addInstruction(new CPU6502Instruction("ORA", 0x11, ORA, CPU6502Instruction.INDIRECT_Y, 5, true));
        addInstruction(new CPU6502Instruction("ORA", 0x1D, ORA, CPU6502Instruction.ABSOLUTE_X, 4, true));
        addInstruction(new CPU6502Instruction("ORA", 0x19, ORA, CPU6502Instruction.ABSOLUTE_Y, 4, true));
        addInstruction(new CPU6502Instruction("ORA", 0x05, ORA, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("ORA", 0x15, ORA, CPU6502Instruction.ZERO_X, 4));
        addInstruction(new CPU6502Instruction("PHA", 0x48, PHA, CPU6502Instruction.IMPLIED, 3));
        addInstruction(new CPU6502Instruction("PHP", 0x08, PHP, CPU6502Instruction.IMPLIED, 3));
        addInstruction(new CPU6502Instruction("PLA", 0x68, PLA, CPU6502Instruction.IMPLIED, 4));
        addInstruction(new CPU6502Instruction("PLP", 0x28, PLP, CPU6502Instruction.IMPLIED, 4));
        addInstruction(new CPU6502Instruction("ROL", 0x2e, ROL, CPU6502Instruction.ABSOLUTE, 6));
        addInstruction(new CPU6502Instruction("ROL", 0x2a, ROL, CPU6502Instruction.ACCUMULATOR, 2));
        addInstruction(new CPU6502Instruction("ROL", 0x3e, ROL, CPU6502Instruction.ABSOLUTE_X, 7));
        addInstruction(new CPU6502Instruction("ROL", 0x26, ROL, CPU6502Instruction.ZERO, 5));
        addInstruction(new CPU6502Instruction("ROL", 0x36, ROL, CPU6502Instruction.ZERO_X, 6));
        addInstruction(new CPU6502Instruction("ROR", 0x6e, ROR, CPU6502Instruction.ABSOLUTE, 6));
        addInstruction(new CPU6502Instruction("ROR", 0x6a, ROR, CPU6502Instruction.ACCUMULATOR, 2));
        addInstruction(new CPU6502Instruction("ROR", 0x7e, ROR, CPU6502Instruction.ABSOLUTE_X, 7));
        addInstruction(new CPU6502Instruction("ROR", 0x66, ROR, CPU6502Instruction.ZERO, 5));
        addInstruction(new CPU6502Instruction("ROR", 0x76, ROR, CPU6502Instruction.ZERO_X, 6));
        addInstruction(new CPU6502Instruction("RTI", 0x40, RTI, CPU6502Instruction.IMPLIED, 6));
        addInstruction(new CPU6502Instruction("RTS", 0x60, RTS, CPU6502Instruction.IMPLIED, 6));
        addInstruction(new CPU6502Instruction("SBC", 0xed, SBC, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("SBC", 0xe9, SBC, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("SBC", 0xe1, SBC, CPU6502Instruction.INDIRECT_X, 6));
        addInstruction(new CPU6502Instruction("SBC", 0xf1, SBC, CPU6502Instruction.INDIRECT_Y, 5, true));
        addInstruction(new CPU6502Instruction("SBC", 0xfd, SBC, CPU6502Instruction.ABSOLUTE_X, 4, true));
        addInstruction(new CPU6502Instruction("SBC", 0xf9, SBC, CPU6502Instruction.ABSOLUTE_Y, 4, true));
        addInstruction(new CPU6502Instruction("SBC", 0xe5, SBC, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("SBC", 0xf5, SBC, CPU6502Instruction.ZERO_X, 4));
        addInstruction(new CPU6502Instruction("SEC", 0x38, SEC, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("SED", 0xf8, SED, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("SEI", 0x78, SEI, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("STA", 0x8d, STA, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("STA", 0x81, STA, CPU6502Instruction.INDIRECT_X, 6));
        addInstruction(new CPU6502Instruction("STA", 0x91, STA, CPU6502Instruction.INDIRECT_Y, 6));
        addInstruction(new CPU6502Instruction("STA", 0x9d, STA, CPU6502Instruction.ABSOLUTE_X, 5));
        addInstruction(new CPU6502Instruction("STA", 0x99, STA, CPU6502Instruction.ABSOLUTE_Y, 5));
        addInstruction(new CPU6502Instruction("STA", 0x85, STA, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("STA", 0x95, STA, CPU6502Instruction.ZERO_X, 4));
        addInstruction(new CPU6502Instruction("STX", 0x8e, STX, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("STX", 0x86, STX, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("STX", 0x96, STX, CPU6502Instruction.ZERO_Y, 4));
        addInstruction(new CPU6502Instruction("STY", 0x8c, STY, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("STY", 0x84, STY, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("STY", 0x94, STY, CPU6502Instruction.ZERO_X, 4));
        addInstruction(new CPU6502Instruction("TAX", 0xaa, TAX, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("TAY", 0xa8, TAY, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("TSX", 0xba, TSX, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("TXA", 0x8a, TXA, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("TXS", 0x9a, TXS, CPU6502Instruction.IMPLIED, 2));
        addInstruction(new CPU6502Instruction("TYA", 0x98, TYA, CPU6502Instruction.IMPLIED, 2));
        // undocumentated instructions
        addInstruction(new CPU6502Instruction("ALR", 0x4b, ALR, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("ANC", 0x0b, ANC, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x0b], 0x2b));
        addInstruction(new CPU6502Instruction("ARR", 0x6b, ARR, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("ASO", 0x0F, ASO, CPU6502Instruction.ABSOLUTE, 6));
        addInstruction(new CPU6502Instruction("ASO", 0x03, ASO, CPU6502Instruction.INDIRECT_X, 8));
        addInstruction(new CPU6502Instruction("ASO", 0x13, ASO, CPU6502Instruction.INDIRECT_Y, 8));
        addInstruction(new CPU6502Instruction("ASO", 0x1f, ASO, CPU6502Instruction.ABSOLUTE_X, 7));
        addInstruction(new CPU6502Instruction("ASO", 0x1b, ASO, CPU6502Instruction.ABSOLUTE_Y, 7));
        addInstruction(new CPU6502Instruction("ASO", 0x07, ASO, CPU6502Instruction.ZERO, 5));
        addInstruction(new CPU6502Instruction("ASO", 0x17, ASO, CPU6502Instruction.ZERO_X, 6));
        addInstruction(new CPU6502Instruction("AXS", 0x8F, AXS, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("AXS", 0x83, AXS, CPU6502Instruction.INDIRECT_X, 6));
        addInstruction(new CPU6502Instruction("AXS", 0x87, AXS, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("AXS", 0x97, AXS, CPU6502Instruction.ZERO_Y, 4));
        addInstruction(new CPU6502Instruction("DCM", 0xCF, DCM, CPU6502Instruction.ABSOLUTE, 6));
        addInstruction(new CPU6502Instruction("DCM", 0xC3, DCM, CPU6502Instruction.INDIRECT_X, 8));
        addInstruction(new CPU6502Instruction("DCM", 0xD3, DCM, CPU6502Instruction.INDIRECT_Y, 8));
        addInstruction(new CPU6502Instruction("DCM", 0xdf, DCM, CPU6502Instruction.ABSOLUTE_X, 7));
        addInstruction(new CPU6502Instruction("DCM", 0xdb, DCM, CPU6502Instruction.ABSOLUTE_Y, 7));
        addInstruction(new CPU6502Instruction("DCM", 0xC7, DCM, CPU6502Instruction.ZERO, 5));
        addInstruction(new CPU6502Instruction("DCM", 0xD7, DCM, CPU6502Instruction.ZERO_X, 6));
        addInstruction(new CPU6502Instruction("INS", 0xEF, INS, CPU6502Instruction.ABSOLUTE, 6));
        addInstruction(new CPU6502Instruction("INS", 0xE3, INS, CPU6502Instruction.INDIRECT_X, 8));
        addInstruction(new CPU6502Instruction("INS", 0xF3, INS, CPU6502Instruction.INDIRECT_Y, 8));
        addInstruction(new CPU6502Instruction("INS", 0xff, INS, CPU6502Instruction.ABSOLUTE_X, 7));
        addInstruction(new CPU6502Instruction("INS", 0xfb, INS, CPU6502Instruction.ABSOLUTE_Y, 7));
        addInstruction(new CPU6502Instruction("INS", 0xE7, INS, CPU6502Instruction.ZERO, 5));
        addInstruction(new CPU6502Instruction("INS", 0xF7, INS, CPU6502Instruction.ZERO_X, 6));
        addInstruction(new CPU6502Instruction("LAS", 0xbb, LAS, CPU6502Instruction.ABSOLUTE_Y, 4, true));
        addInstruction(new CPU6502Instruction("LAX", 0xAF, LAX, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("LAX", 0xA3, LAX, CPU6502Instruction.INDIRECT_X, 6));
        addInstruction(new CPU6502Instruction("LAX", 0xB3, LAX, CPU6502Instruction.INDIRECT_Y, 5, true));
        addInstruction(new CPU6502Instruction("LAX", 0xbf, LAX, CPU6502Instruction.ABSOLUTE_Y, 4));
        addInstruction(new CPU6502Instruction("LAX", 0xA7, LAX, CPU6502Instruction.ZERO, 3));
        addInstruction(new CPU6502Instruction("LAX", 0xB7, LAX, CPU6502Instruction.ZERO_Y, 4));
        addInstruction(new CPU6502Instruction("LSE", 0x4F, LSE, CPU6502Instruction.ABSOLUTE, 6));
        addInstruction(new CPU6502Instruction("LSE", 0x43, LSE, CPU6502Instruction.INDIRECT_X, 8));
        addInstruction(new CPU6502Instruction("LSE", 0x53, LSE, CPU6502Instruction.INDIRECT_Y, 8));
        addInstruction(new CPU6502Instruction("LSE", 0x5f, LSE, CPU6502Instruction.ABSOLUTE_X, 7));
        addInstruction(new CPU6502Instruction("LSE", 0x5b, LSE, CPU6502Instruction.ABSOLUTE_Y, 7));
        addInstruction(new CPU6502Instruction("LSE", 0x47, LSE, CPU6502Instruction.ZERO, 5));
        addInstruction(new CPU6502Instruction("LSE", 0x57, LSE, CPU6502Instruction.ZERO_X, 6));
        addInstruction(new CPU6502Instruction(this.instructions[ 0xea], 0x1a));
        addInstruction(new CPU6502Instruction(this.instructions[ 0xea], 0x3a));
        addInstruction(new CPU6502Instruction(this.instructions[ 0xea], 0x5a));
        addInstruction(new CPU6502Instruction(this.instructions[ 0xea], 0x7a));
        addInstruction(new CPU6502Instruction(this.instructions[ 0xea], 0xda));
        addInstruction(new CPU6502Instruction(this.instructions[ 0xea], 0xfa));
        addInstruction(new CPU6502Instruction("OAL", 0xab, OAL, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("RLA", 0x2F, RLA, CPU6502Instruction.ABSOLUTE, 6));
        addInstruction(new CPU6502Instruction("RLA", 0x23, RLA, CPU6502Instruction.INDIRECT_X, 8));
        addInstruction(new CPU6502Instruction("RLA", 0x33, RLA, CPU6502Instruction.INDIRECT_Y, 8));
        addInstruction(new CPU6502Instruction("RLA", 0x3f, RLA, CPU6502Instruction.ABSOLUTE_X, 7));
        addInstruction(new CPU6502Instruction("RLA", 0x3b, RLA, CPU6502Instruction.ABSOLUTE_Y, 7));
        addInstruction(new CPU6502Instruction("RLA", 0x27, RLA, CPU6502Instruction.ZERO, 5));
        addInstruction(new CPU6502Instruction("RLA", 0x37, RLA, CPU6502Instruction.ZERO_X, 6));
        addInstruction(new CPU6502Instruction("RRA", 0x6F, RRA, CPU6502Instruction.ABSOLUTE, 6));
        addInstruction(new CPU6502Instruction("RRA", 0x63, RRA, CPU6502Instruction.INDIRECT_X, 8));
        addInstruction(new CPU6502Instruction("RRA", 0x73, RRA, CPU6502Instruction.INDIRECT_Y, 8));
        addInstruction(new CPU6502Instruction("RRA", 0x7f, RRA, CPU6502Instruction.ABSOLUTE_X, 7));
        addInstruction(new CPU6502Instruction("RRA", 0x7b, RRA, CPU6502Instruction.ABSOLUTE_Y, 7));
        addInstruction(new CPU6502Instruction("RRA", 0x67, RRA, CPU6502Instruction.ZERO, 5));
        addInstruction(new CPU6502Instruction("RRA", 0x77, RRA, CPU6502Instruction.ZERO_X, 6));
        addInstruction(new CPU6502Instruction("SAX", 0xcb, SAX, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("SAY", 0x9c, SAY, CPU6502Instruction.ABSOLUTE_X, 5));
        addInstruction(new CPU6502Instruction(this.instructions[ 0xe9], 0xeb));
        addInstruction(new CPU6502Instruction("AXA", 0x93, AXA, CPU6502Instruction.INDIRECT_Y, 6));
        addInstruction(new CPU6502Instruction("AXA", 0x9f, AXA, CPU6502Instruction.ABSOLUTE_Y, 5));
        addInstruction(new CPU6502Instruction("SKB", 0x80, SKB, CPU6502Instruction.IMMEDIATE, 3));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x80], 0x82));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x80], 0xc2));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x80], 0xe2));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x80], 0x04));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x80], 0x14));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x80], 0x34));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x80], 0x44));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x80], 0x54));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x80], 0x64));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x80], 0x74));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x80], 0xd4));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x80], 0xf4));
        addInstruction(new CPU6502Instruction("SKB", 0x89, SKB, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("SKW", 0x0c, SKW, CPU6502Instruction.ABSOLUTE, 4));
        addInstruction(new CPU6502Instruction("SKW", 0x1c, SKW, CPU6502Instruction.INDIRECT_X, 4, true));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x1c], 0x3c));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x1c], 0x5c));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x1c], 0x7c));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x1c], 0xdc));
        addInstruction(new CPU6502Instruction(this.instructions[ 0x1c], 0xfc));
        addInstruction(new CPU6502Instruction("TAS", 0x9b, TAS, CPU6502Instruction.ABSOLUTE_Y, 5));
        addInstruction(new CPU6502Instruction("XAA", 0x8b, XAA, CPU6502Instruction.IMMEDIATE, 2));
        addInstruction(new CPU6502Instruction("XAS", 0x9e, XAS, CPU6502Instruction.ABSOLUTE_Y, 5));
    }
    ;

    /**
     * Add a new instruction to this CPU
     *
     * @param instruction   CPU6502Instruction to add
     */
    protected void addInstruction(final CPU6502Instruction instruction) {
        if (this.instructions[instruction.opCode] != null) {
            throw new RuntimeException("Instruction " + instruction.opCode + " already defined!");
        } else {
            this.instructions[instruction.opCode] = instruction;
        }
    }

    /**
     * Get the instruction for a given operation code
     *
     * @param   opCode  code to decode
     * @return  corresponding instruction
     */
    public CPU6502Instruction getInstruction(final int opCode) {
        return this.instructions[opCode];
    }

    /**
     * Get the logger used to log warnings etc.
     *
     * @return the logger instance
     */
    public final Logger getLogger() {
        return this.logger;
    }

    /**
     * Set a new logger instance
     * 
     * @param   logger  the new logger
     */
    public final void setLogger(final Logger logger) {
        this.logger = logger;
    }

    /**
     * Get the CPUs main memory
     *
     * @param the CPUs memory
     */
    public final byte[] getMemory() {
        return this.memory;
    }

    /**
     * Get the number of CPU cycles we already emulated
     *
     * @return  number of already emulated CPU cycles
     */
    public final long getCycles() {
        return this.cycles;
    }

    /**
     * Add cycles the CPU had to wait
     *
     * @param cycles    the number of cycles to add
     */
    public final void addCycles(final int cycles) {
        this.cycles += cycles;
    }

    /**
     * Assign a given cycles count
     *
     * @param   cycles  cycle count to set for the floppy CPU
     */
    public void setCycles(final long cycles) {
        this.cycles = cycles;
    }

    /**
     * Set IRQ/NMI state for a given IO chip
     *
     * @param   signal  set of already activated IRQs or NMIs to modify
     * @param   ioChip  chip whose signal line will be set
     * @param   state   true if the signal should be activate, false to deactivate it
     * @return  true if the signals were modified and the CPU should be notified, otherwise false
     */
    private final boolean setSignal(final Vector signal, final IOChip ioChip, final boolean state) {
        // a signal was not yet raised
        boolean notify = false;

        // the IRQ/NMI should be triggered?
        if (state) {
            // the IRQ/NMI is not yet triggered?
            if (!signal.contains(ioChip)) {
                // then add it to the list of triggered IRQs/NMIs and notify the CPU
                signal.addElement(ioChip);
                notify = true;
                if (DEBUG_INTERRUPTS) {
                    getLogger().info("Raised IRQ/NMI: " + ioChip.getClass().getName());
                }
            }
        } else {
            // the IRQ/NMI is already triggered?
            if (signal.contains(ioChip)) {
                // then clear it from the list of triggered IRQs/NMIs and notify the CPU
                signal.removeElement(ioChip);
                notify = true;
                if (DEBUG_INTERRUPTS) {
                    getLogger().info("Cleared IRQ/NMI: " + ioChip.getClass().getName());
                }
            }
        }

        return notify;
    }

    /**
     * Set IRQ state for a given IO chip
     *
     * @param   ioChip  chip whose IRQ line will be set
     * @param   state   true if the IRQ should be activated, false to deactivate it
     */
    public final void setIRQ(final IOChip ioChip, final boolean state) {
        if (setSignal(this.irqs, ioChip, state)) {
            final boolean setIRQ = !this.irqs.isEmpty();

            if (!this.isIRQLow && setIRQ) {
                this.isCheckInterrupt = true;
            }
            this.isIRQLow = setIRQ;
        }
    }

    /**
     * Set NMI state for a given IO chip
     *
     * @param   ioChip  chip whose NMI line will be set
     * @param   state   true if the NMI should be activate, false to deactivate it
     */
    public final void setNMI(final IOChip ioChip, final boolean state) {
        if (setSignal(this.nmis, ioChip, state)) {
            final boolean setNMI = !this.irqs.isEmpty();

            if (!this.isNMILow && setNMI) {
                this.isCheckInterrupt = true;
            }
            this.isNMILow = setNMI;
        }
    }

    /**
     * Get the current value of the program counter
     *
     * @return  current program counter value
     */
    protected int getPC() {
        return this.pc;
    }

    /**
     * Set the program counter to a new value
     *
     * @param value new program counter value
     */
    protected void setPC(final int value) {
        this.pc = value;
    }

    /**
     * Increase the program counter by the given value
     *
     * @param   size    number to add to the PC
     */
    protected void addPC(final int size) {
        this.pc += size;
    }

    /**
     * Generate the status byte for putting the status on the stack
     *
     * @return  status byte that can be used when saving the processor flags on the stack
     */
    protected final int getStatusByte() {
        return ((this.carryFlag ? 0x01 : 0) + (this.zeroFlag ? 0x02 : 0) + (this.interruptFlag ? 0x04 : 0) + (this.decimalFlag ? 0x08 : 0) + (this.breakFlag ? 0x10 : 0) + 0x20 + (this.overflowFlag ? 0x40 : 0) + (this.signFlag ? 0x80 : 0));
    }

    /**
     * Copy data from a status byte into the internal status flags
     *
     * @param   status  status byte generated from a call to getStatusByte
     * @see de.joergjahnke.c64.CPU6502#getStatusByte
     */
    protected final void setStatusByte(final int status) {
        this.carryFlag = (status & 0x01) != 0;
        this.zeroFlag = (status & 0x02) != 0;
        this.interruptFlag = (status & 0x04) != 0;
        this.decimalFlag = (status & 0x08) != 0;
        this.breakFlag = (status & 0x10) != 0;
        this.overflowFlag = (status & 0x40) != 0;
        this.signFlag = (status & 0x80) != 0;
    }

    /**
     * Get a byte from the stack, modifying the stack counter
     *
     * @return top-most byte from the stack
     */
    protected final byte pop() {
        return this.memory[(++this.sp & 0xff) | 0x100];
    }

    /**
     * Put a byte to the stack, modifying the stack counter
     *
     * @param data  byte to push onto the stack
     */
    protected final void push(final byte data) {
        this.memory[(this.sp-- & 0xff) | 0x100] = data;
    }

    /**
     * Reads the memory with all respect to all flags
     *
     * @param   adr address to read from
     * @return  byte at the given memory address
     */
    protected byte readByte(final int adr) {
        return this.memory[adr];
    }

    /**
     * A byte is written directly to memory or to io chips
     *
     * @param   adr address to write to
     * @param   data    data to write
     */
    protected void writeByte(final int adr, final byte data) {
        this.memory[adr] = data;
    }

    /**
     * Copy file data to a given location in memory
     *
     * @param   bytes   array containing file data to copy to memory, the first two bytes should give the address where the file is normally loaded to
     * @param   address address to load the data to, -1 if using the default location for the file
     * @return  end address of loaded data
     */
    public int copyBytesToMemory(final byte[] bytes, final int address) {
        int address_ = address < 1 ? (bytes[0] & 0xff) + (bytes[1] & 0xff) * 256 : address;

        for (int i = 2, to = bytes.length; i < to; ++i) {
            this.memory[address_++] = bytes[i];
        }

        return address_;
    }

    /**
     * Add Memory to Accumulator with Carry
     */
    protected final void operationADC(final int data) {
        int tmp;

        if (this.decimalFlag) {
            tmp = 10 * (this.ac & 0xf0) + (this.ac & 0x0f) + (10 * (data & 0xf0) + (data & 0x0f));
            tmp = ((tmp / 10) << 4) + tmp % 10;
        } else {
            tmp = data + this.ac + (this.carryFlag ? 1 : 0);
            this.overflowFlag = (((this.ac ^ data) & 0x80) == 0) && (((this.ac ^ tmp) & 0x80) != 0);
        }
        this.carryFlag = tmp > 0xff;
        this.ac = tmp & 0xff;
        this.zeroFlag = this.ac == 0;
        this.signFlag = this.ac >= 0x80;
    }

    /**
     * AND Memory with Accumulator
     */
    protected final void operationAND(final int data) {
        this.ac &= data;
        this.zeroFlag = this.ac == 0;
        this.signFlag = this.ac >= 0x80;
    }

    /**
     * Shift Left One Bit (Memory)
     */
    protected final void operationASL(final int adr) {
        int data = readByte(adr) & 0xff;

        this.carryFlag = data >= 0x80;
        data = (data << 1) & 0xff;
        writeByte(adr, (byte) data);
        this.zeroFlag = data == 0;
        this.signFlag = data >= 0x80;
    }

    /**
     * Test Bits in Memory with Accumulator
     */
    protected final void operationBIT(final int data) {
        this.signFlag = data >= 0x80;
        this.overflowFlag = (data & 0x40) > 0;
        this.zeroFlag = (this.ac & data) == 0;
    }

    /**
     * Compare Memory with Accumulator
     */
    protected final void operationCMP(final int data) {
        this.carryFlag = this.ac >= data;
        this.zeroFlag = this.ac == data;
        this.signFlag = ((this.ac - data) & 0xff) >= 0x80;
    }

    /**
     * Compare Memory and Index X
     */
    protected final void operationCPX(final int data) {
        this.carryFlag = this.x >= data;
        this.zeroFlag = this.x == data;
        this.signFlag = ((this.x - data) & 0xff) >= 0x80;
    }

    /**
     * Compare Memory and Index Y
     */
    protected final void operationCPY(final int data) {
        this.carryFlag = this.y >= data;
        this.zeroFlag = this.y == data;
        this.signFlag = ((this.y - data) & 0xff) >= 0x80;
    }

    /**
     * Decrement Memory by One
     */
    protected final void operationDEC(final int adr) {
        final int data = ((readByte(adr) & 0xff) - 1) & 0xff;

        writeByte(adr, (byte) data);
        this.zeroFlag = data == 0;
        this.signFlag = data >= 0x80;
    }

    /**
     * Exclusive-OR Memory with Accumulator
     */
    protected final void operationEOR(final int data) {
        this.ac ^= data;
        this.zeroFlag = this.ac == 0;
        this.signFlag = this.ac >= 0x80;
    }

    /**
     * Increment Memory by One
     */
    protected final void operationINC(final int adr) {
        final int data = ((readByte(adr) & 0xff) + 1) & 0xff;

        writeByte(adr, (byte) data);
        this.zeroFlag = data == 0;
        this.signFlag = data >= 0x80;
    }

    /**
     * Jump to New Location Saving Return Address
     */
    protected final void operationJSR(final int address) {
        // store old program counter on the stack
        push((byte) (((this.pc - 1) & 0xff00) >> 8));
        push((byte) ((this.pc - 1) & 0x00ff));
        // set the new program counter
        setPC(address);
    }

    /**
     * Load Index X with Memory
     */
    protected final void operationLDX(final int data) {
        this.x = data;
        this.zeroFlag = this.x == 0;
        this.signFlag = this.x >= 0x80;
    }

    /**
     * Load Index Y with Memory
     */
    protected final void operationLDY(final int data) {
        this.y = data;
        this.zeroFlag = this.y == 0;
        this.signFlag = this.y >= 0x80;
    }

    /**
     * Shift One Bit Right (Memory)
     */
    protected final void operationLSR(final int adr) {
        int data = readByte(adr) & 0xff;

        this.carryFlag = (data & 0x01) != 0;
        data >>= 1;
        writeByte(adr, (byte) data);
        this.zeroFlag = (data == 0);
        this.signFlag = false;
    }

    /**
     * Shift One Bit Right (Accumulator)
     */
    protected final void operationLSRAccumulator() {
        this.carryFlag = (this.ac & 0x01) != 0;
        this.ac >>= 1;
        this.zeroFlag = (this.ac == 0);
        this.signFlag = false;
    }

    /**
     * OR Memory with Accumulator
     */
    protected final void operationORA(final int data) {
        this.ac |= data;
        this.zeroFlag = this.ac == 0;
        this.signFlag = this.ac >= 0x80;
    }

    /**
     * Rotate One Bit Left (Memory)
     */
    protected final void operationROL(final int adr) {
        writeByte(adr, operationROL(readByte(adr)));
    }

    /**
     * Rotate one byte left. Uses and updates the carry flag.
     *
     * @param   data    byte to rotate
     * @return  rotated byte
     */
    protected final byte operationROL(final byte data) {
        int data_ = (data & 0xff) << 1;

        if (this.carryFlag) {
            ++data_;
        }
        this.carryFlag = data_ >= 0x100;
        data_ &= 0xff;
        this.zeroFlag = data_ == 0;
        this.signFlag = data_ >= 0x80;

        return (byte)data_;
    }

    /**
     * Rotate One Bit Right (Memory)
     */
    protected final void operationROR(final int adr) {
        writeByte(adr, operationROR(readByte(adr)));
    }

    /**
     * Rotate one byte right. Uses and updates the carry flag.
     *
     * @param   data    byte to rotate
     * @return  rotated byte
     */
    protected final byte operationROR(final byte data) {
        int data_ = data & 0xff;

        if (this.carryFlag) {
            data_ |= 0x100;
        }
        this.carryFlag = (data_ & 0x01) != 0;
        data_ >>= 1;
        this.zeroFlag = data_ == 0;
        this.signFlag = data_ >= 0x80;

        return (byte)data_;
    }

    /**
     * Subtract Memory from Accumulator with Borrow
     */
    protected final void operationSBC(final int data) {
        int tmp;

        if (this.decimalFlag) {
            tmp = 10 * (this.ac & 0xf0) + (this.ac & 0x0f) - (10 * (data & 0xf0) + (data & 0x0f));
            tmp = ((tmp / 10) << 4) + tmp % 10;
        } else {
            tmp = this.ac - data - (this.carryFlag ? 0 : 1);
        }
        this.overflowFlag = (((this.ac ^ tmp) & 0x80) != 0) && (((this.ac ^ data) & 0x80) != 0);
        this.carryFlag = tmp >= 0;
        this.ac = tmp & 0xff;
        this.zeroFlag = this.ac == 0;
        this.signFlag = this.ac >= 0x80;
    }

    /**
     * Service interrupts IRQ and NMI
     */
    public final void serviceIRQorNMI() {
        // NMI requested?
        if (this.isNMILow && !this.lastNMIState) {
            // we need seven cycles just like for a BRK operation
            this.cycles += 7;
            // execute the interrupt routine
            serviceInterrupt(0xfffa);
        // IRQ requested?
        } else if (this.isIRQLow) {
            // is the interrupt allowed?
            if (!this.interruptFlag) {
                // we need seven cycles just like for a BRK operation
                this.cycles += 7;
                // execute the interrupt routine
                serviceInterrupt(0xfffe);
            }
            // we no longer have to check for interrupts as all NMI/IRQ requests have been serviced
            this.isCheckInterrupt = false;
        }
        // remember last NMI state in order to check on next
        this.lastNMIState = this.isNMILow;
    }

    /**
     * Run interrupt routine
     *
     * @param   adr indirect address of interrupt routine
     */
    public final void serviceInterrupt(final int adr) {
        push((byte) ((this.pc & 0xff00) >> 8));
        push((byte) (this.pc & 0x00ff));
        push((byte) getStatusByte());
        setPC(((readByte(adr + 1) & 0xff) << 8) + (readByte(adr) & 0xff));
        this.interruptFlag = true;
    }

    /**
     * Get the operand of an operation.
     * When this method is called the program counter already points to the next instruction.
     *
     * @param   op  operation, this has the addressing mode in the lower 5 bits
     * @return  operand
     */
    private final byte getOperand(final CPU6502Instruction instruction) {
        return instruction.addressMode == CPU6502Instruction.IMMEDIATE
                ? this.memory[this.currentInstructionAddress + 1]
                : readByte(getOperandAddress(instruction));
    }

    /**
     * Get the memory address for the operand of an operation.
     * When this method is called the program counter already points to the next instruction.
     *
     * @param   op  operation, this has the addressing mode in the lower 5 bits
     * @return  memory address
     */
    private int getOperandAddress(final CPU6502Instruction instruction) {
        // switch address mode
        switch (instruction.addressMode) {
            case CPU6502Instruction.ZERO:
                return this.memory[this.currentInstructionAddress + 1] & 0xff;
            case CPU6502Instruction.ZERO_X:
                return ((this.memory[this.currentInstructionAddress + 1] & 0xff) + this.x) & 0xff;
            case CPU6502Instruction.ZERO_Y:
                return ((this.memory[this.currentInstructionAddress + 1] & 0xff) + this.y) & 0xff;
            case CPU6502Instruction.INDIRECT: {
                final int lowAddress = this.memory[this.currentInstructionAddress + 1] & 0xff;
                final int highAddress = (this.memory[this.currentInstructionAddress + 2] & 0xff) << 8;

                return (readByte(highAddress + lowAddress) & 0xff) + ((readByte(highAddress + ((lowAddress + 1) & 0xff)) & 0xff) << 8);
            }
            case CPU6502Instruction.INDIRECT_X: {
                final int p = ((this.memory[this.currentInstructionAddress + 1] & 0xff) + this.x) & 0xff;

                return (this.memory[p] & 0xff) + ((this.memory[(p + 1) & 0xff] & 0xff) << 8);
            }
            case CPU6502Instruction.INDIRECT_Y: {
                // this is the address on the zero page where we read from
                final int p = this.memory[this.currentInstructionAddress + 1] & 0xff;

                // if over page boundary then do another access => one more cycle
                final int lowAddress = (this.memory[p] & 0xff) + this.y;

                if (instruction.addPageBoundaryCycle && lowAddress > 0xff) {
                    ++this.cycles;
                }

                return (lowAddress + ((this.memory[(p + 1) & 0xff] & 0xff) << 8)) & 0xffff;
            }
            case CPU6502Instruction.RELATIVE: {
                // calculate relative address
                final int address = this.memory[this.currentInstructionAddress + 1] + this.pc;

                // add two cycles if crossing page boundary, otherwise one
                this.cycles += ((this.pc) & 0xff00) != (address & 0xff00) ? 2 : 1;
                return address;
            }
            case CPU6502Instruction.ABSOLUTE:
                return (this.memory[this.currentInstructionAddress + 1] & 0xff) + ((this.memory[this.currentInstructionAddress + 2] & 0xff) << 8);
            case CPU6502Instruction.ABSOLUTE_X:
            case CPU6502Instruction.ABSOLUTE_Y: {
                // read low-byte of the address
                final int lowAddress = (this.memory[this.currentInstructionAddress + 1] & 0xff) + (instruction.addressMode == CPU6502Instruction.ABSOLUTE_Y ? this.y : this.x);

                // if over page boundary then do another access => one more cycle
                if (instruction.addPageBoundaryCycle && lowAddress > 0xff) {
                    ++this.cycles;
                }

                return (lowAddress + ((this.memory[this.currentInstructionAddress + 2] & 0xff) << 8)) & 0xffff;
            }

            default:
                throw new RuntimeException("Illegal address mode for instruction " + instruction.toString() + " ($" + Integer.toHexString(instruction.opCode) + ")");
        }
    }

    /**
     * Disassemble a given instruction at the current memory location.
     *
     * @param   instruction instruction to disassemble
     * @return  disassembled instruction
     */
    protected final String disassemble(final CPU6502Instruction instruction) {
        // get basic disassembly
        String result = instruction.toString();
        // do we have an operand to decode?
        final int opStart = result.indexOf(CPU6502Instruction.OPERAND_ID);

        if (opStart > 0) {
            // decode the operand
            int n;
            int address = -1;
            final long oldCycles = this.cycles;

            switch (instruction.addressMode) {
                case CPU6502Instruction.IMMEDIATE:
                    n = getOperand(instruction) & 0xff;
                    break;
                case CPU6502Instruction.ABSOLUTE_X:
                case CPU6502Instruction.ABSOLUTE_Y:
                case CPU6502Instruction.INDIRECT:
                    address = getOperandAddress(instruction);
                    n = (this.memory[this.currentInstructionAddress + 1] & 0xff) + ((this.memory[this.currentInstructionAddress + 2] & 0xff) << 8);
                    break;
                case CPU6502Instruction.ZERO_X:
                case CPU6502Instruction.ZERO_Y:
                case CPU6502Instruction.INDIRECT_X:
                case CPU6502Instruction.INDIRECT_Y:
                    address = getOperandAddress(instruction);
                    n = this.memory[this.currentInstructionAddress + 1] & 0xff;
                    break;
                case CPU6502Instruction.RELATIVE:
                    n = getOperandAddress(instruction);
                    break;
                default:
                    n = getOperandAddress(instruction);
            }

            this.cycles = oldCycles;

            // insert the operand into the result string
            result = result.substring(0, opStart) + Integer.toHexString(n) + result.substring(opStart + CPU6502Instruction.OPERAND_ID.length()) + (address >= 0 ? " ; $" + Integer.toHexString(address) : "");
        }

        return result;
    }

    /**
     * Get the current call-stack.
     * This routine might return partially erroneous data if the stack does contain other information
     * than return data from JSR operations.
     *
     * @return  list of program counter addresses of the last sub-routine calls
     */
    public Vector getStackTrace() {
        final Vector result = new Vector();

        for (int s = ((this.sp + 1) & 0xff) | 0x100; s > 0x100 && s < 0x1ff; s += 2) {
            final int adr = (this.memory[s] & 0xff) + (this.memory[s + 1] & 0xff) * 256;

            if (adr == 0) {
                break;
            }

            result.addElement(new Integer((adr - 2) & 0xffff));
        }

        return result;
    }

    /**
     * Check for interrupts, determine the address of the next instruction and execute it
     */
    protected final void emulateNextInstruction() {
        // we have to check for interrupts?
        if (this.isCheckInterrupt) {
            serviceIRQorNMI();
        }

        // determine address of the next instruction
        this.currentInstructionAddress = getPC();
        // get the instruction from memory
        final CPU6502Instruction instruction = this.instructions[this.memory[this.currentInstructionAddress] & 0xff];

        // interpret the instruction
        this.cycles += instruction.cycles;
        addPC(instruction.size);

        if (DEBUG_CODE) {
            System.out.println("A: " + this.ac + ", X: " + this.x + ", Y: " + this.y + ", status: " + (this.zeroFlag ? "Z" : "-") + (this.carryFlag ? "C" : "-") + (this.signFlag ? "N" : "-"));
            System.out.println("pc: $" + Integer.toHexString(this.pc - instruction.size) + ", opCode: $" + Integer.toHexString(instruction.opCode) + ", ins: " + disassemble(instruction));
        }

        emulateInstruction(instruction);
    }

    /**
     * Emulate an instruction from the standard C64 instruction set.
     *
     * @param   instruction instruction to interpret
     * @see de.joergjahnke.c64.CPU6502#emulateNextInstruction
     */
    protected void emulateInstruction(final CPU6502Instruction instruction) {
        switch (instruction.opGroup) {
            // official instructions
            case ADC:
                operationADC(getOperand(instruction) & 0xff);
                break;

            case AND:
                operationAND(getOperand(instruction) & 0xff);
                break;

            case ASL:
                if (instruction.addressMode == CPU6502Instruction.ACCUMULATOR) {
                    this.carryFlag = this.ac >= 0x80;
                    this.ac = (this.ac << 1) & 0xff;
                    this.zeroFlag = this.ac == 0;
                    this.signFlag = this.ac >= 0x80;
                } else {
                    operationASL(getOperandAddress(instruction));
                }
                break;

            case BCC:
                if (!this.carryFlag) {
                    setPC(getOperandAddress(instruction));
                }
                break;

            case BCS:
                if (this.carryFlag) {
                    setPC(getOperandAddress(instruction));
                }
                break;

            case BEQ:
                if (this.zeroFlag) {
                    setPC(getOperandAddress(instruction));
                }
                break;

            case BIT:
                operationBIT(getOperand(instruction) & 0xff);
                break;

            case BMI:
                if (this.signFlag) {
                    setPC(getOperandAddress(instruction));
                }
                break;

            case BNE:
                if (!this.zeroFlag) {
                    setPC(getOperandAddress(instruction));
                }
                break;

            case BPL:
                if (!this.signFlag) {
                    setPC(getOperandAddress(instruction));
                }
                break;

            case BRK:
                // break is followed by another byte
                addPC(1);
                serviceInterrupt(0xfffe);
                this.breakFlag = true;
                break;

            case BVC:
                if (!this.overflowFlag) {
                    setPC(getOperandAddress(instruction));
                }
                break;

            case BVS:
                if (this.overflowFlag) {
                    setPC(getOperandAddress(instruction));
                }
                break;

            case CLC:
                this.carryFlag = false;
                break;

            case CLV:
                this.overflowFlag = false;
                break;

            case CLD:
                this.decimalFlag = false;
                break;

            case CLI:
                this.interruptFlag = false;
                this.isCheckInterrupt = true;
                break;

            case CMP:
                operationCMP(getOperand(instruction) & 0xff);
                break;

            case CPX:
                operationCPX(getOperand(instruction) & 0xff);
                break;

            case CPY:
                operationCPY(getOperand(instruction) & 0xff);
                break;

            case DEC:
                operationDEC(getOperandAddress(instruction));
                break;

            case DEX:
                --this.x;
                this.x &= 0xff;
                this.zeroFlag = this.x == 0;
                this.signFlag = this.x >= 0x80;
                break;

            case DEY:
                --this.y;
                this.y &= 0xff;
                this.zeroFlag = this.y == 0;
                this.signFlag = this.y >= 0x80;
                break;

            case EOR:
                operationEOR(getOperand(instruction) & 0xff);
                break;

            case INC:
                operationINC(getOperandAddress(instruction));
                break;

            case INX:
                ++this.x;
                this.x &= 0xff;
                this.zeroFlag = this.x == 0;
                this.signFlag = this.x >= 0x80;
                break;

            case INY:
                ++this.y;
                this.y &= 0xff;
                this.zeroFlag = this.y == 0;
                this.signFlag = this.y >= 0x80;
                break;

            case JMP:
                setPC(getOperandAddress(instruction));
                break;

            case JSR:
                operationJSR(getOperandAddress(instruction));
                break;

            case LDA:
                this.ac = getOperand(instruction) & 0xff;
                this.zeroFlag = this.ac == 0;
                this.signFlag = this.ac >= 0x80;
                break;

            case LDX:
                this.x = getOperand(instruction) & 0xff;
                this.zeroFlag = this.x == 0;
                this.signFlag = this.x >= 0x80;
                break;

            case LDY:
                this.y = getOperand(instruction) & 0xff;
                this.zeroFlag = this.y == 0;
                this.signFlag = this.y >= 0x80;
                break;

            case LSR:
                if (instruction.addressMode == CPU6502Instruction.ACCUMULATOR) {
                    operationLSRAccumulator();
                } else {
                    operationLSR(getOperandAddress(instruction));
                }
                break;

            case NOP:
                break;

            case ORA:
                operationORA(getOperand(instruction) & 0xff);
                break;

            case PHA:
                push((byte)this.ac);
                break;

            case PHP:
                this.breakFlag = true;
                push((byte) getStatusByte());
                this.breakFlag = false;
                break;

            case PLA:
                this.ac = pop() & 0xff;
                this.zeroFlag = this.ac == 0;
                this.signFlag = this.ac >= 0x80;
                break;

            case PLP:
                setStatusByte(pop() & 0xff);
                this.breakFlag = false;
                this.isCheckInterrupt = true;
                break;

            case RTS:
                setPC((pop() & 0xff) + ((pop() & 0xff) << 8) + 1);
                break;

            case RTI:
                setStatusByte(pop() & 0xff);
                setPC((pop() & 0xff) + ((pop() & 0xff) << 8));
                this.breakFlag = false;
                this.isCheckInterrupt = true;
                break;

            case ROL:
                if (instruction.addressMode == CPU6502Instruction.ACCUMULATOR) {
                    this.ac = operationROL((byte)this.ac) & 0xff;
                } else {
                    operationROL(getOperandAddress(instruction));
                }
                break;

            case ROR:
                if (instruction.addressMode == CPU6502Instruction.ACCUMULATOR) {
                    this.ac = operationROR((byte)this.ac) & 0xff;
                } else {
                    operationROR(getOperandAddress(instruction));
                }
                break;

            case SBC:
                operationSBC(getOperand(instruction) & 0xff);
                break;

            case SEC:
                this.carryFlag = true;
                break;

            case SED:
                this.decimalFlag = true;
                break;

            case SEI:
                this.interruptFlag = true;
                break;

            case STA:
                writeByte(getOperandAddress(instruction), (byte)this.ac);
                break;

            case STX:
                writeByte(getOperandAddress(instruction), (byte)this.x);
                break;

            case STY:
                writeByte(getOperandAddress(instruction), (byte)this.y);
                break;

            case TSX:
                this.x = this.sp & 0xff;
                this.zeroFlag = this.x == 0;
                this.signFlag = this.x >= 0x80;
                break;

            case TXS:
                this.sp = this.x;
                break;

            case TXA:
                this.ac = this.x;
                this.zeroFlag = this.ac == 0;
                this.signFlag = this.ac >= 0x80;
                break;

            case TAX:
                this.x = this.ac;
                this.zeroFlag = this.x == 0;
                this.signFlag = this.x >= 0x80;
                break;

            case TYA:
                this.ac = this.y;
                this.zeroFlag = this.ac == 0;
                this.signFlag = this.ac >= 0x80;
                break;

            case TAY:
                this.y = this.ac;
                this.zeroFlag = this.y == 0;
                this.signFlag = this.y >= 0x80;
                break;

            // undocumented instructions
            // ALR = AND + LSR
            case ALR:
                operationAND(getOperand(instruction) & 0xff);
                operationLSRAccumulator();
                break;

            // ANC ANDs the contents of the A register with an immediate value and then 
            // moves bit 7 of A into the Carry flag. 
            case ANC:
                operationAND(getOperand(instruction) & 0xff);
                this.carryFlag = (this.ac & 0x80) != 0;
                break;

            // ARR = AND + ROR
            case ARR:
                operationAND(getOperand(instruction) & 0xff);
                this.ac = operationROR((byte)this.ac);
                break;

            // ASO = ASL + ORA
            case ASO: {
                final int address = getOperandAddress(instruction);

                operationASL(address);
                operationORA(readByte(address) & 0xff);
                break;
            }

            // AXA stores the result of A AND X AND the high byte of the target 
            // address of the operand +1 in memory.
            case AXA: {
                final int adr2 = getOperandAddress(instruction);

                writeByte(adr2, (byte) (this.ac & this.x & ((adr2 >> 8) + 1)));
                break;
            }

            // AXS
            case AXS:
                writeByte(getOperandAddress(instruction), (byte) (this.ac & this.x));
                break;

            // DCM = DEC + CMP
            case DCM: {
                final int address = getOperandAddress(instruction);

                operationDEC(address);
                operationCMP(readByte(address) & 0xff);
                break;
            }

            // INS = INC + SBC
            case INS: {
                final int address = getOperandAddress(instruction);

                operationINC(address);
                operationSBC(readByte(address) & 0xff);
                break;
            }

            // LAS is not implemented
            case LAS:
                if (null != getLogger()) {
                    getLogger().warning("Not implemented instruction at $" + Integer.toHexString(this.currentInstructionAddress) + ": $" + Integer.toHexString(this.memory[this.currentInstructionAddress] & 0xff));
                }
                break;

            // LAX = LDA + LDX
            case LAX:
                this.ac = getOperand(instruction) & 0xff;
                operationLDX(this.ac);
                break;

            // LSE = LSR + EOR
            case LSE: {
                final int address = getOperandAddress(instruction);

                operationLSR(address);
                operationEOR(readByte(address) & 0xff);
                break;
            }

            // OAL = ORA #ee + AND + TAX
            case OAL: {
                this.ac |= 0xee;
                this.ac &= (getOperand(instruction) & 0xff);
                this.x = this.ac;
                this.zeroFlag = this.ac == 0;
                this.signFlag = this.ac >= 0x80;
                break;
            }

            // RLA = ROL + AND
            case RLA: {
                final int address = getOperandAddress(instruction);

                operationROL(address);
                operationAND(readByte(address) & 0xff);
                break;
            }

            // RRA = ROR + ADC
            case RRA: {
                final int address = getOperandAddress(instruction);

                operationROR(address);
                operationADC(readByte(address) & 0xff);
                break;
            }

            // SAX ANDs the contents of the A and X registers (leaving the contents of A 
            // intact), subtracts an immediate value, and then stores the result in X.
            case SAX: {
                this.x = (this.ac & this.x) - (getOperand(instruction) & 0xff);
                this.carryFlag = x >= 0;
                this.x &= 0xff;
                this.zeroFlag = this.x == 0;
                this.signFlag = this.x >= 0x80;
                break;
            }

            // SAY ANDs the contents of the Y register with  and stores the 
            // result in memory.
            case SAY: {
                final int oldAc = this.ac;

                this.ac = this.y;
                operationAND(readByte(0x78) & 0xff);
                writeByte(getOperandAddress(instruction), (byte)this.ac);
                this.ac = oldAc;
                break;
            }

            // SKB = skip byte
            case SKB:
                getOperand(instruction);
                break;

            // SKW = skip word
            case SKW:
                getOperand(instruction);
                if (instruction.addressMode == CPU6502Instruction.INDIRECT_X) {
                    addPC(1);
                }
                break;

            // TAS ANDs the contents of the A and X registers (without changing 
            // the contents of either register) and transfers the result to the stack 
            // pointer.  It then ANDs that result with the contents of the high byte of 
            // the target address of the operand +1 and stores that final result in 
            // memory.
            case TAS: {
                final int address = getOperandAddress(instruction);

                this.sp = this.ac & this.x;
                writeByte(address, (byte) (this.sp & ((address >> 8) + 1)));
                break;
            }

            // XAA = TXA + AND
            case XAA:
                this.ac = (readByte(this.pc - 1) & 0xff) & this.x & (this.ac | 0xee);
                this.zeroFlag = this.ac == 0;
                this.signFlag = this.ac >= 0x80;
                break;

            // XAS ANDs the contents of the X register with  and stores the 
            // result in memory
            case XAS: {
                final int oldAc = this.ac;

                this.ac = this.x;
                operationAND(readByte(0x65) & 0xff);
                writeByte(getOperandAddress(instruction), (byte)this.ac);
                this.ac = oldAc;
                break;
            }

            default:
                // try the extended instructions
                emulateExtendedInstruction(instruction);
        }
    }

    /**
     * Emulate an instruction that does not belong to the standard C64 instruction set.
     * This method will only be called if the given instruction could not be interpreted
     * by emulateInstruction. The default behaviour is to pause and issue a warning.
     * Subclasses may override this method to provide support for new instructions.
     *
     * @param   instruction instruction to interpret
     * @see de.joergjahnke.c64.CPU6502#emulateNextInstruction
     */
    protected void emulateExtendedInstruction(final CPU6502Instruction instruction) {
        if (null != getLogger()) {
            getLogger().warning("Not implemented instruction at $" + Integer.toHexString(this.currentInstructionAddress) + ": $" + Integer.toHexString(this.memory[this.currentInstructionAddress] & 0xff));
        }
        try {
            Thread.sleep(100);
        } catch (Exception e) {
        }
    }

    /**
     * Reset the CPU
     */
    public void reset() {
        // reset processor flags
        this.signFlag = false;
        this.zeroFlag = false;
        this.overflowFlag = false;
        this.carryFlag = false;
        this.decimalFlag = false;
        this.breakFlag = false;
        this.interruptFlag = false;
        // reset interrupt flags
        this.isCheckInterrupt = false;
        this.isNMILow = false;
        this.lastNMIState = false;
        this.isIRQLow = false;
        // reset IRQs and NMIs
        this.irqs.removeAllElements();
        this.nmis.removeAllElements();
    }

    /**
     * Read a ROM file to the main memory
     *
     * @param   resource    denotes file to load
     * @param   startMem    start address in memory
     * @param   len length of file
     */
    protected void loadROM(final String resource, final int startMem, final int len) {
        if (null != getLogger()) {
            getLogger().info("Reading ROM: " + resource);
        }

        final InputStream is = this.device.resourceLoader.getResource(resource);

        try {
            for (int i = 0; i < len; ++i) {
                final int b = is.read();

                if (b == -1) {
                    throw new Exception("Unexpected end of ROM file!");
                }
                this.memory[startMem + i] = (byte)b;
            }
            if (null != getLogger()) {
                getLogger().info("Installed ROM at $" + Integer.toHexString(startMem) + ", " + len + " bytes read.");
            }
        } catch (Exception e) {
            if (null != getLogger()) {
                getLogger().warning("Problem reading ROM file " + resource + ": " + e + "!");
            }
        } finally {
            try {
                is.close();
            } catch (Exception e) {
            }
        }
    }

    /**
     * Read the CPUs start address from $fffc and $fffd
     *
     * @param   CPU start address
     */
    protected int getStartAddress() {
        return (readByte(RESET_VECTOR) & 0xff) + ((readByte(RESET_VECTOR + 1) & 0xff) << 8);
    }

    // implementation of the Throttleable interface
    public final void throttle(final long ms) {
        this.throttledMillis += ms;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
        }
    }

    public final long getThrottledTime() {
        return this.throttledMillis;
    }

    public final void resetThrottleTime() {
        this.throttledMillis = 0;
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        out.writeInt(this.ramSize);
        for (int i = 0; i < this.ramSize; ++i) {
            out.writeByte(this.memory[i]);
        }
        out.writeBoolean(this.isNMILow);
        out.writeBoolean(this.lastNMIState);
        out.writeBoolean(this.isIRQLow);
        out.writeBoolean(this.isCheckInterrupt);
        out.writeLong(this.cycles);
        out.writeInt(this.pc);
        out.writeInt(this.currentInstructionAddress);
        out.writeBoolean(this.signFlag);
        out.writeBoolean(this.zeroFlag);
        out.writeBoolean(this.overflowFlag);
        out.writeBoolean(this.carryFlag);
        out.writeBoolean(this.decimalFlag);
        out.writeBoolean(this.breakFlag);
        out.writeBoolean(this.interruptFlag);
        out.writeInt(this.ac);
        out.writeInt(this.x);
        out.writeInt(this.y);
        out.writeInt(this.sp);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        this.ramSize = in.readInt();
        for (int i = 0; i < this.ramSize; ++i) {
            this.memory[i] = in.readByte();
        }
        this.isNMILow = in.readBoolean();
        this.lastNMIState = in.readBoolean();
        this.isIRQLow = in.readBoolean();
        this.isCheckInterrupt = in.readBoolean();
        this.cycles = in.readLong();
        this.pc = in.readInt();
        this.currentInstructionAddress = in.readInt();
        this.signFlag = in.readBoolean();
        this.zeroFlag = in.readBoolean();
        this.overflowFlag = in.readBoolean();
        this.carryFlag = in.readBoolean();
        this.decimalFlag = in.readBoolean();
        this.breakFlag = in.readBoolean();
        this.interruptFlag = in.readBoolean();
        this.ac = in.readInt();
        this.x = in.readInt();
        this.y = in.readInt();
        this.sp = in.readInt();
    }
}
