package my.app.permata.ui.activity;

import static android.media.AudioManager.ADJUST_LOWER;
import static android.media.AudioManager.ADJUST_RAISE;
import static android.media.AudioManager.FLAG_SHOW_UI;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.os.Build.VERSION.SDK_INT;
import static android.view.InputDevice.SOURCE_CLASS_POINTER;
import static android.view.MotionEvent.ACTION_SCROLL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MINUTES;
import static my.app.permata.util.Utils.createDownloader;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.completedNull;
import static my.app.utils.async.Completed.completedVoid;
import static my.app.utils.async.Completed.failed;
import static my.app.utils.misc.MiscUtils.isPackageInstalled;
import static my.app.utils.pref.PreferenceStore.Pref.sa;
import static my.app.utils.ui.UiUtils.showAlert;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.FileProvider;

import com.google.android.play.core.splitcompat.SplitCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import my.app.permata.BuildConfig;
import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.permata.addon.AddonInfo;
import my.app.permata.addon.AddonManager;
import my.app.permata.media.service.PermataMediaServiceConnection;
import my.app.utils.app.App;
import my.app.utils.async.FutureSupplier;
import my.app.utils.collection.NaturalOrderComparator;
import my.app.utils.function.Supplier;
import my.app.utils.log.Log;
import my.app.utils.net.http.HttpConnection;
import my.app.utils.net.http.HttpFileDownloader;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.PreferenceStore.Pref;
import my.app.utils.text.TextUtils;
import my.app.utils.ui.UiUtils;
import my.app.utils.ui.activity.AppActivity;
import my.app.utils.ui.activity.SplitCompatActivityBase;

public class MainActivity extends SplitCompatActivityBase
		implements PermataActivity, AddonManager.Listener {
	private static PermataMediaServiceConnection service;
	private static MainActivity activeInstance;

	@Nullable
	public static MainActivity getActiveInstance() {
		return activeInstance;
	}

	@Override
	protected FutureSupplier<MainActivityDelegate> createDelegate(AppActivity a) {
		PermataMediaServiceConnection s = service;

		if ((s != null) && s.isConnected()) {
			return completed(new MainActivityDelegate(a, service.createBinder()));
		}

		return PermataMediaServiceConnection.connect(a).map(c -> {
			assert service == null;
			service = c;
			return new MainActivityDelegate(a, service.createBinder());
		}).onFailure(err -> showAlert(getContext(), String.valueOf(err)));
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(MainActivityDelegate.attachBaseContext(base));
	}

	@Override
	public void finish() {
		PermataMediaServiceConnection s = service;
		service = null;
		if (s != null) s.disconnect();
		super.finish();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		MainActivityDelegate.setTheme(this,
				isCarActivity() || PermataApplication.get().isMirroringMode());
		AddonManager.get().addBroadcastListener(this);
		getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				getActivityDelegate().onSuccess(MainActivityDelegate::onBackPressed);
			}
		});
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onDestroy() {
		AddonManager.get().removeBroadcastListener(this);
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		super.onResume();
		activeInstance = this;
	}

	@Override
	protected void onPause() {
		super.onPause();
		activeInstance = null;
	}

	@Override
	public boolean isCarActivity() {
		return false;
	}

	@SuppressWarnings("unchecked")
	@NonNull
	@Override
	public FutureSupplier<MainActivityDelegate> getActivityDelegate() {
		return (FutureSupplier<MainActivityDelegate>) super.getActivityDelegate();
	}

	@Override
	public void onAddonChanged(AddonManager mgr, AddonInfo info, boolean installed) {
		SplitCompat.installActivity(this);
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (((event.getSource() & SOURCE_CLASS_POINTER) != 0) && (event.getAction() == ACTION_SCROLL)) {
			AudioManager amgr = (AudioManager) getContext().getSystemService(AUDIO_SERVICE);
			if (amgr == null) return false;
			float v = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
			amgr.adjustStreamVolume(STREAM_MUSIC, (v > 0) ? ADJUST_RAISE : ADJUST_LOWER, FLAG_SHOW_UI);
			return true;
		}

		return super.onGenericMotionEvent(event);
	}

	public FutureSupplier<?> uninstallControl() {
		if (!BuildConfig.AUTO) return completedVoid();

		var pkgName = "my.app.permata.auto.control.dear.google.why";
		if (!isPackageInstalled(this, pkgName)) return completedVoid();

		return startActivityForResult(() -> {
			var i = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + pkgName));
			i.putExtra(Intent.EXTRA_RETURN_RESULT, true);
			return i;
		});
	}

	public void checkUpdates() {
		if (!BuildConfig.AUTO) return;

		PreferenceStore ps = PermataApplication.get().getPreferenceStore();
		Pref<Supplier<String[]>> deletePref = sa("DELETE_ON_STARTUP", new String[0]);
		String[] delete = ps.getStringArrayPref(deletePref);

		if (delete.length != 0) {
			App.get().getScheduler().schedule(() -> {
				for (String f : delete) {
					//noinspection ResultOfMethodCallIgnored
					new File(f).delete();
				}

				synchronized (this) {
					List<String> l = new ArrayList<>(Arrays.asList(ps.getStringArrayPref(deletePref)));
					l.removeAll(Arrays.asList(delete));
					if (l.isEmpty()) ps.removePref(deletePref);
					else ps.applyStringArrayPref(deletePref, l.toArray(new String[0]));
				}
			}, 1, MINUTES);
		}

		String reqUrl = "https://api.github.com/repos/sklchan77/PermataAuto/releases/latest";
		HttpConnection.connect(o -> o.url(reqUrl), (resp, err) -> {
			if (err != null) {
				Log.e(err, "Failed to check updates");
				return failed(err);
			}

			resp.getPayload((p, perr) -> {
				if (perr != null) {
					Log.e(perr, "Failed to read response");
					return completedNull();
				}

				try {
					JSONObject json = new JSONObject(TextUtils.toString(p, UTF_8));
					String tag = json.getString("tag_name");
					String[] res = new String[2];
					res[0] = tag;
					int idx = tag.indexOf('(');
					if (idx != -1) tag = tag.substring(0, idx);

					if (NaturalOrderComparator.compareNatural(BuildConfig.VERSION_NAME, tag.trim()) < 0) {
						Log.i("New version is available: ", res[0]);
						JSONArray assets = json.getJSONArray("assets");
						String ext = "armeabi".equals(Build.SUPPORTED_ABIS[0]) ? "-arm.apk" : "-arm64.apk";

						for (int i = 0, n = assets.length(); i < n; i++) {
							JSONObject asset = assets.getJSONObject(i);
							String name = asset.getString("name");
							if (name.endsWith(ext)) res[1] = asset.getString("browser_download_url");
						}

						return (res[1] != null) ? completed(res) : completedNull();
					} else {
						Log.i("The latest release version - ", res[0], ". Application is up to date");
						return completedNull();
					}
				} catch (Exception ex) {
					Log.e(ex, "Failed to parse response");
					return failed(ex);
				}
			}).main().onSuccess(res -> {
				if (res == null) return;
				UiUtils.showQuestion(getContext(), getString(R.string.update),
								getString(R.string.update_question, res[0]),
								AppCompatResources.getDrawable(getContext(), R.drawable.notification))
						.onSuccess(r -> update(res[1], ps, deletePref));
			});

			return completedVoid();
		});
	}

	private FutureSupplier<Void> update(String uri, PreferenceStore ps,
																			Pref<Supplier<String[]>> deletePref) {
		if (!BuildConfig.AUTO) return completedVoid();
		try {
			File tmp;
			File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

			if ((tmp = createTempFile(dir)) == null) {
				App app = App.get();
				File cache = app.getExternalCacheDir();
				if (cache == null) cache = app.getCacheDir();
				dir = new File(cache, "updates");
				//noinspection ResultOfMethodCallIgnored
				dir.mkdirs();
				if ((tmp = createTempFile(dir)) == null) {
					App.get()
							.run(() -> showAlert(this, "Update failed - unable to create a temporary " + "file"
							));
					return completedVoid();
				}
			}

			File f = tmp;

			synchronized (this) {
				List<String> l = new ArrayList<>(Arrays.asList(ps.getStringArrayPref(deletePref)));
				l.add(f.getAbsolutePath());
				ps.applyStringArrayPref(deletePref, l.toArray(new String[0]));
			}

			HttpFileDownloader d = createDownloader(getContext(), uri);
			return d.download(uri, f).then(s -> {
				Uri u = (SDK_INT >= Build.VERSION_CODES.N) ?
						FileProvider.getUriForFile(getApplicationContext(), getPackageName() + ".FileProvider",
								f) : Uri.fromFile(f);

				try {
					installApk(u, true);
				} catch (Exception ex) {
					Log.e(ex, "Update failed");
					App.get().run(() -> showAlert(this, "Update failed: " + ex.getLocalizedMessage()));
					return failed(ex);
				}

				return completedVoid();
			}).onFailure(err -> {
				Log.e(err, "Failed to download apk: ", uri);
				App.get().run(() -> showAlert(this, "Failed to download apk: " + uri));
			});
		} catch (Exception ex) {
			Log.e(ex, "Update failed");
			App.get().run(() -> showAlert(this, "Update failed: " + ex.getLocalizedMessage()));
			return failed(ex);
		}
	}

	private static File createTempFile(File dir) {
		try {
			if (dir == null) return null;
			return File.createTempFile("Permata-", ".apk", dir);
		} catch (Exception ex) {
			Log.e(ex, "Failed to create a temporary file in the directory ", dir);
			return null;
		}
	}
}
