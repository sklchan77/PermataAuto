package my.app.utils.app;

import android.annotation.SuppressLint;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import my.app.utils.concurrent.NetThreadPool;
import my.app.utils.concurrent.ThreadPool;
import my.app.utils.net.NetHandler;

/**
 * @author sklchan77
 */
public class NetApp extends App {
	@SuppressLint("StaticFieldLeak")
	private volatile NetHandler netHandler;

	public static NetApp get() {
		return App.get();
	}


	@Override
	public void onTerminate() {
		NetHandler net = netHandler;
		if (net != null) net.close();
		super.onTerminate();
	}

	public NetHandler getNetHandler() {
		NetHandler net = netHandler;

		if (net == null) {
			synchronized (this) {
				if ((net = netHandler) == null) {
					try {
						netHandler = net = NetHandler.create(o -> {
							o.executor = getExecutor();
							o.scheduler = getScheduler();
							o.inactivityTimeout = getChannelInactivityTimeout();
						});
					} catch (IOException ex) {
						throw new RuntimeException(ex);
					}
				}
			}
		}

		return net;
	}

	protected int getChannelInactivityTimeout() {
		return 5 * 60;
	}

	protected ThreadPool createExecutor() {
		return new NetThreadPool(getNumberOfCoreThreads(), getMaxNumberOfThreads(), 60L, TimeUnit.SECONDS);
	}
}
