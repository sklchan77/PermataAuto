package my.app.utils.net.http;

import java.nio.ByteBuffer;

import my.app.utils.async.FutureSupplier;
import my.app.utils.log.Log;
import my.app.utils.net.ByteBufferArraySupplier;
import my.app.utils.net.NetChannel;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * @author sklchan77
 */
public class HttpError implements ByteBufferArraySupplier {
	private final byte[] data;

	public HttpError(String response) {
		data = response.getBytes(US_ASCII);
	}

	@Override
	public ByteBuffer[] getByteBufferArray() {
		return new ByteBuffer[]{ByteBuffer.wrap(data)};
	}

	public FutureSupplier<Void> write(NetChannel channel) {
		return channel.write(this, (v, err) -> {
			if (err != null) Log.d(err, "Failed to write HttpError");
			channel.close();
		});
	}

	public interface BadRequest {
		HttpError instance = new HttpError("HTTP/1.1 400 Bad Request\r\n" +
				"Connection: close\r\n" +
				"Content-Length: 0\r\n\r\n"
		);
	}

	public interface Forbidden {
		HttpError instance = new HttpError("HTTP/1.1 403 Forbidden\r\n" +
				"Connection: close\r\n" +
				"Content-Length: 0\r\n\r\n"
		);
	}

	public interface NotFound {
		HttpError instance = new HttpError("HTTP/1.1 404 Not Found\r\n" +
				"Connection: close\r\n" +
				"Content-Length: 0\r\n\r\n"
		);
	}

	public interface MethodNotAllowed {
		HttpError instance = new HttpError("HTTP/1.1 405 Method Not Allowed\r\n" +
				"Connection: close\r\n" +
				"Content-Length: 0\r\n\r\n"
		);
	}

	public interface PayloadTooLarge {
		HttpError instance = new HttpError("HTTP/1.1 413 Payload Too Large\r\n" +
				"Connection: close\r\n" +
				"Content-Length: 0\r\n\r\n"
		);
	}

	public interface UriTooLong {
		HttpError instance = new HttpError("HTTP/1.1 414 URI Too Long\r\n" +
				"Connection: close\r\n" +
				"Content-Length: 0\r\n\r\n"
		);
	}

	public interface RangeNotSatisfiable {
		HttpError instance = new HttpError("HTTP/1.1 416 Requested range not satisfiable\r\n" +
				"Connection: close\r\n" +
				"Content-Length: 0\r\n\r\n"
		);
	}

	public interface ServerError {
		HttpError instance = new HttpError("HTTP/1.1 500 Internal Server Error\r\n" +
				"Connection: close\r\n" +
				"Content-Length: 0\r\n\r\n"
		);
	}

	public interface ServiceUnavailable {
		HttpError instance = new HttpError("HTTP/1.1 503 Service Unavailable\r\n" +
				"Connection: close\r\n" +
				"Content-Length: 0\r\n\r\n"
		);
	}

	public interface VersionNotSupported {
		HttpError instance = new HttpError("HTTP/1.1 505 HTTP Version Not Supported\r\n" +
				"Connection: close\r\n" +
				"Content-Length: 0\r\n\r\n"
		);
	}
}
