/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.emulation;

import de.joergjahnke.common.util.Observable;

/**
 * Interface for objects that own a set of frequency data producers
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public interface FrequencyDataProducerOwner extends Observable {
    /**
     * Get the number of FrequencyDataProducers owned by this owner
     *
     * @return  number of producers
     */
    int getFrequencyDataProducerCount();

    /**
     * Get a specific FrequencyDataProducer
     *
     * @param n the id of the producer
     * @return FrequencyDataProducer instance
     */
    FrequencyDataProducer getFrequencyDataProducers(final int n);
}
