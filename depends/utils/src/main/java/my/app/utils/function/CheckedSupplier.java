package my.app.utils.function;

/**
 * @author sklchan77
 */
public interface CheckedSupplier<R, E extends Throwable> {
	R get() throws E;
}
