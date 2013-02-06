package de.joergjahnke.common.pulseaudio;

import java.io.ByteArrayOutputStream;

public class Test {
	
	public static void main(String arg[]) {
		
		try {
			
			PulseAudioDataLine lineout = new PulseAudioDataLine();
			lineout.start();
			
			// 16 bits/ sample, Little Endian, now 256 bytes (128 samples) of silence
			byte rawPcmSampleData[] = new byte[256]; 
			
			// WAV format header
			//byte wavHeader[] = WavUtils.createWavHeader(8000, 1, pcmSampleData.length);

			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			//buffer.write(wavHeader);
			buffer.write(rawPcmSampleData);
			
			// Feed raw PCM or WAV to lineout
			
			lineout.write(buffer.toByteArray(), 0, buffer.size());
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
