// TFTPSim.java
// This class is the beginnings of an error simulator for a simple TFTP server 
// based on UDP/IP. The simulator receives a read or write packet from a client and
// passes it on to the server.  Upon receiving a response, it passes it on to the 
// client.
// One socket (23) is used to receive from the client, and another to send/receive
// from the server.  A new socket is used for each communication back to the client.   

import java.io.*;
import java.net.*;
import java.util.*;

public class TFTPSim {

	// UDP datagram packets and sockets used to send / receive
	private DatagramPacket sendPacket, receivePacket;
	private DatagramSocket receiveSocket, sendSocket, sendReceiveSocket;

	String ans;
	String dataType;
	int blockNumber;
	int errorType;
	int delay;

	private byte[] blockCounter;

	public static enum Request {
		READ, WRITE, ACK, DATA
	}

	public static enum TransmissionError {
		DELAY, DUPLICATE, LOSE
	}

	public TFTPSim() {
		try {
			// Construct a datagram socket and bind it to port 23
			// on the local host machine. This socket will be used to
			// receive UDP Datagram packets from clients.
			receiveSocket = new DatagramSocket(23);
			// Construct a datagram socket and bind it to any available
			// port on the local host machine. This socket will be used to
			// send and receive UDP Datagram packets from the server.
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}

		blockCounter = new byte[2];
	}

	public void passOnTFTP() {

		byte[] data;

		int clientPort, j = 0, len;

		Scanner sc = new Scanner(System.in);

		// Prompt user for error

		System.out.print("What block number should the error take place on?");
		int blockNumber = sc.nextInt();

		System.out
				.print("Error type on block?(1 = delay, 2 = duplicate, 3 = lose)");
		int errorType = sc.nextInt();

		System.out
				.print("What kind of data would you want the error to be generated on? (ACK, WRQ, RRQ, DATA)");
		String dataType = sc.next().toUpperCase();

		if (errorType == 1 || errorType == 2) {
			System.out.print("How much of a delay (in ms)?");
			int delay = sc.nextInt();
		}

		for (;;) { // loop forever
			// Construct a DatagramPacket for receiving packets up
			// to 100 bytes long (the length of the byte array).

			data = new byte[512];
			receivePacket = new DatagramPacket(data, data.length);

			System.out.println("Simulator: Waiting for packet.");
			// Block until a datagram packet is received from receiveSocket.
			try {
				receiveSocket.receive(receivePacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}

			clientPort = receivePacket.getPort();

			// Process the received datagram.
			System.out.println("Simulator: Packet received:");
			System.out.println("From host: " + receivePacket.getAddress());
			System.out.println("Host port: " + clientPort);
			len = receivePacket.getLength();
			System.out.println("Length: " + len);
			System.out.println("Containing: ");

			// print the bytes
			/*
			 * for (j = 0; j < len; j++) { System.out.println("byte " + j + " "
			 * + data[j]); }
			 */

			// Form a String from the byte array, and print the string.
			String received = new String(data, 0, len);
			System.out.println(received);

			// CHECK TO SEE IF IT IS THE APPROPRIATE PACKAGE TO DELAY
			if (isTransErrorPacket(receivePacket, dataType, blockNumber)) {
				handleError(receivePacket, getError(errorType),
						sendReceiveSocket, 69);
			}

			// **************************************Send to
			// server****************************************************************
			if ((getError(errorType) == TransmissionError.DELAY || getError(errorType) == TransmissionError.DUPLICATE)
					|| getBlock(receivePacket.getData()) != blockNumber) {
				sendPacket = new DatagramPacket(data, len,
						receivePacket.getAddress(), 69);

				System.out.println("Simulator: sending packet.");
				System.out.println("To host: " + sendPacket.getAddress());
				System.out.println("Destination host port: "
						+ sendPacket.getPort());
				len = sendPacket.getLength();
				System.out.println("Length: " + len);
				System.out.println("Containing: ");
				/*
				 * for (j = 0; j < len; j++) { System.out.println("byte " + j +
				 * " " + data[j]); }
				 */

				// Send the datagram packet to the server via the send/receive
				// socket.

				try {
					sendReceiveSocket.send(sendPacket);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}
			}

			// Construct a DatagramPacket for receiving packets up
			// to 100 bytes long (the length of the byte array).

			data = new byte[512];
			receivePacket = new DatagramPacket(data, data.length);

			System.out.println("Simulator: Waiting for packet.");
			try {
				// Block until a datagram is received via sendReceiveSocket.
				sendReceiveSocket.receive(receivePacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}

			// Process the received datagram.
			System.out.println("Simulator: Packet received:");
			System.out.println("From host: " + receivePacket.getAddress());
			System.out.println("Host port: " + receivePacket.getPort());
			len = receivePacket.getLength();
			System.out.println("Length: " + len);
			System.out.println("Containing: ");
			/*
			 * for (j = 0; j < len; j++) { System.out.println("byte " + j + " "
			 * + data[j]); }
			 */

			try {
				// Construct a new datagram socket and bind it to any port
				// on the local host machine. This socket will be used to
				// send UDP Datagram packets.
				sendSocket = new DatagramSocket();
			} catch (SocketException se) {
				se.printStackTrace();
				System.exit(1);
			}

			// CHECK TO SEE IF IT IS THE APPROPRIATE PACKAGE TO DELAY
			if (isTransErrorPacket(receivePacket, dataType, blockNumber)) {
				handleError(receivePacket, getError(errorType), sendSocket,
						clientPort);
			}

			// *************************Send to
			// client**********************************

			// Send the datagram packet to the client via a new socket.

			if ((getError(errorType) == TransmissionError.DELAY || getError(errorType) == TransmissionError.DUPLICATE)
					|| getBlock(receivePacket.getData()) != blockNumber) {
				sendPacket = new DatagramPacket(data,
						receivePacket.getLength(), receivePacket.getAddress(),
						clientPort);

				System.out.println("Simulator: Sending packet:");
				System.out.println("To host: " + sendPacket.getAddress());
				System.out.println("Destination host port: "
						+ sendPacket.getPort());
				len = sendPacket.getLength();
				System.out.println("Length: " + len);
				System.out.println("Containing: ");
				/*
				 * for (j = 0; j < len; j++) { System.out.println("byte " + j +
				 * " " + data[j]); }
				 */

				try {
					sendSocket.send(sendPacket);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(1);
				}

				System.out.println("Simulator: packet sent using port "
						+ sendSocket.getLocalPort());
				System.out.println();
			}
			sc.close();

			// We're finished with this socket, so close it.
			sendSocket.close();
		} // end of loop

	}

	public boolean isTransErrorPacket(DatagramPacket rec, String dataType,
			int blockNum) {
		if (checkOpcode(rec.getData()) == checkOpcode(dataType)) {
			if (getBlock(rec.getData()) == blockNum) {
				return true;
			}
		}

		return false;
	}

	// Handles client to server error
	public void handleError(DatagramPacket rec, TransmissionError x,
			DatagramSocket send, int port) {
		if (x == TransmissionError.DELAY) {
			try {
				Thread.sleep(getDelay());
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(1);
			}
		} else if (x == TransmissionError.DUPLICATE) {
			DatagramPacket sendPacket1 = new DatagramPacket(rec.getData(),
					rec.getLength(), rec.getAddress(), port);
			// Send an exact copy and delay the second.
			try {
				send.send(sendPacket1);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}

			try {
				Thread.sleep(getDelay());
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(1);
			}
		} else {
			// do nothing, packet lost.
		}

	}

	public int getDelay() {
		return delay;
	}

	// public byte[] getBlock(byte[] data){

	// }

	// Check opcode and return request type.
	public Request checkOpcode(byte[] data) {
		Request dataType = null;

		if (data[0] == 0) {
			if (data[1] == 1) {
				dataType = Request.READ;
			} else if (data[1] == 2) {
				dataType = Request.WRITE;
			} else if (data[1] == 3) {
				dataType = Request.DATA;
			} else if (data[1] == 4) {
				dataType = Request.ACK;
			}
		}
		return dataType;
	}

	public Request checkOpcode(String s) {
		Request dataType = null;

		if (s.equals("RRQ")) {
			dataType = Request.READ;
		} else if (s.equals("WRQ")) {
			dataType = Request.WRITE;
		} else if (s.equals("DATA")) {
			dataType = Request.DATA;
		} else if (s.equals("ACK")) {
			dataType = Request.ACK;
		}
		return dataType;
	}

	// Sets the type of error to be handled.
	private TransmissionError getError(int errorType) {
		TransmissionError error = null;
		if (errorType == 1) {
			error = TransmissionError.DELAY;
		} else if (errorType == 2) {
			error = TransmissionError.DUPLICATE;
		} else if (errorType == 3) {
			error = TransmissionError.LOSE;
		}

		return error;
	}

	public static void main(String args[]) {
		TFTPSim s = new TFTPSim();
		s.passOnTFTP();
	}

	// Returns the blockNum as an int.
	public static int getBlock(byte[] data) {
		int x = (int) data[2];
		int y = (int) data[3];
		if (x < 0) {
			x = 256 + x;
		}
		if (y < 0) {
			y = 256 + y;
		}
		return 256 + x + y;
	}
}
