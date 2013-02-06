package de.joergjahnke.common.pulseaudio;

import java.io.ByteArrayOutputStream;

// Erkki Nokso-Koivisto

public class WavUtils {
	
	// http://code.google.com/p/musicg/source/browse/src/com/musicg/wave/WaveHeader.java
	// https://ccrma.stanford.edu/courses/422/projects/WaveFormat/

	static final String RIFF_HEADER = "RIFF";
	static final String WAVE_HEADER = "WAVE";
	static final String FMT_HEADER = "fmt ";
	static final String DATA_HEADER = "data";

	static final long chunkSize = 36;
	static final long subChunk1Size = 16;
	static final int audioFormat = 1;
	static final long byteRate = 16000;
	static final int blockAlign = 2;
	static final int bitsPerSample = 16;

	/**
	 * @param datalen
	 * @return
	 * @throws Exception
	 */
	public static byte[] createWavHeader(long sampleRate,
			int channels, long sampleDataLength) throws Exception {
		
		ByteArrayOutputStream header = new ByteArrayOutputStream();
		
		header.write(RIFF_HEADER.getBytes());
		// little endian
		long _chunkSize = chunkSize + sampleDataLength;
		header.write(new byte[] { (byte) (_chunkSize),
				(byte) (_chunkSize >> 8), (byte) (_chunkSize >> 16),
				(byte) (_chunkSize >> 24) });
		header.write(WAVE_HEADER.getBytes());
		header.write(FMT_HEADER.getBytes());
		header.write(new byte[] { (byte) (subChunk1Size),
				(byte) (subChunk1Size >> 8), (byte) (subChunk1Size >> 16),
				(byte) (subChunk1Size >> 24) });
		header.write(new byte[] { (byte) (audioFormat),
				(byte) (audioFormat >> 8) });
		header.write(new byte[] { (byte) (channels), (byte) (channels >> 8) });
		header.write(new byte[] { (byte) (sampleRate),
				(byte) (sampleRate >> 8), (byte) (sampleRate >> 16),
				(byte) (sampleRate >> 24) });
		header.write(new byte[] { (byte) (byteRate), (byte) (byteRate >> 8),
				(byte) (byteRate >> 16), (byte) (byteRate >> 24) });
		header.write(new byte[] { (byte) (blockAlign), (byte) (blockAlign >> 8) });
		header.write(new byte[] { (byte) (bitsPerSample),
				(byte) (bitsPerSample >> 8) });
		header.write(DATA_HEADER.getBytes());
		long _chunk2Size = sampleDataLength;
		header.write(new byte[] { (byte) (_chunk2Size),
				(byte) (_chunk2Size >> 8), (byte) (_chunk2Size >> 16),
				(byte) (_chunk2Size >> 24) });

		return header.toByteArray();
	}
}
