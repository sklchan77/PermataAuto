package my.app.utils.vfs.smb;

import static my.app.utils.async.Completed.completed;
import static my.app.utils.io.AsyncInputStream.readInputStream;
import static my.app.utils.io.IoUtils.emptyByteBuffer;

import androidx.annotation.NonNull;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.share.File;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.EnumSet;

import my.app.utils.async.FutureSupplier;
import my.app.utils.async.ObjectPool.PooledObject;
import my.app.utils.io.AsyncInputStream;
import my.app.utils.io.IoUtils;
import my.app.utils.log.Log;
import my.app.utils.net.ByteBufferSupplier;
import my.app.utils.vfs.VirtualFile;
import my.app.utils.vfs.VirtualFolder;
import my.app.utils.vfs.smb.SmbRoot.SmbSession;

/**
 * @author sklchan77
 */
class SmbFile extends SmbResource implements VirtualFile {
	private FutureSupplier<Long> length;

	SmbFile(@NonNull SmbRoot root, @NonNull String path) {
		super(root, path);
	}

	SmbFile(@NonNull SmbRoot root, @NonNull String path, VirtualFolder parent) {
		super(root, path, parent);
	}

	@Override
	public FutureSupplier<Long> getLength() {
		if (length != null) return length;
		return length = getRoot().useShare(s -> s.getFileInformation(smbPath())
				.getStandardInformation().getEndOfFile()).onSuccess(len -> length = completed(len));
	}

	@Override
	public AsyncInputStream getInputStream(long offset) {
		return new AsyncInputStream() {
			private final FutureSupplier<PooledObject<SmbSession>> session = getRoot().getSession();
			long pos = offset;
			InputStream stream;
			File file;

			@Override
			public FutureSupplier<ByteBuffer> read(ByteBufferSupplier dst) {
				InputStream in = stream;

				if (in != null) {
					FutureSupplier<ByteBuffer> r = readInputStream(in, dst.getByteBuffer(), getInputBufferLen());

					if (!r.isFailed()) {
						pos += r.getOrThrow().remaining();
						return r;
					}
				}

				return session.then(s -> {
					SmbSession session = s.get();
					if (session == null) return completed(emptyByteBuffer());

					file = session.getShare().openFile(smbPath(),
							EnumSet.of(AccessMask.GENERIC_READ),
							null,
							EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
							SMB2CreateDisposition.FILE_OPEN,
							null);
					InputStream is = stream = file.getInputStream();
					IoUtils.skip(is, pos);
					FutureSupplier<ByteBuffer> r = readInputStream(is, dst.getByteBuffer(), getInputBufferLen());
					pos += r.getOrThrow().remaining();
					return r;
				});
			}

			@Override
			public void close() {
				IoUtils.close(stream, file);
				file = null;
				stream = null;
				session.cancel();
				PooledObject<SmbSession> s = session.peek();
				if (s != null) s.release();
			}
		};
	}

	@Override
	public boolean canDelete() {
		return true;
	}

	@NonNull
	@Override
	public FutureSupplier<Boolean> delete() {
		return getRoot().useShare(s -> {
			try {
				s.rm(getPath());
				return true;
			} catch (Exception ex) {
				Log.e(ex, "Failed to delete file ", getPath());
				return false;
			}
		});
	}
}
