/**
 * HTTP Request
 * 
 * @author Assaf Elovic 200760262 & David Saidon 200341105
 */

import java.io.*;
import java.net.*;
import java.util.*;

final class HttpRequest implements Runnable {
	private static final String CRLF = "\r\n";
	private static int contentLength;
	private static String headerLines;
	private static String httpResponse = "HTTP/1.1 ";
	private static String bodyLine;
	private int threadNumber;
	private boolean isChunked;
	private HashMap<String, String> params;
	protected Socket socket;

	/**
	 * Constructor
	 * 
	 * @param socket
	 * @param index
	 * @throws Exception
	 */
	public HttpRequest(Socket socket, int index) throws Exception {
		this.socket = socket;
		this.threadNumber = index;
		this.params = new HashMap<String, String>();
		this.isChunked = false;
	}

	/**
	 * Implement the run() method of the Runnable interface.
	 */
	@Override
	public void run() {
		try {
			processRequest();
		}
		catch (Exception e) {
			System.err.println("500 Internal Server Error");
			e.printStackTrace();
		}
	}

	/**
	 * Receives header from client and processes it.
	 * 
	 * @throws Exception
	 */
	private void processRequest() throws Exception {
		// Initiate output stream
		DataOutputStream os = new DataOutputStream(socket.getOutputStream());
		// Initiate buffer for input stream from clients
		BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

		// If socket input is null
		if (!isNotNull(br, os)) {
			endClient(os, br);
			return;
		}

		// Save request as string
		String requestString = br.readLine();
		if (requestString == null) {
			endClient(os, br);
			return;
		}

		// Print the input command
		System.out.println();
		System.out.println(requestString);

		// Extracting file names from input
		StringTokenizer tokens = new StringTokenizer(requestString);
		isNotNull(tokens, os);

		if (!tokens.hasMoreTokens()) {
			os.write(("HTTP/1.1 400 Bad Request" + CRLF).getBytes());
		}

		StringBuilder headerSection = new StringBuilder();

		// Get request from header sent by the client.
		String request = tokens.nextToken();

		if (!tokens.hasMoreTokens()) {
			os.write(("HTTP/1.1 400 Bad Request" + CRLF).getBytes());
		}

		// Printing the complete request.
		String headerLine = null;

		// Print header details
		while ((headerLine = br.readLine()).length() != 0) {
			headerSection.append(headerLine);
			headerSection.append("\n");
			if (headerLine.startsWith("Content-Length:")) {
				contentLength = Integer.parseInt(headerLine.split(":")[1].trim());
			}
			if (headerLine.startsWith("chunked: yes")
					|| headerLine.startsWith("Transfer-Encoding: chunked")) {
				isChunked = true;
			}
			System.out.println(headerLine);
		}

		// To check why this dosen't work!!
		headerLines = headerSection.toString();

		// Initialise body line
		// If method is POST
		if (contentLength > 0) {
			bodyLine = initBodyLine(br);
		}
		// If method is GET
		else {
			String[] str = requestString.split(" ");
			// split for the part before the parameters and after
			int paramsPtr = str[1].indexOf('?');
			if (paramsPtr >= 0) {
				bodyLine = str[1].substring(paramsPtr + 1, str[1].length());
			}
		}

		// Insert params into params hash map
		decodeParams(bodyLine, params);

		// Parsing fileName so will not include params.
		String fileName = tokens.nextToken();

		// Parsing fileName so will not include params.
		String[] div = fileName.split("\\?");
		fileName = div[0];

		// Check which request method to process
		if (request.equals("GET") || request.equals("POST")) {
			if (fileName.equals("/")) {
				fileName = WebServer.root + WebServer.defaultPage;
				outputResponse(200, fileName, true, headerSection.toString(), os);
			}
			else {
				if (fileName.equals("/params_info.html")) {
					createParamsInfoPage();
				}

				fileName = WebServer.root + fileName;

				if (new File(fileName).isFile()) {
					outputResponse(200, fileName, true, "", os);
				}
				else {
					String entityBody = "<HTML>" + "<BODY><H1> File/Page Not Found </H1></BODY>"
							+ "</HTML>";
					outputResponse(404, fileName, false, entityBody, os);
				}
			}
		}

		// Process OPTIONS method
		// The OPTIONS method is used by the client to find out what are the
		// HTTP
		// methods and other options supported by a web server.
		else if (request.equals("OPTIONS")) {
			String options = "HTTP/1.0 200 OK" + CRLF + "Allow: GET,POST,HEAD,OPTIONS,TRACE" + CRLF;
			os.writeBytes(options);
		}

		// Process HEAD method
		// The HEAD method is functionally like GET, except that the server
		// replies with a response line and headers, but no entity-body.
		else if (request.equals("HEAD")) {
			// reply headers
			while (true) {
				if (fileName.equals("/")) {
					fileName = WebServer.root + WebServer.defaultPage;
					outputResponse(200, fileName, true, "HEAD", os);
					break;
				}

				fileName = WebServer.root + fileName;
				if (new File(fileName).isFile()) {
					outputResponse(200, fileName, true, "HEAD", os);
					break;
				}
				else {
					outputResponse(404, fileName, false, "HEAD", os);
					break;
				}
			}
		}

		// Process Trace method
		// The TRACE method is used to echo the contents of an HTTP Request
		// back to the requester which can be used for debugging purpose at the
		// time of development.
		else if (request.equals("TRACE")) {
			headerLines = httpResponse + "200 OK" + CRLF + headerLines + CRLF;
			os.write(headerLines.getBytes());
		}

		// Process DELETE method
		/** BONUS **/
		else if (request.equals("DELETE")) {
			// Cannot allow deletion of the index.html file (Our own server
			// rule).
			if (fileName.equals("/") || fileName.equals("/index.html")) {
				String response = "HTTP/1.1 204 No Content";
				os.write(response.getBytes());
				System.out.println(CRLF + response);
			}
			else {
				fileName = WebServer.root + fileName;
				deleteMethod(fileName, os);
			}
		}

		// Process PUT method
		/** BONUS **/
		else if (request.equals("PUT")) {
			// Cannot allow modification of the index.html file (Our own server
			// rule).
			if (fileName.equals("/") || fileName.equals("/index.html")) {
				String response = "HTTP/1.1 204 No Content";
				os.write(response.getBytes());
				System.out.println(CRLF + response);
			}
			else {
				fileName = WebServer.root + fileName;
				putMethod(fileName, bodyLine, os);
			}
		}

		// No option available. 501!
		else {
			outputResponse(501, request, false, "", os);
		}

		WebServer.threads[threadNumber] = null;
		WebServer.clients--;

		// Close streams and socket.
		endClient(os, br);
	}

	/**
	 * Closes streams, sockets and updates thread list.
	 * 
	 * @param os
	 * @param br
	 */
	private void endClient(DataOutputStream os, BufferedReader br) {
		WebServer.threads[threadNumber] = null;
		WebServer.clients--;

		// Close streams and socket.
		try {
			br.close();
			os.close();
			socket.close();
		}
		catch (IOException e) {
			System.out.println(e);
		}
	}

	/**
	 * Receives process request and returns the appropriate response.
	 * 
	 * @param statusCode
	 * @param method
	 * @param isFile
	 * @param method
	 * @param os
	 * @throws Exception
	 */
	private void outputResponse(int statusCode, String fileName, boolean isFile, String method,
			DataOutputStream os) throws Exception {
		String statusLine = null;
		String contentTypeLine = null;
		String contentLength = null;
		StringBuilder str = new StringBuilder();
		boolean headMethod = method.equals("HEAD");

		if (statusCode == 200) {
			statusLine = httpResponse + "200 OK" + CRLF;
			contentTypeLine = "Content-Type: " + contentType(fileName) + CRLF;
			contentLength = "Content-Length: ";
		}
		else if (statusCode == 404) {
			statusLine = httpResponse + "404 Not Found" + CRLF;
			contentTypeLine = "Content-Type: text/html" + CRLF;
			contentLength = "Content-Length: ";
		}
		// Status code = 501
		else if (statusCode == 501) {
			statusLine = httpResponse + "501 Not Implemented" + CRLF;
			contentTypeLine = "Content-Type: text/html" + CRLF;
		}

		FileInputStream file = null;
		// Opening the requested file
		if (isFile) {
			try {
				file = new FileInputStream(fileName);
			}
			catch (FileNotFoundException e) {
				System.out.println("Error opening file");
			}

			// Calculating content length
			contentLength += Integer.toString(file.available()) + CRLF;

		}
		else {
			contentLength += fileName.length() + CRLF;
		}

		str.append(statusLine);
		str.append(contentTypeLine);
		if (statusCode != 501 && !isChunked)
			str.append(contentLength);

		os.write(str.toString().getBytes());
		os.write(CRLF.getBytes());

		if (!isFile && !headMethod)
			os.write(method.getBytes());

		System.out.println(CRLF);
		System.out.println(str.toString());

		// Send the content of the HTTP if request method is not HEAD.
		// else, we just one to report the status line.
		if (!headMethod && isFile) {
			sendBytes(file, os);
			file.close();
		}

	}

	/**
	 * !!BONUS: DELETE HTTP METHOD. If requested source exists, delete it (and
	 * send 200 OK to client). Else, 404 code is sent to client.
	 * 
	 * @param fileName
	 * @param os
	 */
	private void deleteMethod(String fileName, DataOutputStream os) {
		File file = null;
		StringBuilder response = new StringBuilder();
		if ((file = new File(fileName)).exists()) {
			try {
				response.append(httpResponse + "200 OK" + CRLF);
				response.append("Content-Length: " + file.length() + CRLF);
				response.append("Content-Type: " + contentType(fileName) + CRLF);
				os.write(response.toString().getBytes());
				System.out.println(CRLF + response.toString());
				file.delete();
			}
			catch (IOException e) {
				System.out.println(e);
			}
		}
		else {
			try {
				// Even though DELETE by definition, does not contain a 404
				// code,
				// We added it inorder to state that the file does not exist.
				response.append(httpResponse + "404 Not Found" + CRLF);
				os.write((response.toString()).getBytes());
				System.out.println(CRLF + response.toString());
			}
			catch (IOException e) {
				System.out.println(e);
			}
		}
	}

	/**
	 * !!BONUS: PUT HTTP method: If file exists -> modifies it with entity body
	 * (as done in POST) Else, creates a new source (file) with the entity body.
	 * 
	 * @param fileName
	 * @param entity
	 * @param os
	 */
	private void putMethod(String fileName, String entity, DataOutputStream os) {
		StringBuilder response = new StringBuilder();
		FileWriter fw = null;
		try {
			if (entity == null) {
				response.append(httpResponse + "400 Bad Request" + CRLF);
				os.write(response.toString().getBytes());
				System.out.println(CRLF + response.toString());
			}
			else if ((new File(fileName)).exists()) {
				response.append(httpResponse + "200 OK" + CRLF);
				response.append("Content-Length: " + entity.length() + CRLF);
				response.append("Content-Type: " + contentType(fileName) + CRLF);
				os.write(response.toString().getBytes());
				System.out.println(CRLF + response.toString());
				fw = new FileWriter(fileName, true);
				fw.write(entity);
				fw.close();
			}
			else {
				response.append(httpResponse + "201 Created" + CRLF);
				response.append("Content-Length: " + entity.length() + CRLF);
				response.append("Content-Type: " + contentType(fileName) + CRLF);
				os.write((response.toString()).getBytes());
				System.out.println(CRLF + response.toString());
				fw = new FileWriter(fileName);
				fw.write(entity);
				fw.close();
			}
		}
		catch (IOException e) {
			System.out.println(e);
		}
	}

	/**
	 * Sends bytes from input file to output stream (client)
	 * 
	 * @param file
	 * @param os
	 * @throws Exception
	 */
	private void sendBytes(FileInputStream file, DataOutputStream os) throws Exception {
		byte[] buffer = new byte[1024];
		int bytes = 0;

		while ((bytes = file.read(buffer)) != -1) {
			os.write(buffer, 0, bytes);
			// Each chunk starts with the number of octets of the data it embeds
			// expressed as a hexadecimal number in ASCII
			if (isChunked) {
				chunkLine(bytes, os);
			}
		}
		// The terminating chunk is a regular chunk, with the exception that its
		// length is zero.
		if (isChunked) {
			chunkLine(0, os);
		}
	}

	/**
	 * Produces a status line if chunk is requested. delivers content by chunks
	 * of size in hexadecimal followed by a a chunk size of 0 to state that the
	 * transfer has completed.
	 * 
	 * @param size
	 * @param os
	 */
	private static void chunkLine(int size, DataOutputStream os) {
		String chunkSize = Integer.toHexString(size);
		String statusLine = CRLF + chunkSize + CRLF;
		try {
			os.write(statusLine.getBytes());
		}
		catch (IOException e) {
		}
		System.out.println(statusLine);
	}

	/**
	 * Selects the string content type
	 * 
	 * @param fileName
	 * @return
	 */
	private static String contentType(String fileName) {
		// Images
		if (fileName.endsWith(".gif")) {
			return "image/gif";
		}
		else if (fileName.endsWith(".jpg")) {
			return "image/jpg";
		}
		else if (fileName.endsWith(".bmp")) {
			return "image/bmp";
		}
		else if (fileName.endsWith(".png")) {
			return "image/png";
		}
		// html text
		else if (fileName.endsWith(".html")) {
			return "text/html";
		}
		// Icons
		else if (fileName.endsWith(".ico")) {
			return "image/ico";
		}
		return "application/octet-stream";
	}

	/**
	 * Decodes parameters into a given hash map.
	 * 
	 * @param params
	 * @param p
	 * @throws InterruptedException
	 */
	private void decodeParams(String params, HashMap<String, String> p) throws InterruptedException {
		if (params == null)
			return;

		StringTokenizer st = new StringTokenizer(params, "&");
		while (st.hasMoreTokens()) {
			String e = st.nextToken();
			int sep = e.indexOf('=');
			if (sep >= 0)
				p.put(e.substring(0, sep).trim(), e.substring(sep + 1));
		}
	}

	/**
	 * Initialise body params if request method is POST
	 * 
	 * @param br
	 * @return String representation of the requests body
	 */
	private String initBodyLine(BufferedReader br) {
		StringBuilder line = new StringBuilder();
		try {

			int read;
			// parse body params from line request
			while ((read = br.read()) != -1) {
				line.append((char) read);
				if (line.length() == contentLength)
					break;
			}
		}
		catch (IOException e) {
			System.err.println("Error reading request lines at initBodyLine");
		}
		return line.toString();
	}

	/**
	 * Creating the params_info.html file if requested by client
	 */
	private void createParamsInfoPage() {
		// Header and html code
		String htmlString = "<html>" + " <html> <head> "
				+ "<link rel=\"shortcut icon\" href=\"/favicon.ico\" " + "type=\"image/x-icon\" />"
				+ "</head>" + "<html> <body> "
				+ "<center> <table border=\"1\" style=\"width:50%\"> </center>"
				+ "<tr>	<td> Parameter </td> <td> Value </td> </tr>";

		// Adding params info to table in html code
		StringBuilder paramsinfo = new StringBuilder("<tr>");
		for (Map.Entry<String, String> entry : params.entrySet()) {
			paramsinfo.append("<tr>");
			paramsinfo.append("<td>" + entry.getKey() + "</td>");
			paramsinfo.append("<td>" + entry.getValue() + "</td>");
			paramsinfo.append("</tr>");
		}

		paramsinfo.append("</tr>");
		htmlString += paramsinfo.toString() + "</table>	</body>	</html>";

		// Creating a new params_info.html file with htmlString code.
		try {
			File htmlFile = new File((WebServer.root + "/params_info.html"));
			BufferedWriter op = new BufferedWriter(new FileWriter(htmlFile));
			op.write(htmlString);
			op.close();
		}
		catch (IOException e) {
		}
	}

	/**
	 * Method checks if a generic T object is null. if so, stop.
	 * 
	 * @param check
	 * @param os
	 * @param <T>
	 */
	private <T> boolean isNotNull(T check, OutputStream os) {
		if (check == null) {
			try {
				os.write("HTTP/1.1 500 Internal Server Error".getBytes());
				return false;
			}
			catch (IOException e) {
				System.out.println(e);
			}
		}
		return true;
	}
}