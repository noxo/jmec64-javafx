package de.joergjahnke.common.pulseaudio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

// Erkki Nokso-Koivisto

public class PulseAudioDataLine {
	
	private String serverIP = "127.0.0.1";
//	private String serverIP = "192.168.0.107";

	private int port = 8081;

	SocketChannel client;
	
	private boolean open;
	
	public static PulseAudioDataLine getDefault() {
		return new PulseAudioDataLine();
	}
	
	public void start() throws Exception {
		client = SocketChannel.open();
// Use blocking connection, to we can determine when feeding data too fast
// by the time socket starts blocking.
//		client.configureBlocking(false);
		client.connect(new java.net.InetSocketAddress(serverIP,port));
		
//		while (!client.finishConnect())
//			Thread.yield();
		
		open = true;
	}
	
	public void write(byte data[], int off, int len) {
		
		try {
			client.write(ByteBuffer.wrap(data));
		} catch (IOException e) {
			e.printStackTrace();
			stop();
		}
		
	}
	
	public void stop(){
		
		try {
			client.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		open = false;
		
	}
	
	public void close() {
		
		try {
			client.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		open = false;

	}

	public boolean isOpen() {
		return open;
	}
}
