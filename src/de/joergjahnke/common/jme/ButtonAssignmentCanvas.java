/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.jme;

import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * Canvas for assigning emulator buttons to phone keys
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class ButtonAssignmentCanvas extends Canvas implements CommandListener {

    /**
     * mask we set if a key was not released but repeated
     */
    public final static int MASK_REPEAT_KEY = 1 << 31;
    /**
     * 'Back' command
     */
    private final Command backCommand;
    /**
     * 'Skip' command
     */
    private final Command skipCommand;
    /**
     * 'Defaults' command
     */
    private final Command defaultsCommand;
    /**
     * current display
     */
    private final Display display;
    /**
     * previously displayed dialog on that display
     */
    private final Displayable parent;
    /**
     * the buttons to assign
     */
    private final Vector buttons;
    /**
     * map of buttons and assigned keys
     */
    private final Hashtable result = new Hashtable();
    /**
     * button to assign next
     */
    private int currentButton = 0;
    /**
     * optional error message we paint
     */
    private String errorMessage = null;
    /**
     * return state of the dialog
     */
    private int state = Command.CANCEL;

    /**
     * Create a new button assignment canvas.
     * The canvas will guide the user through a series of button assignments.
     * For each button a text will appear that describes the buttons function
     * and the user can assign a key or skip the assignment for this button.
     * The dialog will offer three commands:<br>
     * 1. A Back command for canceling the assignment. When this button is used
     * the state of the dialog will remain as Command.CANCEL.<br>
     * 2. The Defaults command can be used to use the standard assignments defined
     * by the application. The dialog will return an empty result map and have
     * the state Command.OK.<br>
     * 3. The Skip command can be used to skip the assignment for a given button.<br>
     * If all buttons are assigned, or the dialog was ended using one of the
     * aforementioned commands, the dialog will return to the display handed by
     * the Ctor and end with the state Command.OK
     * 
     * @param display   current display
     * @param buttons   names of buttons to assign
     */
    public ButtonAssignmentCanvas(final Display display, final Vector buttons) {
        if (buttons.size() < 1) {
            throw new IllegalArgumentException("At least one button must be provided");
        }
        this.display = display;
        this.parent = display.getCurrent();
        this.buttons = buttons;

        // add commands for skipping a key, resetting to defaults and canceling the assignment procedure
        this.backCommand = new Command(LocalizationSupport.getMessage("Back"), Command.BACK, 99);
        this.skipCommand = new Command(LocalizationSupport.getMessage("Skip"), Command.ITEM, 1);
        this.defaultsCommand = new Command(LocalizationSupport.getMessage("Defaults"), Command.ITEM, 2);

        addCommand(this.backCommand);
        addCommand(this.skipCommand);
        addCommand(this.defaultsCommand);
        setCommandListener(this);
    }

    protected void paint(Graphics g) {
        g.setColor(0xffffffff);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(0xff000000);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
        g.drawString(LocalizationSupport.getMessage("AssignKey"), getWidth() / 2, 0, Graphics.HCENTER | Graphics.TOP);
        g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_LARGE));
        g.drawString(this.buttons.elementAt(this.currentButton).toString(), getWidth() / 2, getHeight() / 2, Graphics.HCENTER | Graphics.TOP);
        if (null != errorMessage) {
            g.setColor(0xffff0000);
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM));
            g.drawString(errorMessage, getWidth() / 2, getHeight(), Graphics.HCENTER | Graphics.BASELINE);
        }
    }

    /**
     * Assign button and proceed to the next button
     * 
     * @param keyCode   released key
     */
    protected void keyReleased(final int keyCode) {
        assignButton(keyCode);
    }

    /**
     * Assign button and proceed to the next button
     * 
     * @param keyCode   pressed key
     */
    protected void keyRepeated(final int keyCode) {
        assignButton(MASK_REPEAT_KEY + keyCode);
    }

    /**
     * Assign the current button to the given key and proceed to the next button
     * 
     * @param keyCode   assigned key
     */
    private void assignButton(final int keyCode) {
        // the key is not yet in use?
        if (!this.result.containsKey(new Integer(keyCode)) && !this.result.containsKey(new Integer(keyCode + MASK_REPEAT_KEY))) {
            // ignore the soft keys that trigger the command actions
            try {
                if (getKeyName(keyCode).indexOf("SOFT") < 0) {
                    // assign the key and proceed to the next
                    this.result.put(new Integer(keyCode), this.buttons.elementAt(this.currentButton));
                    this.errorMessage = null;
                    nextButton();
                }
            } catch (IllegalArgumentException e) {
                // an illegal keycode might be passed to getKeyName
            }
        } else {
            // show a message that says the key is in use
            this.errorMessage = "The key is already in use!";
            repaint();
        }
    }

    /**
     * Continue with the next button
     */
    private void nextButton() {
        ++this.currentButton;
        if (this.currentButton < this.buttons.size()) {
            repaint();
        } else {
            this.state = Command.OK;
            onFinished();
        }
    }

    /**
     * Get the button assignments
     * 
     * @return  map of button names and key codes
     */
    public Hashtable getAssignments() {
        return this.result;
    }

    /**
     * Return the state of the dialog.
     * Results should only be processed further if the state returned here is Command.OK.
     *
     * @return  Command.OK if the dialog was finished successfully and not cancelled
     */
    public int getState() {
        return this.state;
    }

    /**
     * This method is executed when the dialog is finished.
     * The default implementation merely activates the previous displayable.
     * Subclasses may overwrite this method for a specific handling when the dialog is finished.
     */
    public void onFinished() {
        this.display.setCurrent(this.parent);
    }

    // implementation of the CommandListener interface
    public void commandAction(final Command c, final Displayable d) {
        if (c == this.skipCommand) {
            nextButton();
        } else if (c == this.backCommand) {
            onFinished();
        } else if (c == this.defaultsCommand) {
            this.result.clear();
            this.state = Command.OK;
            onFinished();
        }
    }
}
