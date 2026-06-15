package my.app.permata.media.engine;

/**
 * @author sklchan77
 */

public class MediaEngineException extends Exception {

	public MediaEngineException(String message) {
		super(message);
	}

	@SuppressWarnings("unused")
	public MediaEngineException(String message, Throwable cause) {
		super(message, cause);
	}

	@SuppressWarnings("unused")
	public MediaEngineException(Throwable cause) {
		super(cause);
	}
}
