package org.owasp.proxy.daemon;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.owasp.httpclient.BufferedMessage;
import org.owasp.httpclient.BufferedRequest;
import org.owasp.httpclient.BufferedResponse;
import org.owasp.httpclient.MessageFormatException;
import org.owasp.httpclient.NamedValue;
import org.owasp.httpclient.ReadOnlyBufferedRequest;
import org.owasp.httpclient.ReadOnlyBufferedResponse;
import org.owasp.httpclient.ReadOnlyRequestHeader;
import org.owasp.httpclient.RequestHeader;
import org.owasp.httpclient.ResponseHeader;
import org.owasp.httpclient.StreamingRequest;
import org.owasp.httpclient.StreamingResponse;
import org.owasp.httpclient.util.AsciiString;
import org.owasp.httpclient.util.MessageUtils;

public class BufferingHttpRequestHandlerTest {

	private static Mock mock = new Mock();

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testOnline() throws Exception {
		StreamingRequest request = new StreamingRequest.Impl();
		request.setTarget(new InetSocketAddress("ajax.googleapis.com", 443));
		request.setSsl(true);
		request
				.setHeader(AsciiString
						.getBytes("GET /ajax/libs/jquery/1.3.2/jquery.min.js HTTP/1.1\r\n"
								+ "User-Agent: Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_5_6; en-us) AppleWebKit/525.27.1 (KHTML, like Gecko) Version/3.2.1 Safari/525.27.1\r\n"
								+ "Cache-Control: max-age=0\r\n"
								+ "Referer: http://freshmeat.net/\r\n"
								+ "Accept: */*\r\n"
								+ "Accept-Language: en-us\r\n"
								+ "Accept-Encoding: gzip, deflate\r\n"
								+ "Connection: keep-alive\r\n"
								+ "Host: ajax.googleapis.com\r\n\r\n"));
		HttpRequestHandler rh = new DefaultHttpRequestHandler();
		StreamingResponse response = rh.handleRequest(null, request, false);
		BufferedResponse brs = new BufferedResponse.Impl();
		MessageUtils.buffer(response, brs, Integer.MAX_VALUE);
		byte[] content = MessageUtils.decode(brs);
		MessageDigest digest = MessageDigest.getInstance("MD5");
		digest.update(content);
		byte[] d = digest.digest();
		System.out.print("Digest: ");
		for (int i = 0; i < d.length; i++) {
			System.out.print(Integer.toHexString((d[i] & 0xFF)) + " ");
		}
		// System.out.write(content);

		request
				.setHeader(AsciiString
						.getBytes("GET /favicon.ico HTTP/1.1\r\n"
								+ "User-Agent: Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10_5_6; en-us) AppleWebKit/525.27.1 (KHTML, like Gecko) Version/3.2.1 Safari/525.27.1\r\n"
								+ "Cache-Control: max-age=0\r\n"
								+ "Referer: http://freshmeat.net/\r\n"
								+ "Accept: */*\r\n"
								+ "Accept-Language: en-us\r\n"
								+ "Accept-Encoding: gzip, deflate\r\n"
								+ "Connection: keep-alive\r\n"
								+ "Host: ajax.googleapis.com\r\n\r\n"));

		response = rh.handleRequest(null, request, false);
		brs = new BufferedResponse.Impl();
		MessageUtils.buffer(response, brs, Integer.MAX_VALUE);
		content = MessageUtils.decode(brs);
		// System.out.write(content);

		//
		// byte[] buff = new byte[1024];
		// int got;
		//
		// InputStream in;
		// in = new FileInputStream("/Users/rogan/tmp/unchunked");
		// in = new ChunkedInputStream(in);
		// ByteArrayOutputStream chunked = new ByteArrayOutputStream();
		// in = new CopyInputStream(in, chunked);
		// // in = new FileInputStream("/Users/rogan/tmp/op");
		// try {
		// in = new GunzipInputStream(in);
		// while ((got = in.read(buff)) > -1)
		// System.out.println("Got " + got);
		// } catch (IOException ioe) {
		// ioe.printStackTrace();
		// }
		// in = new FileInputStream("/Users/rogan/tmp/op");
		// ByteArrayOutputStream unchunked = new ByteArrayOutputStream();
		// in = new CopyInputStream(in, unchunked);
		// try {
		// in = new GunzipInputStream(in);
		// while ((got = in.read(buff)) > -1)
		// System.out.println("Got " + got);
		// } catch (IOException ioe) {
		// ioe.printStackTrace();
		// }
		// byte[] chunkedBytes = chunked.toByteArray();
		// byte[] unchunkedBytes = unchunked.toByteArray();
		// System.out.println("Chunked = " + chunkedBytes.length +
		// " unchunked = "
		// + unchunkedBytes.length);
		// for (int i = 0; i < Math
		// .min(chunkedBytes.length, unchunkedBytes.length); i++) {
		// if (chunkedBytes[i] != unchunkedBytes[i])
		// System.out.println(i + ": unchunked: " + unchunkedBytes[i]
		// + " chunked: " + chunkedBytes[i]);
		// }
		//
	}

	@Test
	@Ignore
	public void testHandleRequest() throws Exception {
		BufferMock bm = new BufferMock(mock);
		bm.setDecode(true);
		bm.setMaximumContentSize(65536);
		test(bm, false, false, 32768);
		test(bm, true, true, 32768);
	}

	private void test(BufferMock bm, boolean chunked, boolean gzipped, int size)
			throws Exception {
		BufferedRequest brq = createRequest("/?chunked=" + chunked
				+ "&gzipped=" + gzipped + "&size=" + size, size);
		StreamingRequest srq = new StreamingRequest.Impl();
		MessageUtils.stream(brq, srq);

		StreamingResponse srs = bm.handleRequest(null, srq, false);

		BufferedResponse brs = new BufferedResponse.Impl();
		MessageUtils.buffer(srs, brs, Integer.MAX_VALUE);

		compare(brq, bm.result.request);
		compare(brs, bm.result.response);
	}

	private void compare(BufferedMessage a, BufferedMessage b) {
		assertTrue(Arrays.equals(a.getHeader(), b.getHeader()));
		if (!(a.getContent() == b.getContent() && a.getContent() == null)) {
			assertTrue(Arrays.equals(a.getContent(), b.getContent()));
		}
	}

	private BufferedRequest createRequest(String resource, int size)
			throws MessageFormatException {
		BufferedRequest request = new BufferedRequest.Impl();
		request.setStartLine("POST " + resource + " HTTP/1.1");
		request.setHeader("Content-Length", Integer.toString(size));
		byte[] content = new byte[size];
		fill(content);
		request.setContent(content);
		return request;
	}

	private static void fill(byte[] a) {
		for (int i = 0; i < a.length; i++) {
			a[i] = (byte) (i % 256);
		}
	}

	private static class Result {
		public BufferedRequest request = null;
		public BufferedResponse response = null;
		public boolean requestOverflow = false;
		public boolean responseOverflow = false;

		public void reset() {
			request = null;
			response = null;
			requestOverflow = false;
			responseOverflow = false;
		}
	}

	private class BufferMock extends BufferingHttpRequestHandler {

		private Result result = new Result();

		public BufferMock(HttpRequestHandler next) {
			super(next);
		}

		@Override
		protected Action directRequest(RequestHeader request) {
			return Action.BUFFER;
		}

		@Override
		protected Action directResponse(ReadOnlyRequestHeader request,
				ResponseHeader response) {
			return Action.BUFFER;
		}

		@Override
		protected void processRequest(BufferedRequest request) {
			result.request = request;
		}

		@Override
		protected void processResponse(ReadOnlyRequestHeader request,
				BufferedResponse response) {
			result.response = response;
		}

		@Override
		protected void requestContentSizeExceeded(
				ReadOnlyBufferedRequest request) {
			result.requestOverflow = true;
		}

		@Override
		protected void responseContentSizeExceeded(
				ReadOnlyRequestHeader request, ReadOnlyBufferedResponse response) {
			result.responseOverflow = true;
		}

	}

	private static class Mock implements HttpRequestHandler {

		private static Logger logger = Logger.getAnonymousLogger();

		public void dispose() throws IOException {
			logger.info("Dispose called");
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.owasp.proxy.daemon.HttpRequestHandler#handleRequest(java.net.
		 * InetAddress, org.owasp.httpclient.StreamingRequest)
		 */
		public StreamingResponse handleRequest(InetAddress source,
				StreamingRequest request, boolean isContinue)
				throws IOException, MessageFormatException {
			boolean chunked = false;
			boolean gzipped = false;
			int size = 16384;
			try {
				String resource = request.getResource();
				int q = resource.indexOf('?');
				String query = q > -1 ? resource.substring(q + 1) : null;
				NamedValue[] nv = NamedValue.parse(query, "&", "=");
				chunked = "true".equals(NamedValue.findValue(nv, "chunked"));
				gzipped = "true".equals(NamedValue.findValue(nv, "chunked"));
				String s = NamedValue.findValue(nv, "size");
				if (s != null) {
					try {
						size = Integer.parseInt(s);
					} catch (NumberFormatException nfe) {
						logger.info("Invalid message size");
					}
				}
				byte[] content = new byte[size];
				fill(content);
				StreamingResponse response = new StreamingResponse.Impl();
				response.setStartLine("HTTP/1.0 200 Ok");
				if (chunked)
					response.setHeader("Transfer-Encoding", "chunked");
				if (gzipped)
					response.setHeader("Content-Encoding", "gzip");
				response.setContent(new ByteArrayInputStream(content));
				response.setContent(MessageUtils.encode(response));
				logger.info("Response (" + (chunked ? "chunked" : "unchunked")
						+ "," + (gzipped ? "gzipped" : "unzipped")
						+ ") size = " + size);
				return response;
			} catch (MessageFormatException mfe) {
				return null;
			}
		}

	}

}