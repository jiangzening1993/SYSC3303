// TFTPServer.java 
// This class is the server side of a simple TFTP server based on
// UDP/IP. The server receives a read or write packet from a client and
// sends back the appropriate response without any actual file transfer.
// One socket (69) is used to receive (it stays open) and another for each response. 

import java.io.*;
import java.net.*;

public class TFTPServer {

	// types of requests we can receive
	public static enum Request {
		READ, WRITE, ERROR
	};

	// responses for valid requests
	public static final byte[] readResp = { 0, 3, 0, 1 };
	public static final byte[] writeResp = { 0, 4, 0, 0 };

	// UDP datagram packets and sockets used to send / receive
	private DatagramPacket receivePacket;
	private DatagramSocket receiveSocket;

	public TFTPServer() {
		try {
			// Construct a datagram socket and bind it to port 69
			// on the local host machine. This socket will be used to
			// receive UDP Datagram packets.
			receiveSocket = new DatagramSocket(69);
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
	}

	public void receiveAndSendTFTP() throws Exception {
		for (;;) { // loop forever
			// Construct a DatagramPacket for receiving packets up
			// to 100 bytes long (the length of the byte array).

			byte[] data = new byte[516];
			receivePacket = new DatagramPacket(data, data.length);

			try {
				receiveSocket.receive(receivePacket);
				System.out.println("Packet received");
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}

			RequestThread thread = new RequestThread(receivePacket);
			thread.start();
		}
	}

	public static void main(String args[]) throws Exception {
		TFTPServer c = new TFTPServer();
		c.receiveAndSendTFTP();
	}
}
