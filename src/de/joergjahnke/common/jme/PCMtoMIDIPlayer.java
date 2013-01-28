/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.jme;

import de.joergjahnke.common.emulation.FrequencyDataProducer;
import de.joergjahnke.common.emulation.FrequencyDataProducerOwner;
import de.joergjahnke.common.util.Observer;
import java.io.IOException;
import java.util.Vector;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;

/**
 * Observes and plays the wave data delivered by a wave data producer.
 * This implementation uses the MIDIControl class from the Java Media Extensions.<br>
 * <br>
 * For a good (German) page on MIDI event see <a href='http://home.snafu.de/sicpaul/midi/midi1.htm'>http://home.snafu.de/sicpaul/midi/midi1.htm</a>.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class PCMtoMIDIPlayer implements Observer {

    /**
     * we send this signal to stop the player
     * after this signal has been sent the player must no longer be used
     */
    public static final Integer SIGNAL_STOP = new Integer(0);
    /**
     * we send this signal to temporarily stop all sounds
     * the player may still be used after sending this signal
     */
    public static final Integer SIGNAL_PAUSE = new Integer(0);
    /**
     * MIDI command for switching off a sound
     */
    private static final int NOTE_OFF = 0x80;
    /**
     * MIDI command for switching off all sound on a channel
     */
    private static final int CONTROL_ALL_SOUND_OFF = 0x78;
    /**
     * MIDI command to change the main volume of a channel
     */
    private static final int CONTROL_MAIN_VOLUME = 0x07;
    /**
     * MIDIQueueEntry type for short MIDI events
     */
    protected static final int SHORT_MIDI_EVENT = 1;
    /**
     * MIDIQueueEntry type for program (=instrument) changes
     */
    protected static final int PROGRAM_CHANGE = 2;
    /**
     * plays the MIDI data
     */
    private final Player player;
    /**
     * thread we use to process the MIDI events asynchronously
     */
    private final MIDIThread worker;
    /**
     * previous frequency per channel
     */
    private final int[] prevFrequencies;
    /**
     * previous volume per channel
     */
    private final int[] prevVolumes;
    /**
     * previous voice type per channel
     */
    private final int[] prevTypes;
    /**
     * currently played MIDI note per channel, -1 if no note is being played
     */
    private final int[] currentNote;

    /**
     * Creates a new instance of WavePlayer
     *
     * @param   producer    the producer that sends data to this player
     * @throws  IOException or MediaException if the necessary sound instances could not be initialized
     */
    public PCMtoMIDIPlayer(final FrequencyDataProducerOwner producer) throws IOException, MediaException {
        // create Player and MIDIControl instance
        this.player = Manager.createPlayer(Manager.MIDI_DEVICE_LOCATOR);
        this.player.prefetch();

        // initialize array storing the previouos frequencies per channel
        final int channelCount = producer.getFrequencyDataProducerCount();

        this.prevFrequencies = new int[channelCount];
        this.prevVolumes = new int[channelCount];
        this.prevTypes = new int[channelCount];
        this.currentNote = new int[channelCount];
        for (int i = 0; i < this.currentNote.length; ++i) {
            this.currentNote[i] = -1;
        }

        // create a thread to asynchronously process all MIDI events
        final Object control = this.player.getControl("javax.microedition.media.control.MIDIControl");

        this.worker = new MIDIThread(control);
        this.worker.start();
    }

    /**
     * Calculate the MIDI note corresponding to a given frequency
     *
     * @param freq  sound frequency
     * @return  MIDI note no.
     */
    private int getNote(final int freq) {
        return 69 + (int) (12 * MathUtils.log2(freq / 440.0));
    }

    // implementation of the Observer interface
    public void update(final Object observed, final Object obj) {
        if (observed instanceof FrequencyDataProducerOwner) {
            // we received the signal to stop all sounds?
            if (obj == SIGNAL_STOP) {
                // stop the worker thread
                this.worker.pause();
                this.worker.stop();
            } else if (obj == SIGNAL_PAUSE) {
                // we stop all sounds, but the worker remains active for later work
                this.worker.pause();
            } else {
                // process all channels
                final int channelCount = ((FrequencyDataProducerOwner) observed).getFrequencyDataProducerCount();

                for (int i = 0; i < channelCount; ++i) {
                    // get the frequency and volume for the given channel as well as previous values
                    final FrequencyDataProducer channel = ((FrequencyDataProducerOwner) observed).getFrequencyDataProducers(i);
                    final int freq = channel.getFrequency();
                    final int volume = channel.getVolume() * 127 / 100;
                    final int type = channel.getType();
                    final int prevFreq = this.prevFrequencies[i];
                    final int prevVolume = this.prevVolumes[i];
                    final int prevType = this.prevTypes[i];

                    // the voice type has changed?
                    if (type != prevType) {
                        // we have to switch off the old sound?
                        if (this.currentNote[i] >= 0) {
                            this.worker.addMidiEvent(NOTE_OFF + i, this.currentNote[i], 127);
                            this.currentNote[i] = -1;
                            this.prevFrequencies[i] = 0;
                        }
                        // set the requested MIDI program
                        this.worker.addMidiEvent(PROGRAM_CHANGE, i, -1, type);
                        this.prevTypes[i] = type;
                    }

                    // the frequency or volume has changed?
                    if (freq != prevFreq || volume != prevVolume) {
                        // we have to switch off the sound?
                        if (volume == 0 || freq == 0) {
                            if (this.currentNote[i] >= 0) {
                                this.worker.addMidiEvent(NOTE_OFF + i, this.currentNote[i], 127);
                                this.currentNote[i] = -1;
                            }
                        } else {
                            // modify the frequency if necessary (this might mean starting a new sound)
                            if (freq != prevFreq) {
                                // stop previous sound if necessary
                                if (this.currentNote[i] >= 0) {
                                    this.worker.addMidiEvent(NOTE_OFF + i, this.currentNote[i], 127);
                                }
                                // start sound with new frequency
                                this.worker.addMidiEvent(javax.microedition.media.control.MIDIControl.NOTE_ON + i, this.currentNote[i] = getNote(freq), 127);
                            }
                            // modify the volume if necessary
                            if (volume != prevVolume) {
                                this.worker.addMidiEvent(javax.microedition.media.control.MIDIControl.CONTROL_CHANGE + i, CONTROL_MAIN_VOLUME, volume);
                            }
                        }
                        this.prevFrequencies[i] = volume == 0 ? 0 : freq;
                        this.prevVolumes[i] = volume;
                    }
                }
            }
        }
    }

    /**
     * A thread that processes MIDI events
     */
    class MIDIThread extends Thread {

        /**
         * is the thread still running?
         */
        private boolean isRunning = false;
        /**
         * queue where the MIDI events to process are stored in
         */
        private final Vector queue = new Vector();
        /**
         * MIDIControl instance we send the MIDI events to
         */
        private final Object control;

        /**
         * Create a new MIDIThread
         *
         * @param control   control to work with
         */
        public MIDIThread(final Object control) {
            this.control = control;
        }

        public void run() {
            this.isRunning = true;
            while (this.isRunning) {
                while (!this.queue.isEmpty()) {
                    // fetch first entry from the queue
                    final MIDIQueueEntry entry = (MIDIQueueEntry) this.queue.firstElement();

                    this.queue.removeElementAt(0);
                    // send this event to the MIDI player
                    switch (entry.type) {
                        case SHORT_MIDI_EVENT:
                            ((javax.microedition.media.control.MIDIControl) this.control).shortMidiEvent(entry.event, entry.arg1, entry.arg2);
                            break;
                        case PROGRAM_CHANGE:
                            ((javax.microedition.media.control.MIDIControl) this.control).setProgram(entry.event, entry.arg1, entry.arg2);
                            break;
                        default:
                            throw new RuntimeException("Illegal type for MIDIQueueEntry: " + entry.type + "!");
                    }
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    // no problem
                }
            }
        }

        /**
         * Stop the thread.
         * It may last up to 10ms until the thread finally stops working
         */
        public void stop() {
            this.isRunning = false;
            this.queue.removeAllElements();
        }

        /**
         * Stop all sounds and clear the queue
         */
        public void pause() {
            this.queue.removeAllElements();
            for (int i = 0; i < prevFrequencies.length; ++i) {
                ((javax.microedition.media.control.MIDIControl) this.control).shortMidiEvent(javax.microedition.media.control.MIDIControl.CONTROL_CHANGE | i, CONTROL_ALL_SOUND_OFF, 0);
                prevFrequencies[i] = 0;
                prevVolumes[i] = 0;
            }
        }

        /**
         * Add a new MIDI event to be processed by the thread
         *
         * @param event the event, incl. channel
         * @param arg1  first event argument
         * @param arg2  second event argument
         */
        public void addMidiEvent(final int event, final int arg1, final int arg2) {
            this.queue.addElement(new MIDIQueueEntry(event, arg1, arg2));
        }

        /**
         * Add a new MIDI event to be processed by the thread
         *
         * @param type  event type, either SHORT_MIDI_EVENT or PROGRAM_CHANGE
         * @param event the event, incl. channel
         * @param arg1  first event argument
         * @param arg2  second event argument
         */
        public void addMidiEvent(final int type, final int event, final int arg1, final int arg2) {
            this.queue.addElement(new MIDIQueueEntry(type, event, arg1, arg2));
        }
    }

    /**
     * Data structure for the MIDI events to process in the MIDIThread
     */
    class MIDIQueueEntry {

        /**
         * event type
         */
        public final int type;
        /**
         * the event, incl. channel
         */
        public final int event;
        /**
         * the event arguments
         */
        public final int arg1,  arg2;

        /**
         * Create a new MIDI event
         *
         * @param type  event type, either SHORT_MIDI_EVENT or PROGRAM_CHANGE
         * @param event the event, incl. channel
         * @param arg1  first event argument
         * @param arg2  second event argument
         */
        public MIDIQueueEntry(final int type, final int event, final int arg1, final int arg2) {
            this.type = type;
            this.event = event;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        /**
         * Create a new MIDI event
         *
         * @param event the event, incl. channel
         * @param arg1  first event argument
         * @param arg2  second event argument
         */
        public MIDIQueueEntry(final int event, final int arg1, final int arg2) {
            this(SHORT_MIDI_EVENT, event, arg1, arg2);
        }
    }
}
