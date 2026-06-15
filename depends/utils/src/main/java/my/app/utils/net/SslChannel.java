package my.app.utils.net;

import javax.net.ssl.SSLEngine;

import my.app.utils.async.FutureSupplier;

/**
 * @author sklchan77
 */
public interface SslChannel extends NetChannel {

	static FutureSupplier<? extends SslChannel> create(NetChannel channel, SSLEngine engine) {
		return SslChannelImpl.create(channel, engine);
	}
}
