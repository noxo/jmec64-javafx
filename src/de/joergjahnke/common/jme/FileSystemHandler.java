/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.jme;

import de.joergjahnke.common.util.Logger;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Display;

/**
 * Some methods to load and save files from and to the local file system of the mobile device
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class FileSystemHandler {

    /**
     * File extension for the gzipped files that contain the delta for a file image
     */
    public final static String DELTA_FILE_EXTENSION = ".dlt";
    /**
     * Setting name for rotating screen
     */
    protected final static String SETTING_PROGRAMS = "Programs";
    /**
     * the logger used for messages of the FileSystemHandler
     */
    private final Logger logger;
    /**
     * settings to store and retrieve found files
     */
    private final Settings settings;
    /**
     * list of supported extensions
     */
    private final Vector supportedExtensions;

    /**
     * Creates a new instance of FileSystemHandler
     * 
     * @param   logger  logs messages of the FileSystemHandler, can be null to ignore messages
     * @param   supportedExtensions list of accepted file extensions
     * @param   settings    settings instance where we store the found programs
     */
    public FileSystemHandler(final Logger logger, final Vector supportedExtensions, final Settings settings) {
        this.logger = logger;
        this.supportedExtensions = supportedExtensions;
        this.settings = settings;
    }

    /**
     * Get the cached list of programs which is stored in the mobile devices memory
     * 
     * @return  map of programs and their location on the device
     */
    public Hashtable getCachedProgramList() {
        final Hashtable result = new Hashtable();

        try {
            final String prgsString = this.settings.getString(SETTING_PROGRAMS);

            for (int index = 0; index < prgsString.length();) {
                final int ni1 = prgsString.indexOf("\n", index);

                if (ni1 < 0) {
                    break;
                }

                final String name = prgsString.substring(index, ni1);
                int ni2 = prgsString.indexOf("\n", ni1 + 1);

                if (ni2 < 0) {
                    ni2 = prgsString.length();
                }

                final String url = prgsString.substring(ni1 + 1, ni2);

                index = ni2 + 1;

                result.put(name, url);
            }
        } catch (Exception e) {
        // we could not load or decode the settings, that's OK
        }

        return result;
    }

    /**
     * Search the device's file system for supported files
     *
     * @param   fileSearchStartDir  local directory where to start the search
     * @param   programs    map where to store the found files
     * @param   display display where to show a message when the job is finished
     */
    public void readProgramListFromFileSystem(final String fileSearchStartDir, final Hashtable programs, final Display display) {
        final Thread fileCrawler = new Thread() {

            public void run() {
                if (null != logger) {
                    logger.info("Scanning local file system for files...");
                }

                // we also construct a string containing all results
                final StringBuffer prgsString = new StringBuffer();
                // count number of found programs
                int found = 0;

                try {
                    // determine directories where to search for supported files
                    final Vector directories = new Vector();

                    if (fileSearchStartDir != null) {
                        // search directory stored in settings
                        directories.addElement("file:///" + fileSearchStartDir);
                    } else {
                        // iterate over all root directories
                        for (final Enumeration en = javax.microedition.io.file.FileSystemRegistry.listRoots(); en.hasMoreElements();) {
                            directories.addElement("file:///" + en.nextElement());
                        }
                    }

                    // recursively iterate over all found directories
                    for (int i = 0; i < directories.size(); ++i) {
                        final String directory = directories.elementAt(i).toString();

                        try {
                            final javax.microedition.io.file.FileConnection dirConn = (javax.microedition.io.file.FileConnection) Connector.open(directory, Connector.READ);

                            if (dirConn.exists()) {
                                // iterate over all files in the directory
                                for (final Enumeration en = dirConn.list(); en.hasMoreElements();) {
                                    final String url = dirConn.getURL() + en.nextElement();

                                    // this is a sub-directory?
                                    if (url.endsWith("/")) {
                                        // then add it to the list of directories to search
                                        directories.addElement(url);
                                    } else {
                                        final String name = url.indexOf('/') > 0 ? url.substring(url.lastIndexOf('/') + 1) : url;
                                        final String extension = name.indexOf('.') > 0 ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";

                                        // add supported extensions for the program list
                                        if (supportedExtensions.contains(extension) || ("." + extension).equals(DELTA_FILE_EXTENSION)) {
                                            programs.put(name, url);
                                            ++found;

                                            if (prgsString.length() > 0) {
                                                prgsString.append("\n");
                                            }
                                            prgsString.append(name);
                                            prgsString.append("\n");
                                            prgsString.append(url);
                                        }
                                    }
                                }
                            }
                            dirConn.close();
                        } catch (Exception e) {
                            if (null != logger) {
                                logger.info("Scanning of local directory '" + directory + "' caused an exception: " + e.getMessage() + "! Proceeding with next directory.");
                            }
                        }
                    }

                    if (null != logger) {
                        logger.info("Scanning of local file system finished, " + found + " file(s) found.");
                    }
                } catch (Exception e) {
                    if (null != logger) {
                        logger.warning("An error occurred while searching for files in the local file system! Search aborted after " + found + " file(s) were found.");
                    }
                    e.printStackTrace();
                } finally {
                    display.setCurrent(new Alert(LocalizationSupport.getMessage("SearchFinished"), LocalizationSupport.getMessage("FoundNFiles1") + found + " " + LocalizationSupport.getMessage("FoundNFiles2"), null, AlertType.INFO));
                }

                // save found files for next start of the emulator
                if (prgsString.length() > 0) {
                    try {
                        settings.setString(SETTING_PROGRAMS, prgsString.toString());
                    } catch (Exception e) {
                    // we could not save the list of programs on the phone, that's OK
                    }
                }
            }
        };

        fileCrawler.start();
    }

    /**
     * Save difference to old file version
     *
     * @param   currentImage    current images' file name
     * @param   delta   byte array with information about the modifications to save
     * @throws IOException if the delta cannot be saved
     */
    public void saveDelta(final String currentImage, final byte[] delta) throws IOException {
        final String deltaFilename = currentImage + DELTA_FILE_EXTENSION;
        final javax.microedition.io.file.FileConnection fileConn = (javax.microedition.io.file.FileConnection) Connector.open(deltaFilename, Connector.WRITE);

        if (!fileConn.exists()) {
            fileConn.create();
        } else {
            fileConn.truncate(0);
        }

        final OutputStream out = fileConn.openOutputStream();

        for (int i = 0; i < delta.length; ++i) {
            out.write(delta[i]);
        }

        fileConn.close();

        this.logger.info("Saved changes to file '" + deltaFilename + "'!");
    }
}
