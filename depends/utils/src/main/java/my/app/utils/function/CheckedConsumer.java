package my.app.utils.function;

/**
 * @author sklchan77
 */
public interface CheckedConsumer<T, E extends Throwable> {
	void accept(T t) throws E;
}
