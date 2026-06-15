package my.app.utils.holder;

import my.app.utils.function.Consumer;
import my.app.utils.function.Supplier;

/**
 * @author sklchan77
 */
public class Holder<T> implements Consumer<T>, Supplier<T> {
	public T value;

	public Holder() {
	}

	public Holder(T value) {
		this.value = value;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	@Override
	public void accept(T t) {
		set(t);
	}

	@Override
	public String toString() {
		return String.valueOf(get());
	}
}
