package my.app.utils.concurrent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import my.app.utils.text.SharedTextBuilder;

import static my.app.utils.misc.Assert.assertSame;

/**
 * @author sklchan77
 */
public class PooledThread extends Thread {
	private final SharedTextBuilder sb = SharedTextBuilder.create(this);

	public PooledThread() {
	}

	public PooledThread(@Nullable Runnable target) {
		super(target);
	}

	public PooledThread(@Nullable Runnable target, @NonNull String name) {
		super(target, name);
	}

	public SharedTextBuilder getSharedTextBuilder() {
		assertSame(this, Thread.currentThread());
		return sb;
	}
}
