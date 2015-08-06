/**
 * Client
 * 
 * @author David Saidon 200341105 & Assaf Elovic 200760262
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URLDecoder;

public class Client {
	private byte[] bs;
	public volatile long startTime;
	protected Socket socket;
	protected BufferedReader in;
	protected BufferedInputStream inp;
	protected BufferedOutputStream out;

	public Client(Socket s) {
		socket = s;
		try {
			out = new BufferedOutputStream(socket.getOutputStream());
			inp = new BufferedInputStream(socket.getInputStream());
			in = new BufferedReader(new InputStreamReader(inp));
		}
		catch (Exception e) {
			socket = null;
		}
	}

	public void run() throws Exception {
		startTime = System.currentTimeMillis();
		if (socket == null)
			return;
		try {
			while (true) {
				String s = in.readLine();
				if (s == null)
					return;
				System.out.println(s);
				final String fullRequest = s;
				String REQUEST = s.split(" ")[0];
				if (!REQUEST.equals("GET") && !REQUEST.equals("POST")) {
					System.out.println("Connection closed: unsupported request (" + REQUEST + ")");
					out.close();
					in.close();
					socket.close();
					return;
				}
				String addr = s.split(" ")[1];
				String[] pm = null;
				pm = new String[] { addr.split("/")[2], null, null };
				{
					String S = addr.substring(addr.indexOf(pm[0]));
					if (S.equals("content-proxy/logs")) {
						logs();
						System.out.println("Connection closed");
						return;
					}
					else if (S.equals("content-proxy/policies")) {
						policies();
						System.out.println("Connection closed");
						return;
					}
					else if (S.equals("content-proxy/policies/change")) {
						int contentLength = -1;
						while (!(s = in.readLine()).equals("")) {
							if (s.length() > 15
									&& s.substring(0, 15).equalsIgnoreCase("Content-Length:"))
								contentLength = Integer.valueOf(s.substring(15).trim());
						}
						if (contentLength > 0) {
							char[] cs = new char[contentLength];
							in.read(cs);
							s = URLDecoder.decode(String.valueOf(cs).substring(5), "Cp1251");
							String p = proxyServer.getPolicy();
							proxyServer.setPolicy(s);
							String resp = proxyServer.readPolicy();
							if (resp == null)
								resp = "<h1>Policy edited</h1>";
							else
								proxyServer.setPolicy(p);
							out.write(("HTTP/1.1 200 OK\n\n" + resp).getBytes());
							out.flush();
							System.out.println("Connection closed");
							out.close();
							in.close();
							socket.close();
						}
						return;
					}
				}
				{
					String res = URLDecoder.decode(
							addr.contains("?") ? addr.split("\\?")[0] : addr, "UTF-8");
					for (String S : proxyServer.Block.site) {
						if (res.contains(S)) {
							proxyServer.Block.log(fullRequest, "block-site \"" + S + "\"");
							block("block-site \"" + S + "\"");
							return;
						}
					}
					String[] rss = res.split("/");
					if (rss.length > 2) {
						res = res.endsWith("/") ? rss[rss.length - 2] : rss[rss.length - 1];
						for (String S : proxyServer.Block.resource) {
							if (res.contains(S)) {
								proxyServer.Block.log(fullRequest, "block-resource \"" + S + "\"");
								block("block-resource \"" + S + "\"");
								return;
							}
						}
					}
				}
				pm[2] = addr.substring(addr.indexOf(pm[0]) + pm[0].length() + 1);
				s = s.replace(addr, "/" + pm[2]);
				pm[1] = pm[0].contains(":") ? pm[0].split(":")[1] : "80";
				if (pm[0].contains(":"))
					pm[0] = pm[0].split(":")[0];
				Socket uc = new Socket(pm[0], Integer.valueOf(pm[1]));
				{
					String[] SS = uc.getInetAddress().getHostAddress().split("\\.");
					int ip = Integer.valueOf(SS[0]) << 24 | Integer.valueOf(SS[1]) << 16
							| Integer.valueOf(SS[2]) << 8 | Integer.valueOf(SS[3]);
					for (proxyServer.Block.Ip i : proxyServer.Block.ip) {
						if (ip >>> i.mask == i.ip >>> i.mask) {
							proxyServer.Block.log(fullRequest, i.rule);
							block(i.rule);
							return;
						}
					}
				}
				int contentLength = -1;
				try (final BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
						uc.getOutputStream()));
						final BufferedInputStream r = new BufferedInputStream(uc.getInputStream())) {
					w.write(s);
					while ((s = in.readLine()) != null && !s.equals("")) {
						w.write("\r\n");
						w.write(s);
						if (s.length() > 15
								&& s.substring(0, 15).equalsIgnoreCase("Content-Length:"))
							contentLength = Integer.valueOf(s.substring(15).trim());
					}
					w.write("\r\n\r\n");
					w.flush();
					if (contentLength > 0) {
						char[] cs = new char[contentLength];
						in.read(cs);
						w.write(cs);
						w.flush();
					}

					long t = System.currentTimeMillis();
					int rd = -1;
					while (true) {
						while ((rd = r.read(bs, 0, Math.min(r.available(), bs.length))) > 0) {
							out.write(bs, 0, rd);
							out.flush();
							startTime = System.currentTimeMillis();
						}
						if (System.currentTimeMillis() - t >= 1000)
							break;
						else
							Thread.sleep(1);
					}
				}
				System.out.println("Connection closed");
				if (socket.isClosed() || !in.ready())
					break;
			}
		}
		catch (Exception e) {
			System.err.println("Connection closed");
			e.printStackTrace();
		}
		out.close();
		in.close();
		socket.close();
	}

	private void block(String rule) throws Exception {
		String resp = "HTTP/1.1 403 FORBIDDEN\n\n<h1>403 Access denied</h1><br>" + rule;
		out.write(resp.getBytes());
		out.flush();
		out.close();
		in.close();
		socket.close();
	}

	private void logs() throws Exception {
		String resp = "HTTP/1.1 200 OK\n\n<html><head></head><body><pre>"
				+ proxyServer.Block.getLog() + "</pre></body></html>";
		out.write(resp.getBytes());
		out.flush();
		out.close();
		in.close();
		socket.close();
	}

	private void policies() throws Exception {
		String resp = "HTTP/1.1 200 OK\n\n<html><head></head><body><pre><form method=\"post\" action=\"http://content-proxy/policies/change\"><input type=\"submit\" value=\"SUBMIT\" style=\"width: 100%; height: 30px; color: red;\"><br><textarea style=\"width: 100%; height: 90%; resize: none;\" name=\"data\">"
				+ proxyServer.getPolicy() + "</textarea></form></pre></body></html>";
		out.write(resp.getBytes());
		out.flush();
		out.close();
		in.close();
		socket.close();
	}

	public static class T extends Thread {
		private byte[] bs = new byte[1024];
		public Client client;
		public volatile boolean running = true;

		public void run() {
			while (!interrupted()) {
				running = true;
				client.bs = bs;
				try {
					client.run();
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				running = false;
				synchronized (this) {
					try {
						wait();
					}
					catch (InterruptedException e) {
						break;
					}
				}
			}
		}
	}
}
