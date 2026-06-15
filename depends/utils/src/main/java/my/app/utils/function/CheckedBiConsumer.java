package my.app.utils.function;

/**
 * @author sklchan77
 */
public interface CheckedBiConsumer<T, U, E extends Throwable> {
	void accept(T t, U u) throws E;
}
