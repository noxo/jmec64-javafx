/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.emulation;

import de.joergjahnke.common.util.Observable;

/**
 * Creates PCM wave data.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public interface WaveDataProducer extends Observable {

    /**
     * Get the set sampling rate, e.g. 44100 for 44.1 KHz
     *
     * @return  sample rate in Hz
     */
    int getSampleRate();

    /**
     * Get the bits per sample e.g. 8 or 16
     * 
     * @return  no. of bits
     */
    int getBitsPerSample();

    /**
     * Get the number of channels, e.g. 1 or 2
     * 
     * @return  no. of channels
     */
    int getChannels();
}
