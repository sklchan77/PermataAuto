package my.app.utils.function;

import java.util.Objects;

/**
 * @author sklchan77
 */
public interface IntConsumer {

	void accept(int value);

	default IntConsumer andThen(IntConsumer after) {
		Objects.requireNonNull(after);
		return (int t) -> {
			accept(t);
			after.accept(t);
		};
	}
}
