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
 * Extends the CPU6502 class for the specific handling of the C64's 6510 CPU.<br>
 * <br>
 * For documentation on the C64's memory see <a href='http://www.tkk.fi/Misc/cbm/docs/'>http://www.tkk.fi/Misc/cbm/docs/</a>.
 *
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class C64CPU6510 extends CPU6502 {

    /**
     * RAM size
     */
    private final static int RAM_SIZE = 0x10000;
    /**
     * ROM offset, the ROM starts at memory $10000
     */
    private final static int ROM_OFFSET = RAM_SIZE;
    /**
     * Basic ROM size
     */
    private final static int BASIC_ROM_SIZE = 0x2000;
    /**
     * Basic ROM start address
     */
    private final static int BASIC_ROM_ADDRESS = 0xa000;
    /**
     * Kernal ROM size
     */
    private final static int KERNAL_ROM_SIZE = 0x2000;
    /**
     * Kernal ROM start address
     */
    private final static int KERNAL_ROM_ADDRESS = 0xe000;
    /**
     * Char ROM size
     */
    private final static int CHAR_ROM_SIZE = 0x1000;
    /**
     * Char ROM start address
     */
    public final static int CHAR_ROM_ADDRESS = 0xd000;
    /**
     * Color RAM size
     */
    private final static int COLOR_RAM_SIZE = 0x0400;
    /**
     * Color RAM start address
     */
    public final static int COLOR_RAM_ADDRESS = 0xd800;
    /**
     * IO area size
     */
    private final static int IO_AREA_SIZE = 0x0200;
    /**
     * IO area start address
     */
    private final static int IO_AREA_ADDRESS = 0xde00;
    /**
     * Memory offset for color RAM
     */
    public final static int COLOR_RAM_OFFSET = ROM_OFFSET - COLOR_RAM_ADDRESS;
    /**
     * The IO RAM memory location
     */
    private final static int IO_AREA_OFFSET = ROM_OFFSET + COLOR_RAM_SIZE - IO_AREA_ADDRESS;
    /**
     * Memory offset for BASIC ROM
     */
    private final static int BASIC_ROM_OFFSET = ROM_OFFSET + COLOR_RAM_SIZE + IO_AREA_SIZE - BASIC_ROM_ADDRESS;
    /**
     * Memory offset for Kernal ROM
     */
    private final static int KERNAL_ROM_OFFSET = ROM_OFFSET + COLOR_RAM_SIZE + IO_AREA_SIZE + BASIC_ROM_SIZE - KERNAL_ROM_ADDRESS;
    /**
     * Memory offset for character ROM
     */
    public final static int CHAR_ROM_OFFSET = ROM_OFFSET + COLOR_RAM_SIZE + IO_AREA_SIZE + BASIC_ROM_SIZE + KERNAL_ROM_SIZE - CHAR_ROM_ADDRESS;
    // defaults for the ROMs
    /**
     * Is the BASIC ROM active?
     */
    protected boolean isBasicROMActive = true;
    /**
     * Is the Kernal ROM active?
     */
    protected boolean isKernalROMActive = true;
    /**
     * Is the character ROM active?
     */
    protected boolean isCharROMActive = false;
    /**
     * Is the IO area active?
     */
    protected boolean isIOActive = true;
    /**
     * the C64 instance we belong to
     */
    private final C64 c64;
    /**
     * the VIC which might block the bus
     */
    private VIC6569 vic;
    /**
     * temporary copy of the program counter with "fixed" memory address
     */
    private int pcAdjusted;
    /**
     * last difference between pcAdjusted and pc
     */
    private int lastPcAdjustment;

    /**
     * Create a new MOS6510 core
     *
     * @param   c64 the C64 instance we belong to
     */
    public C64CPU6510(final C64 c64) {
        super(c64, RAM_SIZE + COLOR_RAM_SIZE + IO_AREA_SIZE + BASIC_ROM_SIZE + KERNAL_ROM_SIZE + CHAR_ROM_SIZE);
        this.ramSize = RAM_SIZE + COLOR_RAM_SIZE + IO_AREA_SIZE;
        this.c64 = c64;
        setLogger(this.c64.getLogger());
        installROMs();
        setPC(0xfce2);
    }

    /**
     * Assign the video chip.
     * The VIC might block the bus, so we have to check on read accesses.
     *
     * @param vic   the new VIC instance
     */
    public void setVIC(final VIC6569 vic) {
        this.vic = vic;
    }

    /**
     * We return the adjusted value of the PC which might also point to the ROM area
     *
     * @return  adjusted program counter
     */
    protected final int getPC() {
        return this.pcAdjusted;
    }

    /**
     * When setting a new PC value we also calculate the adjusted PC value which might differ if we access the ROM
     *
     * @param   value   new PC value
     */
    protected final void setPC(final int value) {
        // also recalculate adjusted PC value
        if ((value & 0xf000) != (this.pc & 0xf000)) {
            this.pc = value;
            this.pcAdjusted = getAdjustedMemoryAddress(this.pc);
            this.lastPcAdjustment = this.pcAdjusted - this.pc;
        } else {
            this.pc = value;
            this.pcAdjusted = this.pc + this.lastPcAdjustment;
        }
    }

    /**
     * When adding a new PC value we also add this value to the adjusted PC value
     *
     * @param   size    value to add to the program counter
     */
    protected final void addPC(final int size) {
        this.pc += size;
        this.pcAdjusted += size;
    }

    /**
     * Adjust a memory address if we want to access the ROM area
     *
     * @param   adr original address in the area $0000-$ffff
     * @return  adjusted memory address
     */
    private final int getAdjustedMemoryAddress(final int address) {
        switch (address & 0xf000) {
            case 0xa000:
            case 0xb000:
                return this.isBasicROMActive ? BASIC_ROM_OFFSET + address : address;
            case 0xe000:
            case 0xf000:
                return this.isKernalROMActive ? KERNAL_ROM_OFFSET + address : address;
            default:
                return address;
        }
    }

    protected final byte readByte(final int address) {
        // the VIC might block the bus, then we have to wait
        while (!this.vic.isBusAvailable()) {
            this.vic.update(++this.cycles);
        }

        // read from correct RAM/ROM area or IO chips
        switch (address & 0xf000) {
            // read from Basic ROM?
            case 0xa000:
            case 0xb000:
                return this.memory[this.isBasicROMActive ? BASIC_ROM_OFFSET + address : address];

            // read from IO chips or Char ROM?
            case 0xd000:
                if (this.isIOActive) {
                    switch (address & 0xff00) {
                        // VIC-related
                        case 0xd000:
                        case 0xd100:
                        case 0xd200:
                        case 0xd300:
                            return (byte)this.c64.getVIC().readRegister(address & 0x3f);

                        // SID- and mixer-related
                        case 0xd400:
                        case 0xd500:
                        case 0xd600:
                        case 0xd700:
                            return (byte)this.c64.getSID().readRegister(address & 0x1f);

                        // Color RAM
                        case 0xd800:
                        case 0xd900:
                        case 0xda00:
                        case 0xdb00:
                            return this.memory[COLOR_RAM_OFFSET + address];

                        // CIA#1-related
                        case 0xdc00:
                            return (byte)this.c64.getCIA(0).readRegister(address & 0x0f);

                        // CIA#2-related
                        case 0xdd00:
                            return (byte)this.c64.getCIA(1).readRegister(address & 0x0f);

                        // IO area #1 and #2
                        case 0xde00:
                        case 0xdf00:
                            return this.memory[IO_AREA_OFFSET + address];
                    }
                } else {
                    return this.memory[this.isCharROMActive ? CHAR_ROM_OFFSET + address : address];
                }

            // read from Kernal ROM
            case 0xe000:
            case 0xf000:
                return this.memory[this.isKernalROMActive ? KERNAL_ROM_OFFSET + address : address];

            // read from RAM
            default:
                return this.memory[address];
        }
    }

    protected final void writeByte(final int address, final byte data) {
        // write to RAM or IO chips
        switch (address & 0xf000) {
            // when writing to RAM we need to take care of the memory addresses $0000 and $0001
            case 0x0000:
                // we have one of these special memory addresses?
                if (address <= 1) {
                    this.memory[address] = data;

                    final int p = (this.memory[ 0] ^ 0xff) | this.memory[ 1];

                    this.isKernalROMActive = ((p & 2) == 2);
                    this.isBasicROMActive = ((p & 3) == 3);
                    this.isCharROMActive = ((p & 3) != 0) && ((p & 4) == 0);
                    this.isIOActive = ((p & 3) != 0) && ((p & 4) != 0);
                } else {
                    // no, we write to RAM normally
                    this.memory[address] = data;
                }
                break;

            // we write to IO area?
            case 0xd000:
                // are the IO chips active?
                if (this.isIOActive) {
                    switch (address & 0xff00) {
                        // VIC-related registers
                        case 0xd000:
                        case 0xd100:
                        case 0xd200:
                        case 0xd300:
                            this.c64.getVIC().writeRegister(address & 0x3f, data & 0xff);
                            break;

                        // SID- and mixer-related registers
                        case 0xd400:
                        case 0xd500:
                        case 0xd600:
                        case 0xd700: {
                            this.c64.getSID().writeRegister(address & 0x1f, data & 0xff);
                            break;
                        }

                        // Color RAM
                        case 0xd800:
                        case 0xd900:
                        case 0xda00:
                        case 0xdb00:
                            this.memory[COLOR_RAM_OFFSET + address] = data;
                            break;

                        // CIA#1-related address
                        case 0xdc00:
                            this.c64.getCIA(0).writeRegister(address & 0x0f, data & 0xff);
                            break;

                        // CIA#2-related address
                        case 0xdd00:
                            this.c64.getCIA(1).writeRegister(address & 0x0f, data & 0xff);
                            break;

                        // IO area #1 and #2
                        case 0xde00:
                        case 0xdf00:
                            this.memory[IO_AREA_OFFSET + address] = data;
                            break;
                    }
                } else {
                    // no, we write to RAM normally
                    this.memory[address] = data;
                }
                break;

            // write to RAM
            default:
                this.memory[address] = data;
        }
    }

    /**
     * Reset the CPU. Also resets the C64 chips and clears the RAM.
     */
    public void reset() {
        super.reset();

        // clear RAM
        for (int i = 0; i < RAM_SIZE; ++i) {
            this.memory[i] = 0;
        }

        // reset processor port
        writeByte(0, (byte) 0x2f);
        writeByte(1, (byte) 0x37);

        // set program counter to default start address
        setPC(getStartAddress());
    }

    /**
     * Read kernal-, basic- and character-ROM
     */
    protected final void installROMs() {
        loadROM("/roms/kernal.c64", KERNAL_ROM_ADDRESS + KERNAL_ROM_OFFSET, KERNAL_ROM_SIZE);
        loadROM("/roms/basic.c64", BASIC_ROM_ADDRESS + BASIC_ROM_OFFSET, BASIC_ROM_SIZE);
        loadROM("/roms/chargen.c64", CHAR_ROM_ADDRESS + CHAR_ROM_OFFSET, CHAR_ROM_SIZE);
    }

    public void serialize(final DataOutputStream out) throws IOException {
        super.serialize(out);
        out.writeBoolean(this.isBasicROMActive);
        out.writeBoolean(this.isCharROMActive);
        out.writeBoolean(this.isIOActive);
        out.writeBoolean(this.isKernalROMActive);
        out.writeInt(this.pcAdjusted);
        out.writeInt(this.lastPcAdjustment);

        out.writeInt(this.irqs.size());
        for (int i = 0; i < this.irqs.size(); ++i) {
            out.writeUTF(this.irqs.elementAt(i).getClass().getName());
        }

        out.writeInt(this.nmis.size());
        for (int i = 0; i < this.nmis.size(); ++i) {
            out.writeUTF(this.nmis.elementAt(i).getClass().getName());
        }
    }

    public void deserialize(final DataInputStream in) throws IOException {
        super.deserialize(in);
        this.isBasicROMActive = in.readBoolean();
        this.isCharROMActive = in.readBoolean();
        this.isIOActive = in.readBoolean();
        this.isKernalROMActive = in.readBoolean();
        this.pcAdjusted = in.readInt();
        this.lastPcAdjustment = in.readInt();

        int size = in.readInt();

        this.irqs.removeAllElements();
        for (int i = 0; i < size; ++i) {
            final String className = in.readUTF();

            if (VIC6569.class.getName().equals(className)) {
                this.irqs.addElement(this.c64.getVIC());
            } else if (CIA6526_1.class.getName().equals(className)) {
                this.irqs.addElement(this.c64.getCIA(0));
            } else if (CIA6526_2.class.getName().equals(className)) {
                this.irqs.addElement(this.c64.getCIA(1));
            } else {
                throw new IllegalStateException("Unsupported IRQ type for deserialization: '" + className + "'!");
            }
        }

        size = in.readInt();
        this.nmis.removeAllElements();
        for (int i = 0; i < size; ++i) {
            final String className = in.readUTF();

            if (VIC6569.class.getName().equals(className)) {
                this.nmis.addElement(this.c64.getVIC());
            } else if (CIA6526_1.class.getName().equals(className)) {
                this.nmis.addElement(this.c64.getCIA(0));
            } else if (CIA6526_2.class.getName().equals(className)) {
                this.nmis.addElement(this.c64.getCIA(1));
            } else {
                throw new IllegalStateException("Unsupported NMI type for deserialization: '" + className + "'!");
            }
        }
    }
}
