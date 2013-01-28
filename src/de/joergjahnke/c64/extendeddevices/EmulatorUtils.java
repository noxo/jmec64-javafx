/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.extendeddevices;

import de.joergjahnke.c64.core.C64;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Some utility methods for the emulator
 *
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class EmulatorUtils {

    /**
     * File extension for the gzipped files that contain the delta for a C64 file image
     */
    public final static String DELTA_FILE_EXTENSION = ".gzd";

    /**
     * Attach an image to the emulator
     * 
     * @param c64   emulator to attach the image to
     * @param driveNo   the drive to attach the image to
     * @param selectedFile  name of the file to attach
     * @return  attached file
     * @throws java.io.IOException  if an error occurred during loading
     */
    public static File attachImage(final C64 c64, final int driveNo, final String selectedFile) throws IOException {
        // in case of a delta file, first load the original file
        final boolean applyDelta = selectedFile.endsWith(DELTA_FILE_EXTENSION);
        final String filename = applyDelta ? selectedFile.substring(0, selectedFile.length() - 4) : selectedFile;

        // then attach the selected image
        final File fileToLoad = new File(filename);

        c64.getDrive(driveNo).attachImage(new BufferedInputStream(new FileInputStream(fileToLoad)), fileToLoad.getName());

        // now apply delta file if it was selected
        if (applyDelta) {
            c64.getDrive(driveNo).getDriveHandler().applyDelta(new BufferedInputStream(new GZIPInputStream(new FileInputStream(new File(filename)))));
            c64.getLogger().info("Applied delta file '" + filename + "'");
        }

        return fileToLoad;
    }

    /**
     * Save a given delta of an image
     * 
     * @param lastFile  filename of the original image whose delta is going to be save
     * @param delta differing bytes
     * @return delta file
     * @throws java.io.IOException  if the data could not be saved
     */
    public static File saveDeltaFile(final String lastFile, final byte[] delta) throws IOException {
        final File deltaFile = new File(lastFile + EmulatorUtils.DELTA_FILE_EXTENSION);

        if (deltaFile.exists()) {
            deltaFile.delete();
        }

        final GZIPOutputStream gzout = new GZIPOutputStream(new FileOutputStream(deltaFile));
        final BufferedOutputStream out = new BufferedOutputStream(gzout);

        for (int i = 0; i < delta.length; ++i) {
            out.write(delta[i]);
        }

        out.flush();
        gzout.finish();

        return deltaFile;
    }
}
