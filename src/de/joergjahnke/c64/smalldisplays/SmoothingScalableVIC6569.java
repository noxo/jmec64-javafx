/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.smalldisplays;

import de.joergjahnke.c64.core.C64;
import de.joergjahnke.common.ui.Color;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Extension of the VIC6569 class to enable smooth screen scaling
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class SmoothingScalableVIC6569 extends ScalableVIC6569 {

    /**
     * saved information whether we have to paint the current line
     */
    protected boolean savedPaintLine;

    /**
     * Creates a new instance of ScalableVIC6569
     *
     * @param   c64 the C64 the VIC works for
     * @param   scaling   value between 0 and 1 which determines how much we shrink the display
     * @throws  IllegalArgumentException if the scaling factor is <= 0 or > 1
     */
    public SmoothingScalableVIC6569(final C64 c64, final double scaling) {
        super(c64, scaling);
    }

    /**
     * Reserve normal screen memory plus scaled down screen memory
     */
    public void initScreenMemory() {
        this.pixels = new int[TOTAL_WIDTH * TOTAL_HEIGHT];
        this.scaledPixels = new int[getBorderWidth() * getBorderHeight()];
    }

    protected void setNextPixel(final int color) {
        // we paint a pixel only if a full line or column has been finished
        if (this.isPaintLine && (this.nextScaledPixel + this.memInc) % 1024 < this.nextScaledPixel % 1024) {
            // index in pixels array of superclass for pixel1
            final int abovePixelIndex = this.nextPixel - TOTAL_WIDTH;

            // mix colors and set pixel
            final int fracX = (1024 - (this.nextScaledPixel % 1024));
            final int fracY = (1024 - (this.yFraction % 1024));
            final int fraction22 = (fracX * fracY) >> 10;
            final int fraction21 = ((1024 - fracX) * fracY) >> 10;
            final int fraction12 = (fracX * (1024 - fracY)) >> 10;
            final int fraction11 = ((1024 - fracX) * (1024 - fracY)) >> 10;

            this.scaledPixels[this.nextScaledPixel >> MULTIPLIER_BITS] = Color.mix(this.pixels[abovePixelIndex - 1], fraction11, this.pixels[abovePixelIndex], fraction12, this.pixels[this.nextPixel - 1], fraction21, color, fraction22);
        }

        // invalidate the pixel below and to the right of the current pixel as it might need a repaint
        if (color != this.pixels[this.nextPixel]) {
            this.lastPainted[this.paintY + 1][this.hashCol] = -1;
        }

        // set pixel in unscaled memory
        this.pixels[this.nextPixel] = color;

        // proceed to next pixel
        skipPixels(1);
    }

    protected void saveCurrentPixelPosition() {
        super.saveCurrentPixelPosition();
        this.savedPaintLine = this.isPaintLine;
    }

    protected void restoreSavedPixelPosition() {
        this.isPaintLine = this.savedPaintLine;
        super.restoreSavedPixelPosition();
    }

    /**
     * When resetting a scalable VIC we also clear the cached mixed colors to save some memory
     */
    public void reset() {
        super.reset();
    }

    public void serialize(final DataOutputStream out) throws IOException {
        super.serialize(out);
        out.writeBoolean(this.savedPaintLine);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        super.deserialize(in);
        this.savedPaintLine = in.readBoolean();
    }
}
