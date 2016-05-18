// TFTPClient.java
// This class is the client side for a very simple assignment based on TFTP on
// UDP/IP. The client uses one port and sends a read or write request and gets 
// the appropriate response from the server.  No actual file transfer takes place.   

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Scanner;

public class TFTPClient {

	private DatagramSocket sendReceiveSocket;
	private FileInputStream inStream;
	private FileOutputStream outStream;
	private int sendPort;
	private int timeout = 2000;

	// private String saveFolder = System.getProperty("user.dir") +
	// File.separator + "Client Files" + File.separator;

	// we can run in normal (send directly to server) or test
	// (send to simulator) mode
	public static enum Mode {
		NORMAL, TEST
	};

	public static enum Request {
		READ, WRITE, QUIT
	};

	public static enum AckPack {
		INV, VAL
	};

	public TFTPClient() {
		try {
			// Construct a datagram socket and bind it to any available
			// port on the local host machine. This socket will be used to
			// send and receive UDP Datagram packets.
			sendReceiveSocket = new DatagramSocket();

		} catch (SocketException se) { // Can't create the socket.
			se.printStackTrace();
			System.exit(1);
		}
	}

	public void sendAndReceive() {
		Scanner scan = new Scanner(System.in);
		String mode; // filename and mode as Strings
		Request req;
		Mode run;
		// Boolean modeSelect = false, requestSelect = false;

		// run = Mode.TEST; // change to NORMAL to send directly to server

		// Select Test/Normal mode, otherwise loop forever
		System.out.print("Mode select (Test/Normal):");
		while (true) {
			String s = scan.next().toLowerCase();
			if (s.equals("test")) {
				run = Mode.TEST;
				sendPort = 23;
				break;
			} else if (s.equals("normal")) {
				run = Mode.NORMAL;
				sendPort = 69;
				break;
			} else {
				System.out.println("Invalid mode.");
				System.out.print("Mode select (Test/Normal):");
			}
		}

		System.out.println(run.toString() + " mode");

		while (true) {
			// Select Read/Write request, otherwise loop forever
			System.out.print("Request select (read/Write/quit):");
			while (true) {
				String s = scan.next().toLowerCase();
				if (s.equals("read")) {
					req = Request.READ;
					break;
				} else if (s.equals("write")) {
					req = Request.WRITE;
					break;
				} else if (s.equals("quit")) {
					req = Request.QUIT;
					break;
				} else {
					System.out.println("Invalid request");
					System.out.print("Request select (read/Write/quit):");
				}
			}
						
			
			// if Request is quit, then stop the loop;
			if (req == Request.QUIT)
				break;

			
			String fileName;
			// choose file
			System.out
					.print("Enter a file name (with file path), type 'quit' to exit: ");
			fileName = scan.next();
			if (fileName.equalsIgnoreCase("quit")) {
				System.exit(1);
			}
			File file = new File(fileName);

			System.out.println("Select file: " + fileName);
			
			System.out.println("Filename " + file.getName());

			// Select Read/Write request, otherwise loop forever
			System.out.print("Select a mode (octet/netascii): ");
			while (true) {
				mode = scan.next().toLowerCase();
				if (mode.equals("octet") || mode.equals("netascii")) {
					break;
				} else {
					System.out.println("Invalid mode");
					System.out.print("Select a mode (octet/netascii): ");
				}
			}

			if (req == Request.READ) {
				try {
					outStream = new FileOutputStream(new File(file.getName()));
					read(file.getAbsolutePath(), mode);
				} catch (FileNotFoundException e) {
					System.out.println("File not exist");
					continue;
				}
			} else if (req == Request.WRITE) {
				try {
					inStream = new FileInputStream(file);
					write(file.getName(), mode);
				} catch (FileNotFoundException e) {
					System.out.println("File not exist");
					continue;
				}
			}
		}

		// We're finished, so close the socket and scanner.
		sendReceiveSocket.close();
		scan.close();
	}
	
	private void read(String fileName, String mode) {
		byte[] msg = readMsgGenerate(fileName, mode);
		DatagramPacket lastPacket = null; 
		try {
			DatagramPacket sendPacket = new DatagramPacket(msg, msg.length,
					InetAddress.getLocalHost(), sendPort);
			System.out.println("Sending to...");
			printPacket(sendPacket);
			sendReceiveSocket.send(sendPacket);
			lastPacket = sendPacket;
			// loop until data received has length less than 516 bytes
			boolean fileEnd = false;
			for (int i = 1; !fileEnd; i++) {

				// receive data packet for block i
				msg = new byte[516];
				DatagramPacket dataPacket = new DatagramPacket(msg, msg.length);
				
				while(true)
				{
					try					
					{
						sendReceiveSocket.receive(dataPacket);
						break;
					}
					catch(SocketTimeoutException ex){
							ex.printStackTrace();
							sendReceiveSocket.send(lastPacket);
					}
				}
				System.out.println("Received from...");
				printPacket(dataPacket);

				if (parseData(i, dataPacket) == AckPack.INV)
					return;

				// if the packet received a file less than 516, then stop
				if (dataPacket.getLength() != 516)
					fileEnd = true;

				outStream.write(dataPacket.getData(), 4,
						dataPacket.getLength() - 4);

				// send ack packet for block i
				msg = new byte[] { 0, 4, (byte) ((i / 256) & 0xFF),
						(byte) ((i % 256) & 0xFF) };
				DatagramPacket ackPacket = new DatagramPacket(msg, msg.length,
						InetAddress.getLocalHost(), dataPacket.getPort());
				System.out.println("Sending to...");
				printPacket(ackPacket);
				sendReceiveSocket.send(ackPacket);
				lastPacket = ackPacket;

			}
			outStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void write(String fileName, String mode) {
		byte[] msg = writeMsgGenerate(fileName, mode);

		try {
			sendReceiveSocket.setSoTimeout(timeout);
			DatagramPacket sendPacket = new DatagramPacket(msg, msg.length,
					InetAddress.getLocalHost(), sendPort);
			System.out.println("Sending to...");
			printPacket(sendPacket);
			sendReceiveSocket.send(sendPacket);

			msg = new byte[4];
			DatagramPacket receivePacket = new DatagramPacket(msg, msg.length);
			
			while(true){
				try
				{
					sendReceiveSocket.receive(receivePacket);
					break;
				}catch(SocketTimeoutException  exception)
				{
					exception.printStackTrace();
					sendReceiveSocket.send(sendPacket);
				}
			}
			
			System.out.println("Received from...");
			printPacket(receivePacket);

			// check the valid of ack 0
			if (parseAck(0, receivePacket) == AckPack.INV)
				return;

			boolean fileEnd = false;
			for (int i = 1; !fileEnd; i++) {
				msg = new byte[516];
				msg[0] = 0;
				msg[1] = 3;
				msg[2] = (byte) ((i / 256) & 0xFF);
				msg[3] = (byte) ((i % 256) & 0xFF);

				int len = inStream.read(msg, 4, 512);
				if (len != 512)
					fileEnd = true;

				if (len == -1)
					len++;

				DatagramPacket dataPacket = new DatagramPacket(msg, len + 4,
						InetAddress.getLocalHost(), receivePacket.getPort());
				System.out.println("Sending to...");
				printPacket(dataPacket);
				sendReceiveSocket.send(dataPacket);

				msg = new byte[4];
				DatagramPacket ackPacket = new DatagramPacket(msg, msg.length);
				
				while(true){
					try
					{
						sendReceiveSocket.receive(ackPacket);
						break;
					}catch(SocketTimeoutException  exception)
					{
						if(fileEnd)
							break;
						exception.printStackTrace();
						sendReceiveSocket.send(dataPacket);
					}
				}
				
				System.out.println("Received from...");
				printPacket(ackPacket);

				if (parseAck(i, ackPacket) == AckPack.INV)
					return;
			}
			inStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private byte[] readMsgGenerate(String fileName, String mode) {
		byte[] msg = new byte[100];
		msg[0] = 0;
		msg[1] = 1;
		byte[] fn = fileName.getBytes();
		System.arraycopy(fn, 0, msg, 2, fn.length);
		msg[fn.length + 2] = 0;
		byte[] md = mode.getBytes();
		System.arraycopy(md, 0, msg, fn.length + 3, md.length);
		int len = fn.length + md.length + 4;
		msg[len - 1] = 0;

		byte[] extMsg = new byte[len];
		System.arraycopy(msg, 0, extMsg, 0, len);
		
		return extMsg;
	}

	private byte[] writeMsgGenerate(String fileName, String mode) {
		byte[] msg = new byte[100];
		msg[0] = 0;
		msg[1] = 2;
		byte[] fn = fileName.getBytes();
		System.arraycopy(fn, 0, msg, 2, fn.length);
		msg[fn.length + 2] = 0;
		byte[] md = mode.getBytes();
		System.arraycopy(md, 0, msg, fn.length + 3, md.length);
		int len = fn.length + md.length + 4;
		msg[len - 1] = 0;
		
		byte[] extMsg = new byte[len];
		System.arraycopy(msg, 0, extMsg, 0, len);

		return extMsg;
	}

	private void printPacket(DatagramPacket packet) {
		byte[] data = new byte[packet.getLength()];
		System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());
		System.out.println();
		System.out.println("Address: " + packet.getAddress());
		System.out.println("Port: " + packet.getPort());
		System.out.println("Length: " + packet.getLength());
		System.out.println("Bytes: " + Arrays.toString(data));
		System.out.println("String: " + new String(data));
	}

	/**
	 * Calls parse packet for a ack packet (byte 2 is 4)
	 * 
	 * @param number
	 *            the packet block #
	 * @param packet
	 *            the packet from which data is extracted
	 * @return INV enum if packet is invalid, VAL if packet is valid
	 */
	private AckPack parseAck(int number, DatagramPacket packet) {
		return parsePacket(4, number, packet);
	}

	/**
	 * Calls parse packet for the data packet (byte 2 is 3)
	 * 
	 * @param number
	 *            the packet block #
	 * @param packet
	 *            the packet from which data is extracted
	 * @return INV enum if packet is invalid, VAL if packet is valid
	 */
	private AckPack parseData(int number, DatagramPacket packet) {
		return parsePacket(3, number, packet);

	}

	/**
	 * Checks packet for valid type, number and format of first 4 bytes.
	 * 
	 * @param type
	 *            3 for data packet, 4 for ack packet
	 * @param number
	 *            checks that the data/ack block # matches
	 * @param packet
	 *            the packet from which data is extracted
	 * @return the INV enum if packet is invalid, VAL if packet is valid
	 */
	private AckPack parsePacket(int type, int number, DatagramPacket packet) {
		byte[] msg = new byte[packet.getLength()];
		System.arraycopy(packet.getData(), 0, msg, 0, msg.length);

		if (msg[0] != 0)
			return AckPack.INV;
		if (msg[1] != (byte) type)
			return AckPack.INV;
		if ((msg[2] & 0xFF) != ((number / 256) & 0xFF))
			return AckPack.INV;
		if ((msg[3] & 0xFF) != ((number % 256) & 0xFF))
			return AckPack.INV;
		return AckPack.VAL;
	}

	public static void main(String args[]) {
		TFTPClient c = new TFTPClient();
		c.sendAndReceive();
	}
}
