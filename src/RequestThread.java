import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class RequestThread extends Thread {

	private DatagramPacket receivePacket;
	private DatagramSocket sendReceiveSocket;
	private byte[] blockCounter;
	private String fileName, mode;
	
	private static final int TIMEOUT = 2000;
	
	private static final int VALID = 0;
	private static final int ERROR4 = 4;
	private static final int ERROR5 = 5;

	public RequestThread(DatagramPacket receivePacket) {
		this.receivePacket = receivePacket;

		blockCounter = new byte[2];
		resetBlockCounter();
		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private void resetBlockCounter() {
		blockCounter[0] = 0;
		blockCounter[1] = 0;
	}

	private void completeRead() {
		Path filePath = Paths.get(fileName);
		byte[] fileData = null;

		try {
			fileData = Files.readAllBytes(filePath);

		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		incrementBlockCounter();
		
		while (true) {
			int length = 4;
			if (fileData.length / 512 < 1) {
				length += fileData.length;
			} else {
				length += 512;
			}
			byte[] response = new byte[length];
			response[0] = 0;
			response[1] = 3;
			response[2] = blockCounter[0];
			response[3] = blockCounter[1];

			System.arraycopy(fileData, 0, response, 4, length - 4);

			DatagramPacket sendPacket = new DatagramPacket(response,
					response.length, receivePacket.getAddress(),
					receivePacket.getPort());
			int block = (blockCounter[0] & 0xFF) * 256
					+ (blockCounter[1] & 0xFF);
			System.out.println("Sending DATA for block: " + block);
			
			try {
				sendReceiveSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}

			int len = fileData.length - length + 4;
			byte[] msg = new byte[len];
			System.arraycopy(fileData, length - 4, msg, 0, len);
			fileData = msg;

			incrementBlockCounter();

			receivePacket = new DatagramPacket(new byte[5], 5);
			while(true)
			{
				try {
					sendReceiveSocket.receive(receivePacket);
					break;
				}
				catch (IOException e) {
					try{
						if (length < 516) {//last ack packet lost doesn't matter
							break;
						}
						sendReceiveSocket.send(sendPacket);
					}catch(Exception ex)
					{
						ex.printStackTrace(); 
						System.exit(1);
					}
				}
			}
			System.out.println("Received ACK for block: " + block);

			if (length < 516) {
				break;
			}
		}
		System.out.println("Read Request Finished");
		resetBlockCounter();
	}

	private void incrementBlockCounter() {
		if ((blockCounter[1] & 0xFF) == 255) {
			blockCounter[0]++;
			blockCounter[1] = 0;
		} else {
			blockCounter[1]++;
		}

	}

	private void completeWrite() {
		byte[] response = TFTPServer.writeResp;
		DatagramPacket sendPacket = new DatagramPacket(response,
				response.length, receivePacket.getAddress(),
				receivePacket.getPort());
		System.out.println("Sending ACK for block: 0");
		try {
			sendReceiveSocket.send(sendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		byte[] fileData = new byte[516];
		while (true) {
			byte[] data = new byte[516];
			receivePacket = new DatagramPacket(data, data.length);
				try {
					sendReceiveSocket.receive(receivePacket);
				} catch (IOException e) {
					e.printStackTrace();
				}


			int block = (data[2] & 0xFF) * 256 + (data[3] & 0xFF);
			System.out.println("Received DATA for block: " + block);

			fileData = concatArray(fileData, Arrays.copyOfRange(
					receivePacket.getData(), 4, receivePacket.getLength()));

			// Send acknowledge
			response[0] = 0;
			response[1] = 4;
			response[2] = data[2];
			response[3] = data[3];
			sendPacket = new DatagramPacket(response, response.length,
					receivePacket.getAddress(), receivePacket.getPort());
			System.out.println("Sending ACK for block: " + block);
			try {
				sendReceiveSocket.send(sendPacket);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			if (receivePacket.getLength() < 516) {
				break;
			}
		}

		try {
			FileOutputStream out = new FileOutputStream(fileName);
			out.write(fileData);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		System.out.println("Completed Write Request");

	}

	private static byte[] concatArray(byte[] a, byte[] b) {
		int aLen = a.length;
		int bLen = b.length;
		byte[] c = new byte[aLen + bLen];
		System.arraycopy(a, 0, c, 0, aLen);
		System.arraycopy(b, 0, c, aLen, bLen);
		return c;
	}

	public void run() {
		byte[] data = receivePacket.getData();

		TFTPServer.Request req;

		int len, j = 0, k = 0;

		len = receivePacket.getLength();
		// If it's a read, send back DATA (03) block 1
		// If it's a write, send back ACK (04) block 0
		// Otherwise, ignore it
		if (data[0] != 0)
			req = TFTPServer.Request.ERROR; 
		else if (data[1] == 1)
			req = TFTPServer.Request.READ; 
		else if (data[1] == 2)
			req = TFTPServer.Request.WRITE; 
		else
			req = TFTPServer.Request.ERROR; 

		if (req != TFTPServer.Request.ERROR) { 
			// search for next all 0 byte
			for (j = 2; j < len; j++) {
				if (data[j] == 0)
					break;
			}
			if (j == len)
				req = TFTPServer.Request.ERROR;
			fileName = new String(data, 2, j - 2);
		}

		if (req != TFTPServer.Request.ERROR) {
			// search for next all 0 byte
			for (k = j + 1; k < len; k++) {
				if (data[k] == 0)
					break;
			}
			if (k == len)
				req = TFTPServer.Request.ERROR;
			mode = new String(data, j, k - j - 1);
		}

		if (k != len - 1) {
			req = TFTPServer.Request.ERROR; 
		}
		if (req == TFTPServer.Request.READ) {
			System.out.println("Read Request Received");
			completeRead();
		} else if (req == TFTPServer.Request.WRITE) {
			System.out.println("\nWrite Request Received");
			completeWrite();
		} else {
			try {
				throw new Exception("Not yet implemented");
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		sendReceiveSocket.close();
	}

}
