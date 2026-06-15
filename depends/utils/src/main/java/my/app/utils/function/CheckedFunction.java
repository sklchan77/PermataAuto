package my.app.utils.function;

/**
 * @author sklchan77
 */
public interface CheckedFunction<T, R, E extends Throwable> {
	R apply(T t) throws E;
}
