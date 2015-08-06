/**
 * Proxy Server
 * 
 * @author David Saidon 200341105 & Assaf Elovic 200760262
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import javax.swing.JOptionPane;

public class proxyServer {
	private static final Date currentTime = new Date();
	private static SimpleDateFormat sdf = null;

	public static File config = null, policy = null;
	public static ServerSocket server;
	public static volatile ArrayList<Client.T> clients, cache;

	public static BufferedWriter logFile = null;
	public static ArrayList<Block.String2> log = new ArrayList<Block.String2>();
	private static int logMaxString = 0;
	public static int maxThreads = -1;
	public static int port = -1;

	public static class Block {
		public static String[] site, resource;
		public static Ip[] ip;

		public static class Ip {
			public int ip;
			public byte mask;
			public String rule;
		}

		public static class String2 {
			public String s1, s2;

			public String2(String s1, String s2) {
				this.s1 = s1;
				this.s2 = s2;
			}
		}

		public static synchronized void log(String req, String rule) throws IOException {
			currentTime.setTime(System.currentTimeMillis());
			StringBuilder s = new StringBuilder();
			s.append(sdf.format(currentTime));
			s.append("	|	");
			s.append(req);
			logMaxString = Math.max(logMaxString, s.length());
			System.err.println("Block	" + s.toString() + "   |   " + rule);
			synchronized (log) {
				log.add(new String2(s.toString(), "   |   " + rule));
			}
			logFile.write("\n");
			logFile.write(s.toString() + "   |   " + rule);
			logFile.flush();
		}

		public static synchronized String getLog() {
			StringBuilder b = new StringBuilder();
			for (String2 s : log) {
				b.append(s.s1);
				int l = logMaxString - s.s1.length();
				for (int i = 0; i < l; i++)
					b.append(' ');
				b.append(s.s2);
				b.append("<br>");
			}
			for (int i = 0; i < logMaxString + 4; i++)
				b.append('-');
			b.append("<br>");
			b.append("Server stats:");
			b.append("<br>");
			b.append("Working threads: ");
			b.append(clients.size());
			b.append("<br>");
			b.append("Cached threads: ");
			b.append(cache.size());
			return b.toString();
		}
	}

	public static void main(String[] args) {
		if (args.length != 1) {
			JOptionPane.showMessageDialog(null,
					"You should run server with command\njava proxyServer [policy file]");
			return;
		}
		{
			File f = new File("HTTP/run.config");
			if (f.exists()) {
				ArrayList<String> cms = new ArrayList<String>();
				try (BufferedReader r = new BufferedReader(new InputStreamReader(
						new FileInputStream(f)))) {
					String s = null;
					while ((s = r.readLine()) != null)
						cms.add(s);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				if (cms.size() > 0) {
					String run = cms.get(cms.size() - 1);
					for (String s : cms) {
						if (!s.equals(run))
							try {
								Runtime.getRuntime().exec(s);
							}
							catch (IOException e) {
								e.printStackTrace();
							}
					}
					Process p = null;
					try {
						p = Runtime.getRuntime().exec(run);
					}
					catch (IOException e) {
						e.printStackTrace();
					}
					if (p != null) {
						final Process P = p;
						Thread t = new Thread() {
							public void run() {
								while (true) {
									try {
										if (P.getInputStream().available() > 0) {
											while (P.getInputStream().available() > 0)
												System.out.write(P.getInputStream().read());
										}
										else if (P.getErrorStream().available() > 0) {
											while (P.getErrorStream().available() > 0)
												System.out.write(P.getErrorStream().read());
										}
										else
											Thread.sleep(5);
									}
									catch (Exception e) {
										e.printStackTrace();
									}
								}
							}
						};
						t.setDaemon(true);
						t.start();
						t = new Thread() {
							public void run() {
								try (BufferedReader r = new BufferedReader(new InputStreamReader(
										System.in));
										BufferedWriter w = new BufferedWriter(
												new OutputStreamWriter(P.getOutputStream()))) {
									while (true) {
										String s = r.readLine();
										w.write(s);
										w.newLine();
										w.flush();
									}
								}
								catch (Exception e) {
									e.printStackTrace();
								}
							}
						};
						t.setDaemon(true);
						t.start();
					}
				}
			}
		}
		config = new File("config.ini");
		policy = new File(args[0]);
		{
			int port = -1;
			int maxThreads = -1;
			String logPath = null;
			try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(
					config)))) {
				String s = null;
				while ((s = r.readLine()) != null) {
					if (s.startsWith("port"))
						port = Integer.valueOf(s.split("=")[1]);
					else if (s.startsWith("maxThreads"))
						maxThreads = Integer.valueOf(s.split("=")[1]);
					else if (s.startsWith("logPath"))
						logPath = s.split("=")[1].split("\"")[1];
				}
			}
			catch (Exception e) {
				port = -1;
				maxThreads = -1;
				logPath = null;
			}
			if (port == -1 || maxThreads == -1 || logPath == null) {
				JOptionPane
						.showMessageDialog(
								null,
								"The "
										+ config.getName()
										+ " file must be filled minimum with 3 lines:\nport=<port>\nmaxThreads=<0IsInfinity>\nlogPath=\"<path>\"");
				return;
			}
			try {
				File f = new File(logPath);
				if (!f.exists())
					f.createNewFile();
				proxyServer.logFile = new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream(f)));
			}
			catch (Exception e) {
				JOptionPane.showMessageDialog(null, error(e));
				return;
			}
			proxyServer.maxThreads = maxThreads;
			proxyServer.port = port;
		}
		{
			String s;
			if ((s = readPolicy()) != null) {
				JOptionPane.showMessageDialog(null, s);
				return;
			}
		}
		System.setProperty("java.net.preferIPv4Stack", "true");
		try {
			sdf = new SimpleDateFormat("HH:mm:ss");
			clients = new ArrayList<Client.T>();
			cache = new ArrayList<Client.T>();
			try {
				server = new ServerSocket(port);
			}
			catch (Exception e) {
				JOptionPane.showMessageDialog(null, error(e));
				System.exit(0);
			}
			final Thread MainThread = Thread.currentThread();
			new Thread() {
				public void run() {
					while (MainThread.isAlive()) {
						try {
							Thread.sleep(10000);
						}
						catch (Exception e) {
							break;
						}
						synchronized (clients) {
							for (int i = 0; i < clients.size(); i++) {
								Client.T c = clients.get(i);
								if (!c.isAlive()
										|| System.currentTimeMillis() > c.client.startTime + 60000) {
									clients.remove(i);
									i--;
									if (c.isAlive()) {
										try {
											c.client.socket.close();
											c.client.in.close();
											c.client.out.close();
										}
										catch (IOException e) {
										}
									}
									System.err.println("Thread interrupted and removed from cache");
								}
								else if (!c.running) {
									clients.remove(i);
									i--;
									synchronized (cache) {
										cache.add(c);
									}
								}
							}
						}
					}
				}
			}.start();
			System.out.println("Server started at " + port + " port");
			System.out.println("Max threads count: " + (maxThreads == 0 ? "infinity" : maxThreads));
			while (true) {
				try {
					Socket s = server.accept();
					if (s != null) {
						Client.T t = null;
						boolean n = true;
						if (cache.size() == 0) {
							if (clients.size() >= maxThreads && maxThreads > 0) {
								s.close();
								continue;
							}
							t = new Client.T();
						}
						else {
							synchronized (cache) {
								t = cache.get(0);
								cache.remove(0);
							}
							n = false;
						}
						Client c = new Client(s);
						t.client = c;
						if (n)
							t.start();
						else {
							synchronized (t) {
								t.notify();
							}
						}
						synchronized (clients) {
							clients.add(t);
						}
					}
				}
				catch (IOException e) {
					break;
				}
			}
			final Thread mn = Thread.currentThread();
			new Thread() {
				public void run() {
					try {
						for (int i = 0; mn.isAlive() && i < 100; i++) {
							Thread.sleep(20);
						}
						if (mn.isAlive())
							System.exit(0);
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start();
			if (!server.isClosed())
				server.close();
			for (Client.T t : clients)
				t.interrupt();
			for (Client.T t : cache)
				t.interrupt();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String error(Exception e) {
		StringBuilder s = new StringBuilder();
		try {
			StackTraceElement[] trss = e.getStackTrace();
			s.append(e.toString());
			for (int i = 0; i < trss.length; i++)
				s.append("\r\n	at " + trss[i].toString());
		}
		catch (Exception e1) {
		}
		return s.toString();
	}

	public static String readPolicy() {
		@SuppressWarnings("unchecked")
		ArrayList<String>[] data = new ArrayList[2];
		for (int i = 0; i < data.length; i++)
			data[i] = new ArrayList<String>();
		ArrayList<Block.Ip> ips = new ArrayList<Block.Ip>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				new FileInputStream(policy)))) {
			String s = null;
			while ((s = r.readLine()) != null) {
				if (s.startsWith("block-site"))
					data[0].add(s.split(" ")[1].split("\"")[1]);
				else if (s.startsWith("block-resource"))
					data[1].add(s.split(" ")[1].split("\"")[1]);
				else if (s.startsWith("block-ip-mask")) {
					Block.Ip i = new Block.Ip();
					i.rule = s;
					String[] ss = s.split(" ")[1].split("\"")[1].split("/");
					String mask = ss[1];
					ss = ss[0].split("\\.");
					i.mask = Byte.valueOf(mask);
					i.ip = Integer.valueOf(ss[0]) << 24 | Integer.valueOf(ss[1]) << 16
							| Integer.valueOf(ss[2]) << 8 | Integer.valueOf(ss[3]);
					ips.add(i);
				}
			}
		}
		catch (Exception e) {
			return error(e);
		}
		Block.site = new String[data[0].size()];
		Block.resource = new String[data[1].size()];
		Block.ip = new Block.Ip[ips.size()];
		data[0].toArray(Block.site);
		data[1].toArray(Block.resource);
		ips.toArray(Block.ip);
		return null;
	}

	public static String getPolicy() {
		StringBuilder b = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				new FileInputStream(policy)))) {
			String s = null;
			while ((s = r.readLine()) != null) {
				b.append(s);
				b.append('\n');
			}
		}
		catch (Exception e) {
		}
		if (b.length() >= 1)
			b.setLength(b.length() - 1);
		return b.toString();
	}

	public static void setPolicy(String s) {
		try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
				policy)))) {
			w.write(s);
			w.flush();
		}
		catch (Exception e) {
		}
	}
}