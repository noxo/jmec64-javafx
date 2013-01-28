/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.util;

import java.util.Vector;

/**
 * Implementation of the Logger interface which may store log messages
 * as well as it may directly print the messages on stdout and stderr.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class DefaultLogger extends DefaultObservable implements Logger {

    // logger in enabled
    private boolean isVerbose = false;
    // maximum number of entries to store
    private final int maxSize;
    // store messages?
    private boolean storeMessages = false;
    // print messages on stdout and stderr?
    private boolean printMessages = true;
    // stores the log entries
    private final Vector log = new Vector();

    /**
     * Create a new logger with a given maximum capacity
     * 
     * @param   maxLogSize  maximum number of entries for the log file; if this number is > 0
     *                      then the messages automatically get stored
     */
    public DefaultLogger(final int maxLogSize) {
        this.maxSize = maxLogSize;
        setStoreMessages(maxLogSize > 0);
    }

    /**
     * Enable/disable storing of messages
     *
     * @param   store   true to store messages, false to discard messages
     */
    public final void setStoreMessages(final boolean store) {
        this.storeMessages = store;
    }

    /**
     * Enable/disable printing of messages
     *
     * @param   print   true to enable printing of messages on stdout and stderr,
     *                  false to disable printing
     */
    public void setPrintMessages(final boolean print) {
        this.printMessages = print;
    }

    /**
     * Create a dump containing stores log entries of a given set of types
     *
     * @param   types   defines the log entry types that should be included.
     *                  E.g. WARNING | ERROR will only include warnings and
     *                  errors but not informational messages from the log.
     */
    public String dump(final int types) {
        final StringBuffer result = new StringBuffer();

        for (int i = this.log.size() - 1; i >= 0; --i) {
            final LogEntry logEntry = (LogEntry) this.log.elementAt(i);

            if ((types & logEntry.type) != 0) {
                result.append(logEntry);
                result.append('\n');
            }
        }

        return result.toString();
    }

    /**
     * Create a dump containing all stores log entries
     */
    public String dumpAll() {
        return dump(ALL);
    }

    // implementation of the Logger interface
    public void log(final Object message, final int type) {
        // store the message if necessary
        if (this.storeMessages) {
            final LogEntry logEntry = new LogEntry(message, type);

            if (this.log.size() >= this.maxSize) {
                this.log.removeElementAt(0);
            }
            this.log.addElement(logEntry);
        }
        // print the message if necessary
        if (this.printMessages) {
            if (ERROR == type) {
                System.err.println(message);
            } else {
                System.out.println(message);
            }
        }

        // notify observers that a new message was added
        setChanged(true);
        notifyObservers(message);
    }

    public void info(final Object message) {
        log(message, INFO);
    }

    public final void warning(final Object message) {
        log(message, WARNING);
    }

    public void error(final Object message) {
        log(message, ERROR);
    }

    public void setVerbose(final boolean verbose) {
        this.isVerbose = verbose;
    }

    public boolean isVerbose() {
        return this.isVerbose;
    }

    // inner class which defines a log entry
    static class LogEntry {

        public final int type;
        public final Object message;

        public LogEntry(final Object message, final int type) {
            this.message = message;
            this.type = type;
        }

        public final String toString() {
            return message.toString();
        }
    }
}
