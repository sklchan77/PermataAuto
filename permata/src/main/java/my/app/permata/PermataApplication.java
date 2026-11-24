package my.app.permata;

import static my.app.permata.ui.activity.MainActivityPrefs.LOCALE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;
import android.os.LocaleList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.io.File;
import java.util.Locale;

import my.app.permata.addon.AddonManager;
import my.app.permata.media.engine.BitmapCache;
import my.app.permata.ui.activity.MainActivityPrefs;
import my.app.permata.vfs.PermataVfsManager;
import my.app.utils.app.App;
import my.app.utils.app.NetSplitCompatApp;
import my.app.utils.log.Log;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.SharedPreferenceStore;
import my.app.utils.ui.activity.ActivityDelegate;

/**
 * @author sklchan77
 */
public class PermataApplication extends NetSplitCompatApp {
	private PermataVfsManager vfsManager;
	private BitmapCache bitmapCache;
	private volatile SharedPreferenceStore preferenceStore;
	private volatile AddonManager addonManager;
	private int mirroringMode;
	private ServiceConnection eventService;

	// === [GLOBAL MEDIA CROWN] ===
	public static final int CROWN_IPTV = 0;
	public static final int CROWN_WEB = 1;
	public static final int CROWN_NATIVE = 2;
	private static volatile int activeMediaCrown = CROWN_IPTV;

	public static void setMediaCrown(int crownOwner) {
		activeMediaCrown = crownOwner;
		Log.i("[ROUTING_TELEMETRY]", "Media Crown transferred to: " +
				(crownOwner == CROWN_IPTV ? "IPTV" : crownOwner == CROWN_WEB ? "WEB" : "NATIVE"));
	}

	public static int getMediaCrown() {
		return activeMediaCrown;
	}
	// ============================

	public static PermataApplication get() {
		return App.get();
	}

	public static Context applyLocale(Context ctx, Locale loc) {
		Locale.setDefault(loc);
		Configuration cfg = new Configuration(ctx.getResources().getConfiguration());
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			LocaleList list = new LocaleList(loc);
			LocaleList.setDefault(list);
			cfg.setLocales(list);
		} else {
			cfg.setLocale(loc);
		}
		cfg.setLayoutDirection(loc);
		return ctx.createConfigurationContext(cfg);
	}

	@SuppressWarnings("deprecation")
	public void updateAppLocale(Locale loc) {
		Locale.setDefault(loc);
		Resources res = getResources();
		Configuration cfg = new Configuration(res.getConfiguration());
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			LocaleList list = new LocaleList(loc);
			LocaleList.setDefault(list);
			cfg.setLocales(list);
		} else {
			cfg.setLocale(loc);
		}
		cfg.setLayoutDirection(loc);
		res.updateConfiguration(cfg, res.getDisplayMetrics());

		LocaleListCompat locales = (loc != null)
				? LocaleListCompat.forLanguageTags(loc.toLanguageTag())
				: LocaleListCompat.getEmptyLocaleList();
		AppCompatDelegate.setApplicationLocales(locales);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		var ps = getPreferenceStore();
		if (ps.hasPref(LOCALE)) {
			var loc = MainActivityPrefs.Lang.get(ps.getIntPref(LOCALE)).locale;
			if (loc != null) {
				AppCompatDelegate.setApplicationLocales(
						LocaleListCompat.forLanguageTags(loc.toLanguageTag()));
			}
		}
		vfsManager = new PermataVfsManager();
		bitmapCache = new BitmapCache();
	}

	@Override
	protected void attachBaseContext(Context ctx) {
		var ps = SharedPreferenceStore.create(ctx.getSharedPreferences("permata", MODE_PRIVATE));
		if (ps.hasPref(LOCALE)) {
			var loc = MainActivityPrefs.Lang.get(ps.getIntPref(LOCALE)).locale;
			ctx = applyLocale(ctx, loc);
		} else {
			preferenceStore = ps;
		}
		super.attachBaseContext(ctx);
	}

	public boolean isConnectedToAuto() {
		return BuildConfig.AUTO && ActivityDelegate.getContextToDelegate() != null;
	}

	public PermataVfsManager getVfsManager() {
		return vfsManager;
	}

	public BitmapCache getBitmapCache() {
		return bitmapCache;
	}

	public PreferenceStore getPreferenceStore() {
		SharedPreferenceStore ps = preferenceStore;

		if (ps == null) {
			synchronized (this) {
				if ((ps = preferenceStore) == null) {
					preferenceStore =
							ps = SharedPreferenceStore.create(getSharedPreferences("permata", MODE_PRIVATE));
				}
			}
		}

		return ps;
	}

	public SharedPreferences getDefaultSharedPreferences() {
		return ((SharedPreferenceStore) getPreferenceStore()).getSharedPreferences();
	}

	public AddonManager getAddonManager() {
		AddonManager mgr = addonManager;

		if (mgr == null) {
			synchronized (this) {
				if ((mgr = addonManager) == null) {
					addonManager = mgr = new AddonManager(getPreferenceStore());
				}
			}
		}

		return mgr;
	}

	@Override
	protected int getMaxNumberOfThreads() {
		return 5;
	}

	@NonNull
	@Override
	public File getLogFile() {
		File dir = getExternalFilesDir(null);
		if (dir == null) dir = getFilesDir();
		return new File(dir, "Permata.log");
	}

	@Nullable
	@Override
	public String getCrashReportEmail() {
		return "andrey.a.pavlenko@gmail.com";
	}

	public boolean isMirroringMode() {
		return BuildConfig.AUTO && getMirroringMode() != 0;
	}

	public boolean isMirroringLandscape() {
		return BuildConfig.AUTO && getMirroringMode() == 1;
	}

	public int getMirroringMode() {
		return mirroringMode;
	}

	public void setMirroringMode(int mirroringMode) {
		if (!BuildConfig.AUTO) return;
		this.mirroringMode = mirroringMode;

		if (mirroringMode == 0) {
			if (eventService != null) {
				unbindService(eventService);
				eventService = null;
			}
		} else if (eventService == null) {
			eventService = new ServiceConnection() {
				@Override
				public void onServiceConnected(ComponentName name, IBinder service) {
					Log.d("Connected to XposedEventDispatcherService");
				}

				@Override
				public void onServiceDisconnected(ComponentName name) {
					Log.d("Disconnected from XposedEventDispatcherService");
				}
			};
			try {
				Log.i("Starting XposedEventDispatcherService");
				var i = new Intent();
				i.setComponent(
						new ComponentName(this, "my.app.permata.auto" + ".XposedEventDispatcherService"));
				bindService(i, eventService, Context.BIND_AUTO_CREATE);
			} catch (Exception err) {
				eventService = null;
				Log.e(err, "Failed to bind EventDispatcherService");
			}
		}
	}
}