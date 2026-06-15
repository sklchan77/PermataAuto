package my.app.permata.vfs.gdrive;

import android.content.Context;

import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.vfs.VfsProviderBase;
import my.app.utils.async.FutureSupplier;
import my.app.utils.function.Supplier;
import my.app.utils.pref.BasicPreferenceStore;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.ui.activity.AppActivity;
import my.app.utils.vfs.VirtualFileSystem;
import my.app.utils.vfs.VirtualResource;
import my.app.utils.vfs.gdrive.GdriveFileSystem;

import static my.app.utils.async.Completed.completedNull;
import static my.app.utils.async.Completed.completedVoid;
import static my.app.utils.vfs.gdrive.GdriveFileSystem.Provider.GOOGLE_TOKEN;

/**
 * @author sklchan77
 */
public class Provider extends VfsProviderBase {

	@Override
	public FutureSupplier<VirtualFileSystem> createFileSystem(
			Context ctx, Supplier<FutureSupplier<? extends AppActivity>> activitySupplier, PreferenceStore ps) {
		BasicPreferenceStore store = new BasicPreferenceStore();
		store.applyStringPref(GOOGLE_TOKEN, ctx.getString(my.app.permata.R.string.default_web_client_id));
		return new GdriveFileSystem.Provider(activitySupplier).createFileSystem(store);
	}

	@Override
	protected boolean addRemoveSupported() {
		return false;
	}

	@Override
	protected FutureSupplier<? extends VirtualResource> addFolder(MainActivityDelegate a, VirtualFileSystem fs) {
		return completedNull();
	}

	@Override
	protected FutureSupplier<Void> removeFolder(MainActivityDelegate a, VirtualFileSystem fs, VirtualResource folder) {
		return completedVoid();
	}
}
