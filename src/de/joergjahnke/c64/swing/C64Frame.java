/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.swing;

import de.joergjahnke.common.util.Observer;
import de.joergjahnke.c64.core.C64;
import de.joergjahnke.c64.core.C1541;
import de.joergjahnke.c64.drive.DriveHandler;
import de.joergjahnke.c64.extendeddevices.EmulatorUtils;
import de.joergjahnke.common.extendeddevices.WavePlayer;
import de.joergjahnke.common.vmabstraction.sunvm.SunVMResourceLoader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.DateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/**
 * Main window of the C64 emulator
 *
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class C64Frame extends JFrame implements Observer {

    /**
     * Current program version
     */
    private final static String VERSION = "1.10.2";
    /**
     * URL of the online help page
     */
    private final static String URL_ONLINE_HELP = "http://sourceforge.net/apps/mediawiki/jmec64/index.php?title=JSwing_C64_Online_Help";
    /**
     * URL of the project's main web page
     */
    private final static String PROJECT_PAGE_URL = "https://sourceforge.net/projects/jmec64/";
    /**
     * file extension we use for saved games
     */
    private static final String SAVE_EXTENSION = ".sav";
    /**
     * file extension we use for snapshot files
     */
    private static final String SNAPSHOT_EXTENSION = ".snapshot";
    /**
     * status code when loading a saved emulator state was not necessary
     */
    private static final int STATUS_NOTHING_LOADED = 0;
    /**
     * status code when loading a saved emulator state succeeded
     */
    private static final int STATUS_LOAD_OK = 1;
    /**
     * status code when loading a saved emulator state failed
     */
    private static final int STATUS_LOAD_FAILED = 2;
    /**
     * snapshot prefix when no image is attached
     */
    private static final String NO_IMAGE = "no_image.c64";
    /**
     * Name of the settings file
     */
    private final static String PROPERTIES_NAME = "JSwingC64.properties.xml";
    /**
     * Name of the suspend file
     */
    private final static String SUSPENDFILE_NAME = "JSwingC64.suspend";
    /**
     * Name of the program icon file
     */
    private final static String ICON_NAME = "/res/jme/J64_sm.png";
    /**
     * Setting for window scaling
     */
    private final static String SETTING_WINDOW_SCALING = "WindowScaling";
    /**
     * Setting for how the mouse is used for joystick emulation
     */
    private final static String SETTING_MOUSE_USAGE = "MouseUsage";
    /**
     * Setting for the joystick port
     */
    private final static String SETTING_JOYSTICK_PORT = "JoystickPort";
    /**
     * Setting for the directory of the last loaded image
     */
    private final static String SETTING_IMAGE_DIRECTORY = "ImageDirectory";
    /**
     * Setting for drive mode
     */
    private final static String SETTING_DRIVE_MODE = "DriveMode";
    /**
     * Setting for frame-skip
     */
    private final static String SETTING_FRAMESKIP = "FrameSkip";
    /**
     * number of milliseconds after which we clear the drive state LED
     */
    private final static int CLEAR_DRIVE_STATE_TIME = 1000;
    /**
     * main canvas
     */
    private final C64Canvas canvas;
    /**
     * C64 instance
     */
    private C64 c64 = null;
    /**
     * last selected file, used to point to the same directory as before with the file dialog
     */
    private File lastFile = null;
    /**
     * timer we install to display status bar messages only for a short time
     */
    private Timer statusMessageTimer = null;
    /**
     * timer we install to reset the drive state icon after a short period of time
     */
    private Timer driveStateTimer = null;
    private TimerTask driveInactiveTask = null;
    /**
     * current drive state
     */
    private Integer currentDriveState = null;
    /**
     * emulator settings
     */
    private final Properties settings = new Properties();
    /**
     * automatically activate the turbo mode on drive access?
     */
    private boolean automaticTurboMode = true;
    /**
     * images from the mobile device attached to the drives
     */
    private final Hashtable attachedImages = new Hashtable();
    /**
     * localized string resources specific for the C64 emulator
     */
    private final ResourceBundle c64Resources = ResourceBundle.getBundle("res/l10n/c64EmulatorMessages");
    /**
     * common localized string resources
     */
    private final ResourceBundle commonResources = ResourceBundle.getBundle("res/l10n/commonMessages");

    /**
     * Creates new C64Frame
     */
    public C64Frame() {
        // use the system L&F if we can
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // we continue without setting the UI style
        }

        // load settings, if available
        try {
            this.settings.loadFromXML(new FileInputStream(new File(PROPERTIES_NAME)));
        } catch (Exception e) {
            // we could not load settings, that's OK, we just use the defaults
        }

        // initialize menus and other window components
        initComponents();

        // add menu items for selecting the drives
        this.jCheckBoxMenuItemSelectedDrives = new JRadioButtonMenuItem[C64.MAX_NUM_DRIVES];
        for (int i = 0; i < C64.MAX_NUM_DRIVES; ++i) {
            this.jCheckBoxMenuItemSelectedDrives[i] = new javax.swing.JRadioButtonMenuItem(i + " (#" + (i + 8) + ")");

            this.jCheckBoxMenuItemSelectedDrives[i].setSelected(i == 0);
            this.jCheckBoxMenuItemSelectedDrives[i].addItemListener(new java.awt.event.ItemListener() {

                @Override
                public void itemStateChanged(java.awt.event.ItemEvent evt) {
                    jCheckBoxMenuItemSelectDriveItemStateChanged(evt);
                }
            });
            this.jMenuSelectDrive.add(this.jCheckBoxMenuItemSelectedDrives[i]);
        }

        // set the window icon
        try {
            setIconImage(getToolkit().getImage(getClass().getResource(ICON_NAME)));
        } catch (SecurityException e) {
            // we can work without the icon having been set
        }

        // create a C64Canvas for displaying the emulator
        this.canvas = new C64Canvas();

        try {
            // create C64 instance and inform the canvas about this instance
            this.c64 = new C64(new SunVMResourceLoader());
            this.canvas.setC64(c64);

            // get frameskip value from setting
            final int frameskip = Integer.parseInt(this.settings.getProperty(SETTING_FRAMESKIP, "0"));

            switch (frameskip) {
                case 1:
                    jMenuItemFrameSkip1ActionPerformed(null);
                    break;
                case 2:
                    jMenuItemFrameSkip2ActionPerformed(null);
                    break;
                case 3:
                    jMenuItemFrameSkip3ActionPerformed(null);
                    break;
                case 4:
                    jMenuItemFrameSkip4ActionPerformed(null);
                    break;
                default:
                    jMenuItemFrameSkipAutoActionPerformed(null);
            }

            // create a player that observes the SID and plays its sound
            this.c64.getSID().addObserver(new WavePlayer(this.c64.getSID()));

            // add canvas to this window and resize accordingly
            getContentPane().add(this.canvas, BorderLayout.CENTER);

            // apply size from settings
            final int scaling = Integer.parseInt(this.settings.getProperty(SETTING_WINDOW_SCALING, "1"));

            switch (scaling) {
                case 1:
                    jMenuItemSizeX1ActionPerformed(null);
                    break;
                case 2:
                    jMenuItemSizeX2ActionPerformed(null);
                    break;
                case 3:
                    jMenuItemSizeX3ActionPerformed(null);
                    break;
            }

            // apply drive mode from settings
            final int level = Integer.parseInt(this.settings.getProperty(SETTING_DRIVE_MODE, Integer.toString(C1541.BALANCED_EMULATION)));

            switch (level) {
                case C1541.FAST_EMULATION:
                    jMenuItemFastC1541ActionPerformed(null);
                    break;
                case C1541.BALANCED_EMULATION:
                    jMenuItemBalancedC1541ActionPerformed(null);
                    break;
                case C1541.COMPATIBLE_EMULATION:
                    jMenuItemCompatibleC1541ActionPerformed(null);
                    break;
            }

            // center the window of the screen
            final Dimension d = Toolkit.getDefaultToolkit().getScreenSize();

            setLocation((d.width - getSize().width) / 2, (d.height - getSize().height) / 2);
            setResizable(false);

            // we register as observer for the C64's logger instance to show log messages in the status bar
            this.c64.getLogger().addObserver(this);
            // also register as observer to get informed about drive operations
            for (int i = 0; i < C64.MAX_NUM_DRIVES; ++i) {
                this.c64.getDrive(i).addObserver(this);
            }
            this.c64.addObserver(this);

            // we try to resume a previous session
            resume();

            // start the emulation
            new Thread(this.c64).start();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(this.commonResources.getString("CouldNotInitialize") + e);
        }

        // install a window listener which saves the settings on exit
        addWindowListener(
                new WindowAdapter() {

                    @Override
                    public void windowClosing(WindowEvent evt) {
                        try {
                            settings.storeToXML(new FileOutputStream(new File(PROPERTIES_NAME)), "Properties for JSwingC64");
                            c64.stop();
                        } catch (Exception e) {
                            // we can't save the settings, that's a pity, but we'll just use defaults on next startup
                        }
                    }
                });

        // apply some settings
        switch (Integer.parseInt(this.settings.getProperty(SETTING_MOUSE_USAGE, Integer.toString(C64Canvas.MOUSE_AS_FIRE_BUTTON)))) {
            case C64Canvas.MOUSE_FOR_VIRTUAL_JOYSTICK:
                this.jRadioButtonMenuItemVirtualJoystickActionPerformed(null);
                break;
            case C64Canvas.MOUSE_AS_FIRE_BUTTON:
                this.jRadioButtonMenuItemMouseFireButtonEmulationActionPerformed(null);
                break;
            case C64Canvas.MOUSE_NO_USAGE:
                this.jRadioButtonMenuItemNoMouseJoystickEmulationActionPerformed(null);
                break;
        }
        switch (Integer.parseInt(this.settings.getProperty(SETTING_JOYSTICK_PORT, "0"))) {
            case 0:
                this.jRadioButtonMenuItemJoystickPort1ActionPerformed(null);
                break;
            case 1:
                this.jRadioButtonMenuItemJoystickPort2ActionPerformed(null);
                break;
        }
    }

    /**
     * Attach an image to a given drive
     * 
     * @param driveNo   drive to attach the image to
     * @param program   image to attach
     */
    private void attachImage(final int driveNo, final String program) {
        // in case of a delta file, first load the original file
        try {
            this.lastFile = EmulatorUtils.attachImage(this.c64, c64.getActiveDrive(), program);
            this.attachedImages.put(new Integer(driveNo), program);

            // enable menu items for loading files from the image
            this.jMenuLoadProgram.setEnabled(true);
            // now the user might want to detach an image
            this.jMenuItemDetachImages.setEnabled(true);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, this.c64Resources.getString("CouldNotLoadFile") + ": " + program + "!\n" + e, this.c64Resources.getString("CouldNotLoadFile"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Detach all images from all drives
     */
    private void detachImages() {
        // detach images from all drives
        for (int i = 0; i < C64.MAX_NUM_DRIVES; ++i) {
            c64.getDrive(i).detachImage();
        }
        this.attachedImages.clear();

        // disable menu items for loading files from the image
        this.jMenuLoadProgram.setEnabled(false);
        // now the user might want to detach an image
        this.jMenuItemDetachImages.setEnabled(false);
    }

    private void runProgram() {
        this.c64.getKeyboard().textTyped("run");
        this.c64.getKeyboard().keyTyped("ENTER");
    }

    /**
     * Let the user select a C64 disk/tape image
     *
     * @return  true if an image was selected
     */
    private boolean selectImage() {
        // show a dialog to select the C64 file to attach
        final JFileChooser fileChooser = new JFileChooser() {

            @Override
            public boolean accept(File f) {
                return f.getName().toLowerCase().endsWith(".d64") || f.getName().toLowerCase().endsWith(".t64") || f.getName().toLowerCase().endsWith(".prg") || f.getName().toLowerCase().endsWith(".p00") || f.getName().toLowerCase().endsWith(EmulatorUtils.DELTA_FILE_EXTENSION) || f.getName().toLowerCase().endsWith(SNAPSHOT_EXTENSION) || f.isDirectory();
            }
        };
        if (this.lastFile != null) {
            fileChooser.setCurrentDirectory(this.lastFile.getParentFile());
        } else if (this.settings.getProperty(SETTING_IMAGE_DIRECTORY) != null) {
            fileChooser.setCurrentDirectory(new File(this.settings.getProperty(SETTING_IMAGE_DIRECTORY)));
        }
        // a file was selected?
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            final String filename = fileChooser.getSelectedFile().getAbsolutePath();

            if (filename.endsWith(SNAPSHOT_EXTENSION)) {
                runSnapshot(filename);
            } else {
                attachImage(this.c64.getActiveDrive(), fileChooser.getSelectedFile().getAbsolutePath());
            }
            this.settings.setProperty(SETTING_IMAGE_DIRECTORY, this.lastFile.getParentFile().getAbsolutePath());

            return true;
        } else {
            return false;
        }
    }

    /**
     * Save the emulator state to a given file
     *
     * @param   filename    name of the file to save the state in
     */
    private boolean saveState(final String filename) {
        this.c64.pause();

        final File suspend = new File(filename);
        DataOutputStream out = null;
        boolean wasSuccessful = false;

        try {
            out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(suspend)));

            // save the list of attached images
            out.writeInt(this.attachedImages.size());
            for (final Enumeration en = this.attachedImages.keys(); en.hasMoreElements();) {
                final Integer driveNo = (Integer) en.nextElement();

                out.writeInt(driveNo.intValue());
                out.writeUTF(this.attachedImages.get(driveNo).toString());
            }

            // save current emulator state
            this.c64.serialize(out);

            out.close();
            wasSuccessful = true;
        } catch (Throwable t) {
            // show the cause of the error
            JOptionPane.showMessageDialog(this, this.commonResources.getString("FailedToStoreState") + "\n" + t, this.commonResources.getString("SuspendFailed"), JOptionPane.WARNING_MESSAGE);
            t.printStackTrace();
            // delete the suspend file
            try {
                out.close();
            } catch (Exception e) {
            }
            suspend.delete();
            this.c64.resume();
        }

        return wasSuccessful;
    }

    /**
     * Save the emulator state to a file and exit
     */
    private void suspend() {
        final boolean wasSuspended = saveState(SUSPENDFILE_NAME);

        if (wasSuspended) {
            setVisible(false);
            System.exit(0);
        } else {
            JOptionPane.showMessageDialog(this, this.commonResources.getString("FailedToStoreState"), this.commonResources.getString("SuspendFailed"), JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Save a snapshot and then continue with the game
     */
    private void saveSnapshot() {
        // generate name of file to save
        final DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
        String filename = null == this.lastFile ? NO_IMAGE : this.lastFile.getAbsolutePath();

        filename = filename.substring(0, filename.lastIndexOf('.'));
        filename += " ";
        filename += format.format(new Date()).replaceAll("\\:", "");
        filename += SNAPSHOT_EXTENSION;

        // save snapshot
        final boolean wasSaved = saveState(filename);

        // continue with the game or show an error, depending on whether saving succeeded
        if (wasSaved) {
            this.c64.resume();
        } else {
            JOptionPane.showMessageDialog(this, this.commonResources.getString("FailedToStoreState"), this.commonResources.getString("SavingSnapshotFailed"), JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Load saved emulator state
     *
     * @param   filename    name of the file with the saved state
     * @return  status of the action
     */
    private int loadState(final String filename) {
        this.c64.pause();

        boolean hasSuspendData = false;
        int status = STATUS_NOTHING_LOADED;
        final File suspend = new File(filename);
        DataInputStream in = null;

        try {
            in = new DataInputStream(new BufferedInputStream(new FileInputStream(suspend)));

            hasSuspendData = true;

            // attached the previously attached images
            final int size = in.readInt();

            for (int i = 0; i < size; ++i) {
                final int driveNo = in.readInt();
                final String program = in.readUTF();

                attachImage(driveNo, program);
            }

            // load the emulator state
            this.c64.deserialize(in);

            status = STATUS_LOAD_OK;
        } catch (Throwable t) {
            if (hasSuspendData) {
                status = STATUS_LOAD_FAILED;
                t.printStackTrace();
            }
        } finally {
            try {
                in.close();
            } catch (Exception e) {
            }
        }

        this.c64.resume();

        return status;
    }

    /**
     * Resume the old emulator state
     */
    private void resume() {
        // try to load a saved state
        final int status = loadState(SUSPENDFILE_NAME);

        // remove the suspend file if necessary
        if (status == STATUS_LOAD_OK || status == STATUS_LOAD_FAILED) {
            try {
                new File(SUSPENDFILE_NAME).delete();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Could not remove the suspend file! Please remove the suspend file '" + SUSPENDFILE_NAME + "' manually.", this.commonResources.getString("CouldNotRemoveSuspendData"), JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }

        // start emulation or show an error message, depending on the outcome of the resume operation
        switch (status) {
            case STATUS_LOAD_OK:
                break;
            case STATUS_LOAD_FAILED:
                JOptionPane.showMessageDialog(this, this.commonResources.getString("FailedToRestoreState"), this.commonResources.getString("ResumeFailed"), JOptionPane.ERROR_MESSAGE);
                break;
        }
    }

    /**
     * Load and start a given emulator snapshot
     *
     * @param   filename    name of the snapshot to load
     */
    private void runSnapshot(final String filename) {
        // try to load a saved state
        final int status = loadState(filename);

        // start emulation or show an error message, depending on the outcome of the resume operation
        switch (status) {
            case STATUS_LOAD_OK:
                break;
            case STATUS_LOAD_FAILED:
                JOptionPane.showMessageDialog(this, this.commonResources.getString("FailedToRestoreState"), this.commonResources.getString("LoadSnapshotFailed"), JOptionPane.ERROR_MESSAGE);
                break;
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroupTurboModes = new javax.swing.ButtonGroup();
        buttonGroupDriveModes = new javax.swing.ButtonGroup();
        buttonGroupScreenSizes = new javax.swing.ButtonGroup();
        buttonGroupFrameSkipSelections = new javax.swing.ButtonGroup();
        buttonGroupJoystickPorts = new javax.swing.ButtonGroup();
        buttonGroupMouseEmulation = new javax.swing.ButtonGroup();
        jPanelStatus = new javax.swing.JPanel();
        jLabelPerformance = new javax.swing.JLabel();
        jLabelMessages = new javax.swing.JLabel();
        jPanelDrive = new javax.swing.JPanel();
        jLabelTrackSector = new javax.swing.JLabel();
        jLabelDrives = new javax.swing.JLabel();
        jMenuBar = new javax.swing.JMenuBar();
        jMenuFile = new javax.swing.JMenu();
        jMenuAutoStart = new javax.swing.JMenu();
        jMenuItemAutoStart = new javax.swing.JMenuItem();
        jMenuItemFastAutoStart = new javax.swing.JMenuItem();
        jSeparatorFileMenu1 = new javax.swing.JSeparator();
        jMenuItemAttachImage = new javax.swing.JMenuItem();
        jMenuItemDetachImages = new javax.swing.JMenuItem();
        jMenuLoadProgram = new javax.swing.JMenu();
        jMenuItemLoadProgram = new javax.swing.JMenuItem();
        jMenuItemFastLoadProgram = new javax.swing.JMenuItem();
        jMenuItemSaveSnapshot = new javax.swing.JMenuItem();
        jMenuSelectDrive = new javax.swing.JMenu();
        jSeparatorFileMenu2 = new javax.swing.JSeparator();
        jMenuItemExit = new javax.swing.JMenuItem();
        jMenuItemSuspend = new javax.swing.JMenuItem();
        jMenuEmulation = new javax.swing.JMenu();
        jMenuItemTypeText = new javax.swing.JMenuItem();
        jMenuItemSpecialKey = new javax.swing.JMenuItem();
        jMenuItemRun = new javax.swing.JMenuItem();
        jSeparatorEmulationMenu1 = new javax.swing.JSeparator();
        jMenuJoystick = new javax.swing.JMenu();
        jMenuJoystickPort = new javax.swing.JMenu();
        jRadioButtonMenuItemJoystickPort1 = new javax.swing.JRadioButtonMenuItem();
        jRadioButtonMenuItemJoystickPort2 = new javax.swing.JRadioButtonMenuItem();
        jMenuMouseAsJoystick = new javax.swing.JMenu();
        jRadioButtonMenuItemNoMouseJoystickEmulation = new javax.swing.JRadioButtonMenuItem();
        jRadioButtonMenuItemMouseFireButtonEmulation = new javax.swing.JRadioButtonMenuItem();
        jRadioButtonMenuItemVirtualJoystick = new javax.swing.JRadioButtonMenuItem();
        jMenuDriveMode = new javax.swing.JMenu();
        jMenuItemFastC1541 = new javax.swing.JRadioButtonMenuItem();
        jMenuItemBalancedC1541 = new javax.swing.JRadioButtonMenuItem();
        jMenuItemCompatibleC1541 = new javax.swing.JRadioButtonMenuItem();
        jMenuSize = new javax.swing.JMenu();
        jMenuItemSizeX1 = new javax.swing.JRadioButtonMenuItem();
        jMenuItemSizeX2 = new javax.swing.JRadioButtonMenuItem();
        jMenuItemSizeX3 = new javax.swing.JRadioButtonMenuItem();
        jMenuFrameskip = new javax.swing.JMenu();
        jMenuItemFrameSkipAuto = new javax.swing.JRadioButtonMenuItem();
        jMenuItemFrameSkip1 = new javax.swing.JRadioButtonMenuItem();
        jMenuItemFrameSkip2 = new javax.swing.JRadioButtonMenuItem();
        jMenuItemFrameSkip3 = new javax.swing.JRadioButtonMenuItem();
        jMenuItemFrameSkip4 = new javax.swing.JRadioButtonMenuItem();
        jMenuTurboMode = new javax.swing.JMenu();
        jRadioButtonMenuItemTurboModeAuto = new javax.swing.JRadioButtonMenuItem();
        jRadioButtonMenuItemTurboModeOn = new javax.swing.JRadioButtonMenuItem();
        jRadioButtonMenuItemTurboModeOff = new javax.swing.JRadioButtonMenuItem();
        jSeparatorEmulationMenu2 = new javax.swing.JSeparator();
        jMenuItemReset = new javax.swing.JMenuItem();
        jMenuItemShowLog = new javax.swing.JMenuItem();
        jMenuHelp = new javax.swing.JMenu();
        jMenuItemAbout = new javax.swing.JMenuItem();
        jMenuItemContents = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("JSwingC64");
        setResizable(false);

        jPanelStatus.setLayout(new java.awt.BorderLayout());

        jLabelPerformance.setFont(new java.awt.Font("Arial", 0, 10));
        jLabelPerformance.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabelPerformance.setText("      ");
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("res/l10n/c64EmulatorMessages"); // NOI18N
        jLabelPerformance.setToolTipText(bundle.getString("PerformancePanelTooltip")); // NOI18N
        jLabelPerformance.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jLabelPerformance.setOpaque(true);
        jLabelPerformance.setPreferredSize(new java.awt.Dimension(45, 17));
        jPanelStatus.add(jLabelPerformance, java.awt.BorderLayout.WEST);

        jLabelMessages.setFont(new java.awt.Font("Arial", 0, 10));
        jLabelMessages.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        jLabelMessages.setToolTipText(bundle.getString("MessagePanelTooltip")); // NOI18N
        jLabelMessages.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jPanelStatus.add(jLabelMessages, java.awt.BorderLayout.CENTER);

        jPanelDrive.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jPanelDrive.setMinimumSize(new java.awt.Dimension(30, 18));
        jPanelDrive.setPreferredSize(new java.awt.Dimension(50, 18));
        jPanelDrive.setLayout(new java.awt.BorderLayout());

        jLabelTrackSector.setFont(new java.awt.Font("Arial", 0, 10));
        jLabelTrackSector.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabelTrackSector.setToolTipText(bundle.getString("TrackSectorPanelTooltip")); // NOI18N
        jPanelDrive.add(jLabelTrackSector, java.awt.BorderLayout.CENTER);

        jLabelDrives.setIcon(new javax.swing.ImageIcon(getClass().getResource("/res/swing/LED_inactive.gif"))); // NOI18N
        jLabelDrives.setToolTipText(bundle.getString("DriveIsInactive")); // NOI18N
        jPanelDrive.add(jLabelDrives, java.awt.BorderLayout.EAST);

        jPanelStatus.add(jPanelDrive, java.awt.BorderLayout.EAST);

        getContentPane().add(jPanelStatus, java.awt.BorderLayout.SOUTH);

        java.util.ResourceBundle bundle1 = java.util.ResourceBundle.getBundle("res/l10n/commonMessages"); // NOI18N
        jMenuFile.setText(bundle1.getString("File")); // NOI18N

        jMenuAutoStart.setText(bundle.getString("AutoStart2")); // NOI18N
        jMenuAutoStart.setToolTipText(bundle.getString("AutoStart2Tooltip")); // NOI18N

        jMenuItemAutoStart.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemAutoStart.setText(bundle.getString("AutoStart")); // NOI18N
        jMenuItemAutoStart.setToolTipText(bundle.getString("AutoStartToolTip")); // NOI18N
        jMenuItemAutoStart.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemAutoStartActionPerformed(evt);
            }
        });
        jMenuAutoStart.add(jMenuItemAutoStart);

        jMenuItemFastAutoStart.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemFastAutoStart.setText(bundle.getString("FastAutoStart")); // NOI18N
        jMenuItemFastAutoStart.setToolTipText(bundle.getString("FastAutoStartToolTip")); // NOI18N
        jMenuItemFastAutoStart.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemFastAutoStartActionPerformed(evt);
            }
        });
        jMenuAutoStart.add(jMenuItemFastAutoStart);

        jMenuFile.add(jMenuAutoStart);
        jMenuFile.add(jSeparatorFileMenu1);

        jMenuItemAttachImage.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, java.awt.event.InputEvent.ALT_MASK));
        jMenuItemAttachImage.setText(bundle.getString("AttachImage")); // NOI18N
        jMenuItemAttachImage.setToolTipText(bundle.getString("AttachImageTooltip")); // NOI18N
        jMenuItemAttachImage.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemAttachImageActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemAttachImage);

        jMenuItemDetachImages.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_D, java.awt.event.InputEvent.ALT_MASK));
        jMenuItemDetachImages.setText(bundle.getString("DetachAll")); // NOI18N
        jMenuItemDetachImages.setToolTipText(bundle.getString("DetachAllTooltip")); // NOI18N
        jMenuItemDetachImages.setEnabled(false);
        jMenuItemDetachImages.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemDetachImagesActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemDetachImages);

        jMenuLoadProgram.setText(bundle.getString("LoadProgram")); // NOI18N
        jMenuLoadProgram.setToolTipText(bundle.getString("LoadProgramTooltip")); // NOI18N
        jMenuLoadProgram.setEnabled(false);

        jMenuItemLoadProgram.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.event.InputEvent.ALT_MASK));
        jMenuItemLoadProgram.setText(bundle.getString("Load")); // NOI18N
        jMenuItemLoadProgram.setToolTipText(bundle.getString("LoadProgramTooltip")); // NOI18N
        jMenuItemLoadProgram.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemLoadProgramActionPerformed(evt);
            }
        });
        jMenuLoadProgram.add(jMenuItemLoadProgram);

        jMenuItemFastLoadProgram.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.ALT_MASK));
        jMenuItemFastLoadProgram.setText(bundle.getString("FastLoad")); // NOI18N
        jMenuItemFastLoadProgram.setToolTipText(bundle.getString("FastLoadProgramTooltip")); // NOI18N
        jMenuItemFastLoadProgram.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemLoadProgramActionPerformed(evt);
            }
        });
        jMenuLoadProgram.add(jMenuItemFastLoadProgram);

        jMenuFile.add(jMenuLoadProgram);

        jMenuItemSaveSnapshot.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemSaveSnapshot.setText(bundle1.getString("SaveSnapshot")); // NOI18N
        jMenuItemSaveSnapshot.setToolTipText(bundle1.getString("SaveSnapshotTooltip")); // NOI18N
        jMenuItemSaveSnapshot.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSaveSnapshotActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemSaveSnapshot);

        jMenuSelectDrive.setText(bundle.getString("SelectDrive")); // NOI18N
        jMenuSelectDrive.setToolTipText(bundle.getString("SelectDriveTooltip")); // NOI18N
        jMenuFile.add(jMenuSelectDrive);
        jMenuFile.add(jSeparatorFileMenu2);

        jMenuItemExit.setText(bundle1.getString("Exit")); // NOI18N
        jMenuItemExit.setToolTipText(bundle1.getString("ExitTooltip")); // NOI18N
        jMenuItemExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemExitActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemExit);

        jMenuItemSuspend.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.ALT_MASK));
        jMenuItemSuspend.setText(bundle1.getString("Suspend")); // NOI18N
        jMenuItemSuspend.setToolTipText(bundle1.getString("SuspendTooltip")); // NOI18N
        jMenuItemSuspend.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSuspendActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemSuspend);

        jMenuBar.add(jMenuFile);

        jMenuEmulation.setText(bundle1.getString("Emulation")); // NOI18N

        jMenuItemTypeText.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.ALT_MASK));
        jMenuItemTypeText.setText(bundle.getString("TypeText2")); // NOI18N
        jMenuItemTypeText.setToolTipText(bundle.getString("TypeText2Tooltip")); // NOI18N
        jMenuItemTypeText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemTypeTextActionPerformed(evt);
            }
        });
        jMenuEmulation.add(jMenuItemTypeText);

        jMenuItemSpecialKey.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_K, java.awt.event.InputEvent.ALT_MASK));
        jMenuItemSpecialKey.setText(bundle.getString("SpecialKeys2")); // NOI18N
        jMenuItemSpecialKey.setToolTipText(bundle.getString("SpecialKeys2Tooltip")); // NOI18N
        jMenuItemSpecialKey.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSpecialKeyActionPerformed(evt);
            }
        });
        jMenuEmulation.add(jMenuItemSpecialKey);

        jMenuItemRun.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.ALT_MASK));
        jMenuItemRun.setText(bundle.getString("RunCurrent")); // NOI18N
        jMenuItemRun.setToolTipText(bundle.getString("RunCurrentTooltip")); // NOI18N
        jMenuItemRun.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemRunActionPerformed(evt);
            }
        });
        jMenuEmulation.add(jMenuItemRun);
        jMenuEmulation.add(jSeparatorEmulationMenu1);

        jMenuJoystick.setText(bundle.getString("Joystick")); // NOI18N
        jMenuJoystick.setToolTipText(bundle.getString("JoystickTooltip")); // NOI18N

        jMenuJoystickPort.setText(bundle.getString("Port")); // NOI18N

        jRadioButtonMenuItemJoystickPort1.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1, java.awt.event.InputEvent.ALT_MASK));
        buttonGroupJoystickPorts.add(jRadioButtonMenuItemJoystickPort1);
        jRadioButtonMenuItemJoystickPort1.setSelected(true);
        jRadioButtonMenuItemJoystickPort1.setText("1");
        jRadioButtonMenuItemJoystickPort1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonMenuItemJoystickPort1ActionPerformed(evt);
            }
        });
        jMenuJoystickPort.add(jRadioButtonMenuItemJoystickPort1);

        jRadioButtonMenuItemJoystickPort2.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_2, java.awt.event.InputEvent.ALT_MASK));
        buttonGroupJoystickPorts.add(jRadioButtonMenuItemJoystickPort2);
        jRadioButtonMenuItemJoystickPort2.setText("2");
        jRadioButtonMenuItemJoystickPort2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonMenuItemJoystickPort2ActionPerformed(evt);
            }
        });
        jMenuJoystickPort.add(jRadioButtonMenuItemJoystickPort2);

        jMenuJoystick.add(jMenuJoystickPort);

        jMenuMouseAsJoystick.setText(bundle.getString("EmulationViaMouse")); // NOI18N

        buttonGroupMouseEmulation.add(jRadioButtonMenuItemNoMouseJoystickEmulation);
        jRadioButtonMenuItemNoMouseJoystickEmulation.setText(bundle.getString("EmulationViaMouseNone")); // NOI18N
        jRadioButtonMenuItemNoMouseJoystickEmulation.setToolTipText(bundle.getString("EmulationViaMouseNoneTooltip")); // NOI18N
        jRadioButtonMenuItemNoMouseJoystickEmulation.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonMenuItemNoMouseJoystickEmulationActionPerformed(evt);
            }
        });
        jMenuMouseAsJoystick.add(jRadioButtonMenuItemNoMouseJoystickEmulation);

        buttonGroupMouseEmulation.add(jRadioButtonMenuItemMouseFireButtonEmulation);
        jRadioButtonMenuItemMouseFireButtonEmulation.setSelected(true);
        jRadioButtonMenuItemMouseFireButtonEmulation.setText(bundle.getString("EmulationViaMouseButtonOnly")); // NOI18N
        jRadioButtonMenuItemMouseFireButtonEmulation.setToolTipText(bundle.getString("EmulationViaMouseButtonOnlyTooltip")); // NOI18N
        jRadioButtonMenuItemMouseFireButtonEmulation.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonMenuItemMouseFireButtonEmulationActionPerformed(evt);
            }
        });
        jMenuMouseAsJoystick.add(jRadioButtonMenuItemMouseFireButtonEmulation);

        buttonGroupMouseEmulation.add(jRadioButtonMenuItemVirtualJoystick);
        jRadioButtonMenuItemVirtualJoystick.setText(bundle.getString("EmulationViaMouseFull")); // NOI18N
        jRadioButtonMenuItemVirtualJoystick.setToolTipText(bundle.getString("EmulationViaMouseFullTooltip")); // NOI18N
        jRadioButtonMenuItemVirtualJoystick.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonMenuItemVirtualJoystickActionPerformed(evt);
            }
        });
        jMenuMouseAsJoystick.add(jRadioButtonMenuItemVirtualJoystick);

        jMenuJoystick.add(jMenuMouseAsJoystick);

        jMenuEmulation.add(jMenuJoystick);

        jMenuDriveMode.setText(bundle.getString("C1541Mode")); // NOI18N
        jMenuDriveMode.setToolTipText(bundle.getString("C1541ModeTooltip")); // NOI18N

        buttonGroupDriveModes.add(jMenuItemFastC1541);
        jMenuItemFastC1541.setText(bundle.getString("Fast")); // NOI18N
        jMenuItemFastC1541.setToolTipText(bundle.getString("FastTooltip")); // NOI18N
        jMenuItemFastC1541.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemFastC1541ActionPerformed(evt);
            }
        });
        jMenuDriveMode.add(jMenuItemFastC1541);

        buttonGroupDriveModes.add(jMenuItemBalancedC1541);
        jMenuItemBalancedC1541.setText(bundle.getString("Balanced")); // NOI18N
        jMenuItemBalancedC1541.setToolTipText(bundle.getString("BalancedTooltip")); // NOI18N
        jMenuItemBalancedC1541.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemBalancedC1541ActionPerformed(evt);
            }
        });
        jMenuDriveMode.add(jMenuItemBalancedC1541);

        buttonGroupDriveModes.add(jMenuItemCompatibleC1541);
        jMenuItemCompatibleC1541.setText(bundle.getString("Compatible")); // NOI18N
        jMenuItemCompatibleC1541.setToolTipText(bundle.getString("CompatibleTooltip")); // NOI18N
        jMenuItemCompatibleC1541.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemCompatibleC1541ActionPerformed(evt);
            }
        });
        jMenuDriveMode.add(jMenuItemCompatibleC1541);

        jMenuEmulation.add(jMenuDriveMode);

        jMenuSize.setText(bundle1.getString("Size")); // NOI18N
        jMenuSize.setToolTipText(bundle1.getString("SizeTooltip")); // NOI18N

        jMenuItemSizeX1.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.SHIFT_MASK));
        buttonGroupScreenSizes.add(jMenuItemSizeX1);
        jMenuItemSizeX1.setText("100%");
        jMenuItemSizeX1.setToolTipText(bundle.getString("Size100Tooltip")); // NOI18N
        jMenuItemSizeX1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSizeX1ActionPerformed(evt);
            }
        });
        jMenuSize.add(jMenuItemSizeX1);

        jMenuItemSizeX2.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_2, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.SHIFT_MASK));
        buttonGroupScreenSizes.add(jMenuItemSizeX2);
        jMenuItemSizeX2.setText("200%");
        jMenuItemSizeX2.setToolTipText(bundle.getString("Size200Tooltip")); // NOI18N
        jMenuItemSizeX2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSizeX2ActionPerformed(evt);
            }
        });
        jMenuSize.add(jMenuItemSizeX2);

        jMenuItemSizeX3.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_3, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.SHIFT_MASK));
        buttonGroupScreenSizes.add(jMenuItemSizeX3);
        jMenuItemSizeX3.setText("300%");
        jMenuItemSizeX3.setToolTipText(bundle.getString("Size300Tooltip")); // NOI18N
        jMenuItemSizeX3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSizeX3ActionPerformed(evt);
            }
        });
        jMenuSize.add(jMenuItemSizeX3);

        jMenuEmulation.add(jMenuSize);

        jMenuFrameskip.setText(bundle1.getString("SkipFrames")); // NOI18N
        jMenuFrameskip.setToolTipText(bundle1.getString("SkipFramesTooltip")); // NOI18N

        buttonGroupFrameSkipSelections.add(jMenuItemFrameSkipAuto);
        jMenuItemFrameSkipAuto.setText(bundle.getString("Automatic")); // NOI18N
        jMenuItemFrameSkipAuto.setToolTipText(bundle.getString("AutomaticTooltip")); // NOI18N
        jMenuItemFrameSkipAuto.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemFrameSkipAutoActionPerformed(evt);
            }
        });
        jMenuFrameskip.add(jMenuItemFrameSkipAuto);

        buttonGroupFrameSkipSelections.add(jMenuItemFrameSkip1);
        jMenuItemFrameSkip1.setText("1");
        jMenuItemFrameSkip1.setToolTipText(bundle1.getString("SkipFrames1Tooltip")); // NOI18N
        jMenuItemFrameSkip1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemFrameSkip1ActionPerformed(evt);
            }
        });
        jMenuFrameskip.add(jMenuItemFrameSkip1);

        buttonGroupFrameSkipSelections.add(jMenuItemFrameSkip2);
        jMenuItemFrameSkip2.setText("2");
        jMenuItemFrameSkip2.setToolTipText(bundle1.getString("SkipFrames2Tooltip")); // NOI18N
        jMenuItemFrameSkip2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemFrameSkip2ActionPerformed(evt);
            }
        });
        jMenuFrameskip.add(jMenuItemFrameSkip2);

        buttonGroupFrameSkipSelections.add(jMenuItemFrameSkip3);
        jMenuItemFrameSkip3.setText("3");
        jMenuItemFrameSkip3.setToolTipText(bundle1.getString("SkipFrames3Tooltip")); // NOI18N
        jMenuItemFrameSkip3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemFrameSkip3ActionPerformed(evt);
            }
        });
        jMenuFrameskip.add(jMenuItemFrameSkip3);

        buttonGroupFrameSkipSelections.add(jMenuItemFrameSkip4);
        jMenuItemFrameSkip4.setText("4");
        jMenuItemFrameSkip4.setToolTipText(bundle1.getString("SkipFrames4Tooltip")); // NOI18N
        jMenuItemFrameSkip4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemFrameSkip4ActionPerformed(evt);
            }
        });
        jMenuFrameskip.add(jMenuItemFrameSkip4);

        jMenuEmulation.add(jMenuFrameskip);

        jMenuTurboMode.setText(bundle.getString("TurboMode")); // NOI18N
        jMenuTurboMode.setToolTipText(bundle.getString("TurboModeTooltip")); // NOI18N

        jRadioButtonMenuItemTurboModeAuto.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_NUMBER_SIGN, java.awt.event.InputEvent.ALT_MASK));
        buttonGroupTurboModes.add(jRadioButtonMenuItemTurboModeAuto);
        jRadioButtonMenuItemTurboModeAuto.setSelected(true);
        jRadioButtonMenuItemTurboModeAuto.setText(bundle.getString("Automatic")); // NOI18N
        jRadioButtonMenuItemTurboModeAuto.setToolTipText(bundle.getString("TurboModeAutoTooltip")); // NOI18N
        jRadioButtonMenuItemTurboModeAuto.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonMenuItemTurboModeAutoActionPerformed(evt);
            }
        });
        jMenuTurboMode.add(jRadioButtonMenuItemTurboModeAuto);

        jRadioButtonMenuItemTurboModeOn.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_PLUS, java.awt.event.InputEvent.ALT_MASK));
        buttonGroupTurboModes.add(jRadioButtonMenuItemTurboModeOn);
        jRadioButtonMenuItemTurboModeOn.setText(bundle1.getString("On")); // NOI18N
        jRadioButtonMenuItemTurboModeOn.setToolTipText(bundle.getString("TurboModeOnTooltip")); // NOI18N
        jRadioButtonMenuItemTurboModeOn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonMenuItemTurboModeOnActionPerformed(evt);
            }
        });
        jMenuTurboMode.add(jRadioButtonMenuItemTurboModeOn);

        jRadioButtonMenuItemTurboModeOff.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, java.awt.event.InputEvent.ALT_MASK));
        buttonGroupTurboModes.add(jRadioButtonMenuItemTurboModeOff);
        jRadioButtonMenuItemTurboModeOff.setText(bundle1.getString("Off")); // NOI18N
        jRadioButtonMenuItemTurboModeOff.setToolTipText(bundle.getString("TurboModeOffTooltip")); // NOI18N
        jRadioButtonMenuItemTurboModeOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonMenuItemTurboModeOffActionPerformed(evt);
            }
        });
        jMenuTurboMode.add(jRadioButtonMenuItemTurboModeOff);

        jMenuEmulation.add(jMenuTurboMode);
        jMenuEmulation.add(jSeparatorEmulationMenu2);

        jMenuItemReset.setText(bundle.getString("ResetC64")); // NOI18N
        jMenuItemReset.setToolTipText(bundle.getString("ResetC64Tooltip")); // NOI18N
        jMenuItemReset.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemResetActionPerformed(evt);
            }
        });
        jMenuEmulation.add(jMenuItemReset);

        jMenuItemShowLog.setText(bundle1.getString("ShowLog")); // NOI18N
        jMenuItemShowLog.setToolTipText(bundle1.getString("ShowLogTooltip")); // NOI18N
        jMenuItemShowLog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemShowLogActionPerformed(evt);
            }
        });
        jMenuEmulation.add(jMenuItemShowLog);

        jMenuBar.add(jMenuEmulation);

        jMenuHelp.setText(bundle1.getString("Help")); // NOI18N

        jMenuItemAbout.setText(bundle1.getString("About")); // NOI18N
        jMenuItemAbout.setToolTipText(bundle1.getString("AboutTooltip")); // NOI18N
        jMenuItemAbout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemAboutActionPerformed(evt);
            }
        });
        jMenuHelp.add(jMenuItemAbout);

        jMenuItemContents.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, java.awt.event.InputEvent.ALT_MASK));
        jMenuItemContents.setText(bundle1.getString("Contents")); // NOI18N
        jMenuItemContents.setToolTipText(bundle1.getString("ContentsTooltip")); // NOI18N
        jMenuItemContents.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemContentsActionPerformed(evt);
            }
        });
        jMenuHelp.add(jMenuItemContents);

        jMenuBar.add(jMenuHelp);

        setJMenuBar(jMenuBar);

        pack();
    }// </editor-fold>//GEN-END:initComponents
    private void jRadioButtonMenuItemVirtualJoystickActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonMenuItemVirtualJoystickActionPerformed
        this.canvas.setMouseUsage(C64Canvas.MOUSE_FOR_VIRTUAL_JOYSTICK);
        this.settings.setProperty(SETTING_MOUSE_USAGE, Integer.toString(C64Canvas.MOUSE_FOR_VIRTUAL_JOYSTICK));
        this.jRadioButtonMenuItemVirtualJoystick.setSelected(true);
    }//GEN-LAST:event_jRadioButtonMenuItemVirtualJoystickActionPerformed

    private void jRadioButtonMenuItemMouseFireButtonEmulationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonMenuItemMouseFireButtonEmulationActionPerformed
        this.canvas.setMouseUsage(C64Canvas.MOUSE_AS_FIRE_BUTTON);
        this.settings.setProperty(SETTING_MOUSE_USAGE, Integer.toString(C64Canvas.MOUSE_AS_FIRE_BUTTON));
        this.jRadioButtonMenuItemMouseFireButtonEmulation.setSelected(true);
    }//GEN-LAST:event_jRadioButtonMenuItemMouseFireButtonEmulationActionPerformed

    private void jRadioButtonMenuItemNoMouseJoystickEmulationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonMenuItemNoMouseJoystickEmulationActionPerformed
        this.canvas.setMouseUsage(C64Canvas.MOUSE_NO_USAGE);
        this.settings.setProperty(SETTING_MOUSE_USAGE, Integer.toString(C64Canvas.MOUSE_NO_USAGE));
        this.jRadioButtonMenuItemNoMouseJoystickEmulation.setSelected(true);
    }//GEN-LAST:event_jRadioButtonMenuItemNoMouseJoystickEmulationActionPerformed

    private void jMenuItemShowLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemShowLogActionPerformed
        JOptionPane.showMessageDialog(this, this.c64.getLogger().dumpAll(), this.commonResources.getString("LogMessages"), JOptionPane.INFORMATION_MESSAGE);
    }//GEN-LAST:event_jMenuItemShowLogActionPerformed

    private void jMenuItemContentsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemContentsActionPerformed
        try {
            java.awt.Desktop.getDesktop().browse(new URI(URL_ONLINE_HELP));
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(this, this.commonResources.getString("CouldNotStartBrowser") + " '" + URL_ONLINE_HELP + "'", this.commonResources.getString("CouldNotDisplayOnlineHelp"), JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_jMenuItemContentsActionPerformed

    private void jRadioButtonMenuItemJoystickPort2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonMenuItemJoystickPort2ActionPerformed
        this.c64.setActiveJoystick(1);
        this.settings.setProperty(SETTING_JOYSTICK_PORT, "1");
        this.jRadioButtonMenuItemJoystickPort2.setSelected(true);
    }//GEN-LAST:event_jRadioButtonMenuItemJoystickPort2ActionPerformed

    private void jRadioButtonMenuItemJoystickPort1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonMenuItemJoystickPort1ActionPerformed
        this.c64.setActiveJoystick(0);
        this.settings.setProperty(SETTING_JOYSTICK_PORT, "0");
        this.jRadioButtonMenuItemJoystickPort1.setSelected(true);
    }//GEN-LAST:event_jRadioButtonMenuItemJoystickPort1ActionPerformed

    private void jMenuItemSizeX1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSizeX1ActionPerformed
        this.canvas.setScaling(1);
        this.settings.setProperty(SETTING_WINDOW_SCALING, "1");
        pack();
        this.jMenuItemSizeX1.setSelected(true);
    }//GEN-LAST:event_jMenuItemSizeX1ActionPerformed

    private void jMenuItemSizeX2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSizeX2ActionPerformed
        this.canvas.setScaling(2);
        this.settings.setProperty(SETTING_WINDOW_SCALING, "2");
        pack();
        this.jMenuItemSizeX2.setSelected(true);
    }//GEN-LAST:event_jMenuItemSizeX2ActionPerformed

    private void jMenuItemSizeX3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSizeX3ActionPerformed
        this.canvas.setScaling(3);
        this.settings.setProperty(SETTING_WINDOW_SCALING, "3");
        pack();
        this.jMenuItemSizeX3.setSelected(true);
    }//GEN-LAST:event_jMenuItemSizeX3ActionPerformed

    private void jMenuItemAboutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemAboutActionPerformed
        // get About box text and convert it to HTML, inserting the link to the project page
        String text = this.c64Resources.getString("AboutText1") + VERSION + this.c64Resources.getString("AboutText2") + this.c64Resources.getString("AboutText3") + this.c64Resources.getString("AboutText4");

        text = text.replaceAll("\\n", "<br>");
        text = text.replaceAll("\\#PROJECTPAGE\\#", "\\<a href\\=\\'" + PROJECT_PAGE_URL + "\\'\\>" + PROJECT_PAGE_URL + "\\<\\/a\\>");
        text = "<html><body>" + text + "</body></html>";

        // create an editor pane that displays this text and add a listener that uses the system browser to display any hyperlinks activated by the user
        JEditorPane messagePane = new JEditorPane("text/html", text);

        messagePane.setBackground(getBackground());
        messagePane.setEditable(false);
        messagePane.addHyperlinkListener(new HyperlinkListener() {

            @Override
            public void hyperlinkUpdate(final HyperlinkEvent evt) {
                if (evt.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    try {
                        Desktop.getDesktop().browse(evt.getURL().toURI());
                    } catch (Throwable t) {
                        // could not display the web page, what to do now???
                        System.err.println("Could not browse to page " + evt.getURL());
                    }
                }
            }
        });

        JOptionPane.showMessageDialog(this, messagePane);
    }//GEN-LAST:event_jMenuItemAboutActionPerformed

    private void jMenuItemSpecialKeyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSpecialKeyActionPerformed
        // show a dialog to let the user select the special key
        final String[] specialKeys = {"Run", "Break", "Commodore", "Pound"};
        final Object key = JOptionPane.showInputDialog(this, this.c64Resources.getString("SelectKey"), this.c64Resources.getString("SpecialKeys"), JOptionPane.PLAIN_MESSAGE, null, specialKeys, null);

        if (null != key) {
            this.c64.getKeyboard().keyTyped(key.toString().toUpperCase());
        }
    }//GEN-LAST:event_jMenuItemSpecialKeyActionPerformed

    private void jMenuItemTypeTextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemTypeTextActionPerformed
        final JOptionPane pane = new JOptionPane(this.c64Resources.getString("TextToEnter"), JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        final JComboBox combobox = new JComboBox(this.c64.getKeyboard().getTypedTexts());

        combobox.setEditable(true);
        combobox.setSelectedIndex(-1);
        pane.add(combobox, 1);

        final JDialog dialog = pane.createDialog(this, this.c64Resources.getString("TypeText"));

        combobox.requestFocusInWindow();
        dialog.setVisible(true);

        final Object text = combobox.getSelectedItem();

        if (pane.getValue() instanceof Integer && ((Integer) pane.getValue()).intValue() == JOptionPane.OK_OPTION && null != text) {
            this.c64.getKeyboard().textTyped(text.toString());
        }
    }//GEN-LAST:event_jMenuItemTypeTextActionPerformed

    private void jMenuItemLoadProgramActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemLoadProgramActionPerformed
        // do we have programs to display?
        final Vector programs = this.c64.getDrive(this.c64.getActiveDrive()).getFilenames();

        if (programs.isEmpty()) {
            // if not, then tell the user to first attach an image
            JOptionPane.showMessageDialog(this, this.c64Resources.getString("PleaseAttachImageBefore"), this.c64Resources.getString("NoProgramsToLoad"), JOptionPane.WARNING_MESSAGE);
        } else {
            // show a dialog to let the user select the program
            final Object program = JOptionPane.showInputDialog(this, this.c64Resources.getString("SelectProgram"), this.c64Resources.getString("LoadProgram"), JOptionPane.PLAIN_MESSAGE, null, programs.toArray(), programs.elementAt(0));

            if (null != program) {
                if (evt.getActionCommand().indexOf(this.c64Resources.getString("FastLoadProgram")) < 0) {
                    this.c64.loadFile(program.toString());
                } else {
                    try {
                        this.c64.fastLoadFile(program.toString(), -1);
                    } catch (IOException e) {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(this, this.c64Resources.getString("CouldNotLoadFile") + ": " + program.toString() + "!\n" + e, this.c64Resources.getString("CouldNotLoadFile"), JOptionPane.WARNING_MESSAGE);
                    }
                }
            }
        }
    }//GEN-LAST:event_jMenuItemLoadProgramActionPerformed

    private void jMenuItemRunActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemRunActionPerformed
        runProgram();
    }//GEN-LAST:event_jMenuItemRunActionPerformed

    private void jMenuItemResetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemResetActionPerformed
        this.c64.reset();
    }//GEN-LAST:event_jMenuItemResetActionPerformed

    private void jMenuItemExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemExitActionPerformed
        setVisible(false);
        System.exit(0);
    }//GEN-LAST:event_jMenuItemExitActionPerformed

    private void jMenuItemAttachImageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemAttachImageActionPerformed
        selectImage();
    }//GEN-LAST:event_jMenuItemAttachImageActionPerformed

    private void jMenuItemDetachImagesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemDetachImagesActionPerformed
        // check whether we really want to detach all images
        if (JOptionPane.showConfirmDialog(this, this.c64Resources.getString("ReallyDetach")) == JOptionPane.YES_OPTION) {
            detachImages();
        }
    }//GEN-LAST:event_jMenuItemDetachImagesActionPerformed

    private void jMenuItemFastC1541ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemFastC1541ActionPerformed
        for (int i = 0; i < C64.MAX_NUM_DRIVES; ++i) {
            this.c64.getDrive(i).setEmulationLevel(C1541.FAST_EMULATION);
        }
        this.settings.setProperty(SETTING_DRIVE_MODE, Integer.toString(C1541.FAST_EMULATION));
        this.jMenuItemFastC1541.setSelected(true);
}//GEN-LAST:event_jMenuItemFastC1541ActionPerformed

    private void jMenuItemBalancedC1541ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemBalancedC1541ActionPerformed
        for (int i = 0; i < C64.MAX_NUM_DRIVES; ++i) {
            this.c64.getDrive(i).setEmulationLevel(C1541.BALANCED_EMULATION);
        }
        this.settings.setProperty(SETTING_DRIVE_MODE, Integer.toString(C1541.BALANCED_EMULATION));
        this.jMenuItemBalancedC1541.setSelected(true);
}//GEN-LAST:event_jMenuItemBalancedC1541ActionPerformed

    private void jMenuItemCompatibleC1541ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemCompatibleC1541ActionPerformed
        for (int i = 0; i < C64.MAX_NUM_DRIVES; ++i) {
            this.c64.getDrive(i).setEmulationLevel(C1541.COMPATIBLE_EMULATION);
        }
        this.settings.setProperty(SETTING_DRIVE_MODE, Integer.toString(C1541.COMPATIBLE_EMULATION));
        this.jMenuItemCompatibleC1541.setSelected(true);
}//GEN-LAST:event_jMenuItemCompatibleC1541ActionPerformed

    private void jMenuItemFrameSkipAutoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemFrameSkipAutoActionPerformed
        this.c64.setDoAutoAdjustFrameskip(true);
        this.settings.setProperty(SETTING_FRAMESKIP, "0");
        this.jMenuItemFrameSkipAuto.setSelected(true);
    }//GEN-LAST:event_jMenuItemFrameSkipAutoActionPerformed

    private void jMenuItemFrameSkip1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemFrameSkip1ActionPerformed
        this.c64.setDoAutoAdjustFrameskip(false);
        this.c64.getVIC().setFrameSkip(1);
        this.settings.setProperty(SETTING_FRAMESKIP, "1");
        this.jMenuItemFrameSkip1.setSelected(true);
    }//GEN-LAST:event_jMenuItemFrameSkip1ActionPerformed

    private void jMenuItemFrameSkip2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemFrameSkip2ActionPerformed
        this.c64.setDoAutoAdjustFrameskip(false);
        this.c64.getVIC().setFrameSkip(2);
        this.settings.setProperty(SETTING_FRAMESKIP, "2");
        this.jMenuItemFrameSkip2.setSelected(true);
    }//GEN-LAST:event_jMenuItemFrameSkip2ActionPerformed

    private void jMenuItemFrameSkip3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemFrameSkip3ActionPerformed
        this.c64.setDoAutoAdjustFrameskip(false);
        this.c64.getVIC().setFrameSkip(3);
        this.settings.setProperty(SETTING_FRAMESKIP, "3");
        this.jMenuItemFrameSkip3.setSelected(true);
    }//GEN-LAST:event_jMenuItemFrameSkip3ActionPerformed

    private void jMenuItemFrameSkip4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemFrameSkip4ActionPerformed
        this.c64.setDoAutoAdjustFrameskip(false);
        this.c64.getVIC().setFrameSkip(4);
        this.settings.setProperty(SETTING_FRAMESKIP, "4");
        this.jMenuItemFrameSkip4.setSelected(true);
    }//GEN-LAST:event_jMenuItemFrameSkip4ActionPerformed

    private void jRadioButtonMenuItemTurboModeAutoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonMenuItemTurboModeAutoActionPerformed
        this.c64.setThrottlingEnabled(true);
        this.automaticTurboMode = true;
}//GEN-LAST:event_jRadioButtonMenuItemTurboModeAutoActionPerformed

    private void jRadioButtonMenuItemTurboModeOnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonMenuItemTurboModeOnActionPerformed
        this.c64.setThrottlingEnabled(false);
        this.automaticTurboMode = false;
    }//GEN-LAST:event_jRadioButtonMenuItemTurboModeOnActionPerformed

    private void jRadioButtonMenuItemTurboModeOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonMenuItemTurboModeOffActionPerformed
        this.c64.setThrottlingEnabled(true);
        this.automaticTurboMode = false;
}//GEN-LAST:event_jRadioButtonMenuItemTurboModeOffActionPerformed

    private void jMenuItemSuspendActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSuspendActionPerformed
        suspend();
}//GEN-LAST:event_jMenuItemSuspendActionPerformed

    private void jMenuItemAutoStartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemAutoStartActionPerformed
        if (selectImage()) {
            this.c64.getKeyboard().textTyped("load \"*\",8,1");
            this.c64.getKeyboard().keyTyped("ENTER");
            runProgram();
        }
}//GEN-LAST:event_jMenuItemAutoStartActionPerformed

    private void jMenuItemFastAutoStartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemFastAutoStartActionPerformed
        if (selectImage()) {
            try {
                this.c64.fastLoadFile("*", -1);
                runProgram();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, this.c64Resources.getString("CouldNotLoadFile") + "'*'!\n" + e, this.c64Resources.getString("CouldNotLoadFile"), JOptionPane.WARNING_MESSAGE);
            }
        }
}//GEN-LAST:event_jMenuItemFastAutoStartActionPerformed

    private void jMenuItemSaveSnapshotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSaveSnapshotActionPerformed
        saveSnapshot();
    }//GEN-LAST:event_jMenuItemSaveSnapshotActionPerformed

    private void jCheckBoxMenuItemSelectDriveItemStateChanged(java.awt.event.ItemEvent evt) {
        final JRadioButtonMenuItem item = ((JRadioButtonMenuItem) evt.getItem());

        if (item.isSelected()) {
            // set the active drive
            this.c64.setActiveDrive(item.getText().charAt(0) - '0');

            // enable menu items for loading files from the image based on whether an image is already attached
            final boolean hasDisk = this.c64.getDrive(this.c64.getActiveDrive()).getDriveHandler() != null;

            this.jMenuLoadProgram.setEnabled(hasDisk);
        }
        for (int i = 0; i < this.jCheckBoxMenuItemSelectedDrives.length; ++i) {
            this.jCheckBoxMenuItemSelectedDrives[i].setSelected(i == this.c64.getActiveDrive());
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                new C64Frame().setVisible(true);
            }
        });
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroupDriveModes;
    private javax.swing.ButtonGroup buttonGroupFrameSkipSelections;
    private javax.swing.ButtonGroup buttonGroupJoystickPorts;
    private javax.swing.ButtonGroup buttonGroupMouseEmulation;
    private javax.swing.ButtonGroup buttonGroupScreenSizes;
    private javax.swing.ButtonGroup buttonGroupTurboModes;
    private javax.swing.JLabel jLabelDrives;
    private javax.swing.JLabel jLabelMessages;
    private javax.swing.JLabel jLabelPerformance;
    private javax.swing.JLabel jLabelTrackSector;
    private javax.swing.JMenu jMenuAutoStart;
    private javax.swing.JMenuBar jMenuBar;
    private javax.swing.JMenu jMenuDriveMode;
    private javax.swing.JMenu jMenuEmulation;
    private javax.swing.JMenu jMenuFile;
    private javax.swing.JMenu jMenuFrameskip;
    private javax.swing.JMenu jMenuHelp;
    private javax.swing.JMenuItem jMenuItemAbout;
    private javax.swing.JMenuItem jMenuItemAttachImage;
    private javax.swing.JMenuItem jMenuItemAutoStart;
    private javax.swing.JRadioButtonMenuItem jMenuItemBalancedC1541;
    private javax.swing.JRadioButtonMenuItem jMenuItemCompatibleC1541;
    private javax.swing.JMenuItem jMenuItemContents;
    private javax.swing.JMenuItem jMenuItemDetachImages;
    private javax.swing.JMenuItem jMenuItemExit;
    private javax.swing.JMenuItem jMenuItemFastAutoStart;
    private javax.swing.JRadioButtonMenuItem jMenuItemFastC1541;
    private javax.swing.JMenuItem jMenuItemFastLoadProgram;
    private javax.swing.JRadioButtonMenuItem jMenuItemFrameSkip1;
    private javax.swing.JRadioButtonMenuItem jMenuItemFrameSkip2;
    private javax.swing.JRadioButtonMenuItem jMenuItemFrameSkip3;
    private javax.swing.JRadioButtonMenuItem jMenuItemFrameSkip4;
    private javax.swing.JRadioButtonMenuItem jMenuItemFrameSkipAuto;
    private javax.swing.JMenuItem jMenuItemLoadProgram;
    private javax.swing.JMenuItem jMenuItemReset;
    private javax.swing.JMenuItem jMenuItemRun;
    private javax.swing.JMenuItem jMenuItemSaveSnapshot;
    private javax.swing.JMenuItem jMenuItemShowLog;
    private javax.swing.JRadioButtonMenuItem jMenuItemSizeX1;
    private javax.swing.JRadioButtonMenuItem jMenuItemSizeX2;
    private javax.swing.JRadioButtonMenuItem jMenuItemSizeX3;
    private javax.swing.JMenuItem jMenuItemSpecialKey;
    private javax.swing.JMenuItem jMenuItemSuspend;
    private javax.swing.JMenuItem jMenuItemTypeText;
    private javax.swing.JMenu jMenuJoystick;
    private javax.swing.JMenu jMenuJoystickPort;
    private javax.swing.JMenu jMenuLoadProgram;
    private javax.swing.JMenu jMenuMouseAsJoystick;
    private javax.swing.JMenu jMenuSelectDrive;
    private javax.swing.JMenu jMenuSize;
    private javax.swing.JMenu jMenuTurboMode;
    private javax.swing.JPanel jPanelDrive;
    private javax.swing.JPanel jPanelStatus;
    private javax.swing.JRadioButtonMenuItem jRadioButtonMenuItemJoystickPort1;
    private javax.swing.JRadioButtonMenuItem jRadioButtonMenuItemJoystickPort2;
    private javax.swing.JRadioButtonMenuItem jRadioButtonMenuItemMouseFireButtonEmulation;
    private javax.swing.JRadioButtonMenuItem jRadioButtonMenuItemNoMouseJoystickEmulation;
    private javax.swing.JRadioButtonMenuItem jRadioButtonMenuItemTurboModeAuto;
    private javax.swing.JRadioButtonMenuItem jRadioButtonMenuItemTurboModeOff;
    private javax.swing.JRadioButtonMenuItem jRadioButtonMenuItemTurboModeOn;
    private javax.swing.JRadioButtonMenuItem jRadioButtonMenuItemVirtualJoystick;
    private javax.swing.JSeparator jSeparatorEmulationMenu1;
    private javax.swing.JSeparator jSeparatorEmulationMenu2;
    private javax.swing.JSeparator jSeparatorFileMenu1;
    private javax.swing.JSeparator jSeparatorFileMenu2;
    // End of variables declaration//GEN-END:variables
    private javax.swing.JRadioButtonMenuItem[] jCheckBoxMenuItemSelectedDrives;

    // implementation of the Observer interface
    /**
     * We show log messages in the status bar and save changes to modified emulator images
     */
    @Override
    public void update(final Object observed, final Object arg) {
        // this update is from the C64's logger?
        if (observed == this.c64.getLogger()) {
            final String message = arg.toString();

            // do not display a performance info here
            if (!(message.startsWith("Emulator working at ") && message.indexOf("performance") > 0)) {
                // otherwise we have a normal message and display this for a short time
                this.jLabelMessages.setText(message);
                // clear the message after 5 seconds
                if (null != this.statusMessageTimer) {
                    this.statusMessageTimer.cancel();
                }
                this.statusMessageTimer = new Timer();
                this.statusMessageTimer.schedule(
                        new TimerTask() {

                            @Override
                            public void run() {
                                jLabelMessages.setText("");
                                statusMessageTimer = null;
                            }
                        }, 5000);
            }
        // this update is from the C64's drive?
        } else if (observed instanceof C1541) {
            // the drive state (reading/writing) was reported?
            if (arg instanceof Integer) {
                // show the new drive state
                showDriveIcon((Integer) arg);
            // a modified drive was reported?
            } else if (arg instanceof DriveHandler) {
                // save the delta to the current file
                try {
                    // create the delta to the original image
                    final InputStream in = new BufferedInputStream(new FileInputStream(this.lastFile));
                    final byte[] delta = ((DriveHandler) arg).createDelta(in);

                    // anything to do?
                    if (delta.length > 0) {
                        final File deltaFile = EmulatorUtils.saveDeltaFile(this.lastFile.getAbsolutePath(), delta);

                        this.c64.getLogger().info(this.c64Resources.getString("SavedChangesToFile") + "'" + deltaFile + "'.");
                    }
                } catch (IOException e) {
                    // we could not save the delta
                    e.printStackTrace();
                    this.c64.getLogger().warning(this.c64Resources.getString("CouldNotSaveChanges") + "'" + this.lastFile + "'!");
                }
            } else if (arg instanceof String) {
                // show track and sector information given in the string
                this.jLabelTrackSector.setText(arg.toString());
                startDriveStateTimer();
            }
        // we have a new performance measurement result?
        } else if (observed == this.c64) {
            // then display this with the corresponding color
            final int performance = this.c64.getPerformance();
            final Color background = performance >= 120
                    ? Color.CYAN
                    : performance >= 90
                    ? Color.GREEN
                    : performance >= 80
                    ? Color.YELLOW
                    : Color.RED;
            final String performanceText = "   " + performance + "% ";

            this.jLabelPerformance.setBackground(background);
            this.jLabelPerformance.setText(performanceText.substring(performanceText.length() - 6, performanceText.length()));
        }
    }

    /**
     * Start a timer which clears the drive state after some time
     */
    private void startDriveStateTimer() {
        if (null != this.driveStateTimer) {
            this.driveStateTimer.cancel();
        }
        this.driveStateTimer = new Timer();
        this.driveInactiveTask = new TimerTask() {

            @Override
            public void run() {
                jLabelTrackSector.setText("");
                driveStateTimer = null;
                showDriveIcon(null);
            }
        };
        this.driveStateTimer.schedule(this.driveInactiveTask, CLEAR_DRIVE_STATE_TIME);
    }

    /**
     * Show the drive icon depending on the drive state
     *
     * @param   state as defined in class Drive or null for an inactive drive
     */
    protected void showDriveIcon(final Integer state) {
        // an update of the icon is really necessary?
        if (null == this.driveStateTimer || !state.equals(this.currentDriveState) || this.driveInactiveTask.scheduledExecutionTime() - new java.util.Date().getTime() < CLEAR_DRIVE_STATE_TIME / 5) {
            // determine and show the correct drive access icon
            final String icon = "/res/swing/LED_" + (C1541.READING == state ? "reading" : C1541.WRITING == state ? "writing" : "inactive") + ".gif";
            final String tooltip = (C1541.READING == state ? this.c64Resources.getString("DriveIsReadingData") : C1541.WRITING == state ? this.c64Resources.getString("DriveIsWritingData") : this.c64Resources.getString("DriveIsInactive"));

            this.jLabelDrives.setIcon(new javax.swing.ImageIcon(getClass().getResource(icon)));
            this.jLabelDrives.setToolTipText(tooltip);

            this.currentDriveState = state;

            startDriveStateTimer();
        }

        // in automatic mode we disable throttling on disk access
        if (this.automaticTurboMode) {
            if (null != state) {
                if (null != this.driveStateTimer) {
                    this.c64.setThrottlingEnabled(false);
                }
            } else {
                this.c64.setThrottlingEnabled(true);
            }
        }
    }
}
