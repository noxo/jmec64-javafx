/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.jme;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Stack;
import java.util.Vector;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.StringItem;
import javax.microedition.midlet.MIDlet;

/**
 * Creates a form with formatted text.
 * The following formatting options can be used:
 * <UL>
 * <LI>b    will display bold text
 * <LI>i    will display italic text
 * <LI>u    will display underlined text
 * <LI>-    will reduce the font size to smaller text
 * <LI>+    will increase the font size to larger text
 * <LI>h    will display a hyperlink
 * <LI>m    will use a monospaced font
 * <LI>p    will use a proportional font
 * </UL>
 * Formatted text must be started using '{' plus the formatting character
 * and ended using a '}. E.g. '{-{bThis is a text}} will display the text
 * 'This is a test' with a small bold font.
 * 
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class FormattedTextForm extends Form {

    // midlet we are working for
    private final MIDlet midlet;

    /**
     * Create a new formatted text form
     * 
     * @param   title   form title
     * @param   is  input stream containing the text to display
     * @throws IOException if the stream cannot be read from
     */
    public FormattedTextForm(final MIDlet midlet, final String title, final InputStream is) throws IOException {
        super(title);
        this.midlet = midlet;

        final Stack modes = new Stack();
        StringBuffer buffer = new StringBuffer();
        int c;

        while ((c = is.read()) >= 0) {
            switch ((char) c) {
                // opening bracket indicates new formatting, the following character determines which
                case '{':
                    // we show current text with the old formatting...
                    if (buffer.length() > 0) {
                        append(createStringItem(buffer, modes));
                    }
                    // ...create a new buffer and use the new formatting
                    buffer = new StringBuffer();
                    modes.push(new Character((char) is.read()));
                    break;
                // closing bracket indicates end of formatting
                case '}':
                    // we show current text with the selected formatting...
                    if (buffer.length() > 0) {
                        append(createStringItem(buffer, modes));
                    }
                    // ...create a new buffer and remove the formatting
                    buffer = new StringBuffer();
                    modes.pop();
                    break;
                // we ignore carriage returns and use only \n as a line break
                case '\r':
                    break;
                // we just add the new character
                default:
                    buffer.append((char) c);
            }
        }

        if (buffer.length() > 0) {
            append(createStringItem(buffer, modes));
        }
    }

    /**
     * Creates a string item for the form
     * 
     * @param   buffer  contains the text of the item
     * @param   formatting  contains the formatting options
     */
    private StringItem createStringItem(final StringBuffer buffer, final Vector formatting) {
        // the default font is plain text in medium size with the system font
        int style = Font.STYLE_PLAIN;
        int appearance = Item.PLAIN;
        int size = Font.SIZE_MEDIUM;
        int face = Font.FACE_SYSTEM;

        // check the formatting options
        for (final Enumeration en = formatting.elements(); en.hasMoreElements();) {
            final Character c = (Character) en.nextElement();

            switch (c.charValue()) {
                // font style options
                case 'b':
                    style = Font.STYLE_BOLD;
                    break;
                case 'i':
                    style = Font.STYLE_ITALIC;
                    break;
                case 'u':
                    style = Font.STYLE_UNDERLINED;
                    break;

                // hyperlinks
                case 'h':
                    appearance = Item.HYPERLINK;
                    break;

                // font size options
                case '-':
                    size = size == Font.SIZE_LARGE ? Font.SIZE_MEDIUM : Font.SIZE_SMALL;
                    break;
                case '+':
                    size = size == Font.SIZE_SMALL ? Font.SIZE_MEDIUM : Font.SIZE_LARGE;
                    break;

                // font face options
                case 'm':
                    face = Font.FACE_MONOSPACE;
                    break;
                case 'p':
                    style = Font.FACE_PROPORTIONAL;
                    break;
            }
        }

        // create and return the string item with the found formatting options
        final StringItem stringItem = new StringItem(null, LocalizationSupport._convertString(buffer.toString()), appearance);

        stringItem.setFont(Font.getFont(face, style, size));
        if (appearance == Item.HYPERLINK) {
            final Command browseCommand = new Command(LocalizationSupport.getMessage("Browse"), Command.ITEM, 2);

            stringItem.addCommand(browseCommand);
            stringItem.setItemCommandListener(new ItemCommandListener() {

                public void commandAction(final Command c, final Item item) {
                    try {
                        midlet.platformRequest(((StringItem)item).getText());
                    } catch (Exception e) {
                        // we could not invoke the browser, that's a pity but we can live with it
                    }
                }
            });
        }

        return stringItem;
    }
}
