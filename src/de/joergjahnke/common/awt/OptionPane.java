/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.awt;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Label;
import java.awt.List;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.StringTokenizer;

/**
 * This class offers functionality analogous to javax.swing.JOptionPane
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class OptionPane extends Dialog {

    /**
     * Denotes an informational message
     */
    public final static int PLAIN_MESSAGE = 1;
    /**
     * Denotes a warning message
     */
    public final static int WARNING_MESSAGE = 2;
    /**
     * Denotes an error message
     */
    public final static int ERROR_MESSAGE = 3;
    /**
     * Denotes an input dialog
     */
    private final static int INPUT_DIALOG = 0x100;
    // text field whose value can be queried
    private TextField textField;
    // was the OK-button clicked
    private boolean wasOKButtonClicked = false;

    /**
     * Create a new dialog
     */
    private OptionPane(final Frame parent, final String message, final String title, final int type, final Image icon, final Object[] options, final Object defaultValue) {
        super(parent);

        setLayout(new BorderLayout());
        setTitle(title);
        addWindowListener(new WindowAdapter() {

            public void windowClosing(WindowEvent evt) {
                setVisible(false);
            }
        });

        // create buttons
        final Panel buttonPanel = new Panel();
        final Button okButton = new Button("OK");

        okButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                wasOKButtonClicked = true;
                setVisible(false);
            }
        });

        buttonPanel.add(okButton);

        if ((type & INPUT_DIALOG) != 0) {
            final Button cancelButton = new Button("Cancel");

            cancelButton.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent evt) {
                    setVisible(false);
                }
            });

            buttonPanel.add(cancelButton);
        }

        add(buttonPanel, BorderLayout.SOUTH);

        // create label with message
        if (message.indexOf('\n') > 0 || message.indexOf('\r') > 0) {
            int rows = 1;
            for (final StringTokenizer tokenizer = new StringTokenizer(message, "\n"); tokenizer.hasMoreTokens();) {
                rows += Math.max(1, tokenizer.nextToken().length() / 80);
            }

            final TextArea textArea = new TextArea(message, Math.min(20, rows), 80, rows <= 20 ? TextArea.SCROLLBARS_NONE : TextArea.SCROLLBARS_VERTICAL_ONLY);

            textArea.setEditable(false);
            add(textArea, BorderLayout.NORTH);
        } else {
            add(new Label(message), BorderLayout.NORTH);
        }

        // create input fields
        if ((type & INPUT_DIALOG) != 0) {
            Panel editPanel = new Panel();

            editPanel.setLayout(new BorderLayout());

            this.textField = new TextField();
            this.textField.setColumns(40);

            if (null != defaultValue) {
                this.textField.setText(defaultValue.toString());
            }
            editPanel.add(this.textField, BorderLayout.NORTH);
            if (null != options && options.length > 0) {
                final List listbox = new List(Math.min(options.length, 5), false);

                for (int i = 0; i < options.length; ++i) {
                    listbox.add(options[i].toString());
                }
                listbox.addItemListener(new ItemListener() {

                    public void itemStateChanged(ItemEvent evt) {
                        textField.setText(listbox.getSelectedItem());
                    }
                });
                editPanel.add(listbox, BorderLayout.SOUTH);
            }
            add(editPanel, BorderLayout.CENTER);
        }
    }

    /**
     * Get the text which was edited
     */
    private String getInputText() {
        return this.textField.getText();
    }

    /**
     * Create a dialog containing a message label, optional input fields and a title
     *
     * @return  text entered into the input field
     */
    private static OptionPane createDialog(final Frame parent, final String message, final String title, final int type, final Image icon, final Object[] options, final Object defaultValue) {
        OptionPane dialog = new OptionPane(parent, message, title, type, icon, options, defaultValue);

        // show dialog
        dialog.pack();
        dialog.setModal((type & INPUT_DIALOG) != 0);
        if (null != parent) {
            dialog.setLocation(parent.getX() + 100, parent.getY() + 100);
        }
        dialog.setVisible(true);

        return dialog;
    }

    /**
     * Show a simple dialog that displays a message, an OK-button and a Cancel-button
     */
    public static void showMessageDialog(final Frame parent, final String message) {
        createDialog(parent, message, null, PLAIN_MESSAGE, null, null, null);
    }

    /**
     * Show a simple dialog that displays a message, an OK-button and a Cancel-button.
     * The type of the dialog may be selected to be an information message, a warning or an error message.
     */
    public static void showMessageDialog(final Frame parent, final String message, final String title, final int type) {
        createDialog(parent, message, title, type, null, null, null);
    }

    /**
     * Show a simple input dialog that displays a message, a text input field, an OK-button and a Cancel-button
     */
    public static String showInputDialog(final Frame parent, final String message) {
        OptionPane dialog = createDialog(parent, message, null, INPUT_DIALOG, null, null, null);

        if (dialog.wasOKButtonClicked) {
            return dialog.textField.getText();
        } else {
            return null;
        }
    }

    /**
     * Show a simple input dialog that displays a message, a text input field, an OK-button and a Cancel-button.
     * Additionally an icon may be displayed and a listbox with options that automatically fill the text field when selected.
     */
    public static String showInputDialog(final Frame parent, final String message, final String title, final int type, final Image icon, final Object[] options, final Object defaultValue) {
        OptionPane dialog = createDialog(parent, message, title, INPUT_DIALOG | type, icon, options, defaultValue);

        if (dialog.wasOKButtonClicked) {
            return dialog.textField.getText();
        } else {
            return null;
        }
    }
}
