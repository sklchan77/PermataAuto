package my.app.permata.addon.felex;

import static android.speech.RecognitionService.SERVICE_INTERFACE;
import static java.util.Objects.requireNonNull;
import static my.app.permata.addon.felex.dict.DictMgr.DICT_EXT;
import static my.app.permata.util.Utils.getAddonsCacheDir;
import static my.app.permata.util.Utils.getAddonsFileDir;
import static my.app.permata.util.Utils.isExternalStorageManager;
import static my.app.permata.util.Utils.isSafSupported;
import static my.app.utils.io.FileUtils.getFileExtension;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

import my.app.permata.PermataApplication;
import my.app.permata.addon.AddonInfo;
import my.app.permata.addon.PermataAddon;
import my.app.permata.addon.PermataContentAddon;
import my.app.permata.addon.MediaLibAddon;
import my.app.permata.addon.felex.dict.DictInfo;
import my.app.permata.addon.felex.media.FelexItem;
import my.app.permata.addon.felex.view.FelexFragment;
import my.app.permata.media.lib.DefaultMediaLib;
import my.app.permata.media.lib.MediaLib;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.async.FutureSupplier;
import my.app.utils.function.BooleanSupplier;
import my.app.utils.function.IntSupplier;
import my.app.utils.function.Supplier;
import my.app.utils.io.FileUtils;
import my.app.utils.log.Log;
import my.app.utils.misc.ChangeableCondition;
import my.app.utils.pref.PreferenceSet;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.PreferenceStore.Pref;
import my.app.utils.ui.fragment.ActivityFragment;
import my.app.utils.ui.fragment.FilePickerFragment;
import my.app.utils.vfs.VirtualFolder;

/**
 * @author sklchan77
 */
@Keep
@SuppressWarnings("unused")
public class FelexAddon implements MediaLibAddon, PermataContentAddon {
	private static final AddonInfo info = PermataAddon.findAddonInfo(FelexAddon.class.getName());
	public static final Pref<Supplier<String>> DICT_FOLDER =
			Pref.s("FELEX_DICT_FOLDER", () -> getAddonsFileDir(info).getAbsolutePath());
	public static final Pref<Supplier<String>> CACHE_FOLDER =
			Pref.s("FELEX_CACHE_FOLDER", () -> getAddonsCacheDir(info).getAbsolutePath());
	public static final Pref<BooleanSupplier> OFFLINE_MODE = Pref.b("FELEX_OFFLINE_MODE", false);
	public static final Pref<IntSupplier> STT_SERVICE = Pref.i("FELEX_STT_SERVICE", 0);

	public static FelexAddon get() {
		return PermataApplication.get().getAddonManager().getAddon(FelexAddon.class);
	}

	@IdRes
	@Override
	public int getAddonId() {
		return my.app.permata.R.id.felex_fragment;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@Override
	public boolean isSupportedItem(MediaLib.Item i) {
		return i instanceof FelexItem;
	}

	@Override
	public MediaLib.Item getRootItem(DefaultMediaLib lib) {
		return new FelexItem.Root(lib);
	}

	@Nullable
	@Override
	public FutureSupplier<? extends MediaLib.Item> getItem(DefaultMediaLib lib,
																												 @Nullable String scheme, String id) {
		return FelexItem.getItem(lib, scheme, id);
	}

	@Override
	public boolean handleIntent(MainActivityDelegate a, Intent intent) {
		Uri u = intent.getData();
		if (u == null) return false;

		String s = u.getScheme();
		if ((s == null) || (!s.equals("file") && !s.equals("content"))) return false;

		String ext = FileUtils.getFileExtension(u.getPath());

		if ((ext != null) && DICT_EXT.regionMatches(1, ext, 0, ext.length())) {
			a.showFragment(getFragmentId(), u);
			return true;
		}

		try (InputStream in = a.getContext().getContentResolver().openInputStream(u)) {
			if (DictInfo.read(in) == null) return false;
		} catch (IOException ex) {
			Log.d(ex, "Failed to read uri: ", u);
			return false;
		}

		a.showFragment(getFragmentId(), u);
		return true;
	}

	@Nullable
	@Override
	public String getFileType(Uri uri, String displayName) {
		if (displayName == null) displayName = uri.getPath();
		String ext = getFileExtension(displayName);
		if ((ext != null) && DICT_EXT.regionMatches(1, ext, 0, ext.length())) return "text/plain";
		return PermataContentAddon.super.getFileType(uri, displayName);

	}

	public FutureSupplier<VirtualFolder> getDictFolder() {
		return getFolder(DICT_FOLDER);
	}

	public FutureSupplier<VirtualFolder> getCacheFolder() {
		return getFolder(CACHE_FOLDER);
	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	private FutureSupplier<VirtualFolder> getFolder(Pref<Supplier<String>> p) {
		String uri = getPreferenceStore().getStringPref(p);
		if (uri.startsWith("/")) {
			if (p.getDefaultValue().get().equals(uri)) new File(uri).mkdirs();
			uri = "file:/" + uri;
		}
		return PermataApplication.get().getVfsManager().getFolder(uri);
	}

	public boolean isOfflineMode() {
		return getPreferenceStore().getBooleanPref(OFFLINE_MODE);
	}

	public void setOfflineMode(boolean offline) {
		getPreferenceStore().applyBooleanPref(OFFLINE_MODE, offline);
	}

	@Nullable
	public ComponentName getSttServiceName() {
		var services = getSttServices();
		var idx = getPreferenceStore().getIntPref(STT_SERVICE);
		if (idx >= services.size()) return null;
		var name = services.get(services.keySet().toArray(new String[0])[idx]);
		return ComponentName.unflattenFromString(requireNonNull(name));

	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new FelexFragment();
	}

	@Override
	public void contributeSettings(Context ctx, PreferenceStore store, PreferenceSet set,
																 ChangeableCondition visibility) {
		contributeFolder(DICT_FOLDER, R.string.dict_folder, set, visibility);
		contributeFolder(CACHE_FOLDER, R.string.cache_folder, set, visibility);
		set.addListPref(o -> {
			var services = getSttServices();
			var names = services.keySet().toArray(new String[0]);
			if (store.getIntPref(STT_SERVICE) >= names.length) store.applyIntPref(STT_SERVICE, 0);
			o.store = store;
			o.pref = STT_SERVICE;
			o.stringValues = names;
			o.title = R.string.stt_service;
			o.subtitle = my.app.permata.R.string.string_format;
			o.formatSubtitle = true;
			o.visibility = visibility;
		});
	}

	private void contributeFolder(Pref<Supplier<String>> p, int title, PreferenceSet set,
																ChangeableCondition visibility) {
		set.addFilePref(o -> {
			o.pref = p;
			o.store = getPreferenceStore();
			o.mode = FilePickerFragment.FOLDER | FilePickerFragment.WRITABLE;
			o.title = title;
			o.visibility = visibility;
			o.trim = true;
			o.removeBlank = true;
			o.useSaf = !isExternalStorageManager() && isSafSupported(null);
		});
	}

	private PreferenceStore getPreferenceStore() {
		return PermataApplication.get().getPreferenceStore();
	}

	private static Map<String, String> getSttServices() {
		var services = new TreeMap<String, String>();
		services.put("", "");
		var mgr = PermataApplication.get().getPackageManager();
		for (var service : mgr.queryIntentServices(new Intent(SERVICE_INTERFACE), 0)) {
			services.put(service.loadLabel(mgr).toString(),
					service.serviceInfo.packageName + "/" + service.serviceInfo.name);
		}
		return services;
	}
}
