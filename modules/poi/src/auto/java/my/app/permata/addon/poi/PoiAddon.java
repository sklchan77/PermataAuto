package my.app.permata.addon.poi;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import my.app.permata.PermataApplication;
import my.app.permata.addon.AddonInfo;
import my.app.permata.addon.PermataAddon;
import my.app.permata.ui.activity.MainActivity;
import my.app.utils.app.App;
import my.app.utils.async.FutureSupplier;
import my.app.utils.function.Supplier;
import my.app.utils.misc.ChangeableCondition;
import my.app.utils.pref.PreferenceSet;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.PreferenceStore.Pref;
import my.app.utils.ui.fragment.FilePickerFragment;

/**
 * @author sklchan77
 */
@Keep
@SuppressWarnings("unused")
public class PoiAddon implements PermataAddon {
	@NonNull
	private static final AddonInfo info = PermataAddon.findAddonInfo(PoiAddon.class.getName());
	private static final Pref<Supplier<String>> POI_DB_URL = Pref.s("POI_DB_URL", "");
	private FutureSupplier<Voyageur> voyageur;

	public PoiAddon() {
		var main = MainActivity.getActiveInstance();
		if (main != null) main.checkPermissions(ACCESS_FINE_LOCATION);
	}

	@IdRes
	@Override
	public int getAddonId() {
		return my.app.permata.R.id.poi_addon;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	public String getDbUrl() {
		return PermataApplication.get().getPreferenceStore().getStringPref(POI_DB_URL);
	}

	@Override
	public void contributeSettings(Context ctx, PreferenceStore ps, PreferenceSet set,
																 ChangeableCondition visibility) {
		set.addFilePref(o -> {
			o.store = ps;
			o.pref = POI_DB_URL;
			o.title = R.string.poi_file_or_url;
			o.mode = FilePickerFragment.FILE;
			o.visibility = visibility;
		});
	}

	@Override
	public void start() {
		stop();
		if (!getDbUrl().isEmpty()) {
			voyageur = Voyageur.start(App.get());
		}
	}

	@Override
	public void stop() {
		if (voyageur != null) {
			voyageur.onSuccess(Voyageur::stop);
			voyageur = null;
		}
	}
}
