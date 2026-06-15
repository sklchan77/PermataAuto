package my.app.utils.holder;


import my.app.utils.function.IntConsumer;
import my.app.utils.function.IntSupplier;

/**
 * @author sklchan77
 */
public class IntHolder implements IntConsumer, IntSupplier {
	public int value;

	public IntHolder() {
	}

	public IntHolder(int value) {
		this.value = value;
	}

	public int get() {
		return value;
	}

	@Override
	public int getAsInt() {
		return get();
	}

	public void set(int value) {
		this.value = value;
	}

	@Override
	public void accept(int t) {
		set(t);
	}

	@Override
	public String toString() {
		return String.valueOf(get());
	}
}
