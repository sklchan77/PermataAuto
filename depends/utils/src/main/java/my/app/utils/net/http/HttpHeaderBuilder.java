package my.app.utils.net.http;

import java.io.OutputStream;
import java.nio.ByteBuffer;

import my.app.utils.function.CheckedConsumer;

/**
 * @author sklchan77
 */
public interface HttpHeaderBuilder {

	HttpMessageBuilder addHeader(HttpHeader h);

	HttpMessageBuilder addHeader(HttpHeader h, long value);

	HttpMessageBuilder addHeader(CharSequence name, long value);

	HttpMessageBuilder addHeader(HttpHeader h, CharSequence value);

	HttpMessageBuilder addHeader(CharSequence name, CharSequence value);

	HttpMessageBuilder addHeader(CharSequence line);
}
