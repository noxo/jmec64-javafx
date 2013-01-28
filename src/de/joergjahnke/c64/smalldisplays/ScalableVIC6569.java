/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.smalldisplays;

import de.joergjahnke.c64.core.C64;
import de.joergjahnke.c64.core.VIC6569;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Extension of the VIC6569 class to enable screen scaling
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class ScalableVIC6569 extends VIC6569 {

    /**
     * to avoid usage of double values we shift all values this many bits
     */
    protected final static int MULTIPLIER_BITS = 10;
    /**
     * this is the multiplier we reach using the bit shifting
     */
    protected final static int MULTIPLIER = 1 << MULTIPLIER_BITS;
    /**
     * scaling factor for devices with a resolution < 320x200 pixels
     */
    private final double scaling;
    /**
     * determine increment in memory position for increment on x-axis, we need this value quite often
     */
    protected int memInc;
    /**
     * scaled border width
     */
    private int borderWidth;
    /**
     * here we create the C64 pixels
     */
    protected int[] scaledPixels = null;
    /**
     * precise position of next pixel in scaled mode
     */
    protected int nextScaledPixel = 0;
    /**
     * contains the fraction of an y-increment if we rotate and scale
     */
    protected int yFraction;
    /**
     * paint scaled line?
     */
    protected boolean isPaintLine = true;
    /**
     * saved position of last scaled pixel
     */
    protected int savedScaledPosition;
    /**
     * saved position of last y-fraction
     */
    protected int savedYFraction;
    /**
     * this many pixels we set when painting a pixel
     */
    protected final int pixelsPerPaint;

    /**
     * Creates a new instance of ScalableVIC6569
     *
     * @param   c64 the C64 the VIC works for
     * @param   scaling   value >0 which determines how much we shrink the display
     * @throws  IllegalArgumentException if the scaling factor is <= 0
     */
    public ScalableVIC6569(final C64 c64, final double scaling) {
        super(c64);
        if (scaling <= 0) {
            throw new IllegalArgumentException("Scaling factor must be >0!");
        }
        this.scaling = Math.floor(scaling * 8) / 8;
        determineBorderWidth();
        // this is the value we increase the video memory for each pixel per line
        this.memInc = (int) (getScaling() * MULTIPLIER);
        // this many pixels we set when painting a pixel
        this.pixelsPerPaint = (int)Math.floor(scaling + 0.99);
    }

    public void initScreenMemory() {
        // reserve screen memory according to the scaled down size
        this.pixels = new int[getBorderWidth() * getBorderHeight()];
        this.scaledPixels = pixels;
    }

    /**
     * Get screen scaling factor
     *
     * @return  value between 0 and 1 which determines how much we shrink the display
     */
    public final double getScaling() {
        return scaling;
    }

    public int getDisplayWidth() {
        return (int) (super.getDisplayWidth() * getScaling());
    }

    public int getDisplayHeight() {
        return (int) (super.getDisplayHeight() * getScaling());
    }

    public final int getBorderWidth() {
        return this.borderWidth;
    }

    public final int getBorderHeight() {
        return (int) (super.getBorderHeight() * getScaling());
    }

    /**
     * Determine the border width. Must be called after the scaling or the rotation have been modified.
     */
    private void determineBorderWidth() {
        this.borderWidth = (int) (super.getBorderWidth() * getScaling());
    }

    public void gotoPixel(final int x, final int y) {
        // recalculate variables involved in painting scaled pixels
        this.nextScaledPixel = (int) ((Math.floor(y * getScaling()) * getBorderWidth() + x * getScaling()) * MULTIPLIER);
        this.yFraction = (int) (y * getScaling() * MULTIPLIER) % MULTIPLIER;
        this.isPaintLine = (y + 1) * getScaling() % 1.0 < getScaling();

        super.gotoPixel(x, y);
    }

    protected boolean isValidPixel() {
        return this.nextScaledPixel >= 0 && (this.nextScaledPixel >> MULTIPLIER_BITS) < this.scaledPixels.length;
    }

    public int getNextPixel() {
        return this.nextScaledPixel >> MULTIPLIER_BITS;
    }

    protected void setNextPixel(final int color) {
        if (this.isPaintLine) {
            // set pixel color
            if(this.pixelsPerPaint == 1) {
                this.scaledPixels[this.nextScaledPixel >> MULTIPLIER_BITS] = color;
            } else {
                final int pixelsPerPaint_ = this.pixelsPerPaint;
                final int borderWidth_ = this.borderWidth;

                for(int y = 0 ; y < pixelsPerPaint_ ; ++y) {
                    for(int x = 0 ; x < pixelsPerPaint_ ; ++x) {
                        this.scaledPixels[(this.nextScaledPixel >> MULTIPLIER_BITS) + x + y * borderWidth_] = color;
                    }
                }
            }
        }

        // proceed to next pixel
        skipPixels(1);
    }

    protected void skipPixels(final int n) {
        this.nextScaledPixel += n * this.memInc;

        super.skipPixels(n);
    }

    protected void saveCurrentPixelPosition() {
        super.saveCurrentPixelPosition();
        this.savedScaledPosition = this.nextScaledPixel;
        this.savedYFraction = this.yFraction;
    }

    protected void restoreSavedPixelPosition() {
        this.yFraction = this.savedYFraction;
        this.nextScaledPixel = this.savedScaledPosition;
        super.restoreSavedPixelPosition();
    }

    public int[] getRGBData() {
        return this.scaledPixels;
    }

    /**
     * When resetting a scalable VIC we also clear the scaled screen
     */
    public void reset() {
        if (this.scaledPixels != this.pixels) {
            for (int i = 0; i < this.scaledPixels.length; ++i) {
                this.scaledPixels[i] = 0;
            }
        }

        super.reset();
    }

    public void serialize(final DataOutputStream out) throws IOException {
        super.serialize(out);
        out.writeBoolean(this.isPaintLine);
        out.writeInt(this.memInc);
        out.writeInt(this.borderWidth);
        out.writeInt(this.nextScaledPixel);
        out.writeInt(this.yFraction);
        out.writeInt(this.savedScaledPosition);
        out.writeInt(this.savedYFraction);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        super.deserialize(in);
        this.isPaintLine = in.readBoolean();
        this.memInc = in.readInt();
        this.borderWidth = in.readInt();
        this.nextScaledPixel = in.readInt();
        this.yFraction = in.readInt();
        this.savedScaledPosition = in.readInt();
        this.savedYFraction = in.readInt();
    }
}
