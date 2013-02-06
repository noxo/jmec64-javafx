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
    
//    // http://code.google.com/p/musicg/source/browse/src/com/musicg/wave/WaveHeader.java
//    // https://ccrma.stanford.edu/courses/422/projects/WaveFormat/
//    
//    final String RIFF_HEADER = "RIFF";
//    final String WAVE_HEADER = "WAVE";
//    final String FMT_HEADER = "fmt ";
//    final String DATA_HEADER = "data";
//
//    final long chunkSize=36;
//    final long subChunk1Size=16;
//    final int audioFormat=1;
//    final int channels=1;
//    final long sampleRate=8000;
//    final long byteRate=16000;
//    final int blockAlign=2;
//    final int bitsPerSample=16;
//    //final int subChunk2Size=0;
//    
//    final ByteArrayOutputStream header = new ByteArrayOutputStream();
//    
//    private byte[] createHeader(long datalen) throws Exception {
//    	
//    	header.reset();
//    	header.write(RIFF_HEADER.getBytes());
//        // little endian
//    	long _chunkSize = chunkSize + datalen;
//    	header.write(new byte[] { (byte) (_chunkSize), (byte) (_chunkSize >> 8),
//                        (byte) (_chunkSize >> 16), (byte) (_chunkSize >> 24) });
//    	header.write(WAVE_HEADER.getBytes());
//    	header.write(FMT_HEADER.getBytes());
//    	header.write(new byte[] { (byte) (subChunk1Size),
//                        (byte) (subChunk1Size >> 8), (byte) (subChunk1Size >> 16),
//                        (byte) (subChunk1Size >> 24) });
//    	header.write(new byte[] { (byte) (audioFormat),
//                        (byte) (audioFormat >> 8) });
//    	header.write(new byte[] { (byte) (channels), (byte) (channels >> 8) });
//        header.write(new byte[] { (byte) (sampleRate),
//                        (byte) (sampleRate >> 8), (byte) (sampleRate >> 16),
//                        (byte) (sampleRate >> 24) });
//        header.write(new byte[] { (byte) (byteRate), (byte) (byteRate >> 8),
//                        (byte) (byteRate >> 16), (byte) (byteRate >> 24) });
//        header.write(new byte[] { (byte) (blockAlign),
//                        (byte) (blockAlign >> 8) });
//        header.write(new byte[] { (byte) (bitsPerSample),
//                        (byte) (bitsPerSample >> 8) });
//        header.write(DATA_HEADER.getBytes());
//        long _chunk2Size = datalen;
//        header.write(new byte[] { (byte) (_chunk2Size),
//                        (byte) (_chunk2Size >> 8), (byte) (_chunk2Size >> 16),
//                        (byte) (_chunk2Size >> 24) });
//        
//    	return header.toByteArray();
//    }
}