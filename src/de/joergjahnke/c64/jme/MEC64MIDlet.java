/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.jme;

import de.joergjahnke.c64.core.C1541;
import de.joergjahnke.c64.core.C64;
import de.joergjahnke.c64.smalldisplays.SmoothingScalableVIC6569;
import de.joergjahnke.common.jme.ButtonAssignmentCanvas;
import de.joergjahnke.common.jme.CollectionUtils;
import de.joergjahnke.common.jme.FileBrowser;
import de.joergjahnke.common.jme.FileSystemHandler;
import de.joergjahnke.common.jme.FormattedTextForm;
import de.joergjahnke.common.jme.LocalizationSupport;
import de.joergjahnke.common.jme.PCMtoMIDIPlayer;
import de.joergjahnke.common.jme.ProgressForm;
import de.joergjahnke.common.jme.Settings;
import de.joergjahnke.common.jme.WavePlayer;
import de.joergjahnke.common.vmabstraction.sunvm.SunVMResourceLoader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Choice;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.ItemStateListener;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Spacer;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/**
 * Midlet for the J2ME C64 emulator
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class MEC64MIDlet extends MIDlet implements CommandListener {

    /**
     * Setting name for file search starting directory
     */
    private final static String SETTING_FILESEARCH_STARTDIR = "FileSearchStartDir";
    /**
     * Setting name for switching orientation sensor usage on/off
     */
    public final static String SETTING_ACCELEROMETER = "Accelerometer";
    /**
     * Setting name for UI language
     */
    public final static String SETTING_LANGUAGE = "Language";
    /**
     * Setting name for setting sound on/off
     */
    protected final static String SETTING_SOUNDACTIVE = "SoundActive";
    /**
     * Setting name for switching orientation sensor usage on/off
     */
    public final static String SETTING_AUTO_ROTATE = "AutoRotateScreen";
    /**
     * Setting name for UI language
     */
    public final static String SETTING_SNAPSHOTS = "Snapshots";
    /**
     * Prefix for the setting for custom key assignments
     */
    public final static String SETTING_PREFIX_KEYS = "Key_";
    /**
    /**
     * Suffix for the record store name for the suspend data
     */
    private final static String SUSPENDDATA_SUFFIX = "_SuspendData";
    /**
     * delimiter for the names inside the string containing the list of snapshots
     */
    private static final char SNAPSHOT_NAME_DELIMITER = '\n';
    /**
     * list of joystick "keys"
     */
    private final static String[] JOYSTICK_KEYS = {"Up", "Up & Right", "Right", "Down & Right", "Down", "Down & Left", "Left", "Up & Left", "Fire"};
    /**
     * supported emulator languages
     */
    private final static String[] SUPPORTED_LOCALES = {"Device default", "de", "en", "es", "fr", "it", "ru", "zh_CN"};
    /**
     * URL of the project's main web page
     */
    private final static String PROJECT_PAGE_URL = "https://sourceforge.net/projects/jmec64/";
    /**
     * Status code when loading an emulator state worked fine
     */
    private final static int STATUS_LOAD_OK = 0;
    /**
     * Status code when loading an emulator state failed
     */
    private final static int STATUS_LOAD_FAILED = 1;
    /**
     * Status code when the given emulator state did not exist
     */
    private final static int STATUS_NOTHING_LOADED = 2;
    /**
     * length of the program name component for a snapshot
     */
    private static final int SNAPSHOT_PROGRAMNAME_LENGTH = 18;
    /**
     * snapshot prefix when no image is attached
     */
    private static final String NO_IMAGE = "<no image>";
    /**
     * number of drives supported by the J2ME version
     */
    private final static int NUM_DRIVES = 2;
    /**
     * a floppy image
     */
    private static Image floppyImage = null;
    /**
     * a snapshot image
     */
    private static Image snapshotImage = null;
    /**
     * programs that are available as selections
     */
    private final Hashtable programs = new Hashtable();
    /**
     * main frame
     */
    private C64Canvas c64Canvas = null;
    /**
     * C64 instance
     */
    private C64 c64 = null;
    /**
     * starting directory when doing a file search
     */
    private String fileSearchStartDir;
    /**
     * handler for the mobile devices local filesystem, will be null if the FileConnection API is not supported
     */
    private FileSystemHandler fileSystemHandler;
    /**
     * emulator settings
     */
    private Settings settings = null;
    /**
     * images from the mobile device attached to the drives
     */
    private Hashtable attachedImages = new Hashtable();
    /**
     * 'type' command
     */
    private final Command typeCommand;
    /**
     * command to press a special C64 key
     */
    private final Command specialKeysCommand;
    /**
     * command to attach an image to the emulator
     */
    private final Command attachImageCommand;
    /**
     * command to detach all images from the emulator
     */
    private final Command detachImageCommand;
    /**
     * command to select the active drive
     */
    private final Command selectDriveCommand;
    /**
     * command to switch off the sound
     */
    private final Command searchProgramsCommand;
    /**
     * command to load an image
     */
    private final Command runCommand;
    /**
     * command to start with basic
     */
    private final Command resetCommand;
    /**
     * command to show the settings dialog
     */
    private final Command editSettingsCommand;
    /**
     * command to assign joypad keys
     */
    private final Command assignKeysCommand;
    /**
     * 'show log' command
     */
    private final Command showLogCommand;
    /**
     * about message command ...
     */
    private final Command aboutCommand;
    /**
     * help command
     */
    private final Command helpCommand;
    /**
     * command to suspend and exit
     */
    private final Command suspendCommand;
    /**
     * command to exit the application
     */
    private final Command exitCommand;
    /**
     * command to fast-load a program from the image and run it afterwards
     */
    private final Command fastLoadRunProgramCommand;
    /**
     * command to fast-load a program from the image
     */
    private final Command fastLoadProgramCommand;
    /**
     * command to load a program from the image
     */
    private final Command loadProgramCommand;
    /**
     * command to load an image and start its first program using fast-load
     */
    private final Command fastAutoStartCommand;
    /**
     * command to load an image and start its first program
     */
    private final Command autoStartCommand;
    /**
     * OK command
     */
    private final Command okCommand;
    /**
     * OK + Enter command
     */
    private final Command okEnterCommand;
    /**
     * back command
     */
    private final Command backCommand;
    /**
     * Browse command
     */
    private final Command browseCommand;
    /**
     * Snapshots command
     */
    private final Command snapshotCommand;
    /**
     * Remove command
     */
    private final Command removeCommand;

    /**
     * Create new C64 MIDlet
     */
    public MEC64MIDlet() {
        // create Settings instance to load and store emulator settings
        try {
            this.settings = new Settings(getAppProperty("MIDlet-Name"));
        } catch (Exception e) {
            // we have to work without loading and storing settings, that's OK
            }

        // initialize L10N support
        String locale = getLocale();

        LocalizationSupport.initLocalizationSupport(locale, LocalizationSupport.COMMON_MESSAGES);
        LocalizationSupport.initLocalizationSupport(locale, "/res/l10n/c64EmulatorMessages.properties");

        // initialize commands
        this.typeCommand = new Command(LocalizationSupport.getMessage("Type"), LocalizationSupport.getMessage("TypeText"), Command.ITEM, 1);
        this.specialKeysCommand = new Command(LocalizationSupport.getMessage("Keys"), LocalizationSupport.getMessage("SpecialKeys"), Command.ITEM, 2);
        this.attachImageCommand = new Command(LocalizationSupport.getMessage("Load"), LocalizationSupport.getMessage("LoadImage"), Command.ITEM, 3);
        this.detachImageCommand = new Command(LocalizationSupport.getMessage("Detach"), LocalizationSupport.getMessage("DetachAll"), Command.ITEM, 4);
        this.selectDriveCommand = new Command(LocalizationSupport.getMessage("Drive"), LocalizationSupport.getMessage("SelectDrive"), Command.ITEM, 5);
        this.searchProgramsCommand = new Command(LocalizationSupport.getMessage("SearchPrograms"), LocalizationSupport.getMessage("SearchProgramsInFileSystem"), Command.ITEM, 6);
        this.runCommand = new Command(LocalizationSupport.getMessage("Run"), LocalizationSupport.getMessage("RunCurrent"), Command.ITEM, 7);
        this.resetCommand = new Command(LocalizationSupport.getMessage("Reset"), LocalizationSupport.getMessage("ResetC64"), Command.ITEM, 8);
        this.editSettingsCommand = new Command(LocalizationSupport.getMessage("Settings"), LocalizationSupport.getMessage("EditSettings"), Command.ITEM, 9);
        this.assignKeysCommand = new Command(LocalizationSupport.getMessage("Assign"), LocalizationSupport.getMessage("AssignKeys"), Command.ITEM, 10);
        this.showLogCommand = new Command(LocalizationSupport.getMessage("ShowLog"), Command.ITEM, 11);
        this.aboutCommand = new Command(LocalizationSupport.getMessage("About"), Command.HELP, 12);
        this.helpCommand = new Command(LocalizationSupport.getMessage("Help"), Command.HELP, 13);
        this.suspendCommand = new Command(LocalizationSupport.getMessage("Suspend"), LocalizationSupport.getMessage("SuspendAndExit"), Command.EXIT, 98);
        this.exitCommand = new Command(LocalizationSupport.getMessage("Exit"), Command.EXIT, 99);
        this.fastLoadRunProgramCommand = new Command(LocalizationSupport.getMessage("FastLoadRun"), LocalizationSupport.getMessage("FastLoadRunProgram"), Command.ITEM, 1);
        this.fastLoadProgramCommand = new Command(LocalizationSupport.getMessage("FastLoad"), LocalizationSupport.getMessage("FastLoadProgram"), Command.ITEM, 2);
        this.loadProgramCommand = new Command(LocalizationSupport.getMessage("Load"), LocalizationSupport.getMessage("LoadProgram"), Command.ITEM, 3);
        this.fastAutoStartCommand = new Command(LocalizationSupport.getMessage("FastAutoStart"), LocalizationSupport.getMessage("FastAutoStart"), Command.ITEM, 2);
        this.autoStartCommand = new Command(LocalizationSupport.getMessage("AutoStart"), LocalizationSupport.getMessage("AutoStart"), Command.ITEM, 3);
        this.okCommand = new Command(LocalizationSupport.getMessage("OK"), Command.OK, 1);
        this.okEnterCommand = new Command(LocalizationSupport.getMessage("OKEnter"), Command.OK, 2);
        this.backCommand = new Command(LocalizationSupport.getMessage("Back"), Command.BACK, 99);
        this.browseCommand = new Command(LocalizationSupport.getMessage("Browse"), Command.ITEM, 2);
        this.snapshotCommand = new Command(LocalizationSupport.getMessage("SaveSnapshot"), Command.ITEM, 4);
        this.removeCommand = new Command(LocalizationSupport.getMessage("Remove"), Command.ITEM, 2);

        try {
            // create the C64 with sound deactivated
            this.c64 = new C64(new SunVMResourceLoader(), NUM_DRIVES);

            // initialize the display
            this.c64Canvas = new C64Canvas(this);
            this.c64Canvas.setC64(c64);
            this.c64Canvas.calculateScreenSize();
            this.c64Canvas.setCommandListener(this);
            Display.getDisplay(this).setCurrent(this.c64Canvas);

            // apply some settings
            try {
                if (this.settings.exists(SETTING_PREFIX_KEYS + JOYSTICK_KEYS[0])) {
                    this.c64Canvas.setUseAccelerometer(this.settings.getBoolean(SETTING_ACCELEROMETER, this.c64Canvas.isUseAccelerometer()));
                    this.c64Canvas.setAutoChangeOrientation(this.settings.getBoolean(SETTING_AUTO_ROTATE, this.c64Canvas.isAutoChangeOrientation()));

                    final Hashtable assignments = new Hashtable();

                    for (int i = 0; i < JOYSTICK_KEYS.length; ++i) {
                        assignments.put(new Integer(this.settings.getInteger(SETTING_PREFIX_KEYS + JOYSTICK_KEYS[i])), JOYSTICK_KEYS[i]);
                    }
                    this.c64Canvas.setButtonAssignments(assignments);
                    // initialize sound
                    setSound(this.settings.getBoolean(SETTING_SOUNDACTIVE, false));
                }
            } catch (Exception e) {
                // we could not apply the settings and work with defaults
            }

            // read program list
            this.programs.clear();
            try {
                CollectionUtils.putAll(this.programs, readProgramListFromTextFile());
            } catch (Exception e) {
                // we could not read the program list, this is no harm, we just don't have programs from the jar file available
            }

            // if we have the FileConnection API available, then we register as observer for the drive
            // we also add the programs we had found during the last file system search
            if (supportsFileConnectionAPI()) {
                for (int i = 0; i < this.c64.getDriveCount(); ++i) {
                    this.c64.getDrive(i).addObserver(c64Canvas);
                }
                this.fileSystemHandler = new FileSystemHandler(c64.getLogger(), C1541.SUPPORTED_EXTENSIONS, this.settings);
                CollectionUtils.putAll(this.programs, this.fileSystemHandler.getCachedProgramList());
            }

            // try to load the cartridge image
            if (null == floppyImage) {
                try {
                    floppyImage = Image.createImage("/res/drawable/floppy.png");
                } catch (Exception e) {
                    // we can work without the image
                }
            }

            // we try to resume a previous session
            resume();

            // start the emulation
            new Thread(this.c64).start();
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(LocalizationSupport.getMessage("CouldNotInitialize") + t);
        }

        // add commands
        this.c64Canvas.addCommand(exitCommand);
        this.c64Canvas.addCommand(suspendCommand);
        this.c64Canvas.addCommand(snapshotCommand);
        this.c64Canvas.addCommand(aboutCommand);
        this.c64Canvas.addCommand(helpCommand);
        this.c64Canvas.addCommand(showLogCommand);
        this.c64Canvas.addCommand(attachImageCommand);
        this.c64Canvas.addCommand(selectDriveCommand);
        this.c64Canvas.addCommand(runCommand);
        // if we have the FileConnection API available, then also add command to search the file system
        if (supportsFileConnectionAPI()) {
            this.c64Canvas.addCommand(searchProgramsCommand);
        }
        // add other commands
        this.c64Canvas.addCommand(resetCommand);
        // add type text command
        this.c64Canvas.addCommand(typeCommand);
        // add special keys command
        this.c64Canvas.addCommand(specialKeysCommand);
        // add command to modify the settings and change the default keys
        this.c64Canvas.addCommand(editSettingsCommand);
        this.c64Canvas.addCommand(assignKeysCommand);

        // try to load the floppy- and suspend image
        if (null == floppyImage) {
            try {
                floppyImage = Image.createImage("/res/drawable/floppy.png");
            } catch (Exception e) {
                // we can work without the image
            }
        }
        if (null == snapshotImage) {
            try {
                snapshotImage = Image.createImage("/res/drawable/snapshot.png");
            } catch (Exception e) {
                // we can work without the image
            }
        }
    }

    /**
     * Get the program settings
     * 
     * @return  settings instance, null if no settings instance could be created
     */
    public Settings getSettings() {
        return this.settings;
    }

    /**
     * Get the locale for the emulator.
     * The default locale is taken from the system property "microedition.locale"
     * but can be overriden via the settings.
     *
     * @return  locale string e.g. "sv" or "de"
     */
    private String getLocale() {
        // initialize L10N support
        String locale = System.getProperty("microedition.locale");

        try {
            locale = this.settings.getString(SETTING_LANGUAGE, locale);
        } catch (Exception e) {
            // we could not determine the locale setting and will use the system default
        }

        return locale;
    }

    /**
     * Create the canvas displaying the C64 and the C64 instance and show the former
     */
    public void startApp() {
        this.c64.resume();
    }

    /**
     * Pause the C64 if the application is inactive
     */
    public void pauseApp() {
        this.c64.pause();
        notifySoundPlayer(PCMtoMIDIPlayer.SIGNAL_PAUSE);
    }

    /**
     * Destroy must cleanup everything not handled by the garbage collector.
     */
    public void destroyApp(boolean unconditional) {
        this.c64.stop();
        // stop the MIDI sound player, if one is active
        notifySoundPlayer(PCMtoMIDIPlayer.SIGNAL_STOP);
    }

    /**
     * Send a signal to the sound player to stop it from working
     * 
     * @param signal    signal to send
     */
    private void notifySoundPlayer(final Object signal) {
        if (null != this.c64.getSID() && this.c64.getSID().countObservers() > 0) {
            this.c64.getSID().setChanged(true);
            this.c64.getSID().notifyObservers(signal);
        }
    }

    // implementation of the CommandListener interface
    /**
     * Respond to commands, including exit
     * On the exit command, cleanup and notify that the MIDlet has been destroyed.
     */
    public void commandAction(Command c, Displayable s) {
        // we want to exit the emulator?
        if (c == exitCommand) {
            exit();
            // we want to suspend the current state and exit?
        } else if (c == suspendCommand) {
            suspend();
            // an OK or Back command has been entered?
        } else if (c == okCommand || c == backCommand) {
            Display.getDisplay(this).setCurrent(c64Canvas);
            // we want to attach a C64 image?
        } else if (c == attachImageCommand) {
            // show available C64 images
            showImageList();
            // we want to select another drive?
        } else if (c == selectDriveCommand) {
            // show available drives
            showDriveList();
            // we want to detach the drives?
        } else if (c == detachImageCommand) {
            // detach all images from all drives
            detachImages();
            // nothing is attached, so we don't need the detach command for now
            c64Canvas.removeCommand(detachImageCommand);
            // we want to run the current program?
        } else if (c == runCommand) {
            runProgram();
            // we want to show the about dialog?
        } else if (c == aboutCommand) {
            showAboutForm();
            // we want to display the program help?
        } else if (c == helpCommand) {
            showHelpForm();
            // we want to type text?
        } else if (c == typeCommand) {
            showTypeTextForm();
            // we want to select a special C64 key?
        } else if (c == specialKeysCommand) {
            showSpecialKeysForm();
            // we want to reset the emulator?
        } else if (c == resetCommand) {
            this.c64.reset();
            c64Canvas.showEmulatorExceptions = true;
            // we want to display the log?
        } else if (c == showLogCommand) {
            showLogForm();
            // we want to toggle the joystick?
        } else if (c == editSettingsCommand) {
            showSettingsForm();
            // we want to search the local file system for C64 images?
        } else if (c == searchProgramsCommand) {
            CollectionUtils.removeAll(this.programs, this.fileSystemHandler.getCachedProgramList());
            showSelectDirectoryForm();
        } else if (c == assignKeysCommand) {
            showAssignButtonsCanvas();
        } else if (c == snapshotCommand) {
            saveSnapshot();
        }
    }

    /**
     * Check if the FileConnection API is supported by the device
     *
     * @return  true if the API is supported
     */
    private final boolean supportsFileConnectionAPI() {
        return null != System.getProperty("microedition.io.file.FileConnection.version");
    }

    /**
     * Run the currently loaded program
     */
    private void runProgram() {
        this.c64.getKeyboard().textTyped("run");
        this.c64.getKeyboard().keyTyped("ENTER");
    }

    /**
     * Retrieve the list of saved snapshots from the settings.
     * This method assumes that all elements, even the last in the list, are followed by the snapshot string delimiter.
     *
     * @return  list of snapshot names
     */
    private Vector getSnapshots() {
        final Vector result = new Vector();
        final String snapshots = getSettings().getString(SETTING_SNAPSHOTS, null);

        if (null != snapshots) {
            for (int index = 0, newIndex; index < snapshots.length(); index = newIndex + 1) {
                newIndex = snapshots.indexOf(SNAPSHOT_NAME_DELIMITER, index);
                result.addElement(snapshots.substring(index, newIndex));
            }
        }

        return result;
    }

    /**
     * Get all snapshots associated with a given program
     *
     * @param program   program name
     * @param snapshotList  list containing all saved snapshots
     * @return  list of snapshots associated with the given program
     */
    private Vector getSnapshots(final String program, final Vector snapshotList) {
        final Vector result = new Vector();
        final String search = program.substring(0, Math.min(program.length(), SNAPSHOT_PROGRAMNAME_LENGTH));

        for (int i = 0, to = snapshotList == null ? 0 : snapshotList.size(); i < to; ++i) {
            final String name = snapshotList.elementAt(i).toString();

            if (name.startsWith(search)) {
                result.addElement(name);
            }
        }

        return result;
    }

    /**
     * Save a given list of snapshots in the settings.
     * This method stores the list of names in one string, adding a delimiter after each string, even the last one.
     *
     * @param snapshotList  list of snapshot names
     * @throws RecordStoreException if saving in the settings fails
     */
    private void setSnapshots(final Vector snapshotList) throws RecordStoreException {
        if (null != snapshotList) {
            final StringBuffer snapshots = new StringBuffer();

            for (int i = 0, to = snapshotList.size(); i < to; ++i) {
                snapshots.append(snapshotList.elementAt(i));
                snapshots.append(SNAPSHOT_NAME_DELIMITER);
            }

            getSettings().setString(SETTING_SNAPSHOTS, snapshots.toString());
        } else {
            getSettings().remove(SETTING_SNAPSHOTS);
        }
    }

    /**
     * Save the current emulator state in a record store with the given name
     *
     * @param name  name of the record store to use
     * @throws IOException  if the snapshot of the current state cannot be created
     * @throws RecordStoreException if the current state can't be saved inside the record store
     */
    private void saveState(final String name) throws IOException, RecordStoreException {
        // create suspend "file"
        final RecordStore rs = RecordStore.openRecordStore(name, true);
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final DataOutputStream out = new DataOutputStream(os);

        // save the list of attached images
        out.writeInt(this.attachedImages.size());
        for (final Enumeration en = this.attachedImages.keys(); en.hasMoreElements();) {
            final Integer driveNo = (Integer) en.nextElement();

            out.writeInt(driveNo.intValue());
            out.writeUTF(this.attachedImages.get(driveNo).toString());
        }
        // save current emulator state
        this.c64.serialize(out);
        out.flush();
        rs.addRecord(os.toByteArray(), 0, os.size());
        out.close();
        os.close();
        rs.closeRecordStore();
    }

    /**
     * Save a snapshot of the current state
     */
    private void saveSnapshot() {
        this.c64.pause();

        // generate a name for the snapshot
        final Calendar cal = Calendar.getInstance();

        cal.setTime(new java.util.Date());

        final Object attachedImage = this.attachedImages.get(new Integer(this.c64.getActiveDrive()));
        final String currentImage = attachedImage == null ? NO_IMAGE : attachedImage.toString();
        final String name = currentImage.substring(0, Math.min(currentImage.length(), SNAPSHOT_PROGRAMNAME_LENGTH)) + "@" + formatForSnapshot(cal.get(Calendar.YEAR)) + formatForSnapshot(cal.get(Calendar.MONTH) + 1) + formatForSnapshot(cal.get(Calendar.DAY_OF_MONTH)) + "-" + formatForSnapshot(cal.get(Calendar.HOUR_OF_DAY)) + formatForSnapshot(cal.get(Calendar.MINUTE)) + formatForSnapshot(cal.get(Calendar.SECOND));

        try {
            // save the current state under that name
            saveState(name);

            // add the name to the list of snapshots and save this in the settings
            final Vector snapshots = getSnapshots();

            snapshots.addElement(name);
            setSnapshots(snapshots);
        } catch (Throwable t) {
            try {
                RecordStore.deleteRecordStore(name);
            } catch (Throwable t2) {
                // the snapshot probably was not saved at all, so we ignore this
            }
            // show the cause of the error
            Display.getDisplay(this).setCurrent(new Alert(LocalizationSupport.getMessage("SavingSnapshotFailed"), LocalizationSupport.getMessage("FailedToStoreState"), null, AlertType.WARNING));
            t.printStackTrace();
        }

        this.c64.resume();
    }

    /**
     * Return an integer as two-digit string with training zero if necessary
     *
     * @param n number to format
     * @return  formatted string
     */
    private String formatForSnapshot(final int n) {
        final String s = "0" + n;

        return s.substring(s.length() - 2);
    }

    /**
     * Save the emulator state and exit
     */
    private void suspend() {
        this.c64.pause();

        boolean reallyExit = true;

        try {
            saveState(getAppProperty("MIDlet-Name") + SUSPENDDATA_SUFFIX);
        } catch (Throwable t) {
            // show the cause of the error
            Display.getDisplay(this).setCurrent(new Alert(LocalizationSupport.getMessage("SuspendFailed"), LocalizationSupport.getMessage("FailedToStoreState"), null, AlertType.WARNING));
            t.printStackTrace();
            // we don't exit, the user might want to continue now that the suspend failed
            reallyExit = false;
            this.c64.resume();
        }

        if (reallyExit) {
            exit();
        }
    }

    /**
     * Load an emulator state from a record store with a given name.
     * A progress bar will be displayed while loading.
     *
     * @param name  name of the record store to use
     * @return  status of the load operation
     */
    private int loadState(final String name) {
        int status = STATUS_LOAD_OK;
        final Display display = Display.getDisplay(this);

        // create a screen that displays a progress bar
        final ProgressForm progressForm = new ProgressForm(LocalizationSupport.getMessage("Resuming"));

        boolean hasSuspendData = false;
        RecordStore rs = null;

        try {
            // open the suspend "file"
            rs = RecordStore.openRecordStore(name, false);

            byte[] bytes = rs.getRecord(1);

            hasSuspendData = true;

            final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));

            bytes = null;
            // display the prepared progress form
            display.setCurrent(progressForm);

            // reload the attached images
            final int size = in.readInt();

            for (int i = 0; i < size; ++i) {
                final int driveNo = in.readInt();
                final String program = in.readUTF();

                attachImage(driveNo, program);
                progressForm.update(this, new Integer((i + 1) * 100 / size));
            }

            // load saved emulator state
            this.c64.deserialize(in);

            in.close();
            rs.closeRecordStore();
            progressForm.update(this, new Integer(100));
        } catch (Throwable t) {
            if (hasSuspendData) {
                status = STATUS_LOAD_FAILED;
                t.printStackTrace();
            } else {
                status = STATUS_NOTHING_LOADED;
            }
        } finally {
            if (hasSuspendData) {
                try {
                    rs.closeRecordStore();
                } catch (Exception e) {
                    // we could not close the record store, that hopefully does not hinder other actions on it and it was closed anyway...
                }
            }
            display.setCurrent(this.c64Canvas);
        }

        return status;
    }

    /**
     * Load and start a given emulator snapshot
     *
     * @param   name    name of the snapshot to load
     */
    private void runSnapshot(final String name) {
        final Display display = Display.getDisplay(this);
        final Thread loader = new Thread() {

            public void run() {
                switch (loadState(name)) {
                    case STATUS_LOAD_FAILED:
                        display.setCurrent(new Alert(LocalizationSupport.getMessage("LoadSnapshotFailed"), LocalizationSupport.getMessage("FailedToRestoreState"), null, AlertType.WARNING), c64Canvas);
                        break;
                    case STATUS_LOAD_OK:
                        break;
                }
                c64.resume();
            }
        };
        loader.start();
    }

    private void runGame(final String image, final Command c) {
        final Display display = Display.getDisplay(this);

        display.callSerially(
                new Runnable() {

                    public void run() {
                        try {
                            // attach the selected image
                            attachImage(c64.getActiveDrive(), image);

                            // offer a means to detach images
                            c64Canvas.addCommand(detachImageCommand);

                            // attach image and select program manually?
                            if (c == okCommand) {
                                // then show program list
                                showProgramList();
                                // attach and start first program?
                            } else if (c == autoStartCommand) {
                                // then issue command
                                c64.getKeyboard().textTyped("load \"*\",8,1");
                                c64.getKeyboard().keyTyped("ENTER");
                                runProgram();
                                // attach and start first program using fast-load?
                            } else if (c == fastAutoStartCommand) {
                                c64.fastLoadFile("*", -1);
                                runProgram();
                            }
                        } catch (Exception e) {
                            display.setCurrent(new Alert(LocalizationSupport.getMessage("FailedToLoad"), LocalizationSupport.getMessage("FailedToLoad") + ": " + image, null, AlertType.WARNING));
                            e.printStackTrace();
                        } finally {
                            c64.resume();
                        }
                    }
                });
    }

    /**
     * Load suspended emulator state.
     * The emulator needs to be in the paused state for this method to work correctly.
     */
    private void resume() {
        final Display display = Display.getDisplay(this);
        final String name = getAppProperty("MIDlet-Name") + SUSPENDDATA_SUFFIX;
        final Thread loader = new Thread() {

            public void run() {
                // try to load a saved state
                final int status = loadState(name);

                // first remove the suspend file, so that we don't load this old state again
                if (status == STATUS_LOAD_FAILED || status == STATUS_LOAD_OK) {
                    try {
                        RecordStore.deleteRecordStore(name);
                    } catch (Exception e) {
                        display.setCurrent(new Alert(LocalizationSupport.getMessage("CouldNotRemoveSuspendData"), LocalizationSupport.getMessage("FailedToRemoveSuspendData"), null, AlertType.WARNING));
                        e.printStackTrace();
                    }
                }

                // react according to the outcome of the attempt at loading an old state
                switch (status) {
                    case STATUS_LOAD_FAILED:
                        // show a message that resuming the game failed
                        display.setCurrent(new Alert(LocalizationSupport.getMessage("ResumeFailed"), LocalizationSupport.getMessage("FailedToRestoreState"), null, AlertType.WARNING), c64Canvas);
                        break;
                    case STATUS_LOAD_OK:
                        break;
                    default:
                        // nothing loaded, this happens when no hibernation file was written
                        ;
                }
            }
        };
        loader.start();
    }

    /**
     * Exit without saving
     */
    private void exit() {
        this.c64.stop();
        destroyApp(false);
        notifyDestroyed();
    }

    /**
     * Show a dialog that lets the user assign the device's buttons to joypad buttons
     */
    private void showAssignButtonsCanvas() {
        final Display display = Display.getDisplay(this);
        final Vector buttons = new Vector();

        for (int i = 0; i < JOYSTICK_KEYS.length; ++i) {
            buttons.addElement(JOYSTICK_KEYS[i]);
        }

        final ButtonAssignmentCanvas buttonAssignmentCanvas = new ButtonAssignmentCanvas(display, buttons) {

            /**
             * Assign the buttons and save them to settings when finishing the dialog
             */
            public void onFinished() {
                super.onFinished();

                if (getState() == Command.OK) {
                    c64Canvas.setButtonAssignments(getAssignments());
                    try {
                        for (int i = 0; i < buttons.size(); ++i) {
                            try {
                                settings.remove(SETTING_PREFIX_KEYS + buttons.elementAt(i).toString());
                            } catch (IllegalArgumentException e) {
                                // this happens if the key was not assigned, no problem
                            }
                        }
                        for (final Enumeration en = getAssignments().keys(); en.hasMoreElements();) {
                            final Integer key = (Integer) en.nextElement();

                            settings.setInteger(SETTING_PREFIX_KEYS + getAssignments().get(key).toString(), key.intValue());
                        }
                    } catch (Exception e) {
                        // we could not save the key settings, that's OK
                    }
                }
            }
        };

        display.setCurrent(buttonAssignmentCanvas);
    }

    /**
     * Show contents of the log
     */
    private void showLogForm() {
        final Form log = new Form(LocalizationSupport.getMessage("LogMessages"));
        final StringItem logItem = new StringItem("", (this.c64.getLogger()).dumpAll());

        logItem.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
        log.append(logItem);

        log.addCommand(backCommand);
        log.setCommandListener(this);
        Display.getDisplay(this).setCurrent(log);
    }

    /**
     * Offer a list of programs to start from the currently attached image
     */
    private void showProgramList() {
        this.c64.pause();

        // initialize list to select a program from an image
        final List programList = new List(LocalizationSupport.getMessage("SelectProgram"), List.IMPLICIT);

        programList.addCommand(backCommand);
        programList.addCommand(fastLoadRunProgramCommand);
        programList.addCommand(fastLoadProgramCommand);
        programList.addCommand(loadProgramCommand);
        programList.setSelectCommand(fastLoadProgramCommand);

        final Vector prgs = this.c64.getDrive(this.c64.getActiveDrive()).getFilenames();

        for (int i = 0; i < prgs.size(); ++i) {
            programList.append(prgs.elementAt(i).toString(), null);
        }

        final Display display = Display.getDisplay(this);

        programList.setCommandListener(
                new CommandListener() {

                    public void commandAction(Command c, Displayable d) {
                        display.setCurrent(c64Canvas);

                        if (c != backCommand && programList.getSelectedIndex() >= 0) {
                            final String program = programList.getString(programList.getSelectedIndex());

                            if (c == fastLoadRunProgramCommand) {
                                fastLoadSelectedProgram(program);
                                runProgram();
                                // a program has been selected for fast-loading?
                            } else if (c == fastLoadProgramCommand) {
                                fastLoadSelectedProgram(program);
                                // a program has been selected for normal loading?
                            } else if (c == loadProgramCommand) {
                                c64.loadFile(program);
                            }
                        }

                        c64.resume();
                    }
                });

        display.setCurrent(programList);
    }

    /**
     * Start a the selected program from the attached image
     */
    private void fastLoadSelectedProgram(final String program) {
        final Display display = Display.getDisplay(this);
        final Runnable loader = new Runnable() {

            public void run() {
                try {
                    c64.fastLoadFile(program, -1);
                    display.setCurrent(new Alert(LocalizationSupport.getMessage("ProgramLoaded"), program + " " + LocalizationSupport.getMessage("WasLoadedSuccessfully"), null, AlertType.CONFIRMATION));
                } catch (IOException e) {
                    if (c64.getLogger() != null) {
                        c64.getLogger().warning(LocalizationSupport.getMessage("ErrorWhileLoading") + "'" + program + "': " + e);
                    }
                    display.setCurrent(new Alert(LocalizationSupport.getMessage("FailedToLoadProgram"), LocalizationSupport.getMessage("FailedToLoadProgram") + ": " + program + "!", null, AlertType.WARNING));
                }
            }
        };

        display.setCurrent(this.c64Canvas);
        display.callSerially(loader);
    }

    /**
     * Read the list of available programs from the archive file programs.txt
     * 
     * @return  map containing the filenames and locations of the available C64 programs
     */
    private Hashtable readProgramListFromTextFile() throws IOException {
        final Hashtable result = new Hashtable();
        final InputStream is = getClass().getResourceAsStream("/programs/programs.txt");
        int c = 0;

        while (c >= 0) {
            // read a line from the text file
            final StringBuffer line = new StringBuffer();

            while ((c = is.read()) > 0 && c != '\n' && c != '\r') {
                line.append((char) c);
            }

            // we don't have a comment line?
            if (!line.toString().startsWith("#") && line.length() > 0) {
                // then this must be a program name to add to the programs list
                result.put(line.toString(), "/programs/" + line.toString());
            }
        }

        return result;
    }

    /**
     * Show form for typing a text
     */
    private void showTypeTextForm() {
        final Display display = Display.getDisplay(this);
        final Form typeTextForm = new Form(LocalizationSupport.getMessage("TypeText"));
        final TextField typeTextField = new TextField(LocalizationSupport.getMessage("Text"), "", 40, TextField.NON_PREDICTIVE);

        // initialize the form with the text box and buttons
        typeTextForm.append(typeTextField);
        typeTextForm.addCommand(backCommand);
        typeTextForm.addCommand(okCommand);
        typeTextForm.addCommand(okEnterCommand);

        // create a choice group for the command history if necessary
        final Vector typedTexts = this.c64.getKeyboard().getTypedTexts();

        if (typedTexts.size() > 0) {
            // create a ChoiceGroup with the history entries
            final String[] historyArray = new String[typedTexts.size()];

            typedTexts.copyInto(historyArray);

            final ChoiceGroup history = new ChoiceGroup(LocalizationSupport.getMessage("History"), ChoiceGroup.POPUP, historyArray, null);

            // add a listener for changes in the history selection
            typeTextForm.setItemStateListener(
                    new ItemStateListener() {
                        // copy text of the selected item into the text field

                        public void itemStateChanged(final Item item) {
                            if (history == item) {
                                typeTextField.setString(history.getString(history.getSelectedIndex()));
                            }
                        }
                    });

            // add the history to the form
            typeTextForm.insert(1, history);
        }

        typeTextForm.setCommandListener(
                new CommandListener() {

                    public void commandAction(Command c, Displayable d) {
                        if (c == okCommand) {
                            c64.getKeyboard().textTyped(typeTextField.getString());
                        } else if (c == okEnterCommand) {
                            c64.getKeyboard().textTyped(typeTextField.getString());
                            c64.getKeyboard().keyTyped("ENTER");
                        }
                        display.setCurrent(c64Canvas);
                    }
                });

        // activate the form
        display.setCurrent(typeTextForm);
    }

    /**
     * Show list of C64 images that can be loaded
     */
    private void showImageList() {
        this.c64.pause();

        final List imageList = new List(LocalizationSupport.getMessage("SelectProgram"), List.IMPLICIT);

        imageList.addCommand(this.fastAutoStartCommand);
        imageList.addCommand(this.autoStartCommand);
        imageList.addCommand(this.okCommand);
        imageList.addCommand(this.backCommand);
        imageList.addCommand(this.removeCommand);
        imageList.setSelectCommand(this.okCommand);

        // sort program names
        final Vector listItems = new Vector();

        for (final Enumeration en = this.programs.keys(); en.hasMoreElements();) {
            final String program = en.nextElement().toString();
            int p = 0;
            final int n = listItems.size();

            while (p < n && program.compareTo(listItems.elementAt(p).toString()) > 0) {
                ++p;
            }
            listItems.insertElementAt(program, p);
        }

        // add program names plus associated snapshots to listbox
        final Vector snapshots = getSnapshots();
        final Vector noImageSnapshots = getSnapshots(NO_IMAGE, snapshots);

        for (int j = 0, to = noImageSnapshots.size(); j < to; ++j) {
            imageList.append(noImageSnapshots.elementAt(j).toString(), snapshotImage);
        }
        for (int i = 0; i < listItems.size(); ++i) {
            final String name = listItems.elementAt(i).toString();

            imageList.append(name, floppyImage);

            final Vector programSnapshots = getSnapshots(name, snapshots);

            for (int j = 0, to = programSnapshots.size(); j < to; ++j) {
                imageList.append(programSnapshots.elementAt(j).toString(), snapshotImage);
            }
        }

        final Display display = Display.getDisplay(this);

        imageList.setCommandListener(
                new CommandListener() {

                    public void commandAction(final Command c, final Displayable d) {
                        display.setCurrent(c64Canvas);

                        // retrieve name of the selected program or snapshot
                        final String image = imageList.getString(imageList.getSelectedIndex());

                        // the entry was selected for running it?
                        if (c == okCommand || c == autoStartCommand || c == fastAutoStartCommand) {
                            // load game or snapshot and start the emulation
                            if (programs.containsKey(image)) {
                                runGame(image, c);
                            } else {
                                runSnapshot(image);
                            }
                            // the entry was selected for removal?
                        } else if (c == removeCommand) {
                            // a program was selected?
                            if (programs.containsKey(image)) {
                                // this is not allowed
                                display.setCurrent(new Alert(LocalizationSupport.getMessage("NotASnapshot"), LocalizationSupport.getMessage("OnlySnapshotsCanBeRemoved"), null, AlertType.INFO), c64Canvas);
                            } else {
                                try {
                                    // remove the snapshot from the device...
                                    RecordStore.deleteRecordStore(image);

                                    // ...and also from the list of snapshots
                                    final Vector snapshots = getSnapshots();

                                    snapshots.removeElement(image);
                                    setSnapshots(snapshots);

                                    // inform the user about the successful removal
                                    display.setCurrent(new Alert(LocalizationSupport.getMessage("RemovedSnapshotData"), LocalizationSupport.getMessage("RemovedSnapshotDataFor") + " " + image, null, AlertType.INFO), c64Canvas);
                                } catch (Exception e) {
                                    display.setCurrent(new Alert(LocalizationSupport.getMessage("CouldNotRemoveSnapshotData"), LocalizationSupport.getMessage("FailedToRemoveSnapshotDataFor") + " " + image, null, AlertType.WARNING), c64Canvas);
                                }
                            }
                        } else {
                            c64.resume();
                        }
                    }
                });

        // show listbox
        display.setCurrent(imageList);
    }

    /**
     * Attach an image to a C64 drive
     * 
     * @param driveNo   drive to attach the file to
     * @param program   image file to attach
     * @throws java.io.IOException  if the image could not be loaded
     */
    private synchronized void attachImage(final int driveNo, final String program) throws IOException {
        // determine image file and whether we have to attach a delta file
        final String selectedImage = this.programs.get(program).toString();
        String image = this.programs.get(program).toString();

        if (!image.equals(this.attachedImages.get(new Integer(driveNo)))) {
            final boolean applyDelta = image.endsWith(FileSystemHandler.DELTA_FILE_EXTENSION);

            if (applyDelta) {
                image = image.substring(0, image.length() - 4);
            }

            // load the image, file URLs are opened using a Connector, the rest is loaded from the jar archive
            this.c64.getDrive(driveNo).attachImage(image.indexOf("://") > 0 ? Connector.openInputStream(image) : getClass().getResourceAsStream(image), image);

            // now apply delta file if it was selected
            if (applyDelta) {
                this.c64.getDrive(driveNo).getDriveHandler().applyDelta(Connector.openInputStream(selectedImage));
                this.c64.getLogger().info(LocalizationSupport.getMessage("AppliedDelta") + " '" + selectedImage + "'");
            }

            // store the file in the list of attached images
            this.attachedImages.put(new Integer(driveNo), program);
        }
    }

    /**
     * Detach all images from all drives
     */
    private void detachImages() {
        for (int i = 0; i < this.c64.getDriveCount(); ++i) {
            c64.getDrive(i).detachImage();
        }
        this.attachedImages.clear();
    }

    /**
     * Switch the sound on/off
     *
     * @param   active  true to switch the sound on, false to switch it off
     */
    protected void setSound(final boolean active) {
        if (active) {
            if (this.c64.getSID().countObservers() == 0) {
                // first try to initialize the PCMtoMIDIPlayer
                try {
                    this.c64.getSID().addObserver(new PCMtoMIDIPlayer(this.c64.getSID()));
                } catch (Throwable t) {
                    // if that does not work we try the WavePlayer
                    try {
                        this.c64.getSID().addObserver(new WavePlayer(this.c64.getSID()));
                    } catch (Throwable t2) {
                        // we could not add a player, that's OK
                        this.c64.getLogger().warning(LocalizationSupport.getMessage("CouldNotCreateSoundPlayer"));
                        t2.printStackTrace();
                    }
                }
            }
        } else {
            if (this.c64.getSID().countObservers() > 0) {
                notifySoundPlayer(PCMtoMIDIPlayer.SIGNAL_STOP);
                this.c64.getSID().deleteObservers();
            }
        }
        try {
            this.settings.setBoolean(SETTING_SOUNDACTIVE, active);
        } catch (Exception e) {
            // we couldn't store the setting, that's OK
        }
    }

    /**
     * Show list of available drives
     */
    private void showDriveList() {
        this.c64.pause();

        // create listbox
        final List driveList = new List(LocalizationSupport.getMessage("SelectDrive"), List.IMPLICIT);

        driveList.addCommand(okCommand);
        driveList.addCommand(backCommand);
        driveList.setSelectCommand(okCommand);

        // add drives to listbox
        for (int i = 0; i < this.c64.getDriveCount(); ++i) {
            driveList.append(LocalizationSupport.getMessage("Drive") + " " + i + " (#" + (i + 8) + ")", null);
        }

        // add command handler that reacts on the selection
        final Display display = Display.getDisplay(this);

        driveList.setCommandListener(
                new CommandListener() {

                    public void commandAction(
                            Command c, Displayable d) {
                        display.setCurrent(c64Canvas);

                        // on OK we set the selected drive as active
                        if (c == okCommand) {
                            c64.setActiveDrive(driveList.getSelectedIndex());
                        }

                        c64.resume();
                    }
                });

        // show listbox
        display.setCurrent(driveList);
    }

    /**
     * Show the possible selections for the file system search start directory
     */
    private void showSelectDirectoryForm() {
        final Display display = Display.getDisplay(this);
        final String defaultDir = this.settings.getString(SETTING_FILESEARCH_STARTDIR, "");
        final Vector filters = new Vector();

        filters.addElement("/");

        final FileBrowser fileBrowser = new FileBrowser(
                display, defaultDir,
                filters) {

            /**
             * We start the file search when a directory was selected
             */
            public void onSelect() {
                super.onSelect();
                // save the new default directory
                fileSearchStartDir = getSelectedFile();
                try {
                    settings.setString(SETTING_FILESEARCH_STARTDIR, fileSearchStartDir);
                } catch (Exception e) {
                    // we couldn't save the settings, that's OK
                }
                // search for files in this directory and return to the main form
                fileSystemHandler.readProgramListFromFileSystem(fileSearchStartDir, programs, display);
                display.setCurrent(c64Canvas);
            }

            /**
             * We remove the previous start directory, so that the root directory is used on the next attempt
             */
            public void onError(final Throwable t) {
                super.onError(t);
                try {
                    settings.remove(SETTING_FILESEARCH_STARTDIR);
                } catch (Exception e) {
                    // we couldn't save the settings, that's OK
                }
            }
        };

        fileBrowser.show();
    }

    /**
     * Show a form with the emulator settings
     */
    private void showSettingsForm() {
        final Form settingsForm = new Form(LocalizationSupport.getMessage("EmulatorSettings"));
        // show joystick settings
        final String[] ports = {LocalizationSupport.getMessage("Port") + " 1", LocalizationSupport.getMessage("Port") + " 2"};
        final ChoiceGroup portsChoice = new ChoiceGroup(LocalizationSupport.getMessage("JoystickPort"), ChoiceGroup.EXCLUSIVE, ports, null);

        portsChoice.setSelectedIndex(this.c64.getActiveJoystick(), true);
        settingsForm.append(portsChoice);

        final String[] pointerOptions = {LocalizationSupport.getMessage("NoPointer"), LocalizationSupport.getMessage("PointerAsJoystickButton"), LocalizationSupport.getMessage("VirtualJoystick"), LocalizationSupport.getMessage("PointerForStickMovement")};
        final ChoiceGroup pointerOptionsChoice = new ChoiceGroup(LocalizationSupport.getMessage("PointerUsage"), ChoiceGroup.EXCLUSIVE, pointerOptions, null);

        pointerOptionsChoice.setSelectedIndex(this.c64Canvas.getPointerUsage(), true);
        if (c64Canvas.hasPointerEvents() || c64Canvas.hasPointerMotionEvents()) {
            settingsForm.append(pointerOptionsChoice);
        }

        // show sound settings
        settingsForm.append(new Spacer(0, 2));

        final String[] sound = {LocalizationSupport.getMessage("On"), LocalizationSupport.getMessage("Off")};
        final ChoiceGroup soundChoice = new ChoiceGroup(LocalizationSupport.getMessage("Sound"), ChoiceGroup.EXCLUSIVE, sound, null);

        soundChoice.setSelectedIndex(this.c64.getSID().countObservers() > 0 ? 0 : 1, true);
        settingsForm.append(soundChoice);

        // show screen settings
        settingsForm.append(new Spacer(0, 2));

        final String[] videoOptions = {LocalizationSupport.getMessage("SmoothScaling")};
        final ChoiceGroup videoChoice = new ChoiceGroup(LocalizationSupport.getMessage("VideoOptions"), ChoiceGroup.MULTIPLE, videoOptions, null);
        final boolean isSmoothScaling = this.c64.getVIC() instanceof SmoothingScalableVIC6569;

        videoChoice.setSelectedIndex(0, isSmoothScaling);
        settingsForm.append(videoChoice);

        final String[] frameSkipOptions = {LocalizationSupport.getMessage("Automatic"), "1", "2", "3", "4"};
        final ChoiceGroup frameSkipChoice = new ChoiceGroup(LocalizationSupport.getMessage("ShowEveryNthFrame"), ChoiceGroup.EXCLUSIVE, frameSkipOptions, null);
        final int frameSkip = this.c64.doAutoAdjustFrameskip() ? 0 : Math.min(this.c64.getVIC().getFrameSkip(), 4);

        frameSkipChoice.setSelectedIndex(frameSkip, true);
        settingsForm.append(frameSkipChoice);

        // show miscellaneous settings
        final String[] driveModes = {LocalizationSupport.getMessage("Fast"), LocalizationSupport.getMessage("Balanced"), LocalizationSupport.getMessage("Compatible")};
        final ChoiceGroup driveModeChoice = new ChoiceGroup(LocalizationSupport.getMessage("C1541Mode"), ChoiceGroup.EXCLUSIVE, driveModes, null);
        final int level = this.c64.getDrive(0).getEmulationLevel();

        driveModeChoice.setSelectedIndex(level >= C1541.COMPATIBLE_EMULATION ? 2 : level >= C1541.BALANCED_EMULATION ? 1 : 0, true);
        settingsForm.append(driveModeChoice);

        final String[] keyboardTypes = {LocalizationSupport.getMessage("PhoneKeyboard"), LocalizationSupport.getMessage("FullKeyboard")};
        final ChoiceGroup keyboardChoice = new ChoiceGroup(LocalizationSupport.getMessage("KeyboardType"), ChoiceGroup.EXCLUSIVE, keyboardTypes, null);

        keyboardChoice.setSelectedIndex(c64Canvas.isPhoneKeyboard ? 0 : 1, true);
        settingsForm.append(keyboardChoice);

        final ChoiceGroup languageChoice = new ChoiceGroup(LocalizationSupport.getMessage("Language"), ChoiceGroup.EXCLUSIVE, SUPPORTED_LOCALES, null);
        final String activeLanguage = this.settings.getString(SETTING_LANGUAGE, SUPPORTED_LOCALES[0]);
        int activeLanguageIndex = 0;

        for (int i = 0; i < SUPPORTED_LOCALES.length; ++i) {
            if (activeLanguage.equals(SUPPORTED_LOCALES[i])) {
                activeLanguageIndex = i;
                break;
            }
        }
        languageChoice.setSelectedIndex(activeLanguageIndex, true);
        settingsForm.append(languageChoice);

        final String[] accelerometerOptions = {LocalizationSupport.getMessage("ForJoystickEmulation"), LocalizationSupport.getMessage("AutoRotateScreen")};
        final ChoiceGroup accelerometerChoice = new ChoiceGroup(LocalizationSupport.getMessage("UseAccelerometer"), ChoiceGroup.MULTIPLE, accelerometerOptions, null);
        final boolean isUseAccelerometer = c64Canvas.isUseAccelerometer();
        final boolean isRotateScreen = c64Canvas.isAutoChangeOrientation();

        accelerometerChoice.setSelectedIndex(0, isUseAccelerometer);
        accelerometerChoice.setSelectedIndex(1, isRotateScreen);
        try {
            if (de.joergjahnke.common.jme.OrientationSensitiveCanvasHelper.supportsAccelerometer()) {
                settingsForm.append(new Spacer(0, 2));

                settingsForm.append(accelerometerChoice);
            }
        } catch (Throwable t) {
            // the API might not be supported and then this code might fail, that's no problem in that case
        }

        // add OK and Cancel button
        final Display display = Display.getDisplay(this);

        settingsForm.addCommand(okCommand);
        settingsForm.addCommand(backCommand);
        settingsForm.setCommandListener(
                new CommandListener() {

                    public void commandAction(Command c, Displayable d) {
                        boolean isRestartRequired = false;

                        if (c == okCommand) {
                            // apply joystick settings
                            c64.setActiveJoystick(portsChoice.getSelectedIndex());
                            c64Canvas.setPointerUsage(pointerOptionsChoice.getSelectedIndex());
                            // apply sound settings
                            setSound(soundChoice.getSelectedIndex() == 0);
                            // apply video settings
                            if (frameSkipChoice.getSelectedIndex() == 0) {
                                c64.setDoAutoAdjustFrameskip(true);
                            } else {
                                c64.setDoAutoAdjustFrameskip(false);
                                c64.getVIC().setFrameSkip(frameSkipChoice.getSelectedIndex());
                            }
                            // apply miscellaneous settings
                            c64Canvas.setUseAccelerometer(accelerometerChoice.isSelected(0));
                            c64Canvas.setAutoChangeOrientation(accelerometerChoice.isSelected(1));
                            c64Canvas.isPhoneKeyboard = keyboardChoice.getSelectedIndex() == 0;

                            final int level = driveModeChoice.getSelectedIndex() == 0 ? C1541.FAST_EMULATION : driveModeChoice.getSelectedIndex() == 2 ? C1541.COMPATIBLE_EMULATION : C1541.BALANCED_EMULATION;

                            for (int i = 0; i < c64.getDriveCount(); ++i) {
                                c64.getDrive(i).setEmulationLevel(level);
                            }

                            // save settings
                            try {
                                settings.setBoolean(C64Canvas.SETTING_SMOOTH_SCALING, videoChoice.isSelected(0));
                                settings.setInteger(C64Canvas.SETTING_FRAMESKIP, frameSkipChoice.getSelectedIndex());
                                settings.setBoolean(C64Canvas.SETTING_PHONE_KEYBOARD, c64Canvas.isPhoneKeyboard);
                                settings.setInteger(C64Canvas.SETTING_DRIVEMODE, level);
                                settings.setBoolean(SETTING_ACCELEROMETER, c64Canvas.isUseAccelerometer());
                                settings.setBoolean(SETTING_AUTO_ROTATE, c64Canvas.isAutoChangeOrientation());

                                final String newLanguage = SUPPORTED_LOCALES[languageChoice.getSelectedIndex()];

                                isRestartRequired |= !activeLanguage.equals(newLanguage);
                                if (languageChoice.getSelectedIndex() == 0) {
                                    settings.remove(SETTING_LANGUAGE);
                                } else {
                                    settings.setString(SETTING_LANGUAGE, newLanguage);
                                }
                            } catch (Exception e) {
                                // we couldn't save the settings, that's OK
                            }

                            // recalculate screen settings and size if required
                            if (videoChoice.isSelected(0) != isSmoothScaling) {
                                c64Canvas.calculateScreenSize();
                            }
                        }
                        display.setCurrent(c64Canvas);

                        // some settings might require a restart of the emulator, we tell the user about this
                        if (isRestartRequired) {
                            display.callSerially(
                                    new Runnable() {

                                        public void run() {
                                            display.setCurrent(new Alert(LocalizationSupport.getMessage("RestartRequired"), LocalizationSupport.getMessage("SomeSettingsRequireRestart"), null, AlertType.INFO));
                                        }
                                    });
                        }
                    }
                });

        display.setCurrent(settingsForm);
    }

    /**
     * Shows the form to select one of the special C64 keys
     */
    private void showSpecialKeysForm() {
        final Display display = Display.getDisplay(this);
        final String[] specialKeys = {"Escape", "Run", "F1", "F3", "F5", "F7", "Space", "Enter", "Delete", "Break", "Commodore", "Pound", "Cursor Up", "Cursor Down", "Cursor Left", "Cursor Right"};
        final List specialKeyList = new List(LocalizationSupport.getMessage("SelectKey"), Choice.IMPLICIT, specialKeys, null);

        specialKeyList.addCommand(okCommand);
        specialKeyList.addCommand(backCommand);
        specialKeyList.setSelectCommand(okCommand);
        specialKeyList.setCommandListener(
                new CommandListener() {

                    public void commandAction(
                            Command c, Displayable d) {
                        display.setCurrent(c64Canvas);
                        if (c == okCommand) {
                            final String key = specialKeyList.getString(specialKeyList.getSelectedIndex());

                            display.callSerially(
                                    new Runnable() {

                                        public void run() {
                                            c64.getKeyboard().keyTyped(key.toUpperCase());
                                        }
                                    });
                        }
                    }
                });

        display.setCurrent(specialKeyList);
    }

    /**
     * Show an about message form
     */
    private void showAboutForm() {
        final Form about = new Form(LocalizationSupport.getMessage("About"));
        // create a StringItem that calls the project url in a browser when clicked
        final StringItem projectUrl = new StringItem(null, PROJECT_PAGE_URL, Item.HYPERLINK);

        projectUrl.addCommand(this.browseCommand);
        projectUrl.setItemCommandListener(new ItemCommandListener() {

            public void commandAction(final Command c, final Item item) {
                try {
                    platformRequest(((StringItem) item).getText());
                } catch (Exception e) {
                    // we could not invoke the browser, that's a pity but we can live with it
                }
            }
        });

        // get the About box text
        String text = LocalizationSupport.getMessage("AboutText1") + getAppProperty("MIDlet-Version") + LocalizationSupport.getMessage("AboutText2") + LocalizationSupport.getMessage("AboutText3") + LocalizationSupport.getMessage("AboutText4");
        // replace the project page place holder with the project url
        final String pageStr = "#PROJECTPAGE#";
        final int index1 = text.indexOf(pageStr);

        about.append(text.substring(0, index1));
        about.append(projectUrl);
        about.append(text.substring(index1 + pageStr.length(), text.length()));
        about.addCommand(this.backCommand);
        about.setCommandListener(this);

        // display the created form
        final Display display = Display.getDisplay(this);

        display.setCurrent(about);
    }

    /**
     * Show help content form
     */
    private void showHelpForm() {
        final InputStream helpContent = LocalizationSupport.loadLocalizedFile("/docs/help.txt", getLocale());

        try {
            final Form helpForm = new FormattedTextForm(this, LocalizationSupport.getMessage("Help"), helpContent);

            helpForm.addCommand(backCommand);
            helpForm.setCommandListener(this);

            final Display display = Display.getDisplay(this);

            display.setCurrent(helpForm);
        } catch (Exception e) {
            Display.getDisplay(this).setCurrent(new Alert(LocalizationSupport.getMessage("FailedToLoadHelp"), LocalizationSupport.getMessage("FailedToLoadHelpFile"), null, AlertType.WARNING));
            e.printStackTrace();
        }
    }
}
