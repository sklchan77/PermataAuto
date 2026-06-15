package my.app.utils.vfs.local;

import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.failed;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import my.app.utils.async.Completed;
import my.app.utils.async.FutureSupplier;
import my.app.utils.io.AsyncInputStream;
import my.app.utils.io.AsyncOutputStream;
import my.app.utils.io.FileUtils;
import my.app.utils.io.IoUtils;
import my.app.utils.io.RandomAccessChannel;
import my.app.utils.vfs.VirtualFile;
import my.app.utils.vfs.VirtualFolder;

/**
 * @author sklchan77
 */
class LocalFile extends LocalResource implements VirtualFile {

	LocalFile(File file) {
		super(file);
	}

	LocalFile(File file, VirtualFolder parent) {
		super(file, parent);
	}

	@Override
	public FutureSupplier<Long> getLength() {
		return getVirtualFileSystem().getLength(getLocalFile());
	}

	@Override
	public boolean isLocalFile() {
		return true;
	}

	@NonNull
	@Override
	public FutureSupplier<Void> copyTo(VirtualFile to) {
		File toFile = to.getLocalFile();

		if (toFile != null) {
			try {
				FileUtils.copy(file, toFile);
				return Completed.completedNull();
			} catch (IOException ex) {
				return failed(ex);
			}
		}

		return VirtualFile.super.copyTo(to);
	}

	@NonNull
	@Override
	public FutureSupplier<Boolean> moveTo(VirtualFile to) {
		File toFile = to.getLocalFile();
		LocalFileSystem fs = getVirtualFileSystem();
		fs.closeCachedChannels(file);

		if (toFile != null) {
			try {
				fs.closeCachedChannels(toFile);
				FileUtils.move(file, toFile);
				return completed(true);
			} catch (IOException ex) {
				return failed(ex);
			}
		}

		return VirtualFile.super.moveTo(to);
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualFile> rename(CharSequence name) {
		try {
			VirtualFolder p = getParent().peek();
			if (p == null) return failed(new IOException());
			File toFile = new File(p.getLocalFile(), name.toString());
			LocalFileSystem fs = getVirtualFileSystem();
			fs.closeCachedChannels(file, toFile);
			FileUtils.move(file, toFile);
			return completed(new LocalFile(toFile, p));
		} catch (IOException ex) {
			return failed(ex);
		}
	}

	@Override
	public AsyncInputStream getInputStream(long offset) throws IOException {
		FileInputStream in = new FileInputStream(file);
		IoUtils.skip(in, offset);
		return AsyncInputStream.from(in, getInputBufferLen());
	}

	@Override
	public AsyncOutputStream getOutputStream() throws IOException {
		return AsyncOutputStream.from(new FileOutputStream(file), getOutputBufferLen());
	}

	@Nullable
	@Override
	public RandomAccessChannel getChannel(String mode) {
		return getVirtualFileSystem().getChannel(getLocalFile(), mode);
	}
}
