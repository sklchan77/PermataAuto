package my.app.permata.vfs.smb;

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
import my.app.utils.ui.activity.AppActivity;
import my.app.utils.vfs.VirtualFileSystem;
import my.app.utils.vfs.VirtualFolder;
import my.app.utils.vfs.VirtualResource;
import my.app.utils.vfs.smb.SmbFileSystem;

/**
 * @author sklchan77
 */
public class Provider extends VfsProviderBase {
	private final Pref<Supplier<String>> HOST = Pref.s("HOST");
	private final Pref<IntSupplier> PORT = Pref.i("PORT", 445);
	private final Pref<Supplier<String>> SHARE = Pref.s("SHARE");
	private final Pref<Supplier<String>> DOMAIN = Pref.s("DOMAIN");
	private final Pref<Supplier<String>> USER = Pref.s("USER");
	private final Pref<Supplier<String>> PASSWD = Pref.s("PASSWD");

	@Override
	public FutureSupplier<? extends VirtualFileSystem> createFileSystem(
			Context ctx, Supplier<FutureSupplier<? extends AppActivity>> activitySupplier, PreferenceStore ps) {
		return SmbFileSystem.Provider.getInstance().createFileSystem(ps);
	}

	@Override
	protected FutureSupplier<? extends VirtualFolder> addFolder(MainActivityDelegate a, VirtualFileSystem fs) {
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
			o.pref = SHARE;
			o.title = my.app.permata.R.string.share_name;
			o.stringHint = "share";
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = DOMAIN;
			o.title = my.app.permata.R.string.domain;
			o.stringHint = "WORKGROUP";
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = USER;
			o.title = my.app.permata.R.string.username;
			o.stringHint = "Guest";
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = PASSWD;
			o.title = my.app.permata.R.string.password;
			o.stringHint = "secret";
		});

		return requestPrefs(a, prefs, ps).thenRun(ps::removeBroadcastListeners).then(ok -> {
			if (!ok) return completedNull();

			String user = ps.getStringPref(USER);

			if (user != null) {
				String domain = ps.getStringPref(DOMAIN);
				if (domain != null) user = domain + ';' + user;
			}

			return ((SmbFileSystem) fs).addRoot(
					user,
					ps.getStringPref(HOST),
					ps.getIntPref(PORT),
					'/' + ps.getStringPref(SHARE),
					ps.getStringPref(PASSWD),
					null, null);
		});
	}

	@Override
	protected FutureSupplier<Void> removeFolder(MainActivityDelegate a, VirtualFileSystem fs, VirtualResource folder) {
		((SmbFileSystem) fs).removeRoot((VirtualFolder) folder);
		return completedVoid();
	}

	@Override
	protected boolean validate(PreferenceStore ps) {
		return allSet(ps, HOST, SHARE);
	}

	private static final class PrefsHolder extends BasicPreferenceStore {
		static final PrefsHolder instance = new PrefsHolder();
	}
}
