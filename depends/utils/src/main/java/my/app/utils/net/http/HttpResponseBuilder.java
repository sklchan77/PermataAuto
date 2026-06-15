package my.app.utils.net.http;

import java.io.OutputStream;
import java.nio.ByteBuffer;

import my.app.utils.function.CheckedConsumer;
import my.app.utils.function.Function;
import my.app.utils.net.ByteBufferArraySupplier;

/**
 * @author sklchan77
 */
public interface HttpResponseBuilder extends HttpHeaderBuilder {

	static HttpResponseBuilder create() {
		return new HttpMessageBuilder();
	}

	static HttpResponseBuilder create(int initCapacity) {
		return new HttpMessageBuilder(initCapacity);
	}

	static ByteBufferArraySupplier supplier(Function<HttpResponseBuilder, ByteBuffer[]> builder) {
		return HttpMessageBuilder.supplier(builder);
	}

	 HttpMessageBuilder setStatusOk(HttpVersion version);

	 HttpMessageBuilder setStatusPartial(HttpVersion version);

	 HttpMessageBuilder setStatus(HttpVersion version, CharSequence status);

	ByteBuffer[] build();

	ByteBuffer[] build(ByteBuffer payload);

	<E extends Throwable> ByteBuffer[] build(CheckedConsumer<OutputStream, E> payloadWriter) throws E;
}
