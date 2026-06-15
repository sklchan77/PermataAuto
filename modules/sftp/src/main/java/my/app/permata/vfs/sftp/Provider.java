package my.app.permata.vfs.sftp;

import static my.app.utils.async.Completed.completedNull;
import static my.app.utils.async.Completed.completedVoid;

import android.content.Context;

import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.vfs.VfsProviderBase;
import my.app.utils.async.FutureSupplier;
import my.app.utils.function.IntSupplier;
import my.app.utils.function.Supplier;
import my.app.utils.pref.BasicPreferenceStore;
import my.app.utils.pref.PreferenceSet;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.PreferenceStore.Pref;
import my.app.utils.text.TextUtils;
import my.app.utils.ui.activity.AppActivity;
import my.app.utils.ui.fragment.FilePickerFragment;
import my.app.utils.vfs.VirtualFileSystem;
import my.app.utils.vfs.VirtualFolder;
import my.app.utils.vfs.VirtualResource;
import my.app.utils.vfs.sftp.SftpFileSystem;

/**
 * @author sklchan77
 */
public class Provider extends VfsProviderBase {
	private final Pref<Supplier<String>> HOST = Pref.s("HOST");
	private final Pref<IntSupplier> PORT = Pref.i("PORT", 22);
	private final Pref<Supplier<String>> PATH = Pref.s("PATH");
	private final Pref<Supplier<String>> USER = Pref.s("USER");
	private final Pref<Supplier<String>> PASSWD = Pref.s("PASSWD");
	private final Pref<Supplier<String>> KEY = Pref.s("KEY");
	private final Pref<Supplier<String>> KEY_PASSWD = Pref.s("KEY_PASSWD");

	@Override
	public FutureSupplier<? extends VirtualFileSystem> createFileSystem(
			Context ctx, Supplier<FutureSupplier<? extends AppActivity>> activitySupplier, PreferenceStore ps) {
		return SftpFileSystem.Provider.getInstance().createFileSystem(ps);
	}

	@Override
	protected FutureSupplier<? extends VirtualResource> addFolder(MainActivityDelegate a, VirtualFileSystem fs) {
		PreferenceSet prefs = new PreferenceSet();
		PreferenceStore ps = PrefsHolder.instance;

		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = HOST;
			o.title = my.app.permata.R.string.host;
			o.stringHint = "localhost";
		});
		prefs.addIntPref(o -> {
			o.store = ps;
			o.pref = PORT;
			o.title = my.app.permata.R.string.port;
			o.showProgress = false;
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = PATH;
			o.title = my.app.permata.R.string.path;
			o.stringHint = "/home/user";
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = USER;
			o.stringHint = "root";
			o.title = my.app.permata.R.string.username;
		});
		prefs.addFilePref(o -> {
			o.store = ps;
			o.pref = KEY;
			o.title = my.app.permata.R.string.private_key;
			o.mode = FilePickerFragment.FILE;
			o.stringHint = "/sdcard/.ssh/id_rsa";
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = PASSWD;
			o.title = my.app.permata.R.string.password;
			o.stringHint = "secret";
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = KEY_PASSWD;
			o.title = my.app.permata.R.string.private_key_pass;
			o.stringHint = "secret";
		});

		return requestPrefs(a, prefs, ps).thenRun(ps::removeBroadcastListeners)
				.then(ok -> !ok ? completedNull() : ((SftpFileSystem) fs).addRoot(
						ps.getStringPref(USER).trim(),
						ps.getStringPref(HOST).trim(),
						ps.getIntPref(PORT),
						TextUtils.trim(ps.getStringPref(PATH)),
						TextUtils.trim(ps.getStringPref(PASSWD)),
						TextUtils.trim(ps.getStringPref(KEY)),
						TextUtils.trim(ps.getStringPref(KEY_PASSWD))));
	}

	@Override
	protected FutureSupplier<Void> removeFolder(MainActivityDelegate a, VirtualFileSystem fs, VirtualResource folder) {
		((SftpFileSystem) fs).removeRoot((VirtualFolder) folder);
		return completedVoid();
	}

	@Override
	protected boolean validate(PreferenceStore ps) {
		return allSet(ps, HOST, USER) && (anySet(ps, KEY, PASSWD));
	}

	private static final class PrefsHolder extends BasicPreferenceStore {
		static final PrefsHolder instance = new PrefsHolder();
	}
}
