/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.jme;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Image;

/**
 * Some methods to load and save files from and to the local file system of the mobile device
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class FileBrowser {

    /**
     * Use directory command
     */
    private final static Command useDirCommand = new Command(LocalizationSupport.getMessage("Select"), LocalizationSupport.getMessage("SelectThisFile"), Command.OK, 1);
    /**
     * List directory command
     */
    private final static Command listDirCommand = new Command(LocalizationSupport.getMessage("List"), LocalizationSupport.getMessage("ListDirectory"), Command.ITEM, 2);
    /**
     * back command, leads back to the main form
     */
    private final static Command backCommand = new Command(LocalizationSupport.getMessage("Back"), Command.BACK, 99);
    /**
     * root folder for the images
     */
    private final static String RESOURCE_ROOT = "/res/drawable/";
    /**
     * file name for a folder image
     */
    private final static String FOLDER = "folder.png";
    /**
     * file name for a file image
     */
    private final static String FILE = "file.png";
    /**
     * file name for a parent folder image
     */
    private final static String PARENT = "parent.png";
    /**
     * contains all usable images for the file browser dialog
     */
    private final static Hashtable images = new Hashtable();
    

    static {
        try {
            images.put(FOLDER, Image.createImage(RESOURCE_ROOT + FOLDER));
            images.put(PARENT, Image.createImage(RESOURCE_ROOT + PARENT));
            images.put(FILE, Image.createImage(RESOURCE_ROOT + FILE));
        } catch (Throwable t) {
            // we could not load any/all images, but we can work without them
        }
    }
    /**
     * the current directory
     */
    private String selected = "";
    /**
     * the files in the current directory
     */
    private final Vector currentFiles = new Vector();
    /**
     * a file filters being used
     */
    private final Vector filters;
    /**
     * the previous display, used when the Back command is selected
     */
    private final Displayable previous;
    /**
     * the current display
     */
    private final Display display;

    /**
     * Create a new file browser
     *
     * @param   display display to use
     * @param   currentDir  start directory, "" to start at the root
     * @param   filters  list of file extensions that are accepted when browsing the files, directories are accepted if a filter "/" is contained
     */
    public FileBrowser(final Display display, final String currentDir, final Vector filters) {
        this.selected = currentDir;
        this.filters = filters;
        this.previous = display.getCurrent();
        this.display = display;
    }

    /**
     * Get the selected file
     * 
     * @return  file name
     */
    public String getSelectedFile() {
        return this.selected;
    }

    /**
     * Show the file browser on the given display
     */
    public void show() {
        retrieveDirectory();
    }

    /**
     * Retrieve and show contents of a directory
     */
    private void retrieveDirectory() {
        final Thread thread = new Thread() {

            public void run() {
                // retrieve contents of the new directory and add all directories to the choice list
                try {
                    currentFiles.removeAllElements();

                    if (null == selected || "".equals(selected)) {
                        for (final Enumeration en = javax.microedition.io.file.FileSystemRegistry.listRoots(); en.hasMoreElements();) {
                            currentFiles.addElement(en.nextElement());
                        }
                    } else {
                        final String url = "file:///" + selected;
                        final javax.microedition.io.file.FileConnection dirConn = (javax.microedition.io.file.FileConnection) Connector.open(url, Connector.READ);

                        for (final Enumeration en = dirConn.list(); en.hasMoreElements();) {
                            final String file = en.nextElement().toString();

                            for (int i = 0; i < filters.size(); ++i) {
                                if (file.endsWith(filters.elementAt(i).toString())) {
                                    currentFiles.addElement(file);
                                    break;
                                }
                            }
                        }
                    }

                    showFiles();
                } catch (Exception e) {
                    e.printStackTrace();
                    onError(e);
                }
            }
        };

        thread.start();
    }

    /**
     * Show content of the current directory
     */
    private void showFiles() {
        final Form settingsForm = new Form(LocalizationSupport.getMessage("FileBrowser"));
        // create a listbox with the current files
        final ChoiceGroup dirChoice = new ChoiceGroup(LocalizationSupport.getMessage("ContentsOf") + (null == selected || "".equals(selected) ? "/" : selected), ChoiceGroup.EXCLUSIVE);

        if (null != selected && !"".equals(selected)) {
            dirChoice.append("..", (Image) images.get(PARENT));
        }

        for (int i = 0; i < this.currentFiles.size(); ++i) {
            final String file = this.currentFiles.elementAt(i).toString();

            dirChoice.append(file, file.endsWith("/") ? (Image) images.get(FOLDER) : (Image) images.get(FILE));
        }

        settingsForm.append(dirChoice);

        // add OK and Cancel button
        settingsForm.addCommand(useDirCommand);
        settingsForm.addCommand(listDirCommand);
        settingsForm.addCommand(backCommand);
        settingsForm.setCommandListener(
                new CommandListener() {

                    public void commandAction(Command c, Displayable d) {
                        // use a given directory?
                        if (c == useDirCommand) {
                            final String file = dirChoice.getString(dirChoice.getSelectedIndex());

                            if (!"..".equals(file)) {
                                selected += file;
                            }
                            onSelect();
                        // list contents of a given directory?
                        } else if (c == listDirCommand) {
                            // retrieve and move to the selected directory
                            final String directory = dirChoice.getString(dirChoice.getSelectedIndex());

                            if ("..".equals(directory)) {
                                if (selected.indexOf('/') <= 0 || selected.indexOf('/') == selected.lastIndexOf('/')) {
                                    selected = "";
                                } else {
                                    selected = selected.substring(0, selected.substring(0, selected.length() - 1).lastIndexOf('/') + 1);
                                }
                            } else if(directory.endsWith("/")) {
                                selected += directory;
                                retrieveDirectory();
                            } else {
                                display.setCurrent(new Alert(LocalizationSupport.getMessage("NotADirectory"), LocalizationSupport.getMessage("SelectedNotADirectory"), null, AlertType.WARNING));
                            }
                        } else if (c == backCommand) {
                            display.setCurrent(previous);
                        }
                    }
                });

        this.display.setCurrent(settingsForm);
    }

    /**
     * This method is executed when the user selects a given file or directory.
     * The default implementation does nothing. Subclasses may overwrite this method to
     * execute specific actions when a file gets selected.
     */
    public void onSelect() {
    }

    /**
     * This method is executed when an error occurs during file browsing.
     * The default implementation displays an error message and closes the file browser.
     * Subclasses may overwrite this method to execute specific actions when an error occurs.
     *
     * @param   t   root error
     */
    public void onError(final Throwable t) {
        display.setCurrent(previous);
        display.setCurrent(new Alert(LocalizationSupport.getMessage("AnErrorHasOccurred"), LocalizationSupport.getMessage("ErrorWas") + t, null, AlertType.WARNING));
    }
}
