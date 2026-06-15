package my.app.utils.async;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import my.app.utils.function.Cancellable;

import static my.app.utils.function.ResultConsumer.Cancel.isCancellation;
import static my.app.utils.misc.Assert.assertNotNull;
import static my.app.utils.misc.Assert.assertTrue;

/**
 * @author sklchan77
 */
public interface Completable<T> extends Cancellable {

	boolean isDone();

	boolean isCancelled();

	boolean isFailed();

	boolean complete(@Nullable T result);

	boolean completeExceptionally(@NonNull Throwable fail);

	boolean setProgress(T incomplete, int progress, int total);

	default boolean complete(T result, Throwable fail) {
		if (fail == null) return complete(result);
		else if (isCancellation(fail)) return cancel();
		else return completeExceptionally(fail);
	}

	default boolean completeAs(FutureSupplier<? extends T> done) {
		assertTrue(done.isDone());

		if (done.isFailed()) {
			assertNotNull(done.getFailure());
			assert (done.getFailure() != null);
			return done.isCancelled() ? cancel() : completeExceptionally(done.getFailure());
		} else {
			return complete(done.get(null));
		}
	}
}
