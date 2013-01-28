/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.util;


import java.util.Vector;


/**
 * Default implementation of the Observable interface.
 * This implementation is not thread-safe.
 *
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class DefaultObservable implements Observable {
    // contains the observers of this observable object
    private final Vector observers = new Vector();
    // change-status of this observable object
    private boolean hasChanged = false;
    // observed object
    private final Object observed;
    
    
    /**
     * Creates a new instance of DefaultObservable
     */
    public DefaultObservable() {
        this.observed = this;
    }

    /**
     * Creates a new instance of DefaultObservable
     *
     * @param   observed    the observed object, this object is passed with calls to notifyObserver
     */
    public DefaultObservable( final Object observed ) {
        this.observed = observed;
    }

    
    public void addObserver( final Observer o ) {
        if( !this.observers.contains( o ) ) {
            this.observers.addElement( o );
        }
    }

    public void deleteObserver( final Observer o ) {
        this.observers.removeElement( o );
    }

    public void notifyObservers() {
        notifyObservers( null );
    }

    public void notifyObservers( final Object arg ) {
        if( hasChanged() ) {
            for( int i = 0, to = this.observers.size() ; i < to ; ++i ) {
                ( (Observer)this.observers.elementAt( i ) ).update( this.observed, arg );
            }
            setChanged( false );
        }
    }

    public void deleteObservers() {
        this.observers.removeAllElements();
    }

    public void setChanged( final boolean state ) {
        this.hasChanged = state;
    }

    public boolean hasChanged() {
        return this.hasChanged;
    }

    public int countObservers() {
        return this.observers.size();
    }
}
