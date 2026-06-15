package my.app.utils.function;

/**
 * @author sklchan77
 */
public interface IntObjectFunction<T, R> {
	R apply(int i, T t);
}
