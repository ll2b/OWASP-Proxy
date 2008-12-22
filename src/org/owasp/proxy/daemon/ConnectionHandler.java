package org.owasp.proxy.daemon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.owasp.proxy.httpclient.DefaultHttpClientFactory;
import org.owasp.proxy.httpclient.HttpClient;
import org.owasp.proxy.httpclient.HttpClientFactory;
import org.owasp.proxy.io.CopyInputStream;
import org.owasp.proxy.io.SocketWrapper;
import org.owasp.proxy.model.Conversation;
import org.owasp.proxy.model.MessageFormatException;
import org.owasp.proxy.model.Request;
import org.owasp.proxy.model.Response;
import org.owasp.proxy.model.URI;

public class ConnectionHandler implements Runnable {

	private final static String NO_CERTIFICATE_HEADER = "HTTP/1.0 503 Service unavailable"
			+ " - SSL server certificate not available\r\n\r\n";

	private final static String NO_CERTIFICATE_MESSAGE = "There is no SSL server certificate available for use";

	private final static String ERROR_HEADER = "HTTP/1.0 500 OWASP Proxy Error\r\n"
			+ "Content-Type: text/html\r\nConnection: close\r\n\r\n";

	private final static String ERROR_MESSAGE1 = "<html><head><title>OWASP Proxy Error</title></head>"
			+ "<body><h1>OWASP Proxy Error</h1>"
			+ "OWASP Proxy encountered an error fetching the following request : <br/><pre>";

	private final static String ERROR_MESSAGE2 = "</pre><br/>The error was: <br/><pre>";

	private final static String ERROR_MESSAGE3 = "</pre></body></html>";

	private final static Logger logger = Logger
			.getLogger(ConnectionHandler.class.toString());

	private Socket socket;

	private boolean targetSsl = false;

	private String targetHost = null;

	private int targetPort = -1;

	private HttpClient httpClient = null;

	private ProxyMonitor monitor;

	private CertificateProvider certProvider = null;

	private HttpClientFactory clientFactory;

	public ConnectionHandler(Socket accept) {
		this.socket = accept;
		try {
			socket.setSoTimeout(0);
		} catch (SocketException se) {
			se.printStackTrace();
		}
	}

	public void setTarget(boolean ssl, String host, int port) {
		this.targetSsl = ssl;
		this.targetHost = host;
		this.targetPort = port;
	}

	public void setProxyMonitor(ProxyMonitor monitor) {
		this.monitor = monitor;
	}

	public void setCertificateProvider(CertificateProvider certProvider) {
		this.certProvider = certProvider;
	}

	public void setHttpClientFactory(HttpClientFactory clientFactory) {
		this.clientFactory = clientFactory;
	}

	private Socket negotiateSSL(SSLSocketFactory factory, Socket socket)
			throws IOException {
		SSLSocket sslsock = (SSLSocket) factory.createSocket(socket, socket
				.getInetAddress().getHostName(), socket.getPort(), true);
		sslsock.setUseClientMode(false);
		return sslsock;
	}

	private void writeErrorResponse(PrintStream out, Request request,
			Exception e) {
		try {
			out.write(ERROR_HEADER.getBytes());
			out.write(ERROR_MESSAGE1.getBytes());
			out.write(request.getMessage());
			out.write(ERROR_MESSAGE2.getBytes());
			e.printStackTrace(out);
			out.write(ERROR_MESSAGE3.getBytes());
		} catch (IOException ioe) {
			// just eat it
		}
	}

	private SSLSocketFactory getSocketFactory(String host, int port)
			throws IOException {
		if (certProvider != null)
			return certProvider.getSocketFactory(host, port);
		return null;
	}

	private void doConnect(OutputStream out, Request request)
			throws IOException, MessageFormatException {
		String resource = request.getResource();
		int colon = resource.indexOf(':');
		if (colon == -1)
			throw new MessageFormatException("Malformed CONNECT line : '"
					+ resource + "'");
		String host = resource.substring(0, colon);
		if (host.length() == 0)
			throw new MessageFormatException("Malformed CONNECT line : '"
					+ resource + "'");
		int port;
		try {
			port = Integer.parseInt(resource.substring(colon + 1));
		} catch (NumberFormatException nfe) {
			throw new MessageFormatException("Malformed CONNECT line : '"
					+ resource + "'");
		}
		SSLSocketFactory socketFactory = getSocketFactory(host, port);
		if (socketFactory == null) {
			out.write(NO_CERTIFICATE_HEADER.getBytes());
			out.write(NO_CERTIFICATE_MESSAGE.getBytes());
			out.flush();
		} else {
			out.write("HTTP/1.0 200 Ok\r\n\r\n".getBytes());
			out.flush();
			out = null;
			// start over from the beginning to handle this
			// connection as an SSL connection
			socket = negotiateSSL(socketFactory, socket);
			targetSsl = true;
			targetHost = host;
			targetPort = port;
			run();
		}
	}

	private Request readRequest(CopyInputStream in, ByteArrayOutputStream copy,
			OutputStream out) {
		Request request = null;
		Response response = null;
		try {
			// read the whole header.
			// The exact data read can be obtained from the
			// BytearrayInputStream defined above
			try {
				while (!"".equals(in.readLine()))
					;

			} catch (IOException ioe) {
				byte[] headerBytes = copy.toByteArray();
				// connection closed while waiting for a new request
				if (headerBytes == null || headerBytes.length == 0)
					return null;
				throw ioe;
			}

			byte[] headerBytes = copy.toByteArray();

			// empty request line, connection closed?
			if (headerBytes == null || headerBytes.length == 0)
				return null;

			request = new Request();
			request.setMessage(headerBytes);
			headerBytes = null;

			// Get the request content (if any) from the stream,
			if (Request.flushContent(request, in))
				request.setMessage(copy.toByteArray());

			// clear the stream copy
			copy.reset();

			if (targetHost != null) {
				request.setSsl(targetSsl);
				request.setHost(targetHost);
				request.setPort(targetPort);
			} else if (!"CONNECT".equals(request.getMethod())) {
				String resource = request.getResource();
				int css = resource.indexOf("://");
				if (css > 3 && css < 6) {
					try {
						URI uri = new URI(resource);
						request.setSsl("https".equals(uri.getScheme()));
						request.setHost(uri.getHost());
						request.setPort(uri.getPort());
						request.setResource(uri.getResource());
					} catch (URISyntaxException use) {
						throw new MessageFormatException(
								"Couldn't parse resource as a UR", use);
					}
				} else {
					String host = request.getHeader("Host");
					if (host == null)
						throw new MessageFormatException(
								"Couldn't determine target scheme/host/port");
					request.setSsl(socket instanceof SSLSocket);
					int colon = host.indexOf(':');
					if (colon > -1) {
						try {
							request.setHost(host.substring(0, colon));
							int port = Integer.parseInt(host.substring(
									colon + 1).trim());
							request.setPort(port);
						} catch (NumberFormatException nfe) {
							throw new MessageFormatException(
									"Couldn't parse target port from Host: header",
									nfe);
						}
					} else {
						request.setHost(host);
						request.setPort("https".equals(targetSsl) ? 443 : 80);
					}
				}
			}

			response = requestReceived(request);

			if (response == null && request.getMethod().equals("CONNECT")) {
				doConnect(out, request);
				return null;
			}
		} catch (IOException ioe) {
			response = errorReadingRequest(request, ioe);
		} catch (MessageFormatException mfe) {
			response = errorReadingRequest(request, mfe);
		}
		if (response != null) {
			try {
				out.write(response.getMessage());
			} catch (IOException ioe) {
				// just eat it
			}
			return null;
		} else {
			return request;
		}
	}

	private boolean isSSL(byte[] sniff, int len) {
		for (int i = 0; i < len; i++)
			if (sniff[i] == 0x03)
				return true;
		return false;
	}

	public void run() {
		if (clientFactory == null)
			clientFactory = new DefaultHttpClientFactory();
		try {
			InputStream sockIn;
			OutputStream out;
			try {
				sockIn = socket.getInputStream();
				out = socket.getOutputStream();
			} catch (IOException ioe) {
				// shouldn't happen
				ioe.printStackTrace();
				return;
			}

			if (!targetSsl) {
				// check if it is an SSL Connection
				try {
					byte[] sslsniff = new byte[4];
					PushbackInputStream pbis = new PushbackInputStream(socket
							.getInputStream(), sslsniff.length);
					int got = pbis.read(sslsniff);
					pbis.unread(sslsniff, 0, got);
					if (isSSL(sslsniff, got)) {
						SSLSocketFactory factory = getSocketFactory(targetHost,
								targetPort);
						SocketWrapper wrapper = new SocketWrapper(socket, pbis,
								out);
						socket = negotiateSSL(factory, wrapper);
						targetSsl = true;
						run();
						return;
					} else {
						sockIn = pbis;
					}
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}
			boolean close;
			do {
				ByteArrayOutputStream copy = new ByteArrayOutputStream();
				CopyInputStream in = new CopyInputStream(sockIn, copy);
				Request request = readRequest(in, copy, out);
				// request may be null if a response has already been sent
				// to the browser
				// or if there was no Request to be read on the socket
				// (closed)
				if (request == null)
					return;

				Conversation conversation = null;
				try {
					if (httpClient == null)
						httpClient = clientFactory.createHttpClient();
					conversation = httpClient.fetchResponseHeader(request);
				} catch (IOException ioe) {
					errorFetchingResponseHeader(request, ioe);
					writeErrorResponse(new PrintStream(out), request, ioe);
					return;
				} catch (MessageFormatException mfe) {
					errorFetchingResponseHeader(request, mfe);
					writeErrorResponse(new PrintStream(out), request, mfe);
					return;
				}
				boolean stream = responseHeaderReceived(conversation);
				if (stream) {
					try {
						// message only contains headers at this point
						out.write(conversation.getResponse().getMessage());
						httpClient.fetchResponseContent(out);
						responseContentReceived(conversation, stream);
						wroteResponseToBrowser(conversation);
					} catch (IOException ioe) {
						errorFetchingResponseContent(conversation, ioe);
						return;
					}
				} else {
					try {
						httpClient.fetchResponseContent(null);
						responseContentReceived(conversation, stream);
					} catch (IOException ioe) {
						errorFetchingResponseContent(conversation, ioe);
						return;
					}
					try {
						out.write(conversation.getResponse().getMessage());
						wroteResponseToBrowser(conversation);
					} catch (IOException ioe) {
						errorWritingResponseToBrowser(conversation, ioe);
						return;
					}
				}
				String version = conversation.getResponse().getVersion();
				if ("HTTP/1.1".equals(version)) {
					close = false;
				} else {
					close = true;
				}
				String connection = conversation.getResponse().getHeader(
						"Connection");
				if ("close".equals(connection)) {
					close = true;
				} else if ("Keep-Alive".equalsIgnoreCase(connection)) {
					close = false;
				}
				copy = null;
			} while (!close);
		} catch (MessageFormatException mfe) {
			logger.severe(mfe.getMessage());
			mfe.printStackTrace();
		} finally {
			try {
				if (!socket.isClosed())
					socket.close();
			} catch (IOException ioe) {
			}
			if (httpClient != null) {
				try {
					httpClient.close();
				} catch (IOException ioe) {
					// just eat the exception
				}
			}
		}

	}

	private Response errorReadingRequest(Request request, Exception e) {
		try {
			if (monitor != null)
				return monitor.errorReadingRequest(request, e);
		} catch (Exception e2) {
			e2.printStackTrace();
		}
		return null;
	}

	private Response requestReceived(Request request) {
		try {
			if (monitor != null)
				return monitor.requestReceived(request);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private Response errorFetchingResponseHeader(Request request, Exception e) {
		try {
			if (monitor != null)
				return monitor.errorFetchingResponseHeader(request, e);
		} catch (Exception e2) {
			e2.printStackTrace();
		}
		return null;
	}

	private Response errorFetchingResponseContent(Conversation conversation,
			Exception e) {
		try {
			if (monitor != null)
				return monitor.errorFetchingResponseContent(conversation, e);
		} catch (Exception e2) {
			e2.printStackTrace();
		}
		return null;
	}

	private boolean responseHeaderReceived(Conversation conversation) {
		try {
			if (monitor != null)
				return monitor.responseHeaderReceived(conversation);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	private void responseContentReceived(Conversation conversation,
			boolean streamed) {
		try {
			if (monitor != null)
				monitor.responseContentReceived(conversation, streamed);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void errorWritingResponseToBrowser(Conversation conversation,
			Exception e) {
		try {
			if (monitor != null)
				monitor.errorWritingResponseToBrowser(conversation, e);
		} catch (Exception e2) {
			e2.printStackTrace();
		}
	}

	private void wroteResponseToBrowser(Conversation conversation) {
		try {
			if (monitor != null)
				monitor.wroteResponseToBrowser(conversation);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}