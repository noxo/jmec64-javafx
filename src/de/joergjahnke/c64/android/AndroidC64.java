/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import de.joergjahnke.c64.core.C1541;
import de.joergjahnke.c64.core.C64;
import de.joergjahnke.c64.drive.DriveHandler;
import de.joergjahnke.c64.extendeddevices.EmulatorUtils;
import de.joergjahnke.common.android.WavePlayer;
import de.joergjahnke.common.util.Observer;
import de.joergjahnke.common.vmabstraction.androidvm.AndroidVMResourceLoader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Main class for the Android version of the emulator
 * 
 * @author Jörg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class AndroidC64 extends Activity implements Observer {

    /**
     * File extension for the gzipped files that contain the delta for a C64 file image
     */
    public final static String DELTA_FILE_EXTENSION = ".gzd";
    /**
     * Name of the application preferences
     */
    public final static String PREFS_NAME = "C64Preferences";
    /**
     * Setting name for file search starting directory
     */
    private final static String SETTING_FILESEARCH_STARTDIR = "FileSearchStartDir";
    /**
     * Setting for the joystick port
     */
    private final static String SETTING_JOYSTICK_PORT = "JoystickPort";
    /**
     * Setting for drive mode
     */
    private final static String SETTING_DRIVE_MODE = "DriveMode";
    /**
     * Setting for frame-skip
     */
    private final static String SETTING_FRAMESKIP = "FrameSkip";
    /**
     * Setting name switching antialiasing on/off
     */
    private final static String SETTING_ANTIALIASING = "Antialiasing";
    /**
     * Setting name for setting sound on/off
     */
    protected final static String SETTING_SOUNDACTIVE = "SoundActive";
    /**
     * Setting name for setting orientation sensor support on/off
     */
    protected final static String SETTING_ORIENTATIONSENSORACTIVE = "OrientationSensorActive";
    /**
     * number of milliseconds after which we clear the drive state LED
     */
    private final static int CLEAR_DRIVE_STATE_TIME = 1000;
    // menu item IDs
    private final static int MENU_TYPETEXT = 1;
    private final static int MENU_SPECIALKEYS = 2;
    private final static int MENU_ATTACHIMAGE = 3;
    private final static int MENU_DETACHIMAGES = 4;
    private final static int MENU_LOADFILE = 5;
    private final static int MENU_SELECTDRIVE = 6;
    private final static int MENU_RUN = 7;
    private final static int MENU_RESET = 8;
    private final static int MENU_SETTINGS = 9;
    private final static int MENU_SHOWLOG = 10;
    private final static int MENU_ABOUT = 11;
    private final static int MENU_HELP = 12;
    private final static int MENU_EXIT = 14;
    /**
     * URL of the online help page
     */
    private final static String URL_ONLINE_HELP = "http://sourceforge.net/apps/mediawiki/jmec64/index.php?title=Mobile_C64_Online_Help";
    /**
     * message we send to repaint the status icon
     */
    protected final static int MSG_REPAINT_STATUS_ICON = 1;
    /**
     * number of drives supported by the Android version
     */
    private final static int NUM_DRIVES = 2;
    /**
     * main frame
     */
    private C64View frame = null;
    /**
     * C64 instance
     */
    private C64 c64 = null;
    /**
     * the main menu
     */
    private Menu mainmenu = null;
    /**
     * emulator preferences
     */
    private SharedPreferences prefs = null;
    /**
     * last attached file
     */
    private File currentlyAttachedFile = null;
    /**
     * status icon
     */
    private LayerDrawable statusIcon = null;
    private Drawable[] statusIconLayers = new Drawable[4];
    /**
     * message handler for this thread
     */
    protected Handler handler = null;
    /**
     * timer we install to reset the drive state icon after a short period of time
     */
    private Timer driveStateTimer = null;
    private TimerTask driveInactiveTask = null;
    /**
     * the current configuration
     */
    private Configuration config;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);

        // request some display features depending on the display size
        if (getResources().getDisplayMetrics().heightPixels >= 250) {
            requestWindowFeature(Window.FEATURE_LEFT_ICON);
            requestWindowFeature(Window.FEATURE_RIGHT_ICON);
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }

        // store the current configuration
        this.config = new Configuration();

        this.config.setToDefaults();

        try {
            // create and initialize the Android resource loader
            final AndroidVMResourceLoader resourceLoader = new AndroidVMResourceLoader();

            resourceLoader.addResource("/roms/kernal.c64", getResources().openRawResource(R.raw.kernal));
            resourceLoader.addResource("/roms/basic.c64", getResources().openRawResource(R.raw.basic));
            resourceLoader.addResource("/roms/chargen.c64", getResources().openRawResource(R.raw.chargen));
            resourceLoader.addResource("/roms/floppy.c64", getResources().openRawResource(R.raw.floppy));

            // retrieve the application preferences
            this.prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            // create the C64
            this.c64 = new C64(resourceLoader);

            // register as observer to get notified of changes to floppy disks
            for (int i = 0; i < NUM_DRIVES; ++i) {
                this.c64.getDrive(i).addObserver(this);
            }

            // initialize the display
            this.frame = new C64View(this);
            setContentView(this.frame);

            // apply some settings
            setSound(this.prefs.getBoolean(SETTING_SOUNDACTIVE, false));
            //activateOrientationSensorNotifier( this.prefs.getBoolean( SETTING_ORIENTATIONSENSORACTIVE, false ) );
            this.frame.setUseAntialiasing(this.prefs.getBoolean(SETTING_ANTIALIASING, this.frame.isUseAntialiasing()));

            // add title and status icons
            try {
                // add title icon
                setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.a64_sm);

                // add status icons as LayerDrawable
                final int[] resIds = {R.drawable.speedometer_green, R.drawable.speedometer_yellow, R.drawable.speedometer_red, R.drawable.drive_active};

                for (int i = 0; i < this.statusIconLayers.length; ++i) {
                    this.statusIconLayers[i] = new BitmapDrawable(BitmapFactory.decodeResource(getResources(), resIds[i]));
                }
                this.statusIcon = new LayerDrawable(this.statusIconLayers);
                setFeatureDrawable(Window.FEATURE_RIGHT_ICON, this.statusIcon);
                for (int i = 0; i < this.statusIconLayers.length; ++i) {
                    this.statusIconLayers[i].setAlpha(0);
                }

                // initialize a handler which we can use to process messages from other threads
                this.handler = new Handler() {

                    public void handleMessage(final Message msg) {
                        if (msg.what == MSG_REPAINT_STATUS_ICON) {
                            statusIcon.invalidateSelf();
                        }
                    }
                };

                // also register as observer to get informed about drive operations and the C64 performance
                for (int i = 0; i < NUM_DRIVES; ++i) {
                    this.c64.getDrive(i).addObserver(this);
                }
                this.c64.addObserver(this);
            } catch (Exception e) {
                // we could not add the icons, that's OK
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(getResources().getString(R.string.msg_couldNotInitialize) + t);
        }
    }

    /**
     * Start the C64 instance
     */
    @Override
    public void onStart() {
        if (!this.c64.isRunning()) {
            // inform the view about the C64 instance
            this.frame.setC64(c64);

            // apply settings
            this.c64.setActiveJoystick(this.prefs.getInt(SETTING_JOYSTICK_PORT, this.c64.getActiveJoystick()));
            setDriveMode(this.prefs.getInt(SETTING_DRIVE_MODE, this.c64.getDrive(0).getEmulationLevel()));

            final int frameSkip = this.prefs.getInt(SETTING_FRAMESKIP, 0);

            this.c64.setDoAutoAdjustFrameskip(frameSkip == 0);
            if (frameSkip > 0) {
                this.c64.getVIC().setFrameSkip(frameSkip);
            }

            // start the emulation
            new Thread(this.c64).start();
        }

        super.onStart();
    }

    /**
     * Pause the emulator when another activity is used
     */
    @Override
    public void onPause() {
        this.c64.pause();

        super.onPause();
    }

    /**
     * Resume the emulator when the user returns back to it
     */
    @Override
    public void onResume() {
        this.c64.resume();

        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        this.mainmenu = menu;

        boolean result = super.onCreateOptionsMenu(menu);

        menu.add(Menu.NONE, MENU_TYPETEXT, 0, R.string.menu_typeText);
        menu.add(Menu.NONE, MENU_SPECIALKEYS, 1, R.string.menu_specialKeys);
        menu.add(Menu.NONE, MENU_ATTACHIMAGE, 2, R.string.menu_attachImage);
        menu.add(Menu.NONE, MENU_LOADFILE, 3, R.string.menu_loadFile);
        menu.findItem(MENU_LOADFILE).setVisible(false);
        menu.add(Menu.NONE, MENU_DETACHIMAGES, 4, R.string.menu_detachImages);
        menu.findItem(MENU_DETACHIMAGES).setVisible(false);
        menu.add(Menu.NONE, MENU_SELECTDRIVE, 5, R.string.menu_selectDrive);
        menu.add(Menu.NONE, MENU_RUN, 6, R.string.menu_run);
        menu.add(Menu.NONE, MENU_RESET, 8, R.string.menu_reset);
        menu.add(Menu.NONE, MENU_SETTINGS, 9, R.string.menu_settings);
        menu.add(Menu.NONE, MENU_SHOWLOG, 10, R.string.menu_showLog);
        menu.add(Menu.NONE, MENU_ABOUT, 11, R.string.menu_about);
        menu.add(Menu.NONE, MENU_HELP, 12, R.string.menu_help);
        menu.add(Menu.NONE, MENU_EXIT, 15, R.string.menu_exit);

        return result;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case MENU_TYPETEXT: {
                final Intent typeTextIntent = new Intent();

                typeTextIntent.setClass(this, TypeTextDialog.class);
                typeTextIntent.putExtra("de.joergjahnke.c64.android.oldTexts", this.c64.getKeyboard().getTypedTexts());
                startActivityForResult(typeTextIntent, MENU_TYPETEXT);
                break;
            }
            case MENU_SPECIALKEYS:
                startActivityForResult(new Intent(this, SpecialKeysDialog.class), MENU_SPECIALKEYS);
                break;
            case MENU_ATTACHIMAGE: {
                final Intent loadFileIntent = new Intent();

                loadFileIntent.setClass(this, AttachImageDialog.class);
                loadFileIntent.putExtra("de.joergjahnke.c64.android.prgdir", this.prefs.getString(SETTING_FILESEARCH_STARTDIR, "/"));
                startActivityForResult(loadFileIntent, MENU_ATTACHIMAGE);
                break;
            }
            case MENU_LOADFILE: {
                final Intent loadFileIntent = new Intent();

                loadFileIntent.setClass(this, LoadFileDialog.class);
                loadFileIntent.putExtra("de.joergjahnke.c64.android.files", this.c64.getDrive(this.c64.getActiveDrive()).getFilenames());
                startActivityForResult(loadFileIntent, MENU_LOADFILE);
                break;
            }
            case MENU_DETACHIMAGES: {
                // detach all images
                for (int i = 0; i < NUM_DRIVES; ++i) {
                    this.c64.getDrive(i).detachImage();
                }
                // disable menu items for loading a file from the current image and for detaching images
                this.mainmenu.findItem(MENU_LOADFILE).setVisible(false);
                this.mainmenu.findItem(MENU_DETACHIMAGES).setVisible(false);

                showTimedAlert(getResources().getString(R.string.title_imagesDetached), getResources().getString(R.string.msg_imagesDetached));
                break;
            }
            case MENU_SELECTDRIVE:
                startActivityForResult(new Intent(this, SelectDriveDialog.class), MENU_SELECTDRIVE);
                break;
            case MENU_RUN:
                this.c64.getKeyboard().textTyped("run");
                this.c64.getKeyboard().keyTyped("ENTER");
                break;
            case MENU_RESET:
                this.c64.reset();
                break;
            case MENU_SETTINGS: {
                final Intent settingsIntent = new Intent();

                settingsIntent.setClass(this, EditSettingsDialog.class);
                settingsIntent.putExtra("de.joergjahnke.c64.android.joystickPort", this.c64.getActiveJoystick());
                settingsIntent.putExtra("de.joergjahnke.c64.android.frameSkip", this.c64.doAutoAdjustFrameskip() ? 0 : this.c64.getVIC().getFrameSkip());
                settingsIntent.putExtra("de.joergjahnke.c64.android.antialiasing", this.frame.isUseAntialiasing());
                settingsIntent.putExtra("de.joergjahnke.c64.android.driveMode", this.c64.getDrive(0).getEmulationLevel());
                settingsIntent.putExtra("de.joergjahnke.c64.android.soundActive", this.c64.getSID().countObservers() > 0);
                settingsIntent.putExtra("de.joergjahnke.c64.android.orientationSensorActive", this.prefs.getBoolean(SETTING_ORIENTATIONSENSORACTIVE, false));
                startActivityForResult(settingsIntent, MENU_SETTINGS);
                break;
            }
            case MENU_SHOWLOG:
                showAlert(getResources().getString(R.string.title_logMessages), this.c64.getLogger().dumpAll());
                break;
            case MENU_ABOUT:
                showAlert(getResources().getString(R.string.title_about), getResources().getString(R.string.msg_about));
                break;
            case MENU_HELP: {
                // try to open the online help page in a new browser window
                Intent help = null;

                try {
                    help = new Intent("android.intent.action.VIEW", Uri.parse(URL_ONLINE_HELP));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                startActivity(help);
                break;
            }
            case MENU_EXIT:
                this.finish();
                break;

            default:
                showAlert(getResources().getString(R.string.title_warning), getResources().getString(R.string.msg_notImplemented));
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent extras) {
        super.onActivityResult(requestCode, resultCode, extras);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case MENU_TYPETEXT: {
                    final String data = extras.getStringExtra("de.joergjahnke.c64.android.typedText");

                    this.c64.getKeyboard().textTyped(data);
                    if (data.endsWith("\n")) {
                        this.c64.getKeyboard().keyTyped("ENTER");
                    }
                    break;
                }
                case MENU_SPECIALKEYS:
                    this.c64.getKeyboard().keyTyped(extras.getStringExtra("de.joergjahnke.c64.android.selectedKey").toUpperCase());
                    break;
                case MENU_ATTACHIMAGE: {
                    final String data = extras.getStringExtra("de.joergjahnke.gameboy.android.currentFile");

                    try {
                        // attach the file
                        this.currentlyAttachedFile = EmulatorUtils.attachImage(this.c64, this.c64.getActiveDrive(), data);

                        // re-enable some menu items
                        this.mainmenu.findItem(MENU_LOADFILE).setVisible(true);
                        this.mainmenu.findItem(MENU_DETACHIMAGES).setVisible(true);

                        // show success
                        showTimedAlert(getResources().getString(R.string.title_imageAttached), getResources().getString(R.string.msg_imageAttached).replaceFirst("\\#", data));

                        // save directory we selected from, so that this directory appears initially when attaching the next file
                        final Editor prefsEditor = this.prefs.edit();

                        prefsEditor.putString(SETTING_FILESEARCH_STARTDIR, this.currentlyAttachedFile.getParent());
                        prefsEditor.commit();
                    } catch (Exception e) {
                        showAlert(getResources().getString(R.string.title_warning), getResources().getString(R.string.msg_imageNotAttached).replaceFirst("\\#", data) + e);
                    }
                    break;
                }
                case MENU_LOADFILE: {
                    final String filename = extras.getStringExtra("de.joergjahnke.c64.android.selectedFile");
                    final String loadType = extras.getStringExtra("de.joergjahnke.c64.android.loadType");

                    try {
                        if (LoadFileDialog.LOADTYPE_NORMAL_LOAD.equalsIgnoreCase(loadType)) {
                            this.c64.loadFile(filename);
                        } else if (LoadFileDialog.LOADTYPE_FAST_LOAD.equalsIgnoreCase(loadType)) {
                            this.c64.fastLoadFile(filename, -1);
                        } else {
                            this.c64.fastLoadFile(filename, -1);
                            this.c64.getKeyboard().textTyped("run");
                            this.c64.getKeyboard().keyTyped("ENTER");
                            showTimedAlert(getResources().getString(R.string.title_fileLoaded), getResources().getString(R.string.msg_fileLoaded).replaceFirst("\\#", filename));
                        }
                    } catch (Exception e) {
                        showAlert(getResources().getString(R.string.title_warning), getResources().getString(R.string.msg_fileNotLoaded).replaceFirst("\\#", filename) + e);
                    }
                    break;
                }
                case MENU_SELECTDRIVE: {
                    final int n = extras.getIntExtra("de.joergjahnke.c64.android.drive", 0);

                    if (n != this.c64.getActiveDrive()) {
                        this.c64.setActiveDrive(n);
                        showTimedAlert(getResources().getString(R.string.title_driveChanged), getResources().getString(R.string.msg_driveChanged).replaceFirst("\\#", Integer.toString(n + 1)));
                    }
                    break;
                }
                case MENU_SETTINGS: {
                    final Editor prefsEditor = this.prefs.edit();
                    final int joystickPort = extras.getIntExtra("de.joergjahnke.c64.android.joystickPort", this.c64.getActiveJoystick());

                    this.c64.setActiveJoystick(joystickPort);
                    prefsEditor.putInt(SETTING_JOYSTICK_PORT, joystickPort);

                    final int frameSkip = extras.getIntExtra("de.joergjahnke.c64.android.frameSkip", this.c64.doAutoAdjustFrameskip() ? 0 : this.c64.getVIC().getFrameSkip());

                    if (frameSkip == 0) {
                        this.c64.setDoAutoAdjustFrameskip(true);
                    } else {
                        this.c64.setDoAutoAdjustFrameskip(false);
                        this.c64.getVIC().setFrameSkip(frameSkip);
                    }
                    prefsEditor.putInt(SETTING_FRAMESKIP, frameSkip);

                    final boolean useAntialiasing = extras.getBooleanExtra("de.joergjahnke.c64.android.antialiasing", this.frame.isUseAntialiasing());

                    this.frame.setUseAntialiasing(useAntialiasing);
                    prefsEditor.putBoolean(SETTING_ANTIALIASING, useAntialiasing);

                    final int driveMode = extras.getIntExtra("de.joergjahnke.c64.android.driveMode", this.c64.getDrive(0).getEmulationLevel());

                    setDriveMode(driveMode);
                    prefsEditor.putInt(SETTING_DRIVE_MODE, driveMode);

                    final boolean soundActive = extras.getBooleanExtra("de.joergjahnke.c64.android.soundActive", this.c64.getSID().countObservers() > 0);

                    setSound(soundActive);
                    prefsEditor.putBoolean(SETTING_SOUNDACTIVE, soundActive);

                    final boolean orientationSensorActive = extras.getBooleanExtra("de.joergjahnke.c64.android.orientationSensorActive", this.prefs.getBoolean(SETTING_ORIENTATIONSENSORACTIVE, false));

                    activateOrientationSensorNotifier(orientationSensorActive);
                    prefsEditor.putBoolean(SETTING_ORIENTATIONSENSORACTIVE, orientationSensorActive);

                    prefsEditor.commit();
                    break;
                }
            }
        }
    }
    
    @Override
    public void finish() {
		// then ask the user whether he really wants to exit
        final AlertDialog dialog = new android.app.AlertDialog.Builder(this).setTitle(R.string.title_reallyExit).setMessage(R.string.msg_reallyExit).create();

        dialog.setButton(getResources().getText(R.string.msg_yes), new DialogInterface.OnClickListener() {
        	public void onClick(final DialogInterface dialog, final int which) {
        		finish(false);
        	}
        });
        dialog.setButton2(getResources().getText(R.string.msg_no), new DialogInterface.OnClickListener() {
        	public void onClick(final DialogInterface dialog, final int which) {
        	}
        });
        dialog.show();
    }

    /**
     * We stop the emulator thread when stopping the activity
     */
    @Override
    public void onDestroy() {
        this.c64.stop();

        super.onDestroy();
    }

    /**
     * Finish the application
     * 
     * @param confirm	true if a confirmation from the user should be requested, false to finish without confirmation
     */
    public void finish(final boolean confirm) {
    	if(!confirm) {
    		super.finish();
    	} else {
    		finish();
    	}
    }

    /**
     * Set a new mode for the C1541 floppies
     * 
     * @param driveMode	new drive mode, e.g. C1541.BALANCED_MODE
     */
    private void setDriveMode(final int driveMode) {
        for (int i = 0; i < NUM_DRIVES; ++i) {
            this.c64.getDrive(i).setEmulationLevel(driveMode);
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

            public void run() {
                statusIconLayers[ 3].setAlpha(0);
                handler.sendMessage(Message.obtain(handler, MSG_REPAINT_STATUS_ICON));
                driveStateTimer = null;
            }
        };
        this.driveStateTimer.schedule(this.driveInactiveTask, CLEAR_DRIVE_STATE_TIME);
    }

    /**
     * Show alert message
     * 
     * @param title	dialog title
     * @param message	message to display
     * @return	dialog instance
     */
    protected AlertDialog showAlert(final CharSequence title, final CharSequence message) {
        final AlertDialog dialog = new android.app.AlertDialog.Builder(this).setTitle(title).setMessage(message).create();

        dialog.show();
        return dialog;
    }

    /**
     * Show and automatically dismiss an alert message
     * 
     * @param	title	message title
     * @param	message	message text
     * @param	buttonText	button text of the dialog's button
     * @return	created dialog
     */
    private DialogInterface showTimedAlert(final CharSequence title, final CharSequence message) {
        final AlertDialog result = showAlert(title, message);

        this.handler.postDelayed(new Runnable() {

            public void run() {
                result.dismiss();
            }
        }, 2000);

        return result;
    }

    /**
     * Switch the sound on/off
     *
     * @param   active  true to switch the sound on, false to switch it off
     */
    protected void setSound(final boolean active) {
        if (active) {
            if (this.c64.getSID().countObservers() == 0) {
                try {
                    this.c64.getSID().addObserver(new WavePlayer(this.c64.getSID()));
                } catch (Throwable t) {
                    // we could not add a player, that's OK
                    this.c64.getLogger().warning("Could not create sound player! Sound output remains deactivated.");
                    t.printStackTrace();
                }
            }
        } else {
            if (this.c64.getSID().countObservers() > 0) {
                this.c64.getSID().deleteObservers();
            }
        }
    }

    /**
     * We try to attach/detach an orientation sensor notifier
     *
     * @param	active	true to activate, false to deactivate the notifier
     */
    protected void activateOrientationSensorNotifier(final boolean active) {
        if (false && !((SensorManager) getSystemService(Context.SENSOR_SERVICE)).getSensorList(Sensor.TYPE_ORIENTATION).isEmpty()) {
            final SensorManager manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

            manager.unregisterListener(this.frame);
            if (active) {
                manager.registerListener(this.frame, manager.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_GAME);
            }
        }
    }

    // implementation of the Observer interface
    /**
     * We save changes to a disk image when the disk is unmounted
     */
    public void update(Object observed, Object arg) {
        // this update is from the C64's drive?
        if (observed instanceof C1541) {
            // a modified drive was reported?
            if (arg instanceof DriveHandler) {
                // save the delta to the current file
                try {
                    // create the delta to the original image
                    final InputStream in = new BufferedInputStream(new FileInputStream(this.currentlyAttachedFile));
                    final byte[] delta = ((DriveHandler) arg).createDelta(in);

                    // anything to do?
                    if (delta.length > 0) {
                        // save the delta to a file
                        final File deltaFile = EmulatorUtils.saveDeltaFile(this.currentlyAttachedFile.getAbsolutePath(), delta);

                        this.c64.getLogger().info("Saved changes to file '" + deltaFile + "'!");
                    }
                } catch (IOException e) {
                    // we could not save the delta
                    e.printStackTrace();

                    final String warning = "Could not save changes to image '" + this.currentlyAttachedFile + "'!";

                    this.c64.getLogger().warning(warning);
                    showAlert(getResources().getString(R.string.title_warning), warning);
                }
            // the drive state (reading/writing) was reported?
            } else if (arg instanceof Integer) {
                if (null == this.driveStateTimer || this.driveInactiveTask.scheduledExecutionTime() - new java.util.Date().getTime() < 200) {
                    try {
                        // show the new drive state
                        this.statusIconLayers[ 3].clearColorFilter();
                        startDriveStateTimer();
                        handler.sendMessage(Message.obtain(this.handler, MSG_REPAINT_STATUS_ICON));
                    } catch (NullPointerException e) {
                        // we might fail if no title bar is displayed, that's OK
                    }
                }
            }
        // we have a new performance measurement result?
        } else if (observed == this.c64) {
            // then display this with the corresponding color
            final int performance = this.c64.getPerformance();
            final int background = performance >= 120
                    ? Color.CYAN
                    : performance >= 90
                    ? Color.GREEN
                    : performance >= 80
                    ? Color.YELLOW
                    : Color.RED;

            switch (background) {
                case Color.CYAN:
                case Color.GREEN:
                    this.statusIconLayers[ 0].setAlpha(0xff);
                    this.statusIconLayers[ 1].setAlpha(0);
                    this.statusIconLayers[ 2].setAlpha(0);
                    break;
                case Color.YELLOW:
                    this.statusIconLayers[ 0].setAlpha(0);
                    this.statusIconLayers[ 1].setAlpha(0xff);
                    this.statusIconLayers[ 2].setAlpha(0);
                    break;
                case Color.RED:
                    this.statusIconLayers[ 0].setAlpha(0);
                    this.statusIconLayers[ 1].setAlpha(0);
                    this.statusIconLayers[ 2].setAlpha(0xff);
                    break;
                default:
                    ;
            }

            handler.sendMessage(Message.obtain(this.handler, MSG_REPAINT_STATUS_ICON));
        }
    }
}
