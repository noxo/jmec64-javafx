/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.io.SerializationUtils;
import de.joergjahnke.common.ui.Color;
import de.joergjahnke.common.util.DefaultObservable;
import de.joergjahnke.common.util.Observer;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Implements the functionality of the C64's VIC II chip
 *
 * For a good German documentation on the MOS 6567/6569 Videocontroller (VIC-II),
 * see <a href='http://www.minet.uni-jena.de/~andreasg/c64/vic_artikel/vic_artikel_1.htm'>http://www.minet.uni-jena.de/~andreasg/c64/vic_artikel/vic_artikel_1.htm</a>,
 * <a href='http://cbmmuseum.kuto.de/zusatz_6569_vic2.html'>http://cbmmuseum.kuto.de/zusatz_6569_vic2.html</a> or
 * <a href='http://unusedino.de/ec64/technical/misc/vic656x/vic656x-german.html'>http://unusedino.de/ec64/technical/misc/vic656x/vic656x-german.html</a>. An English
 * version of the documentation you can find at <a href='http://www.unusedino.de/ec64/technical/misc/vic656x/vic656x.html'>http://www.unusedino.de/ec64/technical/misc/vic656x/vic656x.html</a>.<br>
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class VIC6569 extends DefaultObservable implements IOChip, Observer, Serializable {

    /**
     * width of a character
     */
    private final static int CHAR_WIDTH = 8;
    /**
     * height of a character
     */
    private final static int CHAR_HEIGHT = 8;
    /**
     * number of columns on the screen
     */
    private final static int CHAR_COLUMNS = 40;
    /**
     * number of rows on the screen
     */
    private final static int CHAR_ROWS = 25;
    /**
     * the inner pixels width without border
     */
    public final static int INNER_WIDTH = CHAR_COLUMNS * CHAR_WIDTH;
    /**
     * the inner pixels height without border
     */
    public final static int INNER_HEIGHT = CHAR_ROWS * CHAR_HEIGHT;
    /**
     * number of "characters" we paint as border on the left and right
     */
    protected final static int BORDER_COLUMNS = 5;
    /**
     * number of "characters" we paint as border on the top and bottom
     */
    private final static int BORDER_ROWS = 5;
    /**
     * border width in pixels
     */
    public final static int BORDER_WIDTH = BORDER_COLUMNS * CHAR_WIDTH;
    /**
     * border height in pixels
     */
    public final static int BORDER_HEIGHT = BORDER_ROWS * CHAR_HEIGHT;
    /**
     * the total pixels width including border
     */
    public final static int TOTAL_WIDTH = INNER_WIDTH + 2 * BORDER_WIDTH;
    /**
     * the total pixels height including border
     */
    public final static int TOTAL_HEIGHT = INNER_HEIGHT + 2 * BORDER_HEIGHT;
    /**
     * standard sprite width in pixels
     */
    private final static int SPRITE_WIDTH = 24;
    /**
     * number of sprites
     */
    private final static int SPRITES = 8;
    /**
     * maximum number of lines to process
     */
    private final static int MAX_RASTERS = 312;
    /**
     * standard VIC colors: black, white, red, cyan, purple, green, blue, yellow, orange,
     *                      brown, light red, dark gray, gray, light green, light blue, light gray
     */
    private final static int[] VIC_COLORS = {
        0xff000000, 0xffffffff, 0xffe04040, 0xff60ffff, 0xffe060e0, 0xff40e040, 0xff4040e0, 0xffffff40,
        0xffe0a040, 0xff9c7448, 0xffffa0a0, 0xff545454, 0xff888888, 0xffa0ffa0, 0xffa0a0ff, 0xffc0c0c0
    };
    // graphic modes
    /**
     * standard text graphics mode
     */
    private final static int MODE_STANDARD_TEXT = 0;
    /**
     * multi-color text graphics mode
     */
    private final static int MODE_MULTICOLOR_TEXT = 1;
    /**
     * standard bitmap graphics mode
     */
    private final static int MODE_STANDARD_BITMAP = 2;
    /**
     * multi-color bitmap graphics mode
     */
    private final static int MODE_MULTICOLOR_BITMAP = 3;
    /**
     * ECM text graphics mode
     */
    private final static int MODE_ECM_TEXT = 4;
    /**
     * invalid text mode
     */
    private final static int MODE_INVALID_TEXT = 5;
    /**
     * invalid bitmpa mode 1
     */
    private final static int MODE_INVALID_BITMAP1 = 6;
    /**
     * invalid bitmpa mode 1
     */
    private final static int MODE_INVALID_BITMAP2 = 7;
    /**
     * bit set in the collision mask if a background pixel is set
     */
    private final static short COLLISION_MASK_BACKGROUND = 1 << 8;
    /**
     * the lowest 8 bits are reserved for the CPU
     */
    private final static short COLLISION_MASK_SPRITES = 0xff;
    // display window coordinates
    private final static int ROW25_START = 51;
    private final static int ROW25_STOP = 251;
    private final static int ROW24_START = 55;
    private final static int ROW24_STOP = 247;
    private final static int COL40_START = 42;
    private final static int COL40_STOP = 362;
    private final static int COL38_START = 49;
    private final static int COL38_STOP = 353;
    /**
     * first line we need to paint
     */
    private final static int FIRST_LINE = ROW25_START - BORDER_HEIGHT;
    /**
     * last line (+1) we need to paint
     */
    private final static int LAST_LINE = ROW25_STOP + BORDER_HEIGHT;
    /**
     * First line when we check for bad lines
     */
    private final static int FIRST_DMA_LINE = 0x30;
    /**
     * Last line when we check for bad lines
     */
    private final static int LAST_DMA_LINE = 0xf7;
    /**
     * number of VIC cycles per line
     */
    private final static int CYCLES_PER_LINE = 63;
    /**
     * raster IRQ flag
     */
    private final static int IRQ_RASTER = 1;
    /**
     * sprite-background collision IRQ flag
     */
    private final static int IRQ_SPRITE_BACKGROUND_COLLISION = 2;
    /**
     * sprite-sprite collision IRQ flag
     */
    private final static int IRQ_SPRITE_SPRITE_COLLISION = 4;
    /**
     * lightpen IRQ flag
     */
    private final static int IRQ_LIGHTPEN = 8;
    /**
     * base address of the color RAM
     */
    private final static int COLOR_RAM_BASE = C64CPU6510.COLOR_RAM_OFFSET + C64CPU6510.COLOR_RAM_ADDRESS;
    /**
     * C64 instance we work for
     */
    private final C64 c64;
    /**
     * cpu working in this C64 instance
     */
    private final CPU6502 cpu;
    /**
     * main memory
     */
    private final byte[] memory;
    /**
     * memory for chip registers
     */
    protected final int[] registers = new int[0x40];
    /**
     * CIA to determine the video memory base address
     */
    private final CIA6526 cia;
    /**
     * video counter, a 10 bit counter
     */
    private int vc = 0;
    /**
     * video counter base, a 10 bit data register
     */
    private int vcBase = 0;
    /**
     * row counter, a 3 bit counter
     */
    private int rc = 7;
    /**
     * does the VIC block the bus or is it free?
     */
    private boolean isBusAvailable = true;
    /**
     * is the display active or idle?
     */
    private boolean isDisplayActive = true;
    /**
     * are bad lines enabled for this display line?
     */
    private boolean areBadLinesEnabled = false;
    /**
     * current vic cycle
     */
    private int lineCycle = 1;
    /**
     * current raster line
     */
    private int rasterY = MAX_RASTERS - 1;
    /**
     * the 8 sprites
     */
    private Sprite[] sprites = new Sprite[SPRITES];
    /**
     * contains the color data for the current line
     */
    private int[] colorData = new int[CHAR_COLUMNS];
    /**
     * contains the video matrix data for the current line
     */
    private int[] videoMatrixData = new int[CHAR_COLUMNS];
    /**
     * contains the graphics data for the next character
     */
    private int graphicsData;
    /**
     * main frame flip-flop
     */
    private boolean showBorderMain = true;
    /**
     * vertical frame flip-flop
     */
    private boolean showBorderVertical = true;
    /**
     * current graphics mode (text/bitmap/ECM etc.)
     */
    private int graphicsMode = 0;
    /**
     * is ECM mode active?
     */
    private boolean isECM = false;
    /**
     * is bitmap mode active?
     */
    private boolean isBMM = false;
    /**
     * is multi-color mode active?
     */
    private boolean isMCM = false;
    /**
     * video memory base address
     */
    private int videoMemBase;
    /**
     * video matrix base address
     */
    private int videoMatrixBase;
    /**
     * character memory  base address
     */
    private int charMemBase;
    /**
     * bitmap memory base address
     */
    private int bitmapMemBase;
    /**
     * background colors as C64 codes
     */
    private int[] backgroundColorCodes = new int[4];
    /**
     * background colors as RGB
     */
    private int[] backgroundColors = new int[4];
    /**
     * border color
     */
    private int borderColor = VIC_COLORS[0];
    /**
     * horizontal scrolling value
     */
    private int xscroll = 0;
    /**
     * vertical scrolling value
     */
    private int yscroll = 0;
    /**
     * internal IRQ flags
     */
    private int irqFlags = 0;
    /**
     * masks for the IRQ flags
     */
    private int irqMask = 0;
    /**
     * raster line where we trigger an IRQ
     */
    private int rasterYIRQ = 0;
    /**
     * here we create the C64 pixels
     */
    protected int[] pixels = null;
    /**
     * index of next pixel to paint
     */
    protected int nextPixel;
    /**
     * here we can save the position of the next pixel to have it restored later
     */
    protected int savedPosition;
    /**
     * we only paint the n-th frame
     */
    private int frameSkip = 1;
    /**
     * number of frames painted
     */
    private int frames = 0;
    /**
     * do we paint this frame?
     */
    private boolean isPaintFrame = true;
    /**
     * do we paint border?
     */
    private boolean isPaintBorders = true;
    /**
     * number of VIC cycles we already emulated, used to synchronize the VIC with the CPU
     */
    private long cycles = 0;
    /**
     * the next update of the screen
     */
    private long nextUpdate = CYCLES_PER_LINE;
    /**
     * current line to paint
     */
    protected int paintY = 0;
    /**
     * line in inner screen to paint
     */
    private int innerY = 0;
    /**
     * do we paint the current line?
     */
    private boolean isPaintableLine = false;
    /**
     * is the current line within the display area?
     */
    private boolean isDisplayLine = false;
    /**
     * do we have a bad-line state?
     */
    private boolean isBadLine = false;
    /**
     * here we store hash-codes of the graphics we last painted
     * this enables us to avoid re-painting if the screen already contains the most current data
     */
    protected int[][] lastPainted = new int[TOTAL_HEIGHT + 1][CHAR_COLUMNS + 2 * BORDER_COLUMNS];
    /**
     * part of the hashcode used for determining the last painted graphics that only
     * changes when some of the VIC registers are modified
     */
    private int hashCodeBase = 0;
    /**
     * contains the collision mask for the current line
     */
    private short[] collisionMask = new short[TOTAL_WIDTH + SPRITE_WIDTH * 2];
    /**
     * background collision mask from the previous frame
     */
    private short[][] lastBackgroundMask = new short[TOTAL_HEIGHT][TOTAL_WIDTH + SPRITE_WIDTH * 2];
    /**
     * current position in the collision mask
     */
    private int collisionPos = 0;
    /**
     * current column in the last painted matrix
     */
    protected int hashCol = 0;

    /**
     * Creates a new instance of VIC6569
     *
     * @param   c64 the C64 the VIC works for
     */
    public VIC6569(final C64 c64) {
        this.c64 = c64;
        this.cpu = c64.getCPU();
        this.cia = c64.getCIA(1);
        this.memory = this.cpu.getMemory();

        // create sprites
        for (int i = 0; i < sprites.length; ++i) {
            this.sprites[i] = new Sprite(this);
        }
    }

    /**
     * Copy member data from another VIC using shallow copy
     *
     * @param vic   vic to copy from
     */
    public void copy(final VIC6569 vic) {
        System.arraycopy(vic.registers, 0, this.registers, 0, this.registers.length);
        this.vc = vic.vc;
        this.vcBase = vic.vcBase;
        this.rc = vic.rc;
        this.isBusAvailable = vic.isBusAvailable;
        this.isDisplayActive = vic.isDisplayActive;
        this.areBadLinesEnabled = vic.areBadLinesEnabled;
        this.lineCycle = vic.lineCycle;
        this.rasterY = vic.rasterY;
        this.sprites = vic.sprites;
        this.colorData = vic.colorData;
        this.videoMatrixData = vic.videoMatrixData;
        this.graphicsData = vic.graphicsData;
        this.showBorderMain = vic.showBorderMain;
        this.showBorderVertical = vic.showBorderVertical;
        this.graphicsMode = vic.graphicsMode;
        this.isECM = vic.isECM;
        this.isBMM = vic.isBMM;
        this.isMCM = vic.isMCM;
        this.videoMemBase = vic.videoMemBase;
        this.videoMatrixBase = vic.videoMatrixBase;
        this.charMemBase = vic.charMemBase;
        this.bitmapMemBase = vic.bitmapMemBase;
        this.backgroundColorCodes = vic.backgroundColorCodes;
        this.backgroundColors = vic.backgroundColors;
        this.borderColor = vic.borderColor;
        this.xscroll = vic.xscroll;
        this.yscroll = vic.yscroll;
        this.irqFlags = vic.irqFlags;
        this.irqMask = vic.irqMask;
        this.rasterYIRQ = vic.rasterYIRQ;
        gotoPixel(0, this.paintY);
        this.frameSkip = vic.frameSkip;
        this.frames = vic.frames;
        this.isPaintFrame = vic.isPaintFrame;
        this.isPaintBorders = vic.isPaintBorders;
        this.cycles = vic.cycles;
        this.nextUpdate = vic.nextUpdate;
        this.paintY = vic.paintY;
        this.innerY = vic.innerY;
        this.isPaintableLine = vic.isPaintableLine;
        this.isDisplayLine = vic.isDisplayLine;
        this.isBadLine = vic.isBadLine;
        this.hashCodeBase = vic.hashCodeBase;
        this.collisionMask = vic.collisionMask;
        this.lastBackgroundMask = vic.lastBackgroundMask;
        this.collisionPos = vic.collisionPos;
        this.hashCol = vic.hashCol;
    }

    /**
     * Free most of the memory of this VIC. This VIC instance cannot be used anymore afterwards.
     */
    public void destroy() {
        this.pixels = null;
        this.lastPainted = null;
        this.lastBackgroundMask = null;
        this.collisionMask = null;
        this.sprites = null;
    }

    /**
     * Can the CPU use the bus?
     *
     * @return  true if the bus is available and can be used, otherwise false
     *          in case of false the next read access should stop the CPU until
     *          the bus is free again
     */
    public final boolean isBusAvailable() {
        return this.isBusAvailable;
    }

    /**
     * Get the width of the display area excluding the border
     *
     * @return width in pixels
     */
    public int getDisplayWidth() {
        return INNER_WIDTH;
    }

    /**
     * Get the height of the display area excluding the border
     *
     * @return height in pixels
     */
    public int getDisplayHeight() {
        return INNER_HEIGHT;
    }

    /**
     * Get the width including the border
     *
     * @return width in pixels
     */
    public int getBorderWidth() {
        return TOTAL_WIDTH;
    }

    /**
     * Get the height including the border
     *
     * @return height in pixels
     */
    public int getBorderHeight() {
        return TOTAL_HEIGHT;
    }

    /**
     * Get screen as RGB data
     *
     * @return  C64 screen pixels as RGB data
     */
    public int[] getRGBData() {
        return this.pixels;
    }

    /**
     * Determine and reserve memory required for the pixels
     */
    public void initScreenMemory() {
        this.pixels = new int[TOTAL_WIDTH * TOTAL_HEIGHT];
    }

    /**
     * Ensure that we repaint every pixel
     */
    public void repaint() {
        for (int i = 0; i < this.pixels.length; ++i) {
            this.pixels[i] = 0;
        }
        for (int i = 0; i < this.lastPainted.length; ++i) {
            for (int j = 0; j < this.lastPainted[i].length; ++j) {
                this.lastPainted[i][j] = -1;
            }
        }
    }

    /**
     * Define a given position on the screen where we paint next
     *
     * @param   x   x coordinate to set
     * @param   y   y coordinate to set
     */
    public void gotoPixel(final int x, final int y) {
        this.nextPixel = y * TOTAL_WIDTH + x;
    }

    /**
     * The next pixel is still inside the memory?
     *
     * @return  true if we can paint at the next pixel's position, false if we are outside the memory boundary
     */
    protected boolean isValidPixel() {
        return this.nextPixel >= 0 && this.nextPixel < this.pixels.length;
    }

    /**
     * Get video memory position of the next pixel to paint
     *
     * @return  video memory index
     */
    public int getNextPixel() {
        return this.nextPixel;
    }

    /**
     * Set the current paintable pixel to a given color and proceed to the next pixel afterwards
     *
     * @param   color   RGB color to set
     */
    protected void setNextPixel(final int color) {
        this.pixels[this.nextPixel++] = color;
    }

    /**
     * Skip a given number of pixels
     *
     * @param   n   number of pixels to skip
     */
    protected void skipPixels(final int n) {
        this.nextPixel += n;
    }

    /**
     * Save the current pixel position for later use
     */
    protected void saveCurrentPixelPosition() {
        this.savedPosition = this.nextPixel;
    }

    /**
     * Restore a saved position on the screen
     */
    protected void restoreSavedPixelPosition() {
        this.nextPixel = this.savedPosition;
    }

    /**
     * Get frames to be displayed
     *
     * @return  1 if every frame is going to be displayed, 2 if every 2nd frame is being displayed etc.
     */
    public final int getFrameSkip() {
        return this.frameSkip;
    }

    /**
     * Set frames to be displayed
     *
     * @param   frameSkip   1 to display every frame, 2 to display every 2nd frame etc.
     */
    public final void setFrameSkip(final int frameSkip) {
        if (frameSkip < 1) {
            throw new IllegalArgumentException("Frameskip cannot be set to values < 1!");
        }
        if (frameSkip != this.frameSkip && null != this.c64.getLogger()) {
            this.c64.getLogger().info("Setting frameskip to " + frameSkip);
        }
        this.frameSkip = frameSkip;
    }

    /**
     * Do we only paint the inner screen?
     *
     * @return  true if only the inner screen is painted but not the borders
     */
    public final boolean isSmallScreen() {
        return !this.isPaintBorders;
    }

    /**
     * Set whether we only paint the inner screen
     *
     * @param   isSmallScreen   true to have only the inner screen painted but not the borders
     */
    public final void setSmallScreen(final boolean isSmallScreen) {
        this.isPaintBorders = !isSmallScreen;
    }

    /**
     * Read the x-coordinate of a sprite
     *
     * @param   spriteNo    no of the sprite (0-7)
     * @return  x-coordinate
     */
    private int readSpriteXCoordinate(final int spriteNo) {
        return this.registers[0x00 + (spriteNo << 1)] + ((this.registers[0x10] & (1 << spriteNo)) != 0 ? 256 : 0);
    }

    /**
     * Read the y-coordinate of a sprite
     *
     * @param   spriteNo    no of the sprite (0-7)
     * @return  y-coordinate
     */
    private int readSpriteYCoordinate(final int spriteNo) {
        return this.registers[0x01 + (spriteNo << 1)];
    }

    /**
     * Read the horizontal scrolling offset
     *
     * @return  horizontal scrolling (0-7)
     */
    private int readXScroll() {
        return this.registers[0x16] & 7;
    }

    /**
     * Read the vertical scrolling offset
     *
     * @return  vertical scrolling (0-7)
     */
    private int readYScroll() {
        return this.registers[0x11] & 7;
    }

    /**
     * Read the RSEL flag which determines whether we display 24 or 25 lines of text
     *
     * @return  true if RSEL is set and we use 25 lines, otherwise false
     */
    private final boolean readRSEL() {
        return (this.registers[0x11] & 8) != 0;
    }

    /**
     * Read the CSEL flag which determines whether we display 38 or 40 columns of text
     *
     * @return  true if CSEL is set and we use 40 columns, otherwise false
     */
    private final boolean readCSEL() {
        return (this.registers[0x16] & 8) != 0;
    }

    /**
     * Read the DEN flag which tells whether the display is enabled
     *
     * @return  true if DEN is set, otherwise false
     */
    private final boolean readIsDisplayEnabled() {
        return (this.registers[0x11] & 0x10) != 0;
    }

    /**
     * Read the BMM flag that tells whether we are in bitmap mode
     *
     * @return  true if BMM is enabled, otherwise false
     */
    private final boolean readIsBitmapMode() {
        return (this.registers[0x11] & 0x20) != 0;
    }

    /**
     * Read the ECM flag that tells whether we are in extended color mode
     *
     * @return  true if ECM is enabled, otherwise false
     */
    private final boolean readIsExtendedColorMode() {
        return (this.registers[0x11] & 0x40) != 0;
    }

    /**
     * Read the MCM flag that tells whether we are in multi-color mode
     *
     * @return  true if MCM is enabled, otherwise false
     */
    private final boolean readIsMulticolorMode() {
        return (this.registers[0x16] & 0x10) != 0;
    }

    /**
     * Read the value of the raster line IRQ
     *
     * @return  raster line when to trigger an IRQ
     */
    private int readRasterLineIRQ() {
        return this.registers[0x12] + ((this.registers[0x11] & 0x80) << 1);
    }

    /**
     * Read the value of the LPX register
     */
    private int readLightPenX() {
        return this.registers[0x13];
    }

    /**
     * Read the value of the LPY register
     */
    private int readLightPenY() {
        return this.registers[0x14];
    }

    /**
     * Read whether a given sprite is activated
     *
     * @param   spriteNo    no of the sprite (0-7)
     * @return  true if the sprite is switched on, otherwise false
     */
    private final boolean readIsSpriteActivated(final int spriteNo) {
        return (this.registers[0x15] & (1 << spriteNo)) != 0;
    }

    /**
     * Read whether a given sprite is horizontally expanded
     *
     * @param   spriteNo    no of the sprite (0-7)
     * @return  true if the sprite is horizontally expanded, otherwise false
     */
    private final boolean readIsSpriteXExpanded(final int spriteNo) {
        return (this.registers[0x1d] & (1 << spriteNo)) != 0;
    }

    /**
     * Read whether a given sprite is vertically expanded
     *
     * @param   spriteNo    no of the sprite (0-7)
     * @return  true if the sprite is vertically expanded, otherwise false
     */
    private final boolean readIsSpriteYExpanded(final int spriteNo) {
        return (this.registers[0x17] & (1 << spriteNo)) != 0;
    }

    /**
     * Read the video matrix address, relative to the video memory base address.
     * Bits 5-8 of $d018 are used to move the video matrix in 1k steps.
     *
     * @see determineVideoMemoryBaseAddress
     */
    private int readVideoMatrixAddress() {
        return (this.registers[0x18] & 0xf0) << 6;
    }

    /**
     * Read the character buffer address, relative to the video memory base address.
     * Bits 1-3 of $d018 are used to move the character data in 2k steps.
     *
     * @see determineVideoMemoryBaseAddress
     */
    private int readCharacterBufferAddress() {
        return (this.registers[0x18] & 0x0e) << 10;
    }

    /**
     * Read the bitmap memory address, relative to the video memory base address.
     * Bit 3 of $d018 is used to move the bitmap data in 8k steps.
     *
     * @see determineVideoMemoryBaseAddress
     */
    private int readBitmapMemoryAddress() {
        return (this.registers[0x18] & 0x08) << 10;
    }

    /**
     * Read whether a given sprite has higher priority than the bitmap
     *
     * @param   spriteNo    no of the sprite (0-7)
     * @return  true if the sprite has higher priority than the bitmap, otherwise false
     */
    private final boolean readHasSpritePriority(final int spriteNo) {
        return (this.registers[0x1b] & (1 << spriteNo)) != 0;
    }

    /**
     * Read whether a given sprite is a multi-color sprite
     *
     * @param   spriteNo    no of the sprite (0-7)
     * @return  true if the sprite is multi-colored, otherwise false
     */
    private final boolean readIsSpriteMulticolored(final int spriteNo) {
        return (this.registers[0x1c] & (1 << spriteNo)) != 0;
    }

    /**
     * Set whether a sprite has collided with another sprite.
     * Triggers an IRQ if this was not previously done since the IRQ was last cleared.
     *
     * @param   collisions  sprite collision data containing the collision value for each sprite
     */
    private final void writeSpriteSpriteCollisions(final short collisions) {
        this.registers[0x1e] |= collisions;
        if (this.registers[0x1e] != 0) {
            activateIRQFlag(IRQ_SPRITE_SPRITE_COLLISION);
        }
    }

    /**
     * Set whether a sprite has collided with the foreground data.
     * Triggers an IRQ if this was not previously done since the IRQ was last cleared.
     *
     * @param   collisions  sprite collision data containing the collision value for each sprite
     */
    private final void writeSpriteBitmapCollisions(final short collisions) {
        this.registers[0x1f] |= collisions;
        if (this.registers[0x1f] != 0) {
            activateIRQFlag(IRQ_SPRITE_BACKGROUND_COLLISION);
        }
    }

    /**
     * Read the border color
     *
     * @return  border color index
     */
    private int readBorderColor() {
        return this.registers[0x20] & 0x0f;
    }

    /**
     * Read one of the four background colors
     *
     * @param   no  background color to read (0-3)
     * @return  background color index
     */
    private int readBackgroundColor(final int no) {
        return this.registers[0x21 + no] & 0x0f;
    }

    /**
     * Read one of the two sprite multi-colors
     *
     * @param   no  multi-color to read (0-1)
     * @return  multi-color index
     */
    private int readSpriteMultiColor(final int no) {
        return this.registers[0x25 + no] & 0x0f;
    }

    /**
     * Read the color of a given sprite
     *
     * @param   spriteNo    no of the sprite (0-7)
     * @return  sprite color index
     */
    private int readSpriteColor(final int spriteNo) {
        return this.registers[0x27 + spriteNo] & 0x0f;
    }

    /**
     * Get the first column of the display window
     */
    private final int getFirstX() {
        return readCSEL() ? COL40_START : COL38_START;
    }

    /**
     * Get the last column of the display window
     */
    private final int getLastX() {
        return readCSEL() ? COL40_STOP : COL38_STOP;
    }

    /**
     * Get the first row of the display window
     */
    private final int getFirstY() {
        return readRSEL() ? ROW25_START : ROW24_START;
    }

    /**
     * Get the last row of the display window
     */
    private final int getLastY() {
        return readRSEL() ? ROW25_STOP : ROW24_STOP;
    }

    /**
     * Check whether we have a "bad line" state.
     * See the documentation on the VIC II for more on "bad lines".
     *
     * We are not really precise about determining this state. A bad line not only
     * occurs if the display is enabled during this check but if at any cycle in
     * raster line $30 the display enable bit was set. We accept this small mistake here.
     *
     * @return  true if we have a bad line state, otherwise false
     */
    private final boolean determineBadLine() {
        return this.areBadLinesEnabled && this.rasterY >= FIRST_DMA_LINE && this.rasterY <= LAST_DMA_LINE && (this.rasterY & 7) == this.yscroll;
    }

    /**
     * Should a VIC IRQ be triggered?
     */
    private final boolean isIRQTriggered() {
        return (this.irqFlags & this.irqMask & 0x0f) != 0;
    }

    /**
     * Get the sprite data pointer for a given sprite
     *
     * @param   spriteNo    no of the sprite (0-7)
     * @return  memory address where to read the sprite data
     */
    private final int getSpriteDataPointer(final int spriteNo) {
        return this.videoMemBase + (readByte(this.videoMatrixBase + 0x03f8 + spriteNo) & 0xff) * 64;
    }

    /**
     * Determine the graphics mode after a change to VIC graphics mode registers
     */
    private void determineGraphicsMode() {
        this.isECM = readIsExtendedColorMode();
        this.isBMM = readIsBitmapMode();
        this.isMCM = readIsMulticolorMode();
        this.graphicsMode = (this.isECM ? 4 : 0) + (this.isBMM ? 2 : 0) + (this.isMCM ? 1 : 0);
        calculateHashCodeBase();
    }

    /**
     * Get the video memory base address, which is determined by the inverted
     * bits 0-1 of port A on CIA 2, plus the video matrix base address plus the
     * character data base address plus the bitmap memory base address.
     */
    private final void determineVideoMemoryBaseAddresses() {
        this.videoMemBase = (~this.cia.readRegister(CIA6526.PRA) & 3) << 14;
        this.videoMatrixBase = this.videoMemBase + readVideoMatrixAddress();
        this.charMemBase = this.videoMemBase + readCharacterBufferAddress();
        this.bitmapMemBase = this.videoMemBase + readBitmapMemoryAddress();
    }

    /**
     * If we are in raster line 48 we check whether bad lines are enabled for this frame
     */
    private final void determineBadLinesEnabled() {
        if (this.rasterY == FIRST_DMA_LINE) {
            this.areBadLinesEnabled = readIsDisplayEnabled();
        }
    }

    /**
     * Calculate the part of the hashcode used for screen caching which changes
     * only when some of the VIC registers are modified.
     */
    private void calculateHashCodeBase() {
        this.hashCodeBase = (this.graphicsMode << 8) ^ (this.backgroundColorCodes[0] << 14) ^ (this.backgroundColorCodes[1] << 16) ^ (this.backgroundColorCodes[2] << 18) ^ (this.backgroundColorCodes[3] << 20) ^ ((this.xscroll ^ this.yscroll) << 28);
    }

    /**
     * Set an IRQ flag and trigger an IRQ if the corresponding IRQ mask is set.
     * The IRQ only gets activated, i.e. flag 0x80 gets set, if it was not active before.
     */
    private void activateIRQFlag(final int flag) {
        if ((this.irqFlags & flag) == 0) {
            this.irqFlags |= flag;
            if ((this.irqMask & flag) != 0) {
                this.irqFlags |= 0x80;
                this.cpu.setIRQ(this, true);
            }
        }
    }

    /**
     * Read video matrix and color data for the next character
     */
    private void doVideoMatrixAccess() {
        final int vmli_ = this.lineCycle - 15;

        // the display is enabled?
        if (this.isDisplayActive) {
            // get data according to the current mode
            final int vc_ = this.vc;

            this.videoMatrixData[vmli_] = readByte(this.videoMatrixBase + vc_) & 0xff;
            this.colorData[vmli_] = this.memory[COLOR_RAM_BASE + vc_] & 0x0f;
        } else {
            this.videoMatrixData[vmli_] = 0;
            this.colorData[vmli_] = 0;
        }
    }

    /**
     * Do a graphics access to memory and read the pixel data to paint
     */
    private void doGraphicsAccess() {
        // determine address to read pixel data from
        if (this.isDisplayActive) {
            int address;

            if (this.isBMM) {
                // in bitmap mode we use bitmap memory base address + vc * 8 for the address
                address = this.bitmapMemBase | (this.vc << 3) | this.rc;
            } else {
                // in text mode we use character memory base + character to display * 8 for the address
                address = this.charMemBase | (this.videoMatrixData[this.lineCycle - 16] << 3) | this.rc;
            }
            // in ECM mode the data addresses are modified
            if (this.isECM) {
                address &= 0x1f9ff;
            }

            this.vc = (this.vc + 1) & 0x3ff;

            // read pixel data
            this.graphicsData = readByte(address) & 0xff;
        } else {
            // we let all pixels have the background color
            this.graphicsData = 0;
        }
    }

    /**
     * Draw pixels with border color
     * 
     * @param   n   column to paint
     */
    private void drawBorder(final int n) {
        // check whether we have to paint, using the negative border color as hashcode
        if (this.lastPainted[this.paintY][n] != -this.borderColor) {
            // then store hashcode and paint
            this.lastPainted[this.paintY][n] = -this.borderColor;

            final int borderColor_ = this.borderColor;
            final short[] collisionMask_ = this.collisionMask;

            for (int i = 0, collisionPos_ = this.collisionPos; i < 8; ++i) {
                setNextPixel(borderColor_);
                collisionMask_[collisionPos_++] = 0;
            }
        } else {
            // otherwise we leave the pixels and the collision mask as they were before
            skipPixels(8);
        }
        this.collisionPos += 8;
    }

    /**
     * Do a graphics access, draw graphics, check collisions and increase vc and vmli when in display mode.
     * For collision checking we assume that the collision mask is empty i.e. the sprites have to be painted
     * and checked for collisions later.
     */
    private void drawGraphics() {
        final int n = this.lineCycle - 17;

        this.hashCol = n + BORDER_COLUMNS;

        // we have to show the border color?
        if (this.showBorderMain) {
            drawBorder(this.hashCol);
        } else {
            // no, show normal graphics
            // determine index in colordata, matrixdata and address arrays
            final int graphicsData_ = this.graphicsData;
            final int videoMatrixData_ = this.videoMatrixData[n];
            final int colorData_ = this.colorData[n];

            // calculate hash-code to determine whether we need to paint
            // a hashcode can be created from data below (8 bits) | graphics-mode (3) |
            // 4 background colors (4x4) | data from color RAM read (4) |
            // vertical and horizontal scrolling value (2x4) | data from matrix read read (8)
            final int hashCode = this.hashCodeBase ^ graphicsData_ ^ (videoMatrixData_ << 10) ^ (colorData_ << 24);

            if (this.lastPainted[this.paintY][this.hashCol] != hashCode) {
                this.lastPainted[this.paintY][this.hashCol] = hashCode;

                final short[] collisionMask_ = this.collisionMask;

                // paint data depending on the graphics mode
                switch (this.graphicsMode) {
                    case MODE_STANDARD_TEXT: {
                        final int foregroundColor = VIC_COLORS[colorData_];
                        final int backgroundColor = this.backgroundColors[0];

                        for (int i = 0, mask = 0x80; i < 8; ++i, mask >>= 1) {
                            final boolean isPixelSet = (graphicsData_ & mask) != 0;

                            setNextPixel(isPixelSet ? foregroundColor : backgroundColor);
                            collisionMask_[this.collisionPos++] = isPixelSet ? COLLISION_MASK_BACKGROUND : 0;
                        }
                        break;
                    }
                    case MODE_MULTICOLOR_TEXT: {
                        final int foregroundColor = VIC_COLORS[colorData_ & 0x07];

                        // multicolor is used?
                        if ((colorData_ & 0x08) != 0) {
                            // yes, use four colors
                            final int[] colors = {this.backgroundColors[0], this.backgroundColors[1],
                                this.backgroundColors[2], foregroundColor
                            };

                            for (int i = 0, mask = 0xc0; i < 8; i += 2, mask >>= 2) {
                                final int val = (graphicsData_ & mask) >> (6 - i);
                                final int color = colors[val];

                                setNextPixel(color);
                                setNextPixel(color);
                                collisionMask_[this.collisionPos] = collisionMask_[this.collisionPos + 1] = val > 1 ? COLLISION_MASK_BACKGROUND : 0;
                                this.collisionPos += 2;
                            }
                        } else {
                            // no, use 2 colors like in standard mode
                            final int backgroundColor = this.backgroundColors[0];

                            for (int i = 0, mask = 0x80; i < 8; ++i, mask >>= 1) {
                                final boolean isPixelSet = (graphicsData_ & mask) != 0;

                                setNextPixel(isPixelSet ? foregroundColor : backgroundColor);
                                collisionMask_[this.collisionPos++] = isPixelSet ? COLLISION_MASK_BACKGROUND : 0;
                            }
                        }
                        break;
                    }
                    case MODE_STANDARD_BITMAP: {
                        final int foregroundColor = VIC_COLORS[videoMatrixData_ >> 4];
                        final int backgroundColor = VIC_COLORS[videoMatrixData_ & 0x0f];

                        for (int i = 0, mask = 0x80; i < 8; ++i, mask >>= 1) {
                            final boolean isPixelSet = (graphicsData_ & mask) != 0;

                            setNextPixel(isPixelSet ? foregroundColor : backgroundColor);
                            collisionMask_[this.collisionPos++] = isPixelSet ? COLLISION_MASK_BACKGROUND : 0;
                        }
                        break;
                    }
                    case MODE_MULTICOLOR_BITMAP: {
                        final int[] colors = {this.backgroundColors[0], VIC_COLORS[videoMatrixData_ >> 4],
                            VIC_COLORS[videoMatrixData_ & 0x0f], VIC_COLORS[colorData_]
                        };

                        for (int i = 0, mask = 0xc0; i < 8; i += 2, mask >>= 2) {
                            final int val = (graphicsData_ & mask) >> (6 - i);
                            final int color = colors[val];

                            setNextPixel(color);
                            setNextPixel(color);
                            collisionMask_[this.collisionPos] = collisionMask_[this.collisionPos + 1] = val > 1 ? COLLISION_MASK_BACKGROUND : 0;
                            this.collisionPos += 2;
                        }
                        break;
                    }
                    case MODE_ECM_TEXT: {
                        final int foregroundColor = VIC_COLORS[colorData_];
                        final int backgroundColor = this.backgroundColors[videoMatrixData_ >> 6];

                        for (int i = 0, mask = 0x80; i < 8; ++i, mask >>= 1) {
                            final boolean isPixelSet = (graphicsData_ & mask) != 0;

                            setNextPixel(isPixelSet ? foregroundColor : backgroundColor);
                            collisionMask_[this.collisionPos++] = isPixelSet ? COLLISION_MASK_BACKGROUND : 0;
                        }
                        break;
                    }
                    case MODE_INVALID_TEXT: {
                        for (int i = 0, mask = 0x80; i < 8; ++i, mask >>= 1) {
                            final boolean isPixelSet = (graphicsData_ & mask) != 0;

                            setNextPixel(0);
                            collisionMask_[this.collisionPos++] = isPixelSet ? COLLISION_MASK_BACKGROUND : 0;
                        }
                        break;
                    }
                    case MODE_INVALID_BITMAP1: {
                        for (int i = 0, mask = 0x80; i < 8; ++i, mask >>= 1) {
                            final boolean isPixelSet = (graphicsData_ & mask) != 0;

                            setNextPixel(0);
                            collisionMask_[this.collisionPos++] = isPixelSet ? COLLISION_MASK_BACKGROUND : 0;
                        }
                        break;
                    }
                    case MODE_INVALID_BITMAP2: {
                        for (int i = 0, mask = 0xc0; i < 8; i += 2, mask >>= 2) {
                            final int val = (graphicsData_ & mask) >> (6 - i);

                            setNextPixel(0);
                            setNextPixel(0);
                            collisionMask_[this.collisionPos] = collisionMask_[this.collisionPos + 1] = val > 1 ? COLLISION_MASK_BACKGROUND : 0;
                            this.collisionPos += 2;
                        }
                        break;
                    }
                    default:
                        throw new IllegalArgumentException("Illegal graphics mode: " + this.graphicsMode + "!");
                }
            } else {
                // we leave the pixels and the collision mask as they were before
                skipPixels(8);
                this.collisionPos += 8;
            }
        }
    }

    /**
     * Do sprite access in a raster line
     */
    private void doSpriteAccess(final int n) {
        // determine the sprite data pointer
        final Sprite sprite = this.sprites[n];

        if (sprite.isPainting()) {
            sprite.setDataPointer(getSpriteDataPointer(n));
            // read sprite data if the sprite is enabled
            if (sprite.isEnabled()) {
                this.isBusAvailable = false;
                sprite.readLineData();
            } else {
                this.isBusAvailable = true;
            }
        } else {
            this.isBusAvailable = true;
        }
    }

    /**
     * Update sprite image data and check for sprite collisions among the sprites and between sprites and background.
     */
    private void drawSprites() {
        // store current screen position
        saveCurrentPixelPosition();

        // determine minimum and maximum x-position to paint
        final int firstX = getFirstX(),  lastX = getLastX();

        // update sprites on the screen
        final Sprite[] sprites_ = this.sprites;

        for (int i = sprites_.length - 1, mask = 0x80; i >= 0; --i, mask >>= 1) {
            final Sprite sprite = sprites_[i];

            // we have to paint this sprite?
            if (sprite.isPainting()) {
                // then go to the sprites position on the screen
                int x = sprite.getX() + BORDER_WIDTH - SPRITE_WIDTH;

                if (x > TOTAL_WIDTH) {
                    continue;
                }

                gotoPixel(x, this.paintY);

                this.hashCol = ((sprite.getX() - this.xscroll - SPRITE_WIDTH) >> 3) + BORDER_COLUMNS;

                // paint all sprite pixels of this line
                while (!sprite.isLineFinished()) {
                    final int colIndex = sprite.getNextPixel();
                    boolean wasPixelSet = false;
                    boolean collidesWithBackground = false;

                    // the sprite pixel is set?
                    if (colIndex != 0) {
                        // check for collisions
                        if (this.collisionMask[x] != 0) {
                            final int coll = this.collisionMask[x];

                            // a collision with the background has occurred?
                            if ((coll & COLLISION_MASK_BACKGROUND) != 0) {
                                // set background collision for this sprite
                                writeSpriteBitmapCollisions((short) mask);
                                collidesWithBackground = true;
                            }
                            // a collision with another sprite has occurred
                            if ((coll & COLLISION_MASK_SPRITES) != 0) {
                                // set sprite collision for this sprite and the other sprites in the mask
                                writeSpriteSpriteCollisions((short) (mask | (coll & COLLISION_MASK_SPRITES)));
                            }
                        }
                        // activate this sprite in the collision mask
                        this.collisionMask[x] |= mask;
                        // don't paint outside the borders and only if we have priority over the background or no background pixel
                        if (this.isDisplayLine && x >= firstX && x < lastX && (!collidesWithBackground || sprite.hasPriority())) {
                            // set sprite pixel
                            setNextPixel(sprite.getColor(colIndex));
                            wasPixelSet = true;
                            // invalidate cached screen content behind this sprite
                            this.lastPainted[this.paintY][this.hashCol] = -1;
                        }
                    }

                    // proceed to the next sprite pixel
                    ++x;
                    if (!wasPixelSet) {
                        skipPixels(1);
                    }
                    if (x % CHAR_WIDTH == 0) {
                        ++this.hashCol;
                    }
                }
            }
        }

        // restore the old pixel position
        restoreSavedPixelPosition();
    }

    /**
     * Invalidate a line in the cache to have it fully repainted
     *
     * @param   n   line to be invalidated
     */
    protected final void invalidateCacheLine(final int n) {
        System.arraycopy(this.lastPainted[TOTAL_HEIGHT], 0, this.lastPainted[n], 0, CHAR_COLUMNS + 2 * BORDER_COLUMNS);
    }

    /**
     * Read a byte from the VIC's memory
     * 
     * @param   adr address to read from
     * @return  byte in memory
     */
    protected final byte readByte(final int adr) {
        if ((adr & 0x7000) == 0x1000) {
            return this.memory[C64CPU6510.CHAR_ROM_ADDRESS + C64CPU6510.CHAR_ROM_OFFSET + (adr & 0x0fff)];
        } else {
            return this.memory[adr];
        }
    }

    // implementation of interface IOChip
    public final int readRegister(final int register) {
        switch (register) {
            // control register 1
            case 0x11: {
                int result = this.registers[register];

                // we don't use the raster bit set in memory or the display enabled bit
                result &= 0x7f;
                // instead we use bit 8 from the internal raster counter
                result |= ((this.rasterY & 0x100) >> 1);
                return result;
            }

            // Raster Counter
            case 0x12:
                return this.rasterY & 0xff;

            // Interrupt Pending Register
            case 0x19:
                return this.irqFlags | 0x70;

            // Interrupt Mask Register
            case 0x1a:
                return this.irqMask | 0xf0;

            // clear sprite collision registers after read
            case 0x1e:
            case 0x1f: {
                final int result = this.registers[register];

                this.registers[register] = 0;
                return result;
            }

            // for addresses < $20 read from register directly, when < $2f set bits of high nibble to 1, for >= $2f return $ff
            default:
                return register < 0x20 ? this.registers[register] : register < 0x2f ? this.registers[register] | 0xf0 : 0xff;
        }
    }

    public final void writeRegister(final int register, final int data) {
        this.registers[register] = data;

        switch (register) {
            // x-coordinate of a spriteID has been modified
            case 0x00:
            case 0x02:
            case 0x04:
            case 0x06:
            case 0x08:
            case 0x0a:
            case 0x0c:
            case 0x0e: {
                // determine sprite to modify
                final int n = register >> 1;

                this.sprites[n].setX(readSpriteXCoordinate(n));
                break;
            }

            // y-coordinate of a spriteID has been modified
            case 0x01:
            case 0x03:
            case 0x05:
            case 0x07:
            case 0x09:
            case 0x0b:
            case 0x0d:
            case 0x0f: {
                // determine sprite to modify
                final int n = register >> 1;

                this.sprites[n].setY(readSpriteYCoordinate(n));
                break;
            }

            // bit 9 of a sprite x-coordinate has been modified
            // recalculate all sprite x-coordinates
            case 0x10: {
                for (int i = 0; i < 8; ++i) {
                    this.sprites[i].setX(readSpriteXCoordinate(i));
                }
                break;
            }

            // the graphics mode might have changed plus y-scroll value
            case 0x11:
                this.yscroll = readYScroll();
                determineGraphicsMode();
                calculateHashCodeBase();
                determineBadLinesEnabled();
                this.isBadLine = determineBadLine();
            // we continue with checking for a modification of the raster IRQ as the highest bit of $d011 influences the raster IRQ line

            // a new raster IRQ was set
            case 0x12:
                // the raster IRQ was modified?
                if (this.rasterYIRQ != readRasterLineIRQ()) {
                    this.rasterYIRQ = readRasterLineIRQ();
                    // a new raster IRQ might trigger a VIC IRQ
                    if (this.rasterY == this.rasterYIRQ) {
                        activateIRQFlag(IRQ_RASTER);
                    }
                }
                break;

            // the sprite enable byte has changed
            case 0x15: {
                for (int i = 0, m = 1; i < 8; i++, m <<= 1) {
                    this.sprites[i].setEnabled((data & m) != 0);
                }
                break;
            }

            // the graphics mode might have changed plus x-scroll value
            case 0x16:
                this.xscroll = readXScroll();
                determineGraphicsMode();
                calculateHashCodeBase();
                break;

            // the sprite y-expansion byte has changed
            case 0x17: {
                for (int i = 0, m = 1; i < 8; i++, m <<= 1) {
                    this.sprites[i].setExpandY((data & m) != 0);
                }
                break;
            }

            // the cached video memory base addresses might have changed
            case 0x18:
                determineVideoMemoryBaseAddresses();
                break;

            // VIC Interrupt Flag Register
            case 0x19:
                this.irqFlags &= (~((data & 0x80) != 0 ? 0xff : data) & 0x0f);

                // clear IRQ if necessary
                if (isIRQTriggered()) {
                    this.irqFlags |= 0x80;
                } else {
                    this.cpu.setIRQ(this, false);
                }
                break;

            // IRQ Mask Register
            case 0x1a:
                this.irqMask = data & 0x0f;

                // setting a new mask might trigger or clear the IRQ
                if (isIRQTriggered()) {
                    this.irqFlags |= 0x80;
                    this.cpu.setIRQ(this, true);
                } else {
                    this.irqFlags &= 0x0f;
                    this.cpu.setIRQ(this, false);
                }
                break;

            // the sprite priority byte has changed
            case 0x1b: {
                for (int i = 0, m = 1; i < 8; i++, m <<= 1) {
                    this.sprites[i].setPriority((data & m) == 0);
                }
                break;
            }

            // Sprites O-7 Multi-Color Mode Selection
            case 0x1c:
                for (int i = 0, m = 1; i < 8; ++i, m <<= 1) {
                    this.sprites[i].setMulticolor((data & m) != 0);
                }
                break;

            // the sprite x-expansion byte has changed
            case 0x1d: {
                for (int i = 0, m = 1; i < 8; i++, m <<= 1) {
                    this.sprites[i].setExpandX((data & m) != 0);
                }
                break;
            }

            // the border color was changed
            case 0x20:
                this.borderColor = VIC_COLORS[readBorderColor()];
                setChanged(true);
                notifyObservers(new Color(this.borderColor));
                break;

            // store color in internal registers
            case 0x21:
            case 0x22:
            case 0x23:
            case 0x24: {
                final int n = register - 0x21;
                final int col = data & 0x0f;

                this.backgroundColorCodes[n] = col;
                this.backgroundColors[n] = VIC_COLORS[col];
                calculateHashCodeBase();
                break;
            }

            // sprite color one has been changed
            case 0x25:
                for (int i = 0; i < 8; ++i) {
                    this.sprites[i].setColor(1, VIC_COLORS[data & 0x0f]);
                }
                this.registers[register] |= 0xf0;
                break;
            // sprite color three has been changed
            case 0x26:
                for (int i = 0; i < 8; ++i) {
                    this.sprites[i].setColor(3, VIC_COLORS[data & 0x0f]);
                }
                this.registers[register] |= 0xf0;
                break;
            // sprite color two has been changed, this can be done per sprite
            case 0x27:
            case 0x28:
            case 0x29:
            case 0x2a:
            case 0x2b:
            case 0x2c:
            case 0x2d:
            case 0x2e: {
                final int n = (register - 0x27);

                this.sprites[n].setColor(2, VIC_COLORS[data & 0x0f]);
                this.registers[register] |= 0xf0;
                break;
            }

            default:
                ;
        }
    }

    public final long getNextUpdate() {
        return this.nextUpdate;
    }

    public final void update(final long cycles) {
        mainloop:
        while (this.cycles < cycles) {
            switch (this.lineCycle) {
                // in cycle 1 we reset vcBase plus we have sprite access for sprite 3
                // we also check whether bad lines are enabled via the DEN bit
                // additionally we increase the raster counter and check whether the trigger a raster IRQ
                case 1: {
                    // increase raster counter
                    ++this.rasterY;

                    // we have reached the end of the screen?
                    if (this.rasterY >= MAX_RASTERS) {
                        this.rasterY = 0;

                        // reset vcBase
                        this.vcBase = 0;

                        // have the content of the screen painted
                        setChanged(this.isPaintFrame);
                        notifyObservers();

                        // we also re-determine whether we paint the current frame
                        ++this.frames;
                        this.isPaintFrame = this.frames % this.frameSkip == 0;

                        // we reset one line of the hashcodes
                        // this ensures that we remove any glitches that might have occurred over the course of TOTAL_HEIGHT / 60 seconds
                        invalidateCacheLine(this.frames % TOTAL_HEIGHT);
                    }

                    // check for raster IRQ
                    if (this.rasterY == this.rasterYIRQ) {
                        activateIRQFlag(IRQ_RASTER);
                    }

                    // check whether we have to enable/disable bad lines
                    determineBadLinesEnabled();

                    // assign bad line state if necessary
                    this.isBadLine = determineBadLine();

                    // enable display in case of a bad line
                    this.isDisplayActive |= this.isBadLine;

                    // determine y-coordinate to paint on the screen...
                    this.paintY = this.rasterY - FIRST_LINE;
                    // ...and y-coordinate inside the inner screen area
                    this.innerY = this.paintY - BORDER_HEIGHT;
                    // do we have to actually draw sprite data for this line?
                    // if not we still check for collisions
                    this.isDisplayLine = this.innerY >= 0 && this.innerY < INNER_HEIGHT && this.isPaintFrame;

                    // we only paint pixels within the screen area and if the frame has to be painted
                    this.isPaintableLine = (this.isPaintBorders ? this.rasterY >= FIRST_LINE && this.rasterY < LAST_LINE : this.rasterY >= FIRST_LINE + BORDER_HEIGHT && this.rasterY < LAST_LINE - BORDER_HEIGHT);
                    // we also have to verify that the first pixel to be filled is still within the pixels array
                    // on displays that are scaled down, it might happen that this is not the case
                    if (this.isPaintableLine) {
                        gotoPixel(0, this.paintY);
                        this.isPaintableLine &= isValidPixel();

                        // copy the last background mask to the current mask so that we only have to care about the changes
                        System.arraycopy(this.lastBackgroundMask[this.paintY], 0, this.collisionMask, 0, this.collisionMask.length);
                    }

                    // reset collision pointer to first pixel in line
                    this.collisionPos = 0;

                // no break here, we fall through
                }
                // in cycles 1,3,5,7 and 9 we have sprite access for sprite 3,4,5,6 and 7
                case 3:
                case 5:
                case 7:
                case 9:
                    // determine the sprite to read data for, will be sprite 3 in cycle 1, sprite 4 in cycle 3 etc.
                    doSpriteAccess(((this.lineCycle - 1) >> 1) + 3);
                    // we skip the next cycle
                    this.cycles += 2;
                    this.lineCycle += 2;
                    continue mainloop;
                // bus might be blocked due to sprite access, we release the lock now
                case 11:
                    // we can continue with cycle 57, where the Sprites are painted, if we don't do paint operations for this frame
                    if (!this.isPaintFrame) {
                        this.cycles += 57 - this.lineCycle;
                        this.lineCycle += 57 - this.lineCycle;
                        continue mainloop;
                    }
                    // block the bus
                    this.isBusAvailable = true;
                    break;
                // in case of a bad-line state we block the bus during cycles 12-54
                case 12:
                    if (this.isBadLine) {
                        this.isBusAvailable = false;
                    }
                    break;
                // draw left border
                case 13:
                    if (this.isPaintableLine) {
                        if (this.isPaintBorders) {
                            for (int i = 0; i < BORDER_COLUMNS; ++i) {
                                drawBorder(i);
                            }
                        } else {
                            skipPixels(BORDER_WIDTH);
                        }
                    }
                    break;
                // in cycle 14 we copy vcBase to vc (vmli not used)
                case 14:
                    this.vc = this.vcBase;
                    if (this.isBadLine) {
                        this.rc = 0;
                    }
                    break;
                // in cycles 15-54 we do access the video matrix and the color RAM
                case 15:
                    if (this.isBadLine) {
                        doVideoMatrixAccess();
                    }
                    break;
                // in cycles 16-55 we do access the bitmap or the character ROM to read the pixels to paint
                // normally in cycle 16 the sprites that were fully painted are switched off, this is instead done in cycle 57
                case 16: {
                    // access video matrix and color RAM and read pixels to paint
                    if (this.isPaintableLine) {
                        doGraphicsAccess();
                    }
                    if (this.isBadLine) {
                        doVideoMatrixAccess();
                    }
                    break;
                }
                // in cycles 17-56 we paint the graphics
                // in cycle 17 we disable the border, normally CSEL is check beforehand, but we do this
                // check later when painting the borders
                case 17:
                    // check if we have to enable/disable the border
                    if (this.rasterY == getLastY()) {
                        this.showBorderVertical = true;
                    } else if (this.rasterY == getFirstY() && readIsDisplayEnabled()) {
                        this.showBorderVertical = false;
                    }

                    // we disable the border
                    this.showBorderMain &= this.showBorderVertical;

                    // paint graphics when we are inside the paintable area and access pixel data, video matrix and color RAM
                    if (this.isPaintableLine) {
                        // we move according to the x-scroll value
                        for (int i = 0; i < this.xscroll; ++i) {
                            setNextPixel(this.borderColor);
                        }
                        this.collisionPos += this.xscroll;

                        drawGraphics();

                        // we have only 38 columns?
                        if (!readCSEL()) {
                            // then draw border for columns 0, enforcing a repaint
                            gotoPixel(BORDER_WIDTH, this.paintY);
                            this.lastPainted[this.paintY][BORDER_COLUMNS] = -0xff;
                            drawBorder(BORDER_COLUMNS);
                        }

                        doGraphicsAccess();
                    } else {
                        // proceed to cycle 56
                        this.cycles += (CHAR_COLUMNS - 1);
                        this.lineCycle += (CHAR_COLUMNS - 1);
                        continue mainloop;
                    }
                    if (this.isBadLine) {
                        doVideoMatrixAccess();
                    }
                    break;
                case 55:
                    // paint graphics when we are inside the paintable area and access pixel data
                    if (this.isPaintableLine) {
                        drawGraphics();
                        doGraphicsAccess();
                    }
                    break;
                // - in cycle 56 we paint the last character and check if we can free the bus
                case 56:
                    // block the bus if we have a sprite access
                    if (!sprites[0].isEnabled()) {
                        this.isBusAvailable = true;
                    }

                    // paint graphics when we are inside the paintable area
                    if (this.isPaintableLine) {
                        drawGraphics();

                        // we have only 38 columns?
                        if (!readCSEL()) {
                            // then draw border for columns 39, enforcing a repaint
                            gotoPixel(BORDER_WIDTH + INNER_WIDTH - CHAR_WIDTH, this.paintY);
                            this.lastPainted[this.paintY][BORDER_COLUMNS + CHAR_COLUMNS - 1] = -0xff;
                            drawBorder(BORDER_COLUMNS + CHAR_COLUMNS - 1);
                        }
                    }
                    break;
                // enable the border, draw right border and the sprites
                // additionally we also check each sprite whether it needs to be disabled, which is normally done in cycle 16
                case 57: {
                    this.showBorderMain = true;
                    if (this.isPaintableLine) {
                        // paint right border
                        gotoPixel(BORDER_WIDTH + INNER_WIDTH, this.paintY);
                        if (this.isPaintBorders) {
                            // we have to repaint the first column in any case as the previous frame might have had an xscroll value > 0
                            this.lastPainted[this.paintY][BORDER_COLUMNS + CHAR_COLUMNS] = -0xff;
                            for (int i = BORDER_COLUMNS + CHAR_COLUMNS; i < BORDER_COLUMNS + CHAR_COLUMNS + BORDER_COLUMNS; ++i) {
                                drawBorder(i);
                            }
                        } else {
                            skipPixels(BORDER_WIDTH);
                        }

                        // now we have finished the background collision mask, we copy it as template for the next frame
                        System.arraycopy(this.collisionMask, 0, this.lastBackgroundMask[this.paintY], 0, this.collisionMask.length);

                        // draw the sprites
                        drawSprites();
                    }

                    // disable sprites if they are fully painted
                    final Sprite[] sprites_ = this.sprites;

                    for (int i = 0, to = sprites_.length; i < to; ++i) {
                        final Sprite sprite = sprites_[i];

                        if (sprite.isPainting() && sprite.isBeyondLastByte()) {
                            sprite.setPainting(false);
                        }
                    }
                    break;
                }
                // in cycle 58 we check whether we go idle plus we have sprite access for sprite 0
                // additionally we check for each sprite whether we have to paint it
                case 58: {
                    // do we go idle?
                    if (this.rc == 7) {
                        this.vcBase = this.vc;
                        this.isDisplayActive = false;
                    }
                    if (this.isBadLine || this.isDisplayActive) {
                        this.rc = (this.rc + 1) & 7;
                    }

                    // check which sprites to paint
                    final Sprite[] sprites_ = this.sprites;

                    for (int i = 0, to = sprites_.length; i < to; ++i) {
                        final Sprite sprite = sprites_[i];

                        if (sprite.isEnabled() && sprite.getY() == (this.rasterY & 0xff)) {
                            sprite.initPainting();
                        }
                    }

                // no break here, we fall through
                }
                // in cycle 60 we have the sprite access for sprite 1
                case 60:
                    doSpriteAccess((this.lineCycle - 58) >> 1);
                    // we skip the next cycle
                    this.cycles += 2;
                    this.lineCycle += 2;
                    continue mainloop;
                // in cycle 62 we have the sprite access for sprite 2
                case 62:
                    doSpriteAccess((this.lineCycle - 58) >> 1);
                    break;
                // this is the last line, we start a new cycle
                case CYCLES_PER_LINE:
                    // next refresh at next end last cycle
                    this.nextUpdate = cycles + CYCLES_PER_LINE;
                    // start with the first cycle again
                    this.lineCycle = 0;
                    break;

                // this part is executed in the cycles 18-54 which are not covered by the cases above
                default:
                    // paint graphics when we are inside the paintable area and access pixel data, video matrix and color RAM
                    if (this.isPaintableLine) {
                        drawGraphics();
                        doGraphicsAccess();
                    }
                    if (this.isBadLine) {
                        doVideoMatrixAccess();
                    }
            }

            // we processed one more VIC cycle
            ++this.cycles;
            ++this.lineCycle;
        }
    }

    public void reset() {
        // reset all sprites
        for (int i = 0; i < this.sprites.length; ++i) {
            this.sprites[i].setEnabled(false);
        }
        // clear the screen
        for (int i = 0; i < this.pixels.length; ++i) {
            this.pixels[i] = 0;
        }
        // assign next cycle when to start working
        this.cycles = this.cpu.getCycles() + 50;
        // clear the cache of last painted characters
        for (int i = 0; i < TOTAL_HEIGHT; ++i) {
            invalidateCacheLine(i);
        }
    }

    // implementation of the observer interface
    public void update(final Object observed, final Object arg) {
        if (observed instanceof CIA6526) {
            // a write to data port A of CIA 2 occurred?
            if (CIA6526_2.ADDRESS_PRA.equals(arg)) {
                // then update the video memory addresses
                determineVideoMemoryBaseAddresses();
            }
        }
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        SerializationUtils.serialize(out, this.registers);
        out.writeInt(this.vc);
        out.writeInt(this.vcBase);
        out.writeInt(this.rc);
        out.writeBoolean(this.isBusAvailable);
        out.writeBoolean(this.isDisplayActive);
        out.writeBoolean(this.areBadLinesEnabled);
        out.writeInt(this.lineCycle);
        out.writeInt(this.rasterY);
        SerializationUtils.serialize(out, this.sprites);
        SerializationUtils.serialize(out, this.colorData);
        SerializationUtils.serialize(out, this.videoMatrixData);
        out.writeInt(this.graphicsData);
        out.writeBoolean(this.showBorderMain);
        out.writeBoolean(this.showBorderVertical);
        out.writeInt(this.graphicsMode);
        out.writeBoolean(this.isECM);
        out.writeBoolean(this.isBMM);
        out.writeBoolean(this.isMCM);
        out.writeInt(this.videoMemBase);
        out.writeInt(this.videoMatrixBase);
        out.writeInt(this.charMemBase);
        out.writeInt(this.bitmapMemBase);
        SerializationUtils.serialize(out, this.backgroundColorCodes);
        SerializationUtils.serialize(out, this.backgroundColors);
        out.writeInt(this.borderColor);
        out.writeInt(this.xscroll);
        out.writeInt(this.yscroll);
        out.writeInt(this.irqFlags);
        out.writeInt(this.irqMask);
        out.writeInt(this.rasterYIRQ);
        out.writeInt(this.nextPixel);
        out.writeInt(this.savedPosition);
        out.writeBoolean(this.isPaintFrame);
        out.writeBoolean(this.isPaintBorders);
        out.writeLong(this.cycles);
        out.writeLong(this.nextUpdate);
        out.writeInt(this.paintY);
        out.writeInt(this.innerY);
        out.writeBoolean(this.isPaintableLine);
        out.writeBoolean(this.isDisplayLine);
        out.writeBoolean(this.isBadLine);
        out.writeInt(this.hashCodeBase);
        SerializationUtils.serialize(out, this.collisionMask);
        out.writeInt(this.collisionPos);
        out.writeInt(this.hashCol);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        SerializationUtils.deserialize(in, this.registers);
        this.vc = in.readInt();
        this.vcBase = in.readInt();
        this.rc = in.readInt();
        this.isBusAvailable = in.readBoolean();
        this.isDisplayActive = in.readBoolean();
        this.areBadLinesEnabled = in.readBoolean();
        this.lineCycle = in.readInt();
        this.rasterY = in.readInt();
        SerializationUtils.deserialize(in, this.sprites);
        SerializationUtils.deserialize(in, this.colorData);
        SerializationUtils.deserialize(in, this.videoMatrixData);
        this.graphicsData = in.readInt();
        this.showBorderMain = in.readBoolean();
        this.showBorderVertical = in.readBoolean();
        this.graphicsMode = in.readInt();
        this.isECM = in.readBoolean();
        this.isBMM = in.readBoolean();
        this.isMCM = in.readBoolean();
        this.videoMemBase = in.readInt();
        this.videoMatrixBase = in.readInt();
        this.charMemBase = in.readInt();
        this.bitmapMemBase = in.readInt();
        SerializationUtils.deserialize(in, this.backgroundColorCodes);
        SerializationUtils.deserialize(in, this.backgroundColors);
        this.borderColor = in.readInt();
        setChanged(true);
        notifyObservers(new Color(this.borderColor));
        this.xscroll = in.readInt();
        this.yscroll = in.readInt();
        this.irqFlags = in.readInt();
        this.irqMask = in.readInt();
        this.rasterYIRQ = in.readInt();
        this.nextPixel = in.readInt();
        this.savedPosition = in.readInt();
        this.isPaintFrame = in.readBoolean();
        this.isPaintBorders = in.readBoolean();
        this.cycles = in.readLong();
        this.nextUpdate = in.readLong();
        this.paintY = in.readInt();
        this.innerY = in.readInt();
        this.isPaintableLine = in.readBoolean();
        this.isDisplayLine = in.readBoolean();
        this.isBadLine = in.readBoolean();
        this.hashCodeBase = in.readInt();
        SerializationUtils.deserialize(in, this.collisionMask);
        this.collisionPos = in.readInt();
        this.hashCol = in.readInt();

        // ensure that we repaint all pixels
        repaint();
    }
}

