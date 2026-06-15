package my.app.utils.function;

import java.io.Closeable;

/**
 * @author sklchan77
 */
public interface Cancellable extends Closeable {
	Cancellable CANCELED = () -> false;

	boolean cancel();

	@Override
	default void close() {
		cancel();
	}
}
