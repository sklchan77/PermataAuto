package my.app.utils.net;

import java.io.IOException;

/**
 * @author sklchan77
 */
public class ConnectionClosedException extends IOException {

	public ConnectionClosedException() {
	}

	public ConnectionClosedException(String message) {
		super(message);
	}

	public ConnectionClosedException(String message, Throwable cause) {
		super(message, cause);
	}

	public ConnectionClosedException(Throwable cause) {
		super(cause);
	}
}
