package de.joergjahnke.common.pulseaudio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class PulseAudioDataLine {

	public final static int DEFAULT_SINK_PORT = 8081;
	public final static String DEFAULT_SINK_IP = "127.0.0.1";

	private String ip = DEFAULT_SINK_IP;
	private int port = DEFAULT_SINK_PORT;

	private SocketChannel client;
	private boolean open;
	
	public PulseAudioDataLine(String ip, int port) {
		this.port = port;
		this.ip = ip;
	}
	
	public static PulseAudioDataLine getDefault() {
		return new PulseAudioDataLine(DEFAULT_SINK_IP, DEFAULT_SINK_PORT);
	}

	public void start() throws Exception {
		client = SocketChannel.open();
// Use blocking connection, to we can determine when feeding data too fast
// by the time socket starts blocking.
//		client.configureBlocking(false);
		client.connect(new java.net.InetSocketAddress(ip,port));
		
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
