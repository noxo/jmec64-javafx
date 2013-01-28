/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.jme;

import de.joergjahnke.common.util.Observer;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Gauge;
import javax.microedition.lcdui.Item;

/**
 * Displays a progress bar on the screen and continues with another display once
 * the operation is finished.
 * The form acts as an observer for an observable and expects that this observable
 * delivers Integer values from 0-100 that indicate the progress of the operation.
 *
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class ProgressForm extends Form implements Observer, CommandListener {

    /**
     * progress bar
     */
    final Gauge gauge;
    /**
     * back command, leads back to the main form
     */
    final Command cancelCommand;

    /**
     * Creates a new progress form
     *
     * @param   title   form title, should be one or two words only
     */
    public ProgressForm(final String title) {
        super(title);
        this.gauge = new Gauge("", false, 100, 0);
        this.gauge.setLayout(Item.LAYOUT_CENTER | Item.LAYOUT_VCENTER);
        this.cancelCommand = new Command(LocalizationSupport.getMessage("Cancel"), Command.BACK, 99);
        append(this.gauge);
    }

    /**
     * Determine whether a cancel command should be available
     *
     * @param isShowCancel  true to add a cancel command to the form
     */
    public void setShowCancelCommand(final boolean isShowCancel) {
        if (isShowCancel) {
            addCommand(cancelCommand);
        } else {
            removeCommand(cancelCommand);
        }
    }

    /**
     * This method gets called once the monitored operation reaches 100%.
     * The default implementation does nothing.
     */
    public void onFinished() {
    }

    /**
     * This method gets called if the Back command of the form got activated
     */
    public void onCancelled() {
    }

    // implementation of the Observer interface
    /**
     * Update the progress bar
     *
     * @param   observed    the observed object
     * @param   arg should be an integer value in the range 0-100, other types are ignored
     */
    public void update(final Object observed, final Object arg) {
        if (arg instanceof Integer) {
            this.gauge.setValue(((Integer) arg).intValue());
            if (this.gauge.getValue() == 100) {
                onFinished();
            }
        }
    }

    // implementation of the CommandListener interface
    public void commandAction(final Command command, final Displayable displayable) {
        if (command == this.cancelCommand) {
            onCancelled();
        }
    }
}
