/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.ui;

/**
 * The Color class is used to encapsulate colors in the default sRGB color space
 *
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class Color {
    // alpha, red, green and blue value of the color

    private final int argb;

    /**
     * Creates an sRGB color with the specified red, green, blue, and alpha values in the range (0 - 255).
     */
    public Color(final int r, final int g, final int b, final int alpha) {
        this.argb = (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Creates an ARGB color with the specified combined RGB value consisting of the alpha value in
     * bits 24-31, the red component in bits 16-23, the green component in bits 8-15, and the blue component
     * in bits 0-7. The actual color used in rendering depends on finding the best match given the color
     * space available for a particular output device.
     */
    public Color(final int argb) {
        this.argb = argb;
    }

    /**
     * Get the alpha value of this color
     *
     * @return  0-255
     */
    public final int getAlpha() {
        return (this.argb >> 24) & 255;
    }

    /**
     * Get the red value of this color
     *
     * @return  0-255
     */
    public final int getRed() {
        return (this.argb >> 16) & 255;
    }

    /**
     * Get the green value of this color
     *
     * @return  0-255
     */
    public final int getGreen() {
        return (this.argb >> 8) & 255;
    }

    /**
     * Get the blue value of this color
     *
     * @return  0-255
     */
    public final int getBlue() {
        return this.argb & 255;
    }

    /**
     * Get the internal RGB representation of this color
     */
    public final int getRGB() {
        return this.argb;
    }

    public boolean equals(final Object obj) {
        return obj instanceof Color && this.argb == ((Color) obj).argb;
    }

    public int hashCode() {
        return this.argb;
    }

    /**
     * Create a new color that is a mix of tow given colors.
     * For some good information on fast color mixing see <a href='http://www.compuphase.com/graphic/scale3.htm'>http://www.compuphase.com/graphic/scale3.htm</a>.
     *
     * @param   color1  RGB color code of the first color to mix
     * @param   color2  RGB color code of the second color to mix
     * @return  RGB color code of the mixed color
     */
    public static int mix(final int color1, final int color2) {
        return (((color1 ^ color2) & 0xfffefefe) >> 1) + (color1 & color2);
    }

    /**
     * Create a new color that is a mix of four given colors
     *
     * @param   color1  RGB color code of the first color to mix
     * @param   color2  RGB color code of the second color to mix
     * @param   color3  RGB color code of the third color to mix
     * @param   color4  RGB color code of the fourth color to mix
     * @return  RGB color code of the mixed color
     */
    public static int mix(final int color1, final int color2, final int color3, final int color4) {
        return mix(mix(color1, color2), mix(color3, color4));
    }

    /**
     * Create a new color that is a mix of four given colors
     *
     * @param   color1  RGB color code of the first color to mix
     * @param   fraction1   fraction of color1 in the total color (*1024)
     * @param   color2  RGB color code of the second color to mix
     * @param   fraction2   fraction of color2 in the total color (*1024)
     * @param   color3  RGB color code of the third color to mix
     * @param   fraction3   fraction of color3 in the total color (*1024)
     * @param   color4  RGB color code of the fourth color to mix
     * @param   fraction4   fraction of color4 in the total color (*1024)
     * @return  RGB color code of the mixed color
     */
    public static int mix(final int color1, final int fraction1, final int color2, final int fraction2, final int color3, final int fraction3, final int color4, final int fraction4) {
        // if all colors are equal we don't need to mix
        if (color1 == color2 && color1 == color3 && color1 == color4) {
            return color1;
        } else {
            // mix the color
            return (color1 & 0xff000000) + ((((color1 & 0x00ff0000) >> 10) * fraction1) + (((color2 & 0x00ff0000) >> 10) * fraction2) + (((color3 & 0x00ff0000) >> 10) * fraction3) + (((color4 & 0x00ff0000) >> 10) * fraction4) & 0x00ff0000) + ((((color1 & 0x0000ff00) * fraction1) >> 10) + (((color2 & 0x0000ff00) * fraction2) >> 10) + (((color3 & 0x0000ff00) * fraction3) >> 10) + (((color4 & 0x0000ff00) * fraction4) >> 10) & 0x0000ff00) + ((((color1 & 0x000000ff) * fraction1) >> 10) + (((color2 & 0x000000ff) * fraction2) >> 10) + (((color3 & 0x000000ff) * fraction3) >> 10) + (((color4 & 0x000000ff) * fraction4) >> 10) & 0x000000ff);
        }
    }
}
