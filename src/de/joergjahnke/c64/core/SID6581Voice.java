/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.c64.core;

import de.joergjahnke.common.emulation.FrequencyDataProducer;
import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.io.SerializationUtils;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Emulates one of the three voices of the SID 6581 sound chip
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 * @todo    also emulate ADSR bug
 */
public class SID6581Voice implements IOChip, Serializable, FrequencyDataProducer {
    // SID voice registers

    /**
     * Frequency Control - Low-Byte
     */
    private final static int FREQUENCY_CONTROL_LOW = 0;
    /**
     * Frequency Control - High-Byte
     */
    private final static int FREQUENCY_CONTROL_HIGH = 1;
    /**
     * Pulse Waveform Width - Low-Byte
     */
    private final static int PULSE_WAVEFORM_WIDTH_LOW = 2;
    /**
     * Pulse Waveform Width - High-Nybble in bits 0-3
     */
    private final static int PULSE_WAVEFORM_WIDTH_HIGH = 3;
    /**
     * Control Register
     */
    private final static int CONTROL = 4;
    /**
     * Envelope Generator: Attack / Decay Cycle Control
     */
    private final static int ENVELOPE_GENERATE_ATTACKDECAY_CONTROL = 5;
    /**
     * Envelope Generator: Sustain / Release Cycle Control
     */
    private final static int ENVELOPE_GENERATE_SUSTAINRELEASE_CONTROL = 6;
    /**
     * The maximum value to generate by the envelope generator
     */
    private final static double ENVELOPE_MAX = 255.0;
    // Wave form phases
    /**
     * Attack phase, here the sound volume rises to its maximum
     */
    private final static int ATTACK = 1;
    /**
     * Decay phase, here the sound volume drops to its sustain level
     */
    private final static int DECAY = 2;
    /**
     * Sustain phase, here the sound volume stays constant until released
     */
    private final static int SUSTAIN = 3;
    /**
     * Release phase, here the sound volumes drops to zero
     */
    private final static int RELEASE = 4;
    /**
     * The sound is finished
     */
    private final static int FINISHED = 5;
    /**
     * Wave form output is 0 to 0x1000, so the zero level should be in the middle at 0x800
     */
    private final static int WAVE_ZERO = 0x800;
    /**
     * Control register value for the triangle wave form
     */
    private final static int WAVEFORM_TRIANGLE = 1;
    /**
     * Attack time in ms for the values of the attack control register.
     * The decay and release values are three times these values.
     */
    private final static int[] ENVELOPE_RATE_TIMES = {
        2000, 8000, 16000, 24000, 38000, 56000, 68000, 80000,
        100000, 240000, 500000, 800000, 1000000, 3000000, 5000000, 8000000
    };
    /**
     * Multiplier for the waveform output when in decay or release phase
     */
    private final static double DECAY_FACTORS[] = {
        0.9038571383911007, 0.9750456476692515, 0.987443997231869, 0.9916117159902613,
        0.9946939186227746, 0.9963963671608145, 0.9970313573998748, 0.9974760913310542,
        0.9979803629404478, 0.9991916551761941, 0.9995957458773989, 0.9997473220188804,
        0.9997978525068952, 0.9999326129613874, 0.9999595672318936, 0.999974729328351
    };
    /**
     * wave form generators
     */
    private final static WaveForm waveForms[] = new WaveForm[16];
    /**
     * Internal accumulator for the wave form generation
     */
    private int ac = 0;
    /**
     * amount the envelope form rises during the attack phase per sample
     */
    private final double attackDeltas[] = new double[ENVELOPE_RATE_TIMES.length];
    /**
     * Current phase of the wave form
     */
    private int envelopePhase = FINISHED;
    /**
     * the SID voice to sync with, should synchronization be enabled
     */
    private SID6581Voice syncSource = null;
    /**
     * sample created for output
     */
    private int output = 0;
    /**
     * current value of the SID voice oscillator
     */
    private int oscillator = 0;
    /**
     * current value of the envelope generator
     */
    private double envelopeOutput = 0;
    /**
     * did we generate a new sample since retrieving the last?
     */
    private boolean hasNewOutput = false;
    /**
     * current ring bit setting
     */
    private boolean isRingBitSet = false;
    /**
     * current sync bit setting
     */
    private boolean isSyncBitSet = false;
    /**
     * xurrent test bit setting
     */
    private boolean isTestBitSet = false;
    /**
     * current wave form no.
     */
    private int waveFormNo = 0;
    /**
     * current wave form
     */
    private WaveForm waveForm;
    /**
     * currently set frequency
     */
    private int frequency = 0;
    /**
     * CPU cycles for the next update of the SID
     */
    private long nextUpdate;
    /**
     * the SID instance we belong to
     */
    private final SID6581 sid;
    /**
     * registers of the SID
     */
    protected final int[] registers;
    /**
     * Memory base address of this SID voice
     */
    private final int offset;

    /**
     * Create a new SID voice instance
     *
     * @param   sid the SID this voice belongs to
     * @param   offset  memory location of the SID voice
     */
    public SID6581Voice(final SID6581 sid, final int offset) {
        this.sid = sid;
        this.registers = sid.registers;
        this.offset = offset;
        this.nextUpdate = sid.c64.getCPU().getCycles();

        // pre-calculate the attack increments
        for (int i = 0; i < ENVELOPE_RATE_TIMES.length; ++i) {
            this.attackDeltas[i] = (ENVELOPE_MAX * C64.ORIGINAL_SPEED) / (this.sid.getSampleRate() * ENVELOPE_RATE_TIMES[i]);
        }

        // we have to initialize the wave forms?
        if (null == waveForms[0]) {
            waveForms[0] = new NullWaveForm();
            waveForms[WAVEFORM_TRIANGLE] = new TriangleWaveForm();
            waveForms[2] = new SawWaveForm();
            waveForms[3] = new CombinedWaveForm(waveForms[1], waveForms[2]);
            waveForms[4] = new PulseWaveForm();
            waveForms[5] = new CombinedWaveForm(waveForms[1], waveForms[4]);
            waveForms[6] = new CombinedWaveForm(waveForms[2], waveForms[4]);
            waveForms[7] = new CombinedWaveForm(waveForms[3], waveForms[4]);
            waveForms[8] = new NoiseWaveForm();
            waveForms[9] = new CombinedWaveForm(waveForms[1], waveForms[8]);
            waveForms[10] = new CombinedWaveForm(waveForms[2], waveForms[8]);
            waveForms[11] = new CombinedWaveForm(waveForms[3], waveForms[8]);
            waveForms[12] = new CombinedWaveForm(waveForms[4], waveForms[8]);
            waveForms[13] = new CombinedWaveForm(waveForms[1], waveForms[12]);
            waveForms[14] = new CombinedWaveForm(waveForms[2], waveForms[12]);
            waveForms[15] = new CombinedWaveForm(waveForms[3], waveForms[12]);
        }

        // currently no wave form selected
        this.waveForm = waveForms[0];
    }

    /**
     * Set the SID voice to sync with if syncronization is enabled
     *
     * @param sync  new sync source
     */
    protected void setSyncSource(final SID6581Voice sync) {
        this.syncSource = sync;
    }

    /**
     * Get the content of the frequency registers
     */
    private int getFrequencyLevel() {
        return this.registers[this.offset + FREQUENCY_CONTROL_LOW] + (this.registers[this.offset + FREQUENCY_CONTROL_HIGH] << 8);
    }

    /**
     * Get the content of the pulse width registers
     *
     * @return  4 bit value
     */
    private int getPulseWidthLevel() {
        return this.registers[this.offset + PULSE_WAVEFORM_WIDTH_LOW] + (this.registers[this.offset + PULSE_WAVEFORM_WIDTH_HIGH] & 0x0f << 8);
    }

    /**
     * Get the pulse width set via the two pulse width registers
     */
    private int getPulseWidth() {
        return getPulseWidthLevel() * this.sid.getSampleRate() / 4095;
    }

    /**
     * Is the gate bit of the control register set?
     */
    private boolean isGateBitSet() {
        return (this.registers[this.offset + CONTROL] & 0x01) != 0;
    }

    /**
     * Is the sync bit of the control register set?
     */
    private boolean isSyncBitSet() {
        return (this.registers[this.offset + CONTROL] & 0x02) != 0;
    }

    /**
     * Is the ring bit of the control register set?
     */
    private boolean isRingBitSet() {
        return (this.registers[this.offset + CONTROL] & 0x04) != 0;
    }

    /**
     * Is the test bit of the control register set?
     */
    private boolean isTestBitSet() {
        return (this.registers[this.offset + CONTROL] & 0x08) != 0;
    }

    /**
     * Get the wave form which is selected via the control register bits 4-7
     *
     * @return  4 bit value
     */
    private int getWaveFormNo() {
        return this.registers[this.offset + CONTROL] >> 4;
    }

    /**
     * Get the attack rate
     *
     * @return  4 bit value
     */
    private int getAttackRate() {
        return this.registers[this.offset + ENVELOPE_GENERATE_ATTACKDECAY_CONTROL] >> 4;
    }

    /**
     * Get the decay rate
     *
     * @return  4 bit value
     */
    private int getDecayRate() {
        return this.registers[this.offset + ENVELOPE_GENERATE_ATTACKDECAY_CONTROL] & 0x0f;
    }

    /**
     * Get the decay multiplier for the current envelope generator output per phase
     *
     * @return  value > 0 and < 1
     */
    private double getDecayFactor() {
        return DECAY_FACTORS[getDecayRate()];
    }

    /**
     * Get the release rate
     *
     * @return  4 bit value
     */
    private int getReleaseRate() {
        return this.registers[this.offset + ENVELOPE_GENERATE_SUSTAINRELEASE_CONTROL] & 0x0f;
    }

    /**
     * Get the release rate multiplier for the current envelope generator output per phase
     *
     * @return  value > 0 and < 1
     */
    private double getReleaseFactor() {
        return DECAY_FACTORS[getReleaseRate()];
    }

    /**
     * Get the value of the sustain register
     *
     * @return  4 bit value
     */
    private int getSustainLevel() {
        return this.registers[this.offset + ENVELOPE_GENERATE_SUSTAINRELEASE_CONTROL] >> 4;
    }

    /**
     * Get the envelope generator value for the sustain phase
     *
     * @return  value between 0 and 255
     */
    private int getSustainValue() {
        final int sustainLevel = getSustainLevel();

        return (sustainLevel << 4) + sustainLevel;
    }

    /**
     * Did we start a sound and not yet release it?
     *
     * @return  true if a sound is playing and not yet in the release or later phase
     */
    private boolean isSoundStarted() {
        return this.envelopePhase < RELEASE;
    }

    /**
     * Get the last output from the oscillator
     *
     * @return last oscillator output
     */
    protected int getOscillatorOutput() {
        return (this.oscillator >> 3) & 0xff;
    }

    /**
     * Get the current output of the envelope generator
     *
     * @return 8 bit value, representing the current value of the envelope generator
     */
    protected int getEnvelopeOutput() {
        return (int) this.envelopeOutput;
    }

    /**
     * Get the generated sound data
     *
     * @return  13 bit sound sample
     */
    protected int getOutput() {
        this.hasNewOutput = false;
        return this.output;
    }

    /**
     * Did we generate a new sample since retrieving the last one?
     *
     * @return  true if new data is available
     */
    protected final boolean hasNewOutput() {
        return this.hasNewOutput;
    }

    // implementation of the IOChip interface
    public void reset() {
        this.ac = 0;
        for (int i = 0; i <= ENVELOPE_GENERATE_SUSTAINRELEASE_CONTROL; ++i) {
            writeRegister(i, 0);
        }
        this.oscillator = 0;
        this.envelopeOutput = 0;
        this.envelopePhase = FINISHED;
        this.output = 0;
        this.hasNewOutput = false;
    }

    public final int readRegister(final int register) {
        return this.registers[this.offset + register];
    }

    public final void writeRegister(final int register, final int data) {
        this.registers[this.offset + register] = data;

        switch (register) {
            case FREQUENCY_CONTROL_LOW:
            case FREQUENCY_CONTROL_HIGH:
                // copy frequency for faster access
                this.frequency = getFrequency();
                break;
            case CONTROL:
                // start or stop a sound if necessary
                if (isGateBitSet()) {
                    if (!isSoundStarted()) {
                        this.envelopePhase = ATTACK;
                    }
                } else {
                    this.envelopePhase = RELEASE;
                }
                // copy some register values for faster access
                this.isRingBitSet = isRingBitSet();
                this.isSyncBitSet = isSyncBitSet();
                this.isTestBitSet = isTestBitSet();
                this.waveFormNo = getWaveFormNo();
                this.waveForm = waveForms[this.waveFormNo];
                break;
            case ENVELOPE_GENERATE_SUSTAINRELEASE_CONTROL: {
                if (this.envelopePhase == SUSTAIN) {
                    if (getSustainValue() < this.envelopeOutput) {
                        this.envelopePhase = RELEASE;
                    }
                }
                break;
            }
            // otherwise do nothing
            default:
                ;
        }
    }

    public final long getNextUpdate() {
        return this.nextUpdate;
    }

    public void update(final long cycles) {
        // the next sample is due?
        if (cycles >= getNextUpdate()) {
            // - we apply ring modulation?
            if (this.isRingBitSet && this.waveFormNo == WAVEFORM_TRIANGLE) {
                // combine output of this and the sync source
                this.oscillator = this.waveForm.getOutput(this.ac ^ this.syncSource.ac);
                // proceed to next sample
                this.ac += this.frequency;
                this.ac %= this.sid.getSampleRate();
            } else {
                // get output from the wave generator
                this.oscillator = this.waveForm.getOutput(this.ac);
                // proceed to next sample
                this.ac += this.frequency;
                this.ac %= this.sid.getSampleRate();
            }
            // - synchronize with sync source if necessary
            if (this.isSyncBitSet) {
                // whenever the master cycles around, it resets the phase of the slave oscillator.
                if (this.syncSource.ac + this.syncSource.frequency > this.sid.getSampleRate()) {
                    this.ac = 0;
                }
            }

            // determine waveform value
            switch (this.envelopePhase) {
                case ATTACK:
                    // let volume rise until the maximum, then change to decay phase
                    this.envelopeOutput += this.attackDeltas[getAttackRate()];
                    if (this.envelopeOutput > ENVELOPE_MAX) {
                        this.envelopeOutput = ENVELOPE_MAX;
                        this.envelopePhase = DECAY;
                    }
                    break;

                case DECAY:
                    // let volume drop until sustain value, then change to sustain phase
                    this.envelopeOutput *= getDecayFactor();
                    if (this.envelopeOutput < getSustainValue()) {
                        this.envelopeOutput = getSustainValue();
                        this.envelopePhase = SUSTAIN;
                    }
                    break;

                case RELEASE:
                    // let volume drop until zero, then we are finished
                    this.envelopeOutput *= getReleaseFactor();
                    if (this.envelopeOutput < 1.0) {
                        this.envelopeOutput = 0.0;
                        this.envelopePhase = FINISHED;
                    }
                    break;

                default:
                    ;
            }

            // combine oscillator and envelope to generate the sample output
            this.output = this.isTestBitSet ? 0 : (((this.oscillator - WAVE_ZERO) * (int) this.envelopeOutput) >> 7);

            // next update will follow when we need the next sample
            this.nextUpdate += this.sid.getUpdateRate();

            // we generated a new sample
            this.hasNewOutput = true;
        }
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        out.writeInt(this.ac);
        out.writeInt(this.envelopePhase);
        out.writeInt(this.output);
        out.writeInt(this.oscillator);
        out.writeDouble(this.envelopeOutput);
        out.writeBoolean(this.hasNewOutput);
        out.writeBoolean(this.isRingBitSet);
        out.writeBoolean(this.isSyncBitSet);
        out.writeBoolean(this.isTestBitSet);
        out.writeInt(this.waveFormNo);
        out.writeInt(this.frequency);
        out.writeLong(this.nextUpdate);
        SerializationUtils.serialize(out, this.registers);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        this.ac = in.readInt();
        this.envelopePhase = in.readInt();
        this.output = in.readInt();
        this.oscillator = in.readInt();
        this.envelopeOutput = in.readDouble();
        this.hasNewOutput = in.readBoolean();
        this.isRingBitSet = in.readBoolean();
        this.isSyncBitSet = in.readBoolean();
        this.isTestBitSet = in.readBoolean();
        this.waveFormNo = in.readInt();
        this.waveForm = waveForms[this.waveFormNo];
        this.frequency = in.readInt();
        this.nextUpdate = in.readLong();
        SerializationUtils.deserialize(in, this.registers);
    }

    // implementation of the FrequencyDataProducer interface
    public int getVolume() {
        return this.waveFormNo == 0 || this.envelopeOutput == 0.0 ? 0 : this.sid.getVolume() * 100 / 0x0f;
    }

    public int getType() {
        switch (this.waveFormNo) {
            case 1:
                // use the Oboe for the triangle wave
                return 69;
            case 2:
                // use the Lead 2 (sawtooth) for the sawtooth wave
                return 81;
            case 4:
                // use the Lead 1 (square) for the pulse (square) wave
                return 80;
            case 8:
                // use the Gunshot for the noise wave
                return 127;
            default:
                return 0;
        }
    }

    /**
     * Get the frequency set via the two frequency registers
     *
     * @return the frequency in Hz
     */
    public int getFrequency() {
        return (int) ((long) getFrequencyLevel() * C64.ORIGINAL_SPEED / 16777216);
    }

    // interface which describes a wave form
    /**
     * Decribes a wave form
     */
    abstract class WaveForm {

        /**
         * Get the wave value at the given sample index
         */
        public abstract int getOutput(int n);
    }

    // implementation of the WaveForm interface for saw, pulse, triangle and triangle2D waves of the SID
    /**
     * Describes the wave form of the saw wave of the SID
     */
    class SawWaveForm extends WaveForm {

        public final int getOutput(final int n) {
            return 0xfff * n / sid.getSampleRate();
        }
    }

    /**
     * Describes the wave form of the pulse wave of the SID
     */
    class PulseWaveForm extends WaveForm {

        public final int getOutput(final int n) {
            return isTestBitSet || n >= getPulseWidth() ? 0xfff : 0;
        }
    }

    /**
     * Describes the wave form of the triangle wave of the SID
     */
    class TriangleWaveForm extends WaveForm {

        public final int getOutput(final int n) {
            final int sampleRate = sid.getSampleRate();

            return n < sampleRate / 2 ? 0xfff * 2 * n / sampleRate : 0xfff * 2 * (sampleRate - n) / sampleRate;
        }
    }

    /**
     * Describes the wave form of the noise wave of the SID
     */
    class NoiseWaveForm extends WaveForm {

        private final Random rand = new Random();

        public final int getOutput(final int n) {
            return this.rand.nextInt(0x1000);
        }
    }

    /**
     * Null wave form always returns 0
     */
    class NullWaveForm extends WaveForm {

        public final int getOutput(final int n) {
            return 0;
        }
    }

    /**
     * Combines two SID waveforms
     */
    class CombinedWaveForm extends WaveForm {
        // the wave forms to be combined

        private final WaveForm waveForm1,  waveForm2;

        /**
         * Create a new wave form combination of two other wave forms
         */
        public CombinedWaveForm(final WaveForm waveForm1, final WaveForm waveForm2) {
            this.waveForm1 = waveForm1;
            this.waveForm2 = waveForm2;
        }

        /**
         * When combining two samples the SID uses a special AND operation which also affects neighboring bits
         */
        public final int getOutput(final int n) {
            final int sample1 = this.waveForm1.getOutput(n);
            final int sample2 = this.waveForm2.getOutput(n);

            return (sample1 << 1) & (sample1 >> 1) & (sample2 << 1) & (sample2 >> 1);
        }
    }
}
