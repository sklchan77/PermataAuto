package my.app.utils.net.http;

import java.io.OutputStream;
import java.nio.ByteBuffer;

import my.app.utils.function.CheckedConsumer;
import my.app.utils.function.Function;
import my.app.utils.net.ByteBufferArraySupplier;

/**
 * @author sklchan77
 */
public interface HttpRequestBuilder extends HttpHeaderBuilder {

	static HttpRequestBuilder create() {
		return new HttpMessageBuilder();
	}

	static HttpRequestBuilder create(int initCapacity) {
		return new HttpMessageBuilder(initCapacity);
	}

	static ByteBufferArraySupplier supplier(Function<HttpRequestBuilder, ByteBuffer[]> builder) {
		return HttpMessageBuilder.supplier(builder);
	}

	HttpMessageBuilder setRequest(CharSequence uri);

	HttpMessageBuilder setRequest(CharSequence uri, HttpMethod m);

	HttpMessageBuilder setRequest(CharSequence uri, HttpMethod m, HttpVersion version);

	ByteBuffer[] build();

	ByteBuffer[] build(ByteBuffer payload);

	<E extends Throwable> ByteBuffer[] build(CheckedConsumer<OutputStream, E> payloadWriter) throws E;
}
