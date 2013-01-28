/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.c64.drive.D64DriveHandler;
import de.joergjahnke.c64.drive.DiskDriveHandler;
import de.joergjahnke.c64.drive.DriveChannel;
import de.joergjahnke.c64.drive.DriveCommandChannel;
import de.joergjahnke.c64.drive.DriveHandler;
import de.joergjahnke.c64.drive.FileReadDriveChannel;
import de.joergjahnke.c64.drive.FileWriteDriveChannel;
import de.joergjahnke.c64.drive.MultiPurposeDriveChannel;
import de.joergjahnke.c64.drive.P00DriveHandler;
import de.joergjahnke.c64.drive.PRGDriveHandler;
import de.joergjahnke.c64.drive.T64DriveHandler;
import de.joergjahnke.c64.drive.Tape2DiskAdapterDriveHandler;
import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.io.SerializationUtils;
import de.joergjahnke.common.util.Observer;
import de.joergjahnke.common.vmabstraction.ResourceLoader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * C1541 instance which manages the Floppy CPU and the two VIAs.<br>
 * <br>
 * A good German documentation on the 1541 floppy can be found at <a href='http://www.trikaliotis.net/download/DieFloppy1541-v4.pdf'>http://www.trikaliotis.net/download/DieFloppy1541-v4.pdf</a> or
 * <a href='http://www.softwolves.pp.se/idoc/alternative/vc1541_de/'>http://www.softwolves.pp.se/idoc/alternative/vc1541_de/</a>.<br>
 * Good English ROM listings can be found at <a href='http://www.ffd2.com/fridge/docs/1541dis.html'>http://www.ffd2.com/fridge/docs/1541dis.html</a> or
 * <a href='http://www.the-dreams.de/aay1541.txt'>http://www.the-dreams.de/aay1541.txt</a>.
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 * @todo    correctly implement disk rotation
 */
public class C1541 extends EmulatedIECBusDevice implements Observer, Serializable {

    // do we print debug info?
    public final static boolean DEBUG = false;
    /**
     * Supported file extensions, separated by commas
     */
    public final static Vector SUPPORTED_EXTENSIONS = new Vector();


    static {
        SUPPORTED_EXTENSIONS.addElement("d64");
        SUPPORTED_EXTENSIONS.addElement("t64");
        SUPPORTED_EXTENSIONS.addElement("prg");
        SUPPORTED_EXTENSIONS.addElement("p00");
    }
    /**
     * Disk is currently performing a read operation
     */
    public final static Integer READING = new Integer(1);
    /**
     * Disk is currently performing a write operation
     */
    public final static Integer WRITING = new Integer(2);
    /**
     * signal when a disk was mounted
     */
    public final static Integer DISK_MOUNTED = new Integer(3);
    /**
     * Number of cycles after which we deactivate the drive
     */
    private final static long DEACTIVATION_CYCLES = 1000000;
    /**
     * Setting for fast drive emulation
     */
    public final static int FAST_EMULATION = 0;
    /**
     * Setting for balanced drive emulation
     */
    public final static int BALANCED_EMULATION = 50;
    /**
     * Setting for compatible drive emulation
     */
    public final static int COMPATIBLE_EMULATION = 100;
    /**
     * Defines directory readers for the supported file types
     */
    private final Hashtable driveHandlers = new Hashtable();
    /**
     * disk id (0-3)
     */
    private final int id;
    /**
     * VIA chips 1&2
     */
    private final VIA6522 vias[];
    /**
     * drive handler used for virtual disk operations
     */
    private DiskDriveHandler driveHandler;
    /**
     * do we emulate the C1541's Disk Controller?
     */
    private boolean isEmulateDC = false;
    /**
     * do we emulate the rotation of the disk?
     */
    private boolean isEmulateDiskRotation = false;
    /**
     * emulate the C1541 drive's CPU?
     */
    private boolean isEmulateDriveCPU = true;
    /**
     * the IEC bus we are connected to
     */
    protected final IECBus iecBus;
    /**
     * actual implementation of the C1541 emulation
     */
    private C1541Impl impl = null;
    /**
     * the current drive emulation level
     */
    private int emulationLevel = -1;

    /**
     * Create a new C1541 instance
     *
     * @param   id  floppy ID (0-3)
     * @param   resourceLoader  ResourceLoader used when loading system resource for the device
     * @param   iecBus  IEC bus the floppy gets attached to
     */
    public C1541(final int id, final ResourceLoader resourceLoader, final IECBus iecBus) {
        super("C1541 #" + (id + 8), resourceLoader);

        if (id < 0 || id > 3) {
            throw new IllegalArgumentException("C1541 drive ID must be 0-3!");
        }

        this.id = id;
        this.iecBus = iecBus;

        // patch ROMs
        ((C1541CPU6502) this.cpu).patchROMs();

        // initialize the IO chips
        this.vias = new VIA6522[2];
        this.vias[0] = new VIA6522_BC(this, iecBus);
        this.vias[1] = new VIA6522_DC(this);

        // register drive handlers if necessary
        driveHandlers.put("d64", new D64DriveHandler());
        driveHandlers.put("t64", new Tape2DiskAdapterDriveHandler(new T64DriveHandler()));
        driveHandlers.put("prg", new Tape2DiskAdapterDriveHandler(new PRGDriveHandler()));
        driveHandlers.put("p00", new Tape2DiskAdapterDriveHandler(new P00DriveHandler()));

        // register as observer for all drive handlers
        for (final Enumeration en = driveHandlers.elements(); en.hasMoreElements();) {
            ((DriveHandler) en.nextElement()).addObserver(this);
        }

        // set emulation level, which also creates a C1541Impl instance
        setEmulationLevel(BALANCED_EMULATION);
    }

    /**
     * We destroy this drive instance which might lead to observers being notified of a change having been done to the current image
     *
     * @throws any Throwable that might occur during shutdown
     */
    protected void finalize() throws Throwable {
        detachImage();
    }

    /**
     * Do we emulate the C1541's Disk Controller?
     * 
     * @return  true if Disk Controller emulation is used
     */
    public boolean isEmulateDiskController() {
        return this.isEmulateDC;
    }

    /**
     * Do we emulate disk rotation?
     * 
     * @return  true if disk rotation is emulated
     */
    public boolean isEmulateDiskRotation() {
        return isEmulateDiskRotation;
    }

    /**
     * Do we emulate the C1541's CPU?
     * 
     * @return  true if the we emulate the floppy CPU
     */
    public boolean isEmulateDriveCPU() {
        return isEmulateDriveCPU;
    }

    /**
     * Get the current emulation level
     * 
     * @return  the current emulation level, e.g. COMPATIBLE_EMULATION
     */
    public int getEmulationLevel() {
        return this.emulationLevel;
    }

    /**
     * Set the level of the drive emulation which determines the performance and
     * compatibility of the drive emulation.
     * 
     * @param   level   COMPATIBLE_EMULATION for slow but very compatible emulation,
     *                  BALANCED_EMULATION for a bit faster and a bit less compatible emulation,
     *                  FAST_EMULATION for fast but significantly less compatible emulation
     */
    public final void setEmulationLevel(final int level) {
        if (level != this.emulationLevel) {
            // create emulation depending on the given level
            String mode = null;

            if (level >= COMPATIBLE_EMULATION) {
                this.isEmulateDriveCPU = true;
                this.isEmulateDC = true;
                this.isEmulateDiskRotation = true;
                mode = "'compatible'";
                this.impl = (C1541Impl) new C1541FullEmulationImpl(this);
            } else if (level >= BALANCED_EMULATION) {
                this.isEmulateDriveCPU = true;
                this.isEmulateDC = false;
                this.isEmulateDiskRotation = false;
                mode = "'balanced'";
                this.impl = (C1541Impl) new C1541FullEmulationImpl(this);
            } else {
                this.isEmulateDriveCPU = false;
                this.isEmulateDC = false;
                this.isEmulateDiskRotation = false;
                mode = "'fast'";
                this.impl = (C1541Impl) new C1541IECBusOnlyImpl(this);
            }

            if (getLogger() != null) {
                getLogger().info("Setting drive #" + (getID() + 8) + " emulation " + mode + " mode");
            }

            // initialize the drive implementation
            this.impl.initialize();
            this.cpu.setCycles(0);

            this.emulationLevel = level;
        }
    }

    /**
     * Get a VIA chip
     *
     * @param   n   via id
     * @return VIA chip instance
     */
    public final VIA6522 getVIA(final int n) {
        return this.vias[n];
    }

    /**
     * Return the drive ID
     * 
     * @return  drive ID, ranging from 0-3
     */
    public final int getID() {
        return this.id;
    }

    /**
     * First destroy the drive handler, then reset normally
     */
    public void reset() {
        detachImage();
        impl.initialize();
        impl.reset();
        super.reset();
        this.vias[0].reset();
        this.vias[1].reset();
    }

    /**
     * Initialize the drive
     */
    public void initialize() {
        impl.initialize();
    }

    /**
     * Synchronize the floppy with another device.
     * This method sets the floppy cycle count to that of the other device.
     *
     * @param   device  device to synchronize with
     */
    public void synchronizeWithDevice(final EmulatedDevice device) {
        super.synchronizeWithDevice(device);
        this.nextUpdate = device.getCPU().getCycles();
        this.vias[0].synchronizeWithDevice(device);
        this.vias[1].synchronizeWithDevice(device);
    }

    /**
     * Use disk or tape image from a given input stream as medium to load from.
     * This method automatically detects the type of the image from its file extension
     * and installs a DriveReader that takes over the file operations for the given
     * format.
     *
     * @param   stream  stream to read the image from
     * @param   imageName   used to determine the type of the file, e.g. .d64 will be interpreted as disk image, .t64 as tape image
     * @throws  IOException if the image could not be used
     */
    public void attachImage(final InputStream stream, final String imageName) throws IOException {
        // a file extension is specified?
        final String lcImage = imageName.toLowerCase();

        if (lcImage.indexOf('.') >= 0) {
            // detach the old image
            detachImage();

            // install a reader depending on the file extension
            final String extension = lcImage.substring(lcImage.lastIndexOf('.') + 1);

            this.driveHandler = (DiskDriveHandler) driveHandlers.get(extension);
            if (null != this.driveHandler) {
                // reserve a byte array for the stream data
                // initially we try to reserve space for a complete .d64 image
                // if only very little memory is available then this might fail
                // then we incrementally increase the buffer size to be able to at least load small tape images
                ByteArrayOutputStream out = null;

                try {
                    out = new ByteArrayOutputStream(175000);
                } catch (OutOfMemoryError e) {
                    out = new ByteArrayOutputStream();
                }

                // copy stream data to byte array
                for (int b; (b = stream.read()) >= 0;) {
                    out.write((byte) b);
                }

                // assign image data to drive handler
                this.driveHandler.mount(out.toByteArray());
                out = null;

                // attach the drive to the IEC bus
                this.iecBus.addDevice(this);
                this.iecBus.addObserver(this);

                // notify observers of the success
                setChanged(true);
                notifyObservers(DISK_MOUNTED);

                // log the success
                if (null != getLogger()) {
                    getLogger().info("Attached disk image '" + this.driveHandler.getLabel().trim() + "' to drive #" + (this.id + 8));
                }
            } else {
                throw new IOException("Image type '" + extension + "' not supported!");
            }
        } else {
            throw new IOException("Could not determine the file type for the image " + imageName + "!");
        }
    }

    /**
     * Destroy the drive handler object, notifying observers beforehand
     */
    public void detachImage() {
        // an image is currently attached?
        if (this.driveHandler != null) {
            // notify observers if we have a modified image
            if (this.driveHandler.wasModified()) {
                setChanged(true);
                notifyObservers(this.driveHandler);
            }

            // delete the old reader if necessary
            this.driveHandler.destroy();
            this.driveHandler = null;

            // remove the drive from the bus
            this.iecBus.removeDevice(this);
            this.iecBus.deleteObserver(this);

            // log the success
            if (null != getLogger()) {
                getLogger().info("Detached disk image from drive #" + (this.id + 8));
            }
        }
    }

    /**
     * Get the file names on the current disk/tape
     *
     * @return  list of strings
     */
    public Vector getFilenames() {
        final Vector result = new Vector();

        for (final Enumeration en = this.driveHandler.directoryElements(); en.hasMoreElements();) {
            result.addElement(((C64FileEntry) en.nextElement()).filename.trim());
        }

        return result;
    }

    /**
     * Read a file of a given type
     *
     * @param   filename    name of file to load from current image
     * @param   fileType    string representation of the file type, e.g. PRG
     * @return  array containing the file data, the first two bytes denote the address the program wants to be loaded at
     * @throws  IOException if the file cannot be found
     */
    public byte[] readFile(final String filename, final String fileType) throws IOException {
        setChanged(true);
        notifyObservers(READING);

        if (null != getLogger()) {
            getLogger().info("Reading file '" + filename + "'");
        }

        return this.driveHandler.readFile(filename, fileType);
    }

    /**
     * Read a file
     *
     * @param   filename    name of file to load from current image
     * @return  array containing the file data, the first two bytes denote the address the program wants to be loaded at
     * @throws  IOException if the file cannot be found
     */
    public byte[] readFile(final String filename) throws IOException {
        return readFile(filename, null);
    }

    /**
     * Get the used drive handler
     *
     * @return the current drive handler
     */
    public DiskDriveHandler getDriveHandler() {
        return this.driveHandler;
    }

    // partial implementation of the IOChip interface
    /**
     * Get the CPU cycles count when the next update of the floppy drive is required
     *
     * @return  CPU cycles count of next update
     */
    public final long getNextUpdate() {
        return this.nextUpdate;
    }

    /**
     * Check if floppy drive needs to be updated and do the update if necessary
     * 
     * @param   cycles  current CPU count, used for synchronization
     */
    public void update(final long cycles) {
        this.isRunning = true;
        this.isPaused = false;
        impl.update(cycles);
    }

    // implementation of the Runnable interface
    /**
     * The main emulation loop is used for startup only.
     * Once the floppy enters the wait-loop, it stops the execution.
     */
    public void run() {
        super.run();

        update(Long.MAX_VALUE);

        getLogger().info(getName() + " ready");
    }

    // implementation of the Observer interface
    /**
     * We report some message from the disk controller to other observers
     */
    public void update(final Object observed, final Object arg) {
        // this update is from the Disk Controller?
        if (observed instanceof DriveHandler) {
            // then propagate the message to observers of the drive
            setChanged(true);
            notifyObservers(arg);
        // pass on message from the IEC bus to the emulation implementation instance
        } else if (observed == this.iecBus) {
            this.impl.update(observed, arg);
        }
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        if (this.iecBus.getController().getCPU().getCycles() - this.lastActiveCycle <= getDeactivationPeriod()) {
            throw new IllegalStateException("Cannot serialize a running floppy drive!");
        }
        SerializationUtils.serialize(out, this.vias);
        out.writeInt(this.emulationLevel);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        SerializationUtils.deserialize(in, this.vias);
        setEmulationLevel(in.readInt());
    }

    // implementation of the abstract methods of superclasses
    protected CPU6502 createCPU() {
        return new C1541CPU6502(this);
    }

    protected void resetIOChips() {
        this.vias[0].reset();
        this.vias[1].reset();
    }

    protected long getDeactivationPeriod() {
        return DEACTIVATION_CYCLES;
    }

    /**
     * The actual implementation of the C1541 emulation
     */
    public abstract class C1541Impl implements Observer {

        /**
         * C1541 instance we use
         */
        protected final C1541 c1541;

        /**
         * Create a new C1541Impl
         * 
         * @param   c1541   the drive to work for
         */
        public C1541Impl(final C1541 c1541) {
            this.c1541 = c1541;
        }

        /**
         * Override this method to do some initialization tasks
         */
        public void initialize() {
        }

        /**
         * Override this method to do some work when a reset occurs
         */
        public void reset() {
        }

        /**
         * Check if floppy drive needs to be updated and do the update if necessary
         * 
         * @param   cycles  current CPU count, used for synchronization
         */
        public abstract void update(final long cycles);
    }

    /**
     * A C1541 emulation that does only emulate the IEC bus's reactions but not the fully C1541.
     * It is therefore much faster than the other implementations.
     */
    public class C1541IECBusOnlyImpl extends C1541Impl {

        /**
         * No. of channels we create for a drive
         */
        public final static int NUM_CHANNELS = 16;

        // command codes
        /**
         * Talk
         */
        private final static int CMD_LISTEN = 0x20;
        /**
         * Untalk
         */
        private final static int CMD_UNLISTEN = 0x30;
        /**
         * Listen
         */
        private final static int CMD_TALK = 0x40;
        /**
         * Unlisten
         */
        private final static int CMD_UNTALK = 0x50;
        /**
         * Open Channel
         */
        private final static int CMD_DATA = 0x60;
        /**
         * Close the channel
         */
        private final static int CMD_CLOSE = 0xe0;
        /**
         * Open a channel
         */
        private final static int CMD_OPEN = 0xf0;

        // working modes
        /**
         * We are ready to receive data
         */
        private final static int MODE_READY_TO_LISTEN = 1;
        /**
         * Waiting for CLK to go to true
         */
        private final static int MODE_WAIT_FOR_TALKER = 2;
        /**
         * Reading bytes
         */
        private final static int MODE_READ = 3;
        /**
         * We are ready to send data
         */
        private final static int MODE_READY_TO_TALK = 4;
        /**
         * We are waiting for the EOI acknowledgement of the listener
         */
        private final static int MODE_WAIT_FOR_EOI_ACKNOWLEDGE = 5;
        /**
         * We are waiting for the listener to signal that he is ready for reading
         */
        private final static int MODE_WAIT_FOR_LISTENER = 6;
        /**
         * Writing bytes
         */
        private final static int MODE_WRITE = 7;
        /**
         * We wait for the listener to acknowledge the reception of the last byte
         */
        private final static int MODE_WAIT_FOR_WRITE_ACKNOWLEDGE = 8;
        /**
         * We wait until EOI acknowledgement is over
         */
        private final static int MODE_WAIT_FOR_END_OF_EOI_ACKNOWLEDGE = 9;
        // timeout types
        /**
         * we are waiting before sending data
         */
        private final static int TIMEOUT_SEND_DATA = 1;
        /**
         * we are holding the click line steady for some time before preparing the next bit to send
         */
        private final static int TIMEOUT_KEEP_WRITE_STEADY = 2;
        /**
         * we are waiting before pulling the click line for a turnaround sequence
         */
        private final static int TIMEOUT_WAIT_FOR_TURNAROUND = 3;
        /**
         * we are running a timer to check for EOI
         */
        private final static int TIMEOUT_WAIT_FOR_EOI = 4;
        /**
         * we keep the data line steady for some time when signaling EOI acknowledgement
         */
        private final static int TIMEOUT_SHOW_EOI_NOTICED = 5;
        /**
         * we wait for 1000ms for a write acknowledgement
         */
        private final static int TIMEOUT_WAIT_FOR_WRITE_ACKNOWLEDGE = 6;
        /**
         * streams attached to the logical channels of the drive
         */
        private DriveChannel[] channels;
        /**
         * channel currently used
         */
        private int currentChannel;
        /**
         * current IEC bus working mode
         */
        private int iecMode = 0;
        /**
         * we have ATN set for some time?
         */
        private boolean isUnderATN = false;
        /**
         * denotes the CPU when a timeout is running out, -1 for no timeout running
         */
        private long timeoutCycle = -1;
        /**
         * denotes the type of timeout
         */
        private int timeoutType = 0;
        /**
         * denotes that after an EOI signal we will only receive one more character
         */
        private boolean isLastChar = false;
        /**
         * data currently read/written
         */
        private final StringBuffer currentByte = new StringBuffer();
        private final ByteArrayOutputStream received = new ByteArrayOutputStream();
        /**
         * we currently have a listener or talker?
         */
        private boolean hasListener = false;
        private boolean hasTalker = false;

        /**
         * Create a new C1541Impl that emulates only the IEC bus but not the full floppy
         * 
         * @param   c1541   the drive to work for
         */
        public C1541IECBusOnlyImpl(final C1541 c1541) {
            super(c1541);
        }

        /**
         * Initialize the drive
         */
        public void initialize() {
            // reset all channels
            this.channels = new DriveChannel[NUM_CHANNELS];
            // setup the command channel
            this.channels[NUM_CHANNELS - 1] = new DriveCommandChannel(this.c1541, channels);
            // reset the channel
            this.currentChannel = 0;
        }

        /**
         * Reset the bus emulation
         */
        public void reset() {
            this.iecMode = 0;
            this.isUnderATN = false;
            this.timeoutCycle = -1;
            this.timeoutType = 0;
            this.isLastChar = false;
            this.hasListener = this.hasTalker = false;
            this.currentByte.delete(0, this.currentByte.length());
            this.received.reset();
            this.c1541.iecBus.setSignal(this.c1541, IECBus.CLK, false);
        }


        // implementation of the drive's channels
        /**
         * Open a logical channel
         *
         * @param   channel logical channel to use, ranging from 0-15.
         *          0 denotes loading a file, 1 writing, 15 the command channel, other channels are free logical channels
         * @throws  IOException if the channel cannot be opened
         */
        public void openChannel(final int channel) throws IOException {
            // we now use the given channel
            this.currentChannel = channel;

            // we want to read a file?
            if (channel == 0) {
                // we read the given file from this channel
                this.channels[channel] = new FileReadDriveChannel(this.c1541);
            // do want to write data?
            } else if (channel == 1) {
                this.channels[channel] = new FileWriteDriveChannel(this.c1541);
            // do we want to access the command channel?
            } else if (channel == 15) {
                // that channel is always open, so we do nothing
                // no, we have channel 2-14
            } else {
                this.channels[channel] = new MultiPurposeDriveChannel(this.c1541);
            }
        }

        /**
         * Set the active channel for reading/writing
         *
         * @param   channel channel (0-15) to activate
         */
        public void setActiveChannel(final int channel) {
            this.currentChannel = channel;
            // the command channel is selected and we have no data in this channel?
            if (15 == channel && getActiveChannel() == null) {
                // then assign a new channel
                this.channels[15] = new DriveCommandChannel(this.c1541, channels);
            }
        }

        /**
         * Get the channel which has been marked active for this drive
         *
         * @return  DriveChannel to read from/write to
         */
        public DriveChannel getActiveChannel() {
            return getChannel(this.currentChannel);
        }

        /**
         * Get a channel of this drive
         * 
         * @param   channel channel no.
         * @return  DriveChannel to read from/write to
         */
        public DriveChannel getChannel(final int channel) {
            return this.channels[channel];
        }

        /**
         * Close a given input channel
         *
         * @param   channel channel (0-15) to close
         */
        public void closeChannel(final int channel) {
            try {
                this.channels[channel].close();
            } catch (Exception e) {
                // for some reason we could not close the file
                // does not matter, we discard the channel anyway
            }
        }


        // implementation of the drive's IEC bus
        /**
         * Stop an ongoing transfer. This is usually done if we get an ATN signal during an
         * ongoing transfer operation.
         */
        private void stopTransfer() {
            // we are now again in idle mode
            this.iecMode = 0;
            this.isLastChar = false;
            // no timeouts are active
            this.timeoutCycle = -1;
            this.timeoutType = 0;
            // clear a partially received byte
            this.currentByte.delete(0, this.currentByte.length());
        }

        /**
         * Handle an ATN command that is located in the first byte of the input buffer
         */
        private void handleATNCommand(final int b) {
            final int command = b & 0xf0;
            final int device = b & 0x1f;
            final int secondary = b & 0x0f;

            if (DEBUG) {
                System.out.println("ATN: " + Integer.toHexString(command) + ", dev: " + Integer.toHexString(device) + ", sec: " + secondary);
            }

            switch (command) {
                case CMD_TALK:
                case CMD_UNTALK:
                    // untalk?
                    if (device == 0x1f) {
                        // then remove talker
                        this.hasTalker = false;
                        // clear the command buffer
                        this.received.reset();
                    } else if (device == this.c1541.getID() + 8) {
                        // otherwise define the designated drive as talker
                        this.hasTalker = true;
                    } else {
                        iecBus.setSignal(this.c1541, IECBus.DATA, false);
                    }
                    break;
                case CMD_LISTEN:
                case CMD_UNLISTEN:
                    // untalk?
                    if (device == 0x1f) {
                        // interpret the command
                        executeListenCommand();
                        // then remove listener
                        this.hasListener = false;
                        // clear the command buffer
                        this.received.reset();
                    } else if (device == this.c1541.getID() + 8) {
                        // create a command buffer containing only the new listen command
                        this.received.reset();
                        this.received.write(b);
                        // otherwise define the designated drive as listener
                        this.hasListener = true;
                    } else {
                        iecBus.setSignal(this.c1541, IECBus.DATA, false);
                    }
                    break;
                case CMD_OPEN:
                    // this is being executed once we get the UNLISTEN command
                    break;
                case CMD_CLOSE:
                    // this is being executed once we get the UNLISTEN command
                    break;
                case CMD_DATA:
                    // set the current channel
                    if (this.hasListener || this.hasTalker) {
                        setActiveChannel(secondary);
                    } else {
                        throw new RuntimeException("No listener/talker set for DATA command!");
                    }
                    break;
                default:
                    throw new RuntimeException("Unknown ATN command: " + command + "!");
            }
        }

        /**
         * Execute the command sequence of a LISTEN command
         */
        private void executeListenCommand() {
            final byte[] bytes = this.received.toByteArray();
            int n = 0;
            int b = bytes[n++] & 0xff;
            int command = b & 0xf0;

            // the sequence must have started with a listen command
            if (command == CMD_LISTEN) {
                b = bytes[n++] & 0xff;
                command = b & 0xf0;
                final int secondary = b & 0x0f;

                if (DEBUG) {
                    try {
                        System.out.println("Command: " + Integer.toHexString(command) + ", sec: " + secondary + ", " + bytes.length + " bytes: '" + new String(bytes, "ISO-8859-1") + "'");
                    } catch (Exception e) {
                    }
                }

                try {
                    switch (command) {
                        case CMD_OPEN:
                            // open the requested channel
                            openChannel(secondary);
                        // we fall through, so that the filename gets written to the channel
                        // on commit this triggers that the file gets loaded etc.

                        case CMD_DATA:
                            // all the bytes until the UNLISTEN are written to the channel
                            getActiveChannel().write(bytes, n, bytes.length - n - 1);
                            getActiveChannel().commit();
                            break;

                        case CMD_CLOSE:
                            closeChannel(secondary);
                            break;

                        // otherwise do nothing
                        default:
                            ;
                    }
                } catch (IOException e) {
                    // failed to read from / write to the channel
                    // TODO: how to react properly?
                    e.printStackTrace();
                    throw new RuntimeException("IOException while writing to the IECBus. Command=$" + Integer.toHexString(command) + ", Sec=" + secondary + ".\nThe error message was " + e);
                }
            }
        }

        /**
         * Check whether we have any kind of timeout to watch for and set the next update cycle
         */
        private final void determineTimeout() {
            this.c1541.nextUpdate = this.timeoutCycle >= 0 ? this.timeoutCycle : Long.MAX_VALUE;
        }


        // partial implementation of the IOChip interface
        /**
         * We react to notifications from the IEC bus
         */
        public void update(final Object observed, final Object arg) {
            if (observed instanceof IECBus) {
                final IECBus iecBus = (IECBus) observed;
                final EmulatedDevice controller = iecBus.getController();
                final boolean isATNOut = iecBus.getSignal(controller, IECBus.ATN);
                final boolean isCLKOut = iecBus.getSignal(controller, IECBus.CLK);
                final boolean isDATAOut = iecBus.getSignal(controller, IECBus.DATA);

                if (DEBUG && this.c1541.getDriveHandler() != null) {
                    System.out.println("Mode=" + this.iecMode);
                }

                // an ATN signal will stop a current transfer
                if (isATNOut && !this.isUnderATN) {
                    stopTransfer();
                }

                switch (this.iecMode) {
                    case MODE_WAIT_FOR_WRITE_ACKNOWLEDGE:
                        if (isDATAOut) {
                            this.iecMode = MODE_READY_TO_TALK;
                            // we wait until sending data
                            this.timeoutCycle = iecBus.getController().getCPU().getCycles() + 100;
                            this.timeoutType = TIMEOUT_SEND_DATA;
                        }
                        break;
                    case MODE_WRITE:
                        try {
                            // we have no more bits to send in the internal buffer?
                            if (this.currentByte.length() == 0) {
                                // get next eight bits
                                final int b = getActiveChannel().read();

                                if (b < 0) {
                                    throw new IOException("End of data channel!");
                                }

                                // convert the byte to a binary string
                                this.currentByte.append(Integer.toBinaryString(b));
                                if (this.currentByte.length() < 8) {
                                    this.currentByte.insert(0, "0000000".substring(0, 8 - currentByte.length()));
                                }
                                if (DEBUG) {
                                    System.out.println("Write byte " + this.currentByte + ", " + getActiveChannel().available() + " available");
                                }
                            }

                            // fetch next bit from the internal buffer
                            final int bitPos = this.currentByte.length() - 1;
                            final boolean bit = this.currentByte.charAt(bitPos) == '0';

                            this.currentByte.deleteCharAt(bitPos);

                            // send the bit to the data line
                            iecBus.setSignal(this.c1541, IECBus.DATA, bit);
                            // we signal that the data is ready
                            iecBus.setSignal(this.c1541, IECBus.CLK, false);
                            // we keep the lines steady for at least 60 microsecs.
                            this.timeoutCycle = controller.getCPU().getCycles() + 100;
                            this.timeoutType = TIMEOUT_KEEP_WRITE_STEADY;
                        } catch (IOException e) {
                            // no more bytes to write
                            iecBus.setSignal(this.c1541, IECBus.CLK, false);
                            iecBus.setSignal(this.c1541, IECBus.DATA, false);
                            stopTransfer();
                        }
                        break;
                    case MODE_WAIT_FOR_END_OF_EOI_ACKNOWLEDGE:
                        if (!isDATAOut) {
                            iecBus.setSignal(this.c1541, IECBus.CLK, true);
                            this.iecMode = MODE_WRITE;
                            // we keep the clock line held for at least 70 microsecs.
                            this.timeoutCycle = controller.getCPU().getCycles() + 100;
                            this.timeoutType = TIMEOUT_SEND_DATA;
                        }
                        break;
                    case MODE_WAIT_FOR_EOI_ACKNOWLEDGE:
                        if (isDATAOut) {
                            this.iecMode = MODE_WAIT_FOR_END_OF_EOI_ACKNOWLEDGE;
                            if (DEBUG) {
                                System.out.println("EOI seen at " + controller.getCPU().getCycles());
                            }
                        }
                        break;
                    case MODE_WAIT_FOR_LISTENER:
                        // listener is ready to listen?
                        if (!isDATAOut) {
                            try {
                                // more than one byte left to send?
                                if (getActiveChannel().available() != 1) {
                                    iecBus.setSignal(this.c1541, IECBus.CLK, true);
                                    this.iecMode = MODE_WRITE;
                                    // we keep the clock line held for at least 70 microsecs.
                                    this.timeoutCycle = controller.getCPU().getCycles() + 100;
                                    this.timeoutType = TIMEOUT_SEND_DATA;
                                } else {
                                    // no, last byte, we have to send EOI
                                    this.iecMode = MODE_WAIT_FOR_EOI_ACKNOWLEDGE;
                                    if (DEBUG) {
                                        System.out.println("EOI initiated at " + controller.getCPU().getCycles());
                                    }
                                }
                            } catch (IOException e) {
                                stopTransfer();
                            } catch (NullPointerException e) {
                                stopTransfer();
                            }
                        }
                        break;
                    case MODE_READY_TO_TALK:
                        // signal that we want to talk
                        iecBus.setSignal(this.c1541, IECBus.CLK, false);
                        this.iecMode = MODE_WAIT_FOR_LISTENER;
                        break;
                    case MODE_READ:
                        // a byte was written
                        if (!isCLKOut) {
                            // read the bit (inverted)...
                            this.currentByte.insert(0, isDATAOut ? '0' : '1');
                            // ...and wait for CLK to go to true again
                            this.iecMode = MODE_WAIT_FOR_TALKER;
                        }
                        break;
                    case MODE_WAIT_FOR_TALKER:
                        if (isCLKOut) {
                            // no longer wait for a timeout
                            this.timeoutCycle = -1;
                            this.timeoutType = 0;
                            // we have read a full byte and data is set to false?
                            if (DEBUG) {
                                System.out.println(this.currentByte.length() + " bits received so far");
                            }
                            if (this.currentByte.length() == 8) {
                                if (!isDATAOut) {
                                    // acknowledge by setting DATA to true
                                    iecBus.setSignal(this.c1541, IECBus.DATA, true);

                                    // store byte
                                    final int b = Integer.parseInt(this.currentByte.toString(), 2);

                                    this.received.write(b);
                                    // clear byte for more data
                                    this.currentByte.delete(0, this.currentByte.length());
                                    // we have to interpret an ATN command?
                                    if (isATNOut) {
                                        handleATNCommand(b);
                                    }
                                    // we are ready to listen again
                                    this.iecMode = MODE_READY_TO_LISTEN;
                                    // this is supposed to be the last character sent?
                                    if (this.isLastChar) {
                                        // TODO: do we have to do something special in this case?
                                        this.isLastChar = false;
                                    }
                                }
                            } else {
                                // wait for CLK to go to false
                                this.iecMode = MODE_READ;
                            }
                        }
                        break;
                    case MODE_READY_TO_LISTEN:
                        // did we receive the turnaround signal?
                        if (!isCLKOut && !isATNOut && isDATAOut && this.hasTalker) {
                            // we see this, release the data line, but wait a bit until grabbing the clock line
                            iecBus.setSignal(this.c1541, IECBus.DATA, false);
                            this.timeoutCycle = controller.getCPU().getCycles() + 50;
                            this.timeoutType = TIMEOUT_WAIT_FOR_TURNAROUND;
                            this.iecMode = MODE_READY_TO_TALK;
                            if (DEBUG) {
                                System.out.println("Turnaround noticed");
                            }
                        // talker is ready to talk?
                        } else if (!isCLKOut) {
                            // the listener responds that he is also ready
                            iecBus.setSignal(this.c1541, IECBus.DATA, false);
                            // we will wait only for 200 microsecs and therefore store the current time
                            this.timeoutCycle = controller.getCPU().getCycles() + 200;
                            this.timeoutType = TIMEOUT_WAIT_FOR_EOI;
                            // we are ready to read the command
                            this.iecMode = MODE_WAIT_FOR_TALKER;
                            if (DEBUG) {
                                System.out.println("Checking for EOI");
                            }
                        }
                        break;
                    default:
                        // did we receive a signal that the talker is here?
                        if (isCLKOut && (isATNOut || this.hasListener)) {
                            // we have an image mounted?
                            if (this.c1541.getDriveHandler() != null) {
                                // then the devices indicate that they are listening by setting data_in to true
                                iecBus.setSignal(this.c1541, IECBus.DATA, true);
                                // we have shown that we are also here
                                this.iecMode = MODE_READY_TO_LISTEN;
                            }
                        }
                }

                this.isUnderATN = isATNOut;

                determineTimeout();
            }
        }


        // implementation of the abstract methods of superclass C1541Impl
        public void update(final long cycles) {
            if (cycles >= this.timeoutCycle) {
                final IECBus iecBus = this.c1541.iecBus;

                switch (this.timeoutType) {
                    // a data transmission timed out?
                    case TIMEOUT_WAIT_FOR_EOI: {
                        // then signal EOI acknowledgement for at least 60 microsecs
                        iecBus.setSignal(this.c1541, IECBus.DATA, true);
                        this.timeoutCycle = cycles + 100;
                        this.timeoutType = TIMEOUT_SHOW_EOI_NOTICED;
                        if (DEBUG) {
                            System.out.println("EOI");
                        }
                        break;
                    }
                    // we have to stop sending EOI?
                    case TIMEOUT_SHOW_EOI_NOTICED: {
                        this.timeoutCycle = -1;
                        this.timeoutType = 0;
                        // set data_in to false again
                        iecBus.setSignal(this.c1541, IECBus.DATA, false);
                        this.isLastChar = true;
                        if (DEBUG) {
                            System.out.println("Acknowledged EOI");
                        }
                        break;
                    }
                    // we can pull the clock line true again?
                    case TIMEOUT_KEEP_WRITE_STEADY: {
                        iecBus.setSignal(this.c1541, IECBus.CLK, true);
                        if (DEBUG) {
                            System.out.println("Write steady");
                        }
                        // this was the last bit?
                        if (this.currentByte.length() == 0) {
                            // then clear the data line and wait for acknowledgement from the CPU
                            iecBus.setSignal(this.c1541, IECBus.DATA, false);
                            this.timeoutCycle = iecBus.getController().getCPU().getCycles() + 1000;
                            this.timeoutType = TIMEOUT_WAIT_FOR_WRITE_ACKNOWLEDGE;
                            this.iecMode = MODE_WAIT_FOR_WRITE_ACKNOWLEDGE;
                        } else {
                            this.timeoutCycle = iecBus.getController().getCPU().getCycles() + 70;
                            this.timeoutType = TIMEOUT_SEND_DATA;
                        }
                        break;
                    }
                    // we can send the next bit of data?
                    case TIMEOUT_SEND_DATA: {
                        this.timeoutCycle = -1;
                        this.timeoutType = 0;
                        // update method will send the next bit
                        update(iecBus, null);
                        break;
                    }
                    // a written byte was not confirmed in time?
                    case TIMEOUT_WAIT_FOR_WRITE_ACKNOWLEDGE: {
                        this.timeoutCycle = -1;
                        this.timeoutType = 0;
                        // we stop the current transfer
                        iecBus.setSignal(this.c1541, IECBus.CLK, false);
                        stopTransfer();
                        if (DEBUG) {
                            System.out.println("Write not acknowledged");
                        }
                        break;
                    }
                    // we have to initiate the turnaround sequence?
                    case TIMEOUT_WAIT_FOR_TURNAROUND: {
                        // we grab the clock line and release the data line
                        iecBus.setSignal(this.c1541, IECBus.CLK, true);
                        // we also wait a bit until sending the first data
                        this.timeoutCycle = iecBus.getController().getCPU().getCycles() + 100;
                        this.timeoutType = TIMEOUT_SEND_DATA;
                        if (DEBUG) {
                            System.out.println("Turnaround initiated");
                        }
                        break;
                    }
                }

                determineTimeout();
            }
        }
    }

    /**
     * An implementation of the C1541 with full emulation of the floppy CPU.
     * This increases compatibilty with the original C1541 floppy at the cost of emulation speed.
     */
    public class C1541FullEmulationImpl extends C1541Impl {

        /**
         * Number of cycles after which we want a new CIA chip update
         */
        private final static int UPDATE_CYCLES = 10;

        /**
         * Create a new C1541Impl fully emulating the C1541
         * 
         * @param   c1541   the drive to work for
         */
        public C1541FullEmulationImpl(final C1541 c1541) {
            super(c1541);
        }


        // implementation of the abstract methods of superclass C1541Impl
        public void update(final long cycles) {
            // set next update cycle
            this.c1541.nextUpdate = cycles == Long.MAX_VALUE ? Long.MAX_VALUE : cycles + UPDATE_CYCLES;

            // we use some local variables for better performance
            final CPU6502 cpu_ = getCPU();
            final VIA6522 via0 = this.c1541.getVIA(0);
            final VIA6522_DC via1 = (VIA6522_DC) this.c1541.getVIA(1);
            final C1541 c1541_ = this.c1541;
            long floppyCycles;
            long nextIOUpdate = 0;

            // run until target cycle count or we pause or end
            while (c1541_.isRunning() && (!c1541_.isPaused() || cycles == Long.MAX_VALUE) && cpu_.getCycles() < cycles) {
                // let the CPU emulate the next instruction
                cpu_.emulateNextInstruction();

                // update IO chips: VIA 0+1
                floppyCycles = cpu.getCycles();
                if (floppyCycles >= nextIOUpdate) {
                    if (floppyCycles >= via0.getNextUpdate()) {
                        via0.update(floppyCycles);
                    }
                    if (floppyCycles >= via1.getNextUpdate()) {
                        via1.update(floppyCycles);
                    }

                    nextIOUpdate = Math.min(Math.min(floppyCycles + 1000, via0.getNextUpdate()), via1.getNextUpdate());
                }

                // we have to rotate the disk?
                if (isEmulateDiskRotation()) {
                    if (floppyCycles >= via1.nextMove) {
                        // only if the motor is on
                        if (via1.isMotorOn()) {
                            via1.rotateDisk();
                        }
                        via1.nextMove = floppyCycles + VIA6522_DC.INTERVAL_MOVE_TO_NEXT_BYTE;
                    }
                }
            }
        }

        public void update(final Object observed, final Object arg) {
            if (observed == this.c1541.iecBus) {
                ((VIA6522_BC) this.c1541.getVIA(0)).update(observed, arg);
            }
        }
    }
}
