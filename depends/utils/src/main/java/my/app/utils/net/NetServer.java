package my.app.utils.net;

import java.io.Closeable;
import java.net.SocketAddress;

/**
 * @author sklchan77
 */
public interface NetServer extends Closeable {

	NetHandler getHandler();

	int getPort();

	SocketAddress getBindAddress();

	boolean isOpen();

	@Override
	void close();

	interface ConnectionHandler {
		void acceptConnection(NetChannel channel);
	}
}
