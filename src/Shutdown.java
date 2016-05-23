import java.util.Scanner;

public class Shutdown extends Thread {
	
	public static enum Request {QUIT, VERBOSE};
	Scanner scan;
	TFTPServer server;

	public Shutdown(TFTPServer server) {
		scan = new Scanner(System.in);
		this.server = server;
	}
	
	public void run() {
		while (true) {
			String s = scan.next().toLowerCase();
			if (s.equals("quit")) {
				server.getReceiveSocket().close();
				server.setShutdown();
				System.out.println("Server Close");
				System.exit(1);
			}
			else if (s.equals("verbose")) {
				
				break;
			}
			else{
				System.out.println("Invalid Request");
			}
		}
	}
}
