package my.app.utils.vfs.local;

import static my.app.utils.async.Completed.failed;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import my.app.utils.async.Completed;
import my.app.utils.async.FutureSupplier;
import my.app.utils.io.FileUtils;
import my.app.utils.vfs.VirtualFile;
import my.app.utils.vfs.VirtualFolder;
import my.app.utils.vfs.VirtualResource;

/**
 * @author sklchan77
 */
class LocalFolder extends LocalResource implements VirtualFolder {

	LocalFolder(File file) {
		super(file);
	}

	LocalFolder(File file, VirtualFolder parent) {
		super(file, parent);
	}

	@Override
	public FutureSupplier<List<VirtualResource>> getChildren() {
		File[] files = file.listFiles();
		if ((files == null) || (files.length == 0)) return Completed.completedEmptyList();

		List<VirtualResource> list = new ArrayList<>(files.length);
		for (File f : files) {
			list.add(f.isFile() ? new LocalFile(f, this) : new LocalFolder(f, this));
		}
		return Completed.completed(list);
	}

	@Override
	public FutureSupplier<VirtualResource> getChild(CharSequence name) {
		File f = new File(file, name.toString());
		if (f.isFile()) return Completed.completed(new LocalFile(f, this));
		if (!f.exists()) return Completed.completedNull();
		return Completed.completed(new LocalFolder(f, this));
	}

	@Override
	public FutureSupplier<VirtualFile> createFile(CharSequence name) {
		try {
			File f = new File(file, name.toString());
			//noinspection ResultOfMethodCallIgnored
			f.createNewFile();
			return Completed.completed(new LocalFile(f, this));
		} catch (Throwable ex) {
			return failed(ex);
		}
	}

	@Override
	public FutureSupplier<VirtualFolder> createFolder(CharSequence name) {
		try {
			File f = new File(file, name.toString());
			FileUtils.mkdirs(f);
			return Completed.completed(new LocalFolder(f, this));
		} catch (Throwable ex) {
			return failed(ex);
		}
	}

	@Override
	public FutureSupplier<VirtualFile> createTempFile(CharSequence prefix, CharSequence suffix) {
		try {
			File f = File.createTempFile(prefix.toString(), suffix.toString(), file);
			return Completed.completed(new LocalFile(f, this));
		} catch (IOException ex) {
			return failed(ex);
		}
	}
}
