/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.util;

/**
 * An observer class similar to java.util.Observer
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 * @see java.util.Observable
 */
public interface Observer {

    /**
     * This method is called whenever the observed object is changed.
     *
     * @param   observed    the observed object
     * @param   arg   an argument passed to the notifyObservers method
     * @see de.joergjahnke.common.util.Observable#notifyObservers
     */
    void update(Object observed, Object arg);
}
