/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.extendeddevices;

import de.joergjahnke.common.emulation.WaveDataProducer;
import de.joergjahnke.common.util.Observer;
import java.io.ByteArrayOutputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * Observes and plays wave data delivered by an observable object.
 * This implementation works for J2SE.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class WavePlayer implements Observer {

    /**
     * buffer size where we notify the thread filling the data line
     */
    private final static int NOTIFY_THRESHOLD_BUFFERSIZE = 256;
    /**
     * plays the generated .wav data
     */
    private SourceDataLine dataLine;
    /**
     * thread that writes data to the data line
     */
    private Thread datalineWriterThread = null;
    /**
     * buffer where we write new data to before it gets passed to the data line
     */
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    /**
     * Creates a new instance of WavePlayer
     * 
     * @param   producer    data source delivering the wave data
     */
    public WavePlayer(final WaveDataProducer producer) {
        try {
            final AudioFormat audioFormat = new AudioFormat(producer.getSampleRate(), producer.getBitsPerSample(), producer.getChannels(), true, false);
            final DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, audioFormat);

            this.dataLine = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
            this.dataLine.open(dataLine.getFormat());
            this.dataLine.start();

            this.datalineWriterThread = new Thread() {

                @Override
                public void run() {
                    while (dataLine.isOpen()) {
                        final byte[] data = buffer.toByteArray();

                        dataLine.write(data, 0, data.length);
                        synchronized (buffer) {
                            buffer.reset();
                            try {
                                buffer.wait();
                            } catch (InterruptedException e) {
                            }
                        }
                    }
                }
            };
            this.datalineWriterThread.start();
        } catch (Exception e) {
            // we cannot not play audio, that's OK
        }
    }

    /**
     * Stop the wave player
     */
    public void stop() {
        this.dataLine.stop();
        this.dataLine.close();
    }

    // implementation of the Observer interface
    /**
     * We write the sound data sent from the producer into the dataline
     */
    public void update(final Object observed, final Object obj) {
        if (null != this.dataLine && observed instanceof WaveDataProducer) {
            final byte[] data = (byte[]) obj;

            synchronized (this.buffer) {
                this.buffer.write(data, 0, data.length);
                if (this.buffer.size() > NOTIFY_THRESHOLD_BUFFERSIZE) {
                    this.buffer.notifyAll();
                }
            }
        }
    }
}
