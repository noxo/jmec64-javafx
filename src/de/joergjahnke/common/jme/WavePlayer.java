/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.jme;

import de.joergjahnke.common.emulation.WaveDataProducer;
import de.joergjahnke.common.util.Observer;
import java.io.ByteArrayInputStream;
import java.util.Vector;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;

/**
 * Observes and plays the wave data delivered by a wave data producer.
 * This implementation works for J2ME.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class WavePlayer implements PlayerListener, Observer {

    /**
     * heaver for the generated .wav format streams
     */
    private final static short[] WAV_HEADER = {
        'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E',
        'f', 'm', 't', ' ', 16, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 16, 0,
        'd', 'a', 't', 'a', 0, 0, 0, 0
    };
    /**
     * list of players containing the wave data
     */
    protected final Vector players = new Vector();
    /**
     * buffers to fill with wave data
     */
    private byte[][] buffers = new byte[3][];
    /**
     * buffer we currently fill
     */
    private int currentBuffer = 0;
    /**
     * current position in the buffer
     */
    private int currentBufferPos = WAV_HEADER.length;
    /**
     * plays the generated .wav data
     */
    private Player currentPlayer = null;
    /**
     * wave data producer
     */
    private final WaveDataProducer producer;

    /**
     * Creates a new instance of WavePlayer
     *
     * @param producer  the data producer we play for
     */
    public WavePlayer(final WaveDataProducer producer) {
        // store WaveDataProducer instance
        this.producer = producer;

        // store the channels and bits per sample at the correct position of the wav-header
        WAV_HEADER[ 22] = (short) producer.getChannels();
        WAV_HEADER[ 32] = (short) producer.getChannels();
        WAV_HEADER[ 34] = (short) producer.getBitsPerSample();

        // store the buffer size and sample rate at the correct position of the wav-header
        final int defaultBufferSize = producer.getSampleRate() / 10;
        final int bs = defaultBufferSize + WAV_HEADER.length;

        for (int i = 0, shift = 0; i < 4; ++i, shift += 8) {
            WAV_HEADER[ 4 + i] = (short) ((bs >> shift) & 0xff);
            WAV_HEADER[ 40 + i] = (short) ((defaultBufferSize >> shift) & 0xff);
            WAV_HEADER[ 24 + i] = (short) ((producer.getSampleRate() >> shift) & 0xff);
            WAV_HEADER[ 28 + i] = (short) (((producer.getSampleRate() * producer.getChannels() * producer.getBitsPerSample() / 8) >> shift) & 0xff);
        }

        // create the zero buffer as a buffer that plays no sound
        for (int i = 0; i < this.buffers.length; ++i) {
            this.buffers[i] = new byte[bs];
            initBuffer(this.buffers[i]);
        }
    }

    /**
     * Copy wave header to buffer
     */
    private final void initBuffer(final byte[] buffer) {
        for (int i = 0; i < WAV_HEADER.length; ++i) {
            buffer[i] = (byte) (WAV_HEADER[i] & 0xff);
        }
    }

    // implementation of the Observer interface
    public void update(final Object observed, final Object obj) {
        if (observed instanceof WaveDataProducer) {
            if (obj instanceof byte[]) {
                final byte[] buffer = (byte[]) obj;

                update(buffer, 0, buffer.length);
            }
        }
    }

    /**
     * fill current buffer with bytes delivered
     *
     * @param   buffer  buffer with data to copy
     * @param   offset  offset where to start copying
     * @param   numBytes    number of bytes to copy
     */
    public void update(final byte[] buffer, final int offset, final int numBytes) {
        final byte[] cb = this.buffers[this.currentBuffer];
        final int n = Math.min(numBytes, cb.length - this.currentBufferPos);

        synchronized (this) {
            System.arraycopy(buffer, offset, cb, this.currentBufferPos, n);
            this.currentBufferPos += n;

            // the buffer is full?
            if (this.currentBufferPos >= cb.length) {
                // then initialize a new player
                try {
                    final Player player = Manager.createPlayer(new ByteArrayInputStream(cb), "audio/x-wav");

                    try {
                        player.realize();
                    } catch (Exception e) {
                        // we could not realize the player, this will be done later
                    }
                    try {
                        player.prefetch();
                    } catch (Exception e) {
                        // we could not prefetch player data, this will be done later
                    }
                    player.addPlayerListener(this);
                    // we currently don't play a sound?
                    if (null == this.currentPlayer) {
                        // then start this player
                        this.currentPlayer = player;
                        this.currentPlayer.start();
                    } else {
                        // otherwise add it to the queue
                        this.players.addElement(player);
                    }
                } catch (Exception e) {
                    // we could not create a player, switch off the sound
                    e.printStackTrace();
                    producer.deleteObservers();
                }

                // switch to the next buffer
                ++this.currentBuffer;
                this.currentBuffer %= this.buffers.length;
                this.currentBufferPos = WAV_HEADER.length;
            }
        }

        // there was more data than fit into the current buffer?
        if (n < numBytes) {
            // then also process the rest
            update(buffer, offset + n, numBytes - n);
        }
    }

    // implementation of the PlayerListener interface
    /**
     * We start the next available player when the media ends
     */
    public void playerUpdate(final Player player, final String event, final Object eventData) {
        if (PlayerListener.END_OF_MEDIA.equals(event)) {
            // we have more players in the queue?
            if (!this.players.isEmpty()) {
                // then use the next player from the queue and play it
                this.currentPlayer = (Player) this.players.elementAt(0);
                this.players.removeElementAt(0);
                try {
                    this.currentPlayer.start();
                } catch (Exception e) {
                    // we couldn't start the player, we stop the sound as it does not seem to work
                    producer.deleteObservers();
                }
            } else {
                // otherwise we are idle
                this.currentPlayer = null;
            }
        }
    }
}
