package my.app.utils.net.http;

import my.app.utils.async.FutureSupplier;
import my.app.utils.log.Log;
import my.app.utils.net.NetChannel;

/**
 * @author sklchan77
 */
public interface HttpResponseHandler {

	FutureSupplier<?> handleResponse(HttpResponse resp);

	default void onFailure(NetChannel channel, Throwable fail) {
		channel.close();
		Log.d(fail, "Failed to receive HTTP response");
	}
}
