package my.app.utils.async;

/**
 * @author sklchan77
 */
public class Promise<T> extends CompletableSupplier<T, T> {
	@Override
	protected T map(T value) {
		return value;
	}
}
