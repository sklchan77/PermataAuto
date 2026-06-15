package my.app.utils.vfs.smb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hierynomus.mssmb2.SMB2Dialect;
import com.hierynomus.mssmb2.messages.SMB2Echo;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.io.IOException;
import java.util.Objects;

import my.app.utils.app.App;
import my.app.utils.async.Async;
import my.app.utils.async.FutureSupplier;
import my.app.utils.async.ObjectPool;
import my.app.utils.async.ObjectPool.PooledObject;
import my.app.utils.function.CheckedFunction;
import my.app.utils.function.IntSupplier;
import my.app.utils.io.IoUtils;
import my.app.utils.log.Log;
import my.app.utils.pref.PreferenceStore.Pref;
import my.app.utils.resource.Rid;
import my.app.utils.vfs.VirtualFileSystem;
import my.app.utils.vfs.VirtualFolder;

import static java.util.Objects.requireNonNull;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.completedNull;
import static my.app.utils.vfs.smb.SmbFileSystem.SCHEME_SMB;

/**
 * @author sklchan77
 */
class SmbRoot extends SmbFolder {
	private final SessionPool pool;
	private final Rid rid;

	SmbRoot(@NonNull SessionPool pool, @NonNull String path) {
		//noinspection ConstantConditions
		super(null, path);
		this.pool = pool;
		String user = "".equals(pool.user) ? null : pool.user;
		int port = (pool.port == pool.fs.getDefaultPort()) ? -1 : pool.port;
		rid = Rid.create(SCHEME_SMB, user, pool.host, port, path);
	}

	@NonNull
	@Override
	protected SmbRoot getRoot() {
		return this;
	}

	@NonNull
	@Override
	public Rid getRid() {
		return rid;
	}

	@Override
	String smbPath() {
		return "";
	}

	@NonNull
	@Override
	public VirtualFileSystem getVirtualFileSystem() {
		return pool.fs;
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualFolder> getParent() {
		return completedNull();
	}

	static SmbRoot create(
			@NonNull SmbFileSystem fs, @Nullable String user, @NonNull String host, int port,
			@Nullable String path, @Nullable String password) {
		SessionPool pool = new SessionPool(fs, user, host, port, requireNonNull(path), password);
		return new SmbRoot(pool, path);
	}

	static FutureSupplier<VirtualFolder> createConnected(
			@NonNull SmbFileSystem fs, @Nullable String user, @NonNull String host, int port,
			@Nullable String path, @Nullable String password) {
		SessionPool pool = new SessionPool(fs, user, host, port, requireNonNull(path), password);
		return pool.getObject().closeableThen(session -> completed(new SmbRoot(pool, path)));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		return getRid().equals(((SmbRoot) o).getRid());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getRid());
	}

	FutureSupplier<PooledObject<SmbSession>> getSession() {
		return pool.getObject();
	}

	<T> FutureSupplier<T> useShare(CheckedFunction<DiskShare, T, Throwable> task) {
		return Async.retry(() -> getSession().then(ref -> App.get().execute(() -> {
			SmbSession session = null;

			try {
				session = ref.get();
				T result = task.apply(session.getShare());
				session = null;
				return result;
			} finally {
				if (session != null) {
					try {
						session.session.send(new SMB2Echo(SMB2Dialect.SMB_2_0_2)).get();
					} catch (Throwable ex) {
						session.close();
					}
				}

				ref.release();
			}
		})));
	}

	private static class SessionPool extends ObjectPool<SmbSession> {
		private static final Pref<IntSupplier> MAX_SESSIONS = Pref.i("SMB_MAX_SESSIONS", 3);
		final SmbFileSystem fs;
		@Nullable
		final String user;
		@NonNull
		final String host;
		final int port;
		@NonNull
		private final String share;
		@NonNull
		private final char[] password;
		@Nullable
		private final String smbUser;
		private final String smbDomain;

		public SessionPool(SmbFileSystem fs, @Nullable String user, @NonNull String host,
											 int port, @NonNull String path, @Nullable String password) {
			super(fs.getPreferenceStore().getIntPref(MAX_SESSIONS));
			this.fs = fs;
			this.user = user;
			this.host = host;
			this.port = port;
			this.share = SmbFileSystem.smbPath(path, false);
			this.password = (password == null) ? new char[0] : password.toCharArray();

			if (user == null) {
				smbUser = "";
				smbDomain = null;
			} else {
				String[] s = user.split(";");

				if (s.length == 2) {
					smbUser = s[1];
					smbDomain = s[0];
				} else {
					smbUser = s[0];
					smbDomain = null;
				}
			}
		}

		@Override
		protected FutureSupplier<SmbSession> createObject() {
			return App.get().execute(this::createSession);
		}

		private SmbSession createSession() throws IOException {
			SMBClient client = new SMBClient();
			Connection connection = null;
			boolean ok = false;

			try {
				connection = client.connect(host, port);
				AuthenticationContext ac = new AuthenticationContext(smbUser, password, smbDomain);
				Session session = connection.authenticate(ac);
				DiskShare share = (DiskShare) session.connectShare(this.share);
				ok = true;
				return new SmbSession(session, share);
			} finally {
				if (!ok) IoUtils.close(connection);
			}
		}

		@Override
		protected boolean validateObject(SmbSession session, boolean releasing) {
			return session.isValid();
		}

		@Override
		protected void destroyObject(SmbSession session) {
			IoUtils.close(session);
		}
	}

	static final class SmbSession implements AutoCloseable {
		private final Session session;
		private final DiskShare share;

		public SmbSession(Session session, DiskShare share) {
			this.session = session;
			this.share = share;
		}

		DiskShare getShare() {
			return share;
		}

		boolean isValid() {
			try {
				return share.isConnected() && session.getConnection().isConnected();
			} catch (Throwable ex) {
				Log.d(ex, "Session is not valid");
				return false;
			}
		}

		@Override
		public void close() {
			IoUtils.close(session.getConnection());
		}
	}
}
