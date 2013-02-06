package de.joergjahnke.common.pulseaudio;

import de.joergjahnke.common.emulation.WaveDataProducer;
import de.joergjahnke.common.util.Observer;
import java.io.ByteArrayOutputStream;


/**
 * Observes and plays wave data delivered by an observable object.
 */

public class PulseAudioWavePlayer implements Observer {

    /**
     * buffer size where we notify the thread filling the data line
     */
    private final static int NOTIFY_THRESHOLD_BUFFERSIZE = 512;
    /**
     * plays the generated .wav data
     */
    private PulseAudioDataLine dataLine;
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
    public PulseAudioWavePlayer(final WaveDataProducer producer) {
    	System.out.println(producer.getChannels() + "/" + producer.getSampleRate());
    	try {
 
            this.dataLine = PulseAudioDataLine.getDefault();
            //this.dataLine.open(dataLine.getFormat());
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
        	e.printStackTrace();
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