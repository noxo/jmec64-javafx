/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.util;


/**
 * Interface for objects which log messages for a specific system or component
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public interface Logger {
    /**
     * An information message
     */
    int INFO = 1;
    /**
     * A warning message
     */
    int WARNING = 2;
    /**
     * An error message
     */
    int ERROR = 4;
    /**
     * Combination of all message types
     */
    int ALL = INFO | WARNING | ERROR;
    

    /**
     * Logs a given message of a given type e.g. INFO, WARNING, ERROR
     *
     * @param   message message to log
     * @param   type    message type e.g. INFO
     */
    void log( Object message, int type );
    
    /**
     * Log an INFO message
     *
     * @param   message message to log
     */
    void info( Object message );
    
    /**
     * Log a WARNING message
     *
     * @param   message message to log
     */
    void warning( Object message );
    
    /**
     * Log an ERROR message
     *
     * @param   message message to log
     */
    void error( Object message );
    
    /**
     * Enable/disable verbose logging
     *
     * @param   enabled true to enable verbose logging, false to disable
     */
    void setVerbose( boolean enabled );
    
    /**
     * Check whether verbose logging is enabled
     *
     * @return  true if verbose logging is enabled, otherwise false
     */
    boolean isVerbose();
}
