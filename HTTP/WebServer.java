/**
 * Web Server
 * 
 * @author Assaf Elovic 200760262 & David Saidon 200341105
 */

import java.io.*;
import java.net.*;
import java.util.*;

public final class WebServer {
	private static int port;
	private static int maxThreads;
	private static final String configFilePath = "HTTP\\config.ini";
	protected static Thread[] threads;
	public static int clients = 0;
	public static String root;
	public static String defaultPage;

	public static void main(String argv[]) throws Exception {
		// Initiate a hash map containing the config.ini variables and values.
		Map<String, String> configtable = ExtractConfig(configFilePath);

		// Holding the variables in a list for later efficiency
		String[] configVars = { "port", "root", "defaultPage", "maxThreads" };

		// Extracting variable's values.
		port = Integer.parseInt(configtable.get(configVars[0]));
		root = configtable.get(configVars[1]);
		defaultPage = configtable.get(configVars[2]);
		maxThreads = Integer.parseInt(configtable.get(configVars[3]));

		threads = new Thread[maxThreads];
		ServerSocket socket = null;

		try {
			// Establish the listen socket.
			socket = new ServerSocket(port);
			System.out.println("Listening to port: " + port);
			System.out.println("Open a browesr in the address: localhost:" + port);
		}
		catch (IOException e) {
			System.out.println(e);
		}

		// Process HTTP service requests in an infinite loop.
		while (true) {
			try {
				// Listen for a TCP connection request.
				Socket connection = socket.accept();
				while (clients == maxThreads) {
				}

				for (int i = 0; i < maxThreads; i++) {
					if (threads[i] == null) {
						// Construct an object to process the HTTP request
						// message.
						HttpRequest request = new HttpRequest(connection, i);

						// Create a new thread to process the request and start
						// the thread.
						(threads[i] = new Thread(request)).start();
						clients++;
						break;
					}
				}

			}
			catch (IOException e) {
				System.out.println(e);
				socket.close();
			}
		}
	}

	/**
	 * Parses parameters from config.ini file and adds them to given hash-map.
	 * 
	 * @param fileName
	 * @return A hash-map containing config.ini values and variables.
	 */
	private static Map<String, String> ExtractConfig(String fileName) {
		Map<String, String> hashmap = new HashMap<String, String>();
		BufferedReader br = null;

		try {
			br = new BufferedReader(new FileReader(fileName));
			String fileLine;
			while ((fileLine = br.readLine()) != null) {
				String var = fileLine.split("=")[0];
				String val = fileLine.split("=")[1].trim();
				hashmap.put(var, val);
			}
		}
		catch (FileNotFoundException e) {
			System.err.println(e);
		}
		catch (IOException e) {
			System.err.println(e);
		}

		try {
			br.close();
		}
		catch (IOException e) {
			System.out.println(e);
		}

		return hashmap;
	}
}