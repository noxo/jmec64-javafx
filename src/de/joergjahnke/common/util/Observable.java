/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.util;


/**
 * Interface an observable object has to implement.
 * This interface is similar in appearance to java.util.Observable but may
 * be used when a class cannot be extended as it already extends another
 * class.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 * @see java.util.Observable
 * @see de.joergjahnke.common.util.Observer
 */
public interface Observable {
    /**
     * Adds an observer to the set of observers for this object
     *
     * @param   o   an observer to be added.
     */
    void addObserver( Observer o );

    /**
     * Deletes an observer from the set of observers of this object.
     * This is an optional operation and may have an empty implementation.
     *
     * @param   o   the observer to be deleted
     */
    void deleteObserver( Observer o );

    /**
     * If this object has changed then notify all of its observers
     *
     * @see de.joergjahnke.common.util.Observable#notifyObservers
     */
    void notifyObservers();

    /**
     * If this object has changed then notify all of its observers and pass an
     * object to the observer.
     *
     * @param   arg argument to pass to the observers
     * @see de.joergjahnke.common.util.Observable#notifyObservers
     */
    void notifyObservers( Object arg );

    /**
     * Clears the observer list so that this object no longer has any observers.
     * This is an optional operation and may have an empty implementation.
     */
    void deleteObservers();

    /**
     * Marks this observable object as either changed or unchanged, depending on the state passed
     *
     * @param   state   the new change state, true for a changed object, otherwise false
     */
    void setChanged( boolean state );

    /**
     * Tests if this object has changed
     */
    boolean hasChanged();

    /**
     * Returns the number of observers of this observable object.
     *
     * @return  the number of observers of this object.
     */
    int countObservers();
}
