package my.app.utils.function;

/**
 * @author sklchan77
 */
public interface LongObjectFunction<T, R> {
	R apply(long i, T t);
}
