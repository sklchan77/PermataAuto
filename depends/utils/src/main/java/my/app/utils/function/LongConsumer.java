package my.app.utils.function;

/**
 * @author sklchan77
 */
public interface LongConsumer {
	void accept(long value);

	default java.util.function.LongConsumer andThen(LongConsumer after) {
		return (long t) -> {
			accept(t);
			after.accept(t);
		};
	}
}
