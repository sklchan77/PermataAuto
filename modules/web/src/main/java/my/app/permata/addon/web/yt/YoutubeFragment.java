package my.app.permata.addon.web.yt;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import my.app.permata.BuildConfig;
import my.app.permata.addon.AddonManager;
import my.app.permata.addon.web.PermataChromeClient;
import my.app.permata.addon.web.PermataWebView;
import my.app.permata.addon.web.R;
import my.app.permata.addon.web.WebBrowserAddon;
import my.app.permata.addon.web.WebBrowserFragment;
import my.app.permata.media.engine.MediaEngine;
import my.app.permata.media.lib.MediaLib;
import my.app.permata.media.service.PermataServiceUiBinder;
import my.app.permata.media.service.MediaSessionCallback;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.view.VideoView;
import my.app.utils.function.LongSupplier;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.PreferenceStore.Pref;
import my.app.utils.pref.SharedPreferenceStore;
import my.app.utils.ui.view.ToolBarView;

/**
 * @author sklchan77
 */
@Keep
@SuppressWarnings("unused")
public class YoutubeFragment extends WebBrowserFragment implements PermataServiceUiBinder.Listener {
	private static final String DEFAULT_URL = "https://m.youtube.com";
	private static final Set<String> DEFAULT_URLS = new HashSet<>(Arrays.asList(DEFAULT_URL, DEFAULT_URL + '/'));
	private static final Pref<LongSupplier> RESUME_POS = Pref.l("YT_RESUME_POS", 0L);
	private boolean playOnResume;

	@Override
	public int getFragmentId() {
		return my.app.permata.R.id.youtube_fragment;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.youtube, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
		YoutubeAddon addon = AddonManager.get().getAddon(YoutubeAddon.class);
		if (addon == null) return;

		String url;
		boolean pause;

		if (state != null) {
			url = state.getString("url", DEFAULT_URL);
			pause = state.getBoolean("pause", false);
		} else {
			url = DEFAULT_URL;
			pause = false;
		}

		MainActivityDelegate.getActivityDelegate(view.getContext()).onSuccess(a -> {
			YoutubeWebView webView = a.findViewById(R.id.ytWebView);
			VideoView videoView = a.findViewById(R.id.ytVideoView);
			YoutubeWebClient webClient = new YoutubeWebClient();
			YoutubeChromeClient chromeClient = new YoutubeChromeClient(webView, videoView);
			webView.init(addon, webClient, chromeClient);
			registerListeners(a);
			webView.loadUrl(DEFAULT_URL);
			if (!DEFAULT_URL.equals(url)) a.post(() -> webView.loadUrl(url));
			a.postDelayed(() -> {
				PreferenceStore ps = addon.getPreferenceStore();
				long pos = ps.getLongPref(RESUME_POS);
				ps.removePref(RESUME_POS);
				MediaSessionCallback cb = a.getMediaSessionCallback();
				if (cb.getEngine() instanceof YoutubeMediaEngine) {
					if (pos > 0L) cb.onSeekTo(pos);
					if (pause) cb.onPause();
				}
			}, 3000L);
		});
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle state) {
		super.onSaveInstanceState(state);
		String url = getUrl();
		if (url != null) state.putString("url", url);
		WebBrowserAddon addon = getAddon();
		if (addon == null) return;
		MainActivityDelegate a = MainActivityDelegate.getActivityDelegate(getContext()).peek();
		if (a == null) return;

		SharedPreferenceStore ps = addon.getPreferenceStore();
		MediaSessionCallback cb = a.getMediaSessionCallback();
		MediaEngine eng = cb.getEngine();

		if (eng instanceof YoutubeMediaEngine) {
			state.putBoolean("pause", !cb.isPlaying());
			eng.getPosition().onSuccess(pos -> ps.applyLongPref(RESUME_POS, pos));
		} else {
			ps.removePref(RESUME_POS);
		}
	}

	@Override
	public void onDestroyView() {
		unregisterListeners(MainActivityDelegate.get(requireContext()));
		super.onDestroyView();
	}

	@Override
	protected void registerListeners(MainActivityDelegate a) {
		super.registerListeners(a);
		a.getMediaServiceBinder().addBroadcastListener(this);
	}

	protected void unregisterListeners(MainActivityDelegate a) {
		super.unregisterListeners(a);
		a.getMediaServiceBinder().removeBroadcastListener(this);
	}

	@Override
	public void onPause() {
		if (!BuildConfig.AUTO) {
			MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
				PermataServiceUiBinder b = a.getMediaServiceBinder();
				if (YoutubeMediaEngine.isYoutubeItem(b.getCurrentItem()) && b.isPlaying()) {
					b.getMediaSessionCallback().onPause();
					playOnResume = true;
				} else {
					playOnResume = false;
				}
			});
		}
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (BuildConfig.AUTO || !playOnResume) return;
		playOnResume = false;
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
			PermataServiceUiBinder b = a.getMediaServiceBinder();
			if (YoutubeMediaEngine.isYoutubeItem(b.getCurrentItem())) {
				b.getMediaSessionCallback().onPlay();
			}
		});
	}

	public void loadUrl(String url) {
		PermataWebView v = getWebView();
		if (v != null) v.loadUrl(url);
	}

	@Override
	public void onPlayableChanged(MediaLib.PlayableItem oldItem, MediaLib.PlayableItem newItem) {
		if (isHidden()) return;

		if (YoutubeMediaEngine.isYoutubeItem(newItem)) {
			PermataWebView v = getWebView();
			MainActivityDelegate a = MainActivityDelegate.get(getContext());
			if (v == null) return;

			PermataChromeClient chrome = v.getWebChromeClient();
			if (chrome == null) return;

			if (!DEFAULT_URLS.contains(getUrl())) chrome.enterFullScreen();
		} else if (YoutubeMediaEngine.isYoutubeItem(oldItem)) {
			PermataWebView v = getWebView();
			if (v == null) return;
			PermataChromeClient chrome = v.getWebChromeClient();
			if (chrome != null) chrome.exitFullScreen();
		}
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return ToolBarView.Mediator.Invisible.instance;
	}

	@Override
	public boolean canScrollUp() {
		PermataWebView v = getWebView();
		if (v == null) return false;
		PermataChromeClient chrome = v.getWebChromeClient();
		return (chrome != null) && (chrome.isFullScreen() || (v.getScrollY() > 0));
	}

	@Nullable
	protected WebBrowserAddon getAddon() {
		return AddonManager.get().getAddon(YoutubeAddon.class);
	}

	@Nullable
	protected YoutubeWebView getWebView() {
		View v = getView();
		return (v != null) ? v.findViewById(R.id.ytWebView) : null;
	}

	protected boolean isDesktopVersionSupported() {
		return false;
	}

	@Override
	protected String getSearchUrl() {
		return "https://www.youtube.com/results?search_query=";
	}
}
