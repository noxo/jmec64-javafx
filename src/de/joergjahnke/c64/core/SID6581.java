/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.common.emulation.FrequencyDataProducer;
import de.joergjahnke.common.emulation.FrequencyDataProducerOwner;
import de.joergjahnke.common.emulation.WaveDataProducer;
import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.io.SerializationUtils;
import de.joergjahnke.common.util.DefaultObservable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Implements the sound chip SID6581.
 * <br>
 * For a documentation on the SID6581, please have a look at
 * <a href='http://www.htu.tugraz.at/~herwig/c64/sid.php'>http://www.htu.tugraz.at/~herwig/c64/sid.php</a> (German),
 * <a href='http://stud1.tuwien.ac.at/~e9426444/sidtech2.html'>http://stud1.tuwien.ac.at/~e9426444/sidtech2.html</a> or
 * <a href='http://aachen.heimat.de/leute/nico/wolf/wolf/4/data/c64.pdf'>http://aachen.heimat.de/leute/nico/wolf/wolf/4/data/c64.pdf</a> or
 * <a href='http://www.6502.org/documents/datasheets/mos/mos_6581_sid.pdf'http://www.6502.org/documents/datasheets/mos/mos_6581_sid.pdf</a>.
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 * @todo    implement sound filters
 */
public class SID6581 extends DefaultObservable implements IOChip, WaveDataProducer, Serializable, FrequencyDataProducerOwner {

    /**
     * Filter Cutoff Frequency: Low-Nybble
     */
    private final static int FILTER_CUTOFF_FREQ_LOW = 0x15;
    /**
     * Filter Cutoff Frequency: High-Byte
     */
    private final static int FILTER_CUTOFF_FREQ_HIGH = 0x16;
    /**
     * Filter Resonance Control / Voice Input Control
     */
    private final static int FILTER_RESONANCE_VOICEINPUT_CONTROL = 0x17;
    /**
     * Select Filter Mode and Volume
     */
    private final static int FILTER_MODE_VOLUME = 0x18;
    /**
     * Analog/Digital Converter: Game Paddle 1
     */
    private final static int PADDLE1 = 0x19;
    /**
     * Analog/Digital Converter: Game Paddle 2
     */
    private final static int PADDLE2 = 0x1a;
    /**
     * Oscillator 3 Random Number Generator
     */
    private final static int RANDOM_GENERATOR = 0x1b;
    /**
     * Envelope Generator 3 Output
     */
    private final static int ENVELOPE3_OUTPUT = 0x1c;
    /**
     * number of channels, 1 = mono, 2 = stereo
     * this value must not be changed!
     */
    public final static int CHANNELS = 1;
    /**
     * bytes per sample
     * this value must not be changed!
     */
    public final static int BYTES_PER_SAMPLE = 2;
    /**
     * The three voices of the SID
     */
    private final SID6581Voice[] voices;
    /**
     * the set sample rate, e.g. 8000 for 8 KHz
     */
    private final int sampleRate;
    /**
     * C64 instance that the SID is attached to
     */
    protected final C64 c64;
    /**
     * memory for chip registers
     */
    protected final int[] registers = new int[0x20];
    /**
     * last byte written to a SID register
     */
    private int lastWritten = 0;
    /**
     * sample buffer
     */
    private final byte[] buffer = new byte[BYTES_PER_SAMPLE];

    /**
     * Create a new SID6581
     *
     * @param   c64 C64 this SID is attached to
     * @param   sampleRate  sample rate, e.g. 8000 for 8 KHz sampling
     */
    public SID6581(final C64 c64, final int sampleRate) {
        this.c64 = c64;
        this.sampleRate = sampleRate;

        // initialize SID voices
        this.voices = new SID6581Voice[3];

        for (int i = 0; i < this.voices.length; ++i) {
            this.voices[i] = new SID6581Voice(this, i * 7);
        }

        this.voices[0].setSyncSource(voices[2]);
        this.voices[1].setSyncSource(voices[0]);
        this.voices[2].setSyncSource(voices[1]);
    }

    /**
     * Get the number of clock cycles which pass until the next sample is generated
     *
     * @return number of clock cycles
     */
    public final int getUpdateRate() {
        return C64.ORIGINAL_SPEED / this.sampleRate;
    }

    /**
     * Get the cut off frequency
     *
     * @return  frequency between 30 and ~12000 Hz
     */
    private int getCutOffFrequency() {
        final int cutoff = this.registers[FILTER_CUTOFF_FREQ_LOW] & 0x07 + (this.registers[FILTER_CUTOFF_FREQ_HIGH] << 3);

        return (int) (30 + cutoff * 5.8182);
    }

    /**
     * Get the resonance for the cut off frequency
     *
     * @return  resonance value between 0 and 15
     */
    private int getResonance() {
        return this.registers[FILTER_RESONANCE_VOICEINPUT_CONTROL] >> 4;
    }

    /**
     * Is the filter for the given voice active?
     *
     * @param   voice   0 for SID voice 1, 1 for voice 2, 2 for voice 3
     * @return  true if the filters should be applied, otherwise false
     */
    private boolean isFilterActive(final int voice) {
        final int filters = this.registers[FILTER_RESONANCE_VOICEINPUT_CONTROL] & 0x0f;

        return (filters & (1 << voice)) != 0;
    }

    /**
     * Is a filter for any one of the voices active?
     *
     * @return  true if a filter must be applied, otherwise false
     */
    private boolean isFilterActive() {
        final int filters = this.registers[FILTER_RESONANCE_VOICEINPUT_CONTROL] & 0x0f;

        return filters > 0;
    }

    /**
     * Get the SID's volume
     *
     * @return  volume value between 0 and 15
     */
    protected int getVolume() {
        return this.registers[FILTER_MODE_VOLUME] & 0x0f;
    }

    /**
     * Do we activate a low pass filter?
     */
    private boolean isLowPassFilterActive() {
        return (this.registers[FILTER_MODE_VOLUME] & 0x10) != 0;
    }

    /**
     * Do we activate a band pass filter?
     */
    private boolean isBandPassFilterActive() {
        return (this.registers[FILTER_MODE_VOLUME] & 0x20) != 0;
    }

    /**
     * Do we activate a high pass filter?
     */
    private boolean isHighPassFilterActive() {
        return (this.registers[FILTER_MODE_VOLUME] & 0x40) != 0;
    }

    /**
     * Do we disconnect oscillator 3 from the output?
     */
    private boolean isDisconnectOscillator3() {
        return (this.registers[FILTER_MODE_VOLUME] & 0x80) != 0;
    }

    // implementation of the IOChip interface
    public void reset() {
        this.lastWritten = 0;
        for (int i = 0; i < this.voices.length; ++i) {
            this.voices[i].reset();
        }
    }

    public final int readRegister(final int register) {
        switch (register) {
            // paddle 1 and 2
            case PADDLE1:
            case PADDLE2:
                return 0;

            // oscillator 3 output
            case RANDOM_GENERATOR:
                return this.voices[2].getOscillatorOutput();

            // envelope generator 3 output
            case ENVELOPE3_OUTPUT:
                return this.voices[2].getEnvelopeOutput();

            // otherwise return directly from memory
            default:
                return this.lastWritten;
        }
    }

    public final void writeRegister(final int register, final int data) {
        // remember the last byte written to a SID register
        this.lastWritten = data;

        // this is data for a SID voice?
        if (register < FILTER_CUTOFF_FREQ_LOW) {
            // yes, then pass data on to the correct voice
            this.voices[register / 7].writeRegister(register % 7, data);
        } else {
            // no, instead we want to modify filters, volume etc.
            this.registers[register] = data;
        }
    }

    public final long getNextUpdate() {
        return this.voices[0].getNextUpdate();
    }

    public void update(final long cycles) {
        // update all SID voices
        final SID6581Voice[] voices_ = this.voices;

        for (int i = 0, to = voices_.length; i < to; ++i) {
            voices_[i].update(cycles);
        }

        // only continue if we have new data
        // this is the same for all voices, so we check only the first
        if (voices_[0].hasNewOutput()) {
            // here we compute the output for all voices that should be filtered
            int filterSamples = 0;
            // here we collect the output for all voices were no filters are applied
            int directSamples = 0;

            // get output from all SID voices
            for (int i = 0, to = voices_.length; i < to; ++i) {
                final int output = voices_[i].getOutput();

                if (isFilterActive(i)) {
                    filterSamples += (output >> 2);
                } else if (i == 2 && isDisconnectOscillator3()) {
                    // we do nothing
                } else {
                    directSamples += (output >> 2);
                }
            }

            // apply filters
            int sample = filterSamples;

            // add non-filtered output
            sample += directSamples;

            // apply volume and reduce to 14 bit sample
            // we got 13 bits from each voice, combined these voices but kept it 13 bits,
            // then multiplied this by the 4 bit volume to get 17 bits,
            // so we shift 3 bits to get 14 bits
            sample *= getVolume();
            sample >>= 3;

            // copy sample to buffer
            this.buffer[0] = (byte) (sample & 0xff);
            this.buffer[1] = (byte) (sample >> 8);

            // notify observers about new buffer content
            setChanged(true);
            notifyObservers(this.buffer);
        }
    }

    // implementation of the WaveDataProducer interface
    public final int getSampleRate() {
        return this.sampleRate;
    }

    public final int getBitsPerSample() {
        return BYTES_PER_SAMPLE * 8;
    }

    public final int getChannels() {
        return CHANNELS;
    }

    // implementation of the FrequencyDataProducerOwner interface
    public int getFrequencyDataProducerCount() {
        return this.voices.length;
    }

    public FrequencyDataProducer getFrequencyDataProducers(final int n) {
        return this.voices[n];
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        out.writeInt(this.sampleRate);
        out.writeInt(this.lastWritten);
        SerializationUtils.serialize(out, this.registers);
        SerializationUtils.serialize(out, this.buffer);
        SerializationUtils.serialize(out, this.voices);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        if (this.sampleRate != in.readInt()) {
            throw new IllegalStateException("Sample rate of the emulator does not match the saved sample rate!");
        }

        this.lastWritten = in.readInt();
        SerializationUtils.deserialize(in, this.registers);
        SerializationUtils.deserialize(in, this.buffer);
        SerializationUtils.deserialize(in, this.voices);
    }
}
