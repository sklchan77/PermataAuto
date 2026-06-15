package my.app.utils.vfs.local;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import my.app.utils.vfs.VfsTest;
import my.app.utils.vfs.VirtualFileSystem;

/**
 * @author sklchan77
 */
public class LocalFileSystemTest extends VfsTest {
	@Override
	protected VirtualFileSystem vfsCreate(File rootDir, String... roots) {
		return new LocalFileSystem(() -> {
			List<File> list = new ArrayList<>(roots.length);
			for (String r : roots) {
				list.add(new File(rootDir, r));
			}
			return list;
		});
	}
}
