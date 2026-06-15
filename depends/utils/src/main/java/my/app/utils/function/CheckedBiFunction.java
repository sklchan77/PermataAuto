package my.app.utils.function;

/**
 * @author sklchan77
 */
public interface CheckedBiFunction<T, U, R, E extends Throwable> {
	R apply(T t, U u) throws E;
}
