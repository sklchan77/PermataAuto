package my.app.permata.vfs;

import android.content.Context;

import java.util.List;

import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.async.FutureSupplier;
import my.app.utils.function.Supplier;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.ui.activity.AppActivity;
import my.app.utils.vfs.VirtualFileSystem;
import my.app.utils.vfs.VirtualFolder;
import my.app.utils.vfs.VirtualResource;

/**
 * @author sklchan77
 */
public interface VfsProvider {

	FutureSupplier<? extends VirtualFileSystem> createFileSystem(
			Context ctx, Supplier<FutureSupplier<? extends AppActivity>> activitySupplier,
			PreferenceStore ps);

	FutureSupplier<? extends VirtualResource> select(MainActivityDelegate a, List<? extends VirtualFileSystem> fs);
}
