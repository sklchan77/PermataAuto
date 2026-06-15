package my.app.utils.net.http;


import androidx.annotation.Nullable;

import my.app.utils.async.FutureSupplier;

/**
 * @author sklchan77
 */
public interface HttpRequestHandler {

	FutureSupplier<?> handleRequest(HttpRequest req);

	interface Provider {
		@Nullable
		HttpRequestHandler getHandler(CharSequence path, HttpMethod method, HttpVersion version);
	}
}
