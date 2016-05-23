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
	private InetAddress sendIP;
	private int receivePort;
	private InetAddress receiveHost;
	private String errorMsg;

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

	private static final int TIMEOUT = 2000;
	private static final int READ = 1;
	private static final int WRITE = 2;
	private static final int DATA = 3;
	private static final int ACK = 4;
	private static final int ERROR = 5;

	private static final int VALID = 0, ERROR4 = 4, ERROR5 = 5, ERROR_DUP = 6;

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

		try {
			sendIP = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
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
		try {
			DatagramPacket sendPacket = new DatagramPacket(msg, msg.length,
					sendIP, sendPort);
			System.out.println("Sending to...");
			printPacket(sendPacket);
			sendReceiveSocket.setSoTimeout(TIMEOUT);

			int timeouts = 0;

			do {
				try {
					sendReceiveSocket.send(sendPacket);
					break;
				} catch (SocketTimeoutException e) {
					System.out.println("Read request timed out " + TIMEOUT
							+ "ms.");
					timeouts++;
				}
			} while (timeouts < 5);

			if (timeouts >= 5) {
				System.out
						.println("Client reached 5 timeout, transfer aborted");
				return;
			}

			// loop until data received has length less than 516 bytes
			boolean fileEnd = false;
			for (int i = 1; !fileEnd; i++) {

				// receive data packet for block i
				msg = new byte[516];
				DatagramPacket dataPacket = new DatagramPacket(msg, msg.length);
				sendReceiveSocket.setSoTimeout(TIMEOUT);

				try {
					sendReceiveSocket.receive(dataPacket);

				} catch (SocketTimeoutException ex) {
					System.out.println("Client timed out " + TIMEOUT
							+ "ms while waiting for data. Transfer aborted");
				}

				System.out.println("Received from...");
				printPacket(dataPacket);

				receivePort = dataPacket.getPort();
				receiveHost = dataPacket.getAddress();
				int result = verifyPacket(dataPacket, i, DATA, receivePort,
						receiveHost);

				if (result == ERROR4) {
					handleError4(receivePort, receiveHost);
					outStream.close();
					new File(fileName).delete();
					return;
				}

				if (result == ERROR5) {
					handleError5(dataPacket.getPort(), dataPacket.getAddress());
					i--;
					continue;
				}

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

			}
			outStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void handleError4(int port, InetAddress host) {
		System.out.println("Error Code 4");
		sendErrorPacket(port, host, ERROR4, "Illegal TFTP operation: "
				+ errorMsg);
		System.out.println("Transfer Aborted.");

	}

	private void handleError5(int port, InetAddress host) {
		System.out.println("Error Code 5");
		sendErrorPacket(port, host, ERROR5, "Unknown transfer ID.");
	}

	private void sendErrorPacket(int port, InetAddress host, int error,
			String msg) {
		try {
			byte[] errorData = generateErrorData(error, msg);
			DatagramPacket errorPacket = new DatagramPacket(errorData,
					errorData.length, host, port);
			System.out.println("Sending to...");
			printPacket(errorPacket);
			sendReceiveSocket.send(errorPacket);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private byte[] generateErrorData(int error, String message) {
		byte[] data = new byte[message.length() + 5];
		data[0] = 0;
		data[1] = 5;
		data[2] = (byte) ((error / 256) & 0xFF);
		data[3] = (byte) ((error % 256) & 0xFF);
		System.arraycopy(message.getBytes(), 0, data, 4, message.length());
		data[data.length - 1] = 0;
		return data;
	}

	private int verifyPacket(DatagramPacket received, int block, int type,
			int port, InetAddress host) {

		if ((received.getPort() != port)
				|| (!received.getAddress().equals(host))) {
			return ERROR5;
		}

		if (type == ACK && received.getLength() != 4) {
			errorMsg = "Invalid ACK packet";
			return ERROR4;
		}

		if (type == DATA
				&& ((received.getLength() < 4) || (received.getLength() > 516))) {
			errorMsg = "Invalid DATA packet";
			return ERROR4;
		}

		return parsePacket(type, block, received);
	}

	private void write(String fileName, String mode) {
		byte[] msg = writeMsgGenerate(fileName, mode);

		try {
			DatagramPacket sendPacket = new DatagramPacket(msg, msg.length,
					InetAddress.getLocalHost(), sendPort);
			System.out.println("Sending to...");
			printPacket(sendPacket);
			sendReceiveSocket.setSoTimeout(TIMEOUT);
			int timeouts = 0;

			do {
				try {
					sendReceiveSocket.send(sendPacket);
					break;
				} catch (SocketTimeoutException e) {
					System.out.println("Write request timed out " + TIMEOUT
							+ "ms.");
					timeouts++;
				}
			} while (timeouts < 5);

			if (timeouts >= 5) {
				System.out
						.println("Client reached 5 timeout, transfer aborted");
				return;
			}

			msg = new byte[516];
			DatagramPacket receivePacket = new DatagramPacket(msg, msg.length);
			sendReceiveSocket.setSoTimeout(TIMEOUT);
			timeouts = 0;
			do {
				try {
					sendReceiveSocket.receive(receivePacket);
					break;
				} catch (SocketTimeoutException e) {
					System.out.println("Client timed out " + TIMEOUT + "ms.");
					timeouts++;
				}
			} while (timeouts < 5);
			if (timeouts >= 5) {
				System.out.println("Client timed out " + TIMEOUT
						+ "ms while waiting for data. Transfer aborted");
				return;
			}
			System.out.println("Received from...");
			printPacket(receivePacket);

			receivePort = receivePacket.getPort();
			receiveHost = receivePacket.getAddress();

			if (VALID != handleErrors(receivePacket, 0, ACK, receivePort,
					receiveHost))
				return;

			boolean fileEnd = false;
			for (int i = 1; !fileEnd; i++) {
				msg = new byte[516];
				msg[0] = 0;
				msg[1] = 3;
				msg[2] = (byte) ((i / 256) & 0xFF);
				msg[3] = (byte) ((i % 256) & 0xFF);

				int len;
				if ((len = inStream.read(msg, 4, 512)) != 512)
					fileEnd = true;

				if (len == -1)
					len++;

				DatagramPacket dataPacket = new DatagramPacket(msg, len + 4,
						InetAddress.getLocalHost(), receivePacket.getPort());

				sendReceiveSocket.setSoTimeout(TIMEOUT);
				timeouts = 0;

				do {
					System.out.println("Sending to...");
					printPacket(dataPacket);
					sendReceiveSocket.send(dataPacket);
					try {
						int result;
						do {
							msg = new byte[516];
							DatagramPacket ackPacket = new DatagramPacket(msg,
									msg.length);
							sendReceiveSocket.receive(ackPacket);
							System.out.println("Received from...");
							printPacket(ackPacket);

							result = handleErrors(ackPacket, i, ACK,
									receivePort, receiveHost);
							if (result == ERROR4)
								return;
						} while (result != VALID);
						break;
					} catch (SocketTimeoutException e) {
						System.out.println("Client timed out " + TIMEOUT + "ms.");
						timeouts++;
					}
				} while (timeouts < 5);

				if (timeouts >= 5) {
					System.out.println("Client reached 5 timeout, transfer aborted");
					return;
				}
			}
			inStream.close();
		} catch (Exception e) {

		}
	}

	private int handleErrors(DatagramPacket packet, int block, int type,
			int port, InetAddress ip) {
		int result = verifyPacket(packet, block, type, port, ip);
		if (result == ERROR4)
			handleError4(receivePort, receiveHost);
		else if (result == ERROR5)
			handleError5(packet.getPort(), packet.getAddress());
		else if (result == ERROR_DUP)
			System.out.println("Duplicate packet received");
		return result;
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
	private int parsePacket(int type, int number, DatagramPacket packet) {
		byte[] msg = new byte[packet.getLength()];
		System.arraycopy(packet.getData(), 0, msg, 0, msg.length);

		if (msg[0] != 0) {
			errorMsg = "Invalid opcode";
			return ERROR4;
		}

		if (msg[1] != (byte) type) {
			errorMsg = "Invalid packet type";
			return ERROR4;
		}
		if ((msg[2] & 0xFF) < ((number / 256) & 0xFF)) {
			return ERROR_DUP;
		} else if ((msg[2] & 0xFF) > ((number / 256) & 0xFF)) {
			errorMsg = "Invalid block number";
			return ERROR4;
		}

		if ((msg[3] & 0xFF) < ((number % 256) & 0xFF)) {
			return ERROR_DUP;
		} else if ((msg[3] & 0xFF) > ((number % 256) & 0xFF)) {
			errorMsg = "Invalid block number";
			return ERROR4;
		}

		return VALID;
	}

	public static void main(String args[]) {
		TFTPClient c = new TFTPClient();
		c.sendAndReceive();
	}
}
