/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.jme;

/**
 * Additional mathematical functions for the MIDP toolkit
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class MathUtils {

    /**
     * Double values have a mantisse of 52 bits
     */
    private static final int DOUBLE_MANTISSE_SIZE = 52;

    /**
     * Compute the natural logarithm
     *
     * @param   x   number to calculate the natural logarithm for
     * @return  approximation for ln(x)
     */
    public static double ln(final double x) {
        return log2(x) / log2(Math.E);
    }

    /**
     * Compute the logarithm to the base 10
     *
     * @param   x   number to calculate the logarithm for
     * @return  approximation for log10(x)
     */
    public static double log10(final double x) {
        return log2(x) / log2(10);
    }

    /**
     * Compute the logarithm to the base 2.
     * See <a href='http://de.wikipedia.org/wiki/Logarithmus#Potenzreihe'>http://de.wikipedia.org/wiki/Logarithmus#Potenzreihe</a>
     * for details on the computation.
     *
     * @param   x   number to calculate the logarithm for
     * @return  approximation for log2(x)
     */
    public static double log2(double x) {
        if (x <= 0) {
            throw new IllegalArgumentException("Argument for logarithm function must be > 0!");
        }

        double result = 0;
        // normalize to the range of 1-2
        int mult = 1;

        while (x < 0.5 || x > 2) {
            x = Math.sqrt(x);
            mult *= 2;
        }
        while (x < 1) {
            x *= 2;
            --result;
        }

        // calculate the positions after the decimal point
        double add = 0.5;

        for (int i = 0; i < DOUBLE_MANTISSE_SIZE; ++i) {
            x = x * x;
            if (x >= 2) {
                x = x / 2;
                result += add;
            }
            add /= 2;
        }

        return result * mult;
    }
}
