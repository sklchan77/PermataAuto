package my.app.permata.vfs;

import static my.app.permata.BuildConfig.ENABLE_GS;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.completedNull;
import static my.app.utils.async.Completed.failed;

import android.content.Context;

import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;

import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.permata.ui.activity.MainActivity;
import my.app.permata.vfs.m3u.M3uFileSystem;
import my.app.permata.vfs.m3u.M3uFileSystemProvider;
import my.app.utils.async.FutureSupplier;
import my.app.utils.async.Promise;
import my.app.utils.function.BooleanSupplier;
import my.app.utils.log.Log;
import my.app.utils.module.DynamicModuleInstaller;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.PreferenceStore.Pref;
import my.app.utils.ui.activity.ActivityBase;
import my.app.utils.vfs.VfsException;
import my.app.utils.vfs.VfsManager;
import my.app.utils.vfs.VirtualFileSystem;
import my.app.utils.vfs.content.ContentFileSystem;
import my.app.utils.vfs.generic.GenericFileSystem;
import my.app.utils.vfs.local.LocalFileSystem;

/**
 * @author sklchan77
 */
public class PermataVfsManager extends VfsManager {
	public static final String GDRIVE_ID = "gdrive";
	public static final String SFTP_ID = "sftp";
	public static final String SMB_ID = "smb";
	public static final String M3U_ID = "m3u";
	private static final String CHANNEL_ID = "permata.vfs.install";
	private static final Pref<BooleanSupplier> ENABLE_GDRIVE = Pref.b("ENABLE_GDRIVE", false);
	private static final Pref<BooleanSupplier> ENABLE_SFTP = Pref.b("ENABLE_SFTP", false);
	private static final Pref<BooleanSupplier> ENABLE_SMB = Pref.b("ENABLE_SMB", false);
	private static final String GDRIVE_CLASS = "my.app.permata.vfs.gdrive.Provider";
	private static final String SFTP_CLASS = "my.app.permata.vfs.sftp.Provider";
	private static final String SMB_CLASS = "my.app.permata.vfs.smb.Provider";

	public PermataVfsManager() {
		super(filesystems());

		PreferenceStore ps = PermataApplication.get().getPreferenceStore();
		if (ENABLE_GS) initProvider(ps, ENABLE_GDRIVE, GDRIVE_ID);
		initProvider(ps, ENABLE_SFTP, SFTP_ID);
		initProvider(ps, ENABLE_SMB, SMB_ID);
	}

	public FutureSupplier<VfsProvider> getProvider(String scheme) {
		switch (scheme) {
			case GDRIVE_ID:
				return ENABLE_GS
						? getProvider(scheme, ENABLE_GDRIVE, GDRIVE_CLASS, GDRIVE_ID, R.string.vfs_gdrive)
						: completedNull();
			case SFTP_ID:
				return getProvider(scheme, ENABLE_SFTP, SFTP_CLASS, SFTP_ID, R.string.vfs_sftp);
			case SMB_ID:
				return getProvider(scheme, ENABLE_SMB, SMB_CLASS, SMB_ID, R.string.vfs_smb);
			case M3U_ID:
				return completed(new M3uFileSystemProvider());
			default:
				return completedNull();
		}
	}

	private static FutureSupplier<MainActivity> getActivity(Context ctx, @StringRes int moduleName) {
		String name = ctx.getString(moduleName);
		String title = ctx.getString(R.string.module_installation, name);
		return ActivityBase.create(ctx, CHANNEL_ID, title, R.drawable.notification,
				title, null, MainActivity.class);
	}

	private static List<VirtualFileSystem> filesystems() {
		PermataApplication app = PermataApplication.get();
		PreferenceStore ps = app.getPreferenceStore();
		List<VirtualFileSystem> p = new ArrayList<>(7);
		p.add(LocalFileSystem.Provider.getInstance().createFileSystem(ps).getOrThrow());
		p.add(GenericFileSystem.Provider.getInstance().createFileSystem(ps).getOrThrow());
		p.add(ContentFileSystem.Provider.getInstance().createFileSystem(ps).getOrThrow());
		p.add(M3uFileSystem.Provider.getInstance().createFileSystem(ps).getOrThrow());
		if (ENABLE_GS)
			addFileSystem(p, ps, ENABLE_GDRIVE, app, GDRIVE_CLASS, GDRIVE_ID, R.string.vfs_gdrive);
		addFileSystem(p, ps, ENABLE_SFTP, app, SFTP_CLASS, SFTP_ID, R.string.vfs_sftp);
		addFileSystem(p, ps, ENABLE_SMB, app, SMB_CLASS, SMB_ID, R.string.vfs_smb);
		return p;
	}

	private static void addFileSystem(
			List<VirtualFileSystem> fileSystems, PreferenceStore ps,
			Pref<BooleanSupplier> p, Context ctx, String className,
			String moduleId, @StringRes int moduleName) {
		if (!ps.getBooleanPref(p)) return;
		VfsProvider provider = loadProvider(className, moduleId);

		if (provider != null) {
			FutureSupplier<? extends VirtualFileSystem> f = provider
					.createFileSystem(ctx, () -> getActivity(ctx, moduleName), ps);
			if (f.isDone() && !f.isFailed()) fileSystems.add(f.getOrThrow());
		}
	}

	private static VfsProvider loadProvider(String className, String moduleId) {
		try {
			return (VfsProvider) Class.forName(className).newInstance();
		} catch (Throwable ex) {
			Log.e("Failed to load module ", moduleId);
			return null;
		}
	}

	private void initProvider(PreferenceStore ps, Pref<BooleanSupplier> p, String scheme) {
		if (!ps.getBooleanPref(p) || isSupportedScheme(scheme)) return;
		getProvider(scheme).onFailure(fail -> Log.e(fail, "Failed to initiate provider ", scheme));
	}

	private FutureSupplier<VfsProvider> getProvider(
			String scheme, Pref<BooleanSupplier> pref,
			String className, String moduleId, @StringRes int moduleName) {
		VfsProvider p = loadProvider(className, moduleId);

		if (p != null) {
			if (isSupportedScheme(scheme)) return completed(p);
			PermataApplication.get().getPreferenceStore().applyBooleanPref(pref, true);
			return addProvider(p, moduleName).map(fs -> p);
		} else {
			return installModule(className, moduleId, moduleName, pref)
					.then(provider -> addProvider(provider, moduleName).map(fs -> provider));
		}
	}

	private FutureSupplier<? extends VirtualFileSystem> addProvider(VfsProvider p, @StringRes int moduleName) {
		PermataApplication app = PermataApplication.get();
		return p.createFileSystem(app, () -> getActivity(app, moduleName), app.getPreferenceStore())
				.onSuccess(this::mount);
	}

	private FutureSupplier<VfsProvider> installModule(
			String className, String moduleId, @StringRes int moduleName, Pref<BooleanSupplier> pref) {
		PermataApplication app = PermataApplication.get();
		return getActivity(app, moduleName).then(a -> {
			String name = a.getString(moduleName);
			String title = a.getString(R.string.module_installation, name);
			DynamicModuleInstaller i = new DynamicModuleInstaller(a);
			i.setSmallIcon(R.drawable.notification);
			i.setTitle(a.getString(R.string.install_pending, name));
			i.setNotificationChannel(CHANNEL_ID, title);
			i.setPendingMessage(a.getString(R.string.install_pending, name));
			i.setDownloadingMessage(a.getString(R.string.downloading, name));
			i.setInstallingMessage(a.getString(R.string.installing, name));

			Promise<VfsProvider> contentLoading = new Promise<>();
			FutureSupplier<VfsProvider> install = i.install(moduleId).main().then(v -> {
				VfsProvider p = loadProvider(className, moduleId);

				if (p != null) {
					app.getPreferenceStore().applyBooleanPref(pref, true);
					return completed(p);
				} else {
					return failed(new VfsException("Failed to install module " + moduleId));
				}
			}).thenComplete(contentLoading);

			a.getActivityDelegate().onSuccess(d -> d.setContentLoading(contentLoading));
			return install;
		});
	}
}
