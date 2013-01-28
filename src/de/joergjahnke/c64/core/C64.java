/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.common.emulation.PerformanceMeter;
import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.io.SerializationUtils;
import de.joergjahnke.common.util.DefaultLogger;
import de.joergjahnke.common.util.Observer;
import de.joergjahnke.common.vmabstraction.ResourceLoader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

/**
 * C64 instance which manages the MOS6510, the video-chip, the CIAs etc.<br>
 * <br>
 * For a good German documentation on the C64 setup see <a href='http://www.htu.tugraz.at/~herwig/c64/'>http://www.htu.tugraz.at/~herwig/c64/</a>,
 * <a href='http://www.zimmers.net/cbmpics/cbm/c64/c64prg.txt'>http://www.zimmers.net/cbmpics/cbm/c64/c64prg.txt</a>
 * or <a href='http://www.dwe.at/thesis/DA-C64/pages/page00.htm'>http://www.dwe.at/thesis/DA-C64/pages/page00.htm</a>.
 * For a good English documentation see <a href='http://www.unusedino.de/ec64/technical/project64/mapping_c64.html'>http://www.unusedino.de/ec64/technical/project64/mapping_c64.html</a>.
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class C64 extends EmulatedDevice implements Observer, Serializable {

    /**
     * Maximum frame-skip value to use
     */
    private final static int FRAMESKIP_MAX = 4;
    /**
     * the C64 runs with ~1 MHz
     */
    public final static int ORIGINAL_SPEED = 985248;
    /**
     * default sampling size for the sid
     */
    private final static int SID_SAMPLE_SIZE = 8000;
    /**
     * number of supported C1541 drives
     */
    public final static int MAX_NUM_DRIVES = 4;
    /**
     * attached C1541 disk drives
     */
    private C1541 drives[];
    /**
     * the currently active floppy drive
     */
    private int activeDrive = 0;
    /**
     * CIA chips 1&2
     */
    private CIA6526 cias[];
    /**
     * serial bus
     */
    private IECBus iecBus;
    /**
     * video chip
     */
    private VIC6569 vic;
    /**
     * sound chip
     */
    private SID6581 sid;
    /**
     * keyboard attached to the unit
     */
    private Keyboard keyboard;
    /**
     * joysticks attached to the C64
     */
    private Joystick[] joysticks;
    /**
     * index of currently active joystick
     */
    private int activeJoystick = 0;
    /**
     * the minimum frameskip value we use
     */
    private int frameSkipMin = 1;
    /**
     * automatically adjust frameskip value to get best performance?
     */
    private boolean doAutoAdjustFrameskip = true;
    /**
     * performance meter used to measure and throttle, if necessary, the performance
     */
    private PerformanceMeter performanceMeter;
    /**
     * number of attached drives
     */
    private final int numDrives;

    /**
     * Create a new C64 instance
     *
     * @param   resourceLoader  ResourceLoader instance used for loading the Kernal ROM etc.
     */
    public C64(final ResourceLoader resourceLoader) {
        this(resourceLoader, MAX_NUM_DRIVES);
    }

    /**
     * Create a new C64 instance
     *
     * @param   resourceLoader  ResourceLoader instance used for loading the Kernal ROM etc.
     * @param   numDrives   number of supported disk drives (1-4)
     */
    public C64(final ResourceLoader resourceLoader, final int numDrives) {
        super("C64", resourceLoader);
        
        if (numDrives < 1 && numDrives > 4) {
            throw new IllegalArgumentException("Number of C64 drives must be between 1 and 4!");
        }
        this.numDrives = numDrives;

        // initialize the logger
        setLogger(new DefaultLogger(100));

        // we observe ourselves to ensure the correct emulator speed
        this.addObserver(this);

        // initialize the IO chips
        this.cias = new CIA6526[2];
        this.cias[0] = new CIA6526_1(this);
        this.cias[1] = new CIA6526_2(this);
        setVIC(new VIC6569(this));
        this.iecBus = new IECBus(this);
        this.sid = new SID6581(this, SID_SAMPLE_SIZE);

        // initialize keyboards and joysticks
        this.keyboard = new Keyboard();
        this.joysticks = new Joystick[2];
        this.joysticks[0] = new Joystick();
        this.joysticks[1] = new Joystick();

        // initialize the floppy drive
        this.drives = new C1541[getDriveCount()];
        for (int i = 0; i < getDriveCount(); ++i) {
            this.drives[i] = new C1541(i, resourceLoader, this.iecBus);
            this.drives[i].setLogger(getLogger());
        }

        // initialize performance measurements
        this.performanceMeter = new PerformanceMeter(this.cpu, ORIGINAL_SPEED);
        this.performanceMeter.addObserver(this);

        getLogger().info(getName() + " initialized");
    }

    /**
     * Return the video chip instance
     *
     * @return VIC instance
     */
    public final VIC6569 getVIC() {
        return this.vic;
    }

    /**
     * Assign the video chip instance
     *
     * @param vic   the new VIC instance
     */
    public final void setVIC(final VIC6569 vic) {
        if (isRunning() && !isPaused()) {
            throw new IllegalStateException("C64 must be paused while setting a new VIC!");
        }

        if (null != this.vic) {
            this.vic.deleteObserver(this);
            this.cias[1].deleteObserver(this.vic);
        }
        this.vic = vic;
        this.vic.addObserver(this);
        ((C64CPU6510) this.cpu).setVIC(vic);
        this.cias[1].addObserver(vic);
    }

    /**
     * Get the IECBus emulation
     *
     * @return the IECBus instance
     */
    public final IECBus getIECBus() {
        return this.iecBus;
    }

    /**
     * Get a CIA chip
     *
     * @param   n   cia ID
     * @return  CIA instance
     */
    public final CIA6526 getCIA(final int n) {
        return this.cias[n];
    }

    /**
     * Get the keyboard instance
     *
     * @param the keyboard instance
     */
    public final Keyboard getKeyboard() {
        return this.keyboard;
    }

    /**
     * Get the sound chip
     *
     * @return  the SID instance
     */
    public final SID6581 getSID() {
        return this.sid;
    }

    /**
     * Get the an attached C1541 drive
     *
     * @param   n   drive ID
     * @return  Drive instance
     */
    public final C1541 getDrive(final int n) {
        return this.drives[n];
    }

    /**
     * Get the index of the currently active floppy drive
     *
     * @return  drive ID (0-3)
     */
    public int getActiveDrive() {
        return this.activeDrive;
    }

    /**
     * Set the currently active floppy drive
     *
     * @param   n   index of the active drive (0-3)
     */
    public void setActiveDrive(final int n) {
        this.activeDrive = n;
    }

    /**
     * Get the number of attached drives
     *
     * @return the number of attached drives
     */
    public final int getDriveCount() {
        return this.numDrives;
    }

    /**
     * Get a joystick
     *
     * @param   n   joystick ID
     * @return joystick instance
     */
    public final Joystick getJoystick(final int n) {
        return this.joysticks[n];
    }

    /**
     * Get the index of the currently active joystick
     *
     * @return  0 or 1 for joystick 1 and 2 respectively
     */
    public int getActiveJoystick() {
        return this.activeJoystick;
    }

    /**
     * Set the currently active joystick
     *
     * @param   n   index of the active joystick
     */
    public void setActiveJoystick(final int n) {
        if (n < 0 || n > 1) {
            throw new IllegalArgumentException("Cannot activate joystick ID " + n + "!");
        }
        this.activeJoystick = n;
    }

    /**
     * Check whether the C64 has finished booting after a reset
     *
     * @return  true if the C64 is ready, otherwise false
     */
    public final boolean isReady() {
        return ((CIA6526_1) this.cias[0]).getPRBReads() >= 20;
    }

    /**
     * Get the result of the last performance measurement
     *
     * @return  performance in percent of the original C64 performance
     */
    public final int getPerformance() {
        return this.performanceMeter.getLastPerformance();
    }

    /**
     * Get the result of the last throttle time measurement
     *
     * @return  percentage the CPU was throttled during the last measurement interval
     */
    public final int getThrottlePercentage() {
        return this.performanceMeter.getThrottlePercentage();
    }

    /**
     * Set CPU throttling
     *
     * @param   doThrottling    true to limit the CPU to a maximum of 100% of the C64's original speed
     */
    public void setThrottlingEnabled(final boolean doThrottling) {
        this.performanceMeter.setDoThrottling(doThrottling);
        if (doThrottling) {
            this.performanceMeter.resetThrottleMeasurement(this.cpu.getCycles());
        }
    }

    /**
     * Check whether we automatically adjust the frameskip value for best performance
     * 
     * @return  true if the frameskip value gets modified automatically, false if it is fixed
     */
    public boolean doAutoAdjustFrameskip() {
        return doAutoAdjustFrameskip;
    }

    /**
     * Determine whether we automatically adjust the frameskip value for best performance
     * 
     * @param doAutoAdjustFrameskip true to automatically adjust the frameskip value, false to keep it fixed
     */
    public void setDoAutoAdjustFrameskip(final boolean doAutoAdjustFrameskip) {
        this.doAutoAdjustFrameskip = doAutoAdjustFrameskip;
    }

    /**
     * Also stop the floppy when stopping the C64
     */
    public void stop() {
        for (int i = 0; i < getDriveCount(); ++i) {
            this.drives[i].pause();
        }
        super.stop();
        for (int i = 0; i < getDriveCount(); ++i) {
            this.drives[i].stop();
            this.drives[i].detachImage();
        }
    }

    /**
     * Load a file by issuing the LOAD "<filename>" command
     *
     * @param   filename file name to load
     */
    public void loadFile(final String filename) {
        getKeyboard().textTyped("Load \"" + filename + "\"," + (getActiveDrive() + 8) + ",1");
        getKeyboard().keyTyped("ENTER");
    }

    /**
     * Load a file from the currently loaded image
     *
     * @param   filename file name to load
     * @param   address memory address to load to
     * @throws  IOException if the file cannot be loaded
     */
    public void fastLoadFile(final String filename, final int address) throws IOException {
        int endAddress = this.cpu.copyBytesToMemory(getDrive(this.activeDrive).readFile(filename, C64FileEntry.TYPE_PROGRAM), address);

        this.cpu.writeByte(0xae, (byte) (endAddress & 0xff));
        this.cpu.writeByte(0xaf, (byte) ((endAddress >> 8) & 0xff));

        if (endAddress > 0x9f00) {
            endAddress = 0x9f00;
        }
        this.cpu.writeByte(0x2d, (byte) (endAddress & 0xff));
        this.cpu.writeByte(0x2f, (byte) (endAddress & 0xff));
        this.cpu.writeByte(0x31, (byte) (endAddress & 0xff));
        this.cpu.writeByte(0x2e, (byte) ((endAddress & 0xff00) >> 8));
        this.cpu.writeByte(0x30, (byte) ((endAddress & 0xff00) >> 8));
        this.cpu.writeByte(0x32, (byte) ((endAddress & 0xff00) >> 8));
    }

    // implementation of the Observer interface
    /**
     * Adapt the frame-skip value depending on the performance and throttle the CPU if necessary
     */
    public void update(final Object observed, final Object arg) {
        // this update is for the C64's CPU performance?
        if (observed == this) {
            if (doAutoAdjustFrameskip()) {
                final int performance = getPerformance();
                final int frameskip = getVIC().getFrameSkip();

                // check if we were idle for quite some time and can decrease the frame-skip minimum
                if (getThrottlePercentage() >= 50 && this.frameSkipMin > 1) {
                    --this.frameSkipMin;
                }
                // we are fast enough to reduce the frame-skip rate?
                if (performance > 95 && frameskip > this.frameSkipMin) {
                    getVIC().setFrameSkip(frameskip - 1);
                // are we too slow and have to decrease the frameskip rate?
                } else if (performance < 90 && frameskip < FRAMESKIP_MAX) {
                    getVIC().setFrameSkip(frameskip + 1);
                    // we won't try the higher speed again
                    if (this.frameSkipMin <= FRAMESKIP_MAX) {
                        ++this.frameSkipMin;
                    }
                }
            }
        // an update from the VIC when it has painted a full screen
        } else if (observed == this.vic) {
            // we use this regular update to initiate performance measurements
            this.performanceMeter.measure(this.cpu.getCycles());
        } else if (observed == this.performanceMeter) {
            // log the message and propagate the information to listeners of the C64
            getLogger().info(arg.toString());
            setChanged(true);
            notifyObservers();
        }
    }

    // implementation of the Runnable interface
    /**
     * The main emulation loop.
     * To optimize performance we check for IO chips updates only every six CPU instructions,
     * for VIC updates every two instructions and for floppy updates every three instructions.
     */
    public void run() {
        super.run();

        // we use some local variables for better performance
        final CPU6502 cpu_ = this.cpu;
        final C1541[] drives_ = this.drives;
        final SID6581 sid_ = this.sid;
        final CIA6526 cia0 = this.cias[0],  cia1 = this.cias[1];
        long cycles;
        long nextIOUpdate = 0;
        long nextDriveUpdate = 0;

        // start the C64 main emulation loop
        while (this.isRunning) {
            try {
                final VIC6569 vic_ = this.vic;

                while (!this.isPaused) {
                    // let the CPU and VIC emulate instructions
                    cpu_.emulateNextInstruction();
                    cycles = cpu_.getCycles();
                    vic_.update(cycles);

                    // update the C1541 drives
                    if (cycles >= nextDriveUpdate) {
                        nextDriveUpdate = cycles + 1000;

                        for (int i = 0, to = getDriveCount(); i < to; ++i) {
                            if (cycles >= drives_[i].getNextUpdate()) {
                                drives_[i].update(cycles);
                            }
                            nextDriveUpdate = Math.min(nextDriveUpdate, drives[i].getNextUpdate());
                        }
                    }

                    // update IO chips: SID and CIAs
                    if (cycles >= nextIOUpdate) {
                        if (cycles >= sid_.getNextUpdate()) {
                            sid_.update(cycles);
                        }
                        if (cycles >= cia0.getNextUpdate()) {
                            cia0.update(cycles);
                        }
                        if (cycles >= cia1.getNextUpdate()) {
                            cia1.update(cycles);
                        }

                        nextIOUpdate = Math.min(Math.min(sid_.getNextUpdate(), cia0.getNextUpdate()), cia1.getNextUpdate());
                    }
                }
                if (this.isPaused) {
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                // an unhandled exception during the emulation has occurred, log this and tell any observers
                if (null != getLogger()) {
                    getLogger().error("Exception before $" + Integer.toHexString(this.cpu.getPC()) + ", exception: " + e);

                    final Vector stack = this.cpu.getStackTrace();
                    final StringBuffer st = new StringBuffer("Call-stack:");

                    for (int i = 0; i < stack.size(); ++i) {
                        st.append(" $");
                        st.append(Integer.toHexString(((Integer) stack.elementAt(i)).intValue()));
                    }

                    getLogger().error(st.toString());
                }
                e.printStackTrace();
                setChanged(true);
                notifyObservers(e);
            }
        }
    }

    // implementation of the abstract methods of superclass EmulatedDevice
    protected CPU6502 createCPU() {
        return new C64CPU6510(this);
    }

    protected void resetIOChips() {
        this.vic.reset();
        this.sid.reset();
        getKeyboard().reset();
        this.iecBus.reset();
        this.cpu.reset();
        this.cias[0].reset();
        this.cias[1].reset();
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        SerializationUtils.serialize(out, this.cias);
        SerializationUtils.setMarker(out);
        this.keyboard.serialize(out);
        SerializationUtils.setMarker(out);
        SerializationUtils.serialize(out, this.joysticks);
        SerializationUtils.setMarker(out);
        this.iecBus.serialize(out);
        SerializationUtils.setMarker(out);
        this.sid.serialize(out);
        SerializationUtils.setMarker(out);
        this.vic.serialize(out);
        SerializationUtils.setMarker(out);
        this.cpu.serialize(out);
        SerializationUtils.setMarker(out);
        SerializationUtils.serialize(out, this.drives);
        SerializationUtils.setMarker(out);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        SerializationUtils.deserialize(in, this.cias);
        SerializationUtils.verifyMarker(in);
        this.keyboard.deserialize(in);
        SerializationUtils.verifyMarker(in);
        SerializationUtils.deserialize(in, this.joysticks);
        SerializationUtils.verifyMarker(in);
        this.iecBus.deserialize(in);
        SerializationUtils.verifyMarker(in);
        this.sid.deserialize(in);
        SerializationUtils.verifyMarker(in);
        this.vic.deserialize(in);
        SerializationUtils.verifyMarker(in);
        this.cpu.deserialize(in);
        SerializationUtils.verifyMarker(in);
        SerializationUtils.deserialize(in, this.drives);
        SerializationUtils.verifyMarker(in);

        this.performanceMeter.setupNextMeasurement(this.cpu.getCycles());
    }
}
