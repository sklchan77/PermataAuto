package my.app.permata;

import static my.app.permata.ui.activity.MainActivityPrefs.LOCALE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

	public static PermataApplication get() {
		return App.get();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		vfsManager = new PermataVfsManager();
		bitmapCache = new BitmapCache();
	}

	@Override
	protected void attachBaseContext(Context ctx) {
		var ps = SharedPreferenceStore.create(ctx.getSharedPreferences("permata", MODE_PRIVATE));
		if (ps.hasPref(LOCALE)) {
			var loc = MainActivityPrefs.Lang.get(ps.getIntPref(LOCALE)).locale;
			var cfg = ctx.getResources().getConfiguration();
			cfg.setLocale(loc);
			Locale.setDefault(loc);
			ctx = ctx.createConfigurationContext(cfg);
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
