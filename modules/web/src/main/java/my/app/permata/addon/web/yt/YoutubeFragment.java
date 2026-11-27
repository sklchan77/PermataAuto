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
import my.app.utils.log.Log;
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
	private static final String PROFILE_URL = "https://m.youtube.com/feed/you";
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

		String url = (state != null) ? state.getString("url", DEFAULT_URL) : DEFAULT_URL;
		boolean pause = (state != null) && state.getBoolean("pause", false);
		PreferenceStore ps = addon.getPreferenceStore();
		long pos = ps.getLongPref(RESUME_POS);
		ps.removePref(RESUME_POS);
		MainActivityDelegate.getActivityDelegate(view.getContext()).onSuccess(a -> {
			YoutubeWebView webView = a.findViewById(R.id.ytWebView);
			VideoView videoView = a.findViewById(R.id.ytVideoView);
			YoutubeWebClient webClient = new YoutubeWebClient();
			YoutubeChromeClient chromeClient = new YoutubeChromeClient(webView, videoView);
			webView.init(addon, webClient, chromeClient);
			registerListeners(a);
			YoutubeWebView.Video v = (state != null) ? new YoutubeWebView.Video(url, pos, !pause) :
					a.isCarActivity() ? YoutubeWebView.takeOverVideo(a.getMediaSessionCallback(), webView) : null;
			if ((v == null) && (state == null)) {
				// Fresh start, e.g. the process was killed after Android Auto disconnected
				v = addon.getLastVideo();
				if (v != null) Log.i("Resuming the last video: ", v.url());
			}
			if (v != null) webView.reopen(v);
			else webView.loadUrl(DEFAULT_URL);
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
		MainActivityDelegate a = MainActivityDelegate.getActivityDelegate(getContext()).peek();
		if (a != null) {
			PermataServiceUiBinder b = a.getMediaServiceBinder();
			if (b != null && YoutubeMediaEngine.isYoutubeItem(b.getCurrentItem())) {
				b.getMediaSessionCallback().onStop();
			}
			unregisterListeners(a);
		}
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

	private void exitFullScreenIfNeeded() {
		PermataWebView v = getWebView();
		if (v == null) return;
		PermataChromeClient chrome = v.getWebChromeClient();
		if (chrome != null && chrome.isFullScreen()) {
			chrome.exitFullScreen();
		}
	}

	private void restoreWebViewSurface() {
		YoutubeWebView v = getWebView();
		if (v == null) return;
		v.onResume();
		PermataChromeClient chrome = v.getWebChromeClient();
		if (chrome == null || !chrome.isFullScreen()) {
			v.setVisibility(View.VISIBLE);
			View root = getView();
			if (root != null) {
				View videoView = root.findViewById(R.id.ytVideoView);
				if (videoView != null && videoView.getVisibility() != View.GONE) {
					videoView.setVisibility(View.GONE);
				}
			}
		}
	}

	@Override
	public void onPause() {
		if (isHidden()) {
			exitFullScreenIfNeeded();
			super.onPause();
			return;
		}

		if ((getAddon() instanceof YoutubeAddon addon) && addon.pauseOnLeave()) {
			MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
				if (isHidden()) return;
				PermataServiceUiBinder b = a.getMediaServiceBinder();
				if (YoutubeMediaEngine.isYoutubeItem(b.getCurrentItem()) && b.isPlaying()) {
					b.getMediaSessionCallback().onPause();
					playOnResume = true;
				} else {
					playOnResume = false;
				}
			});
		}

		// Pause all active HTML5 media tags before Chromium suspends to release audio focus cleanly.
		PermataWebView v = getWebView();
		if (v != null) {
			v.evaluateJavascript(
				"(function() { " +
				"  document.querySelectorAll('video, audio').forEach(function(media) { " +
				"    if (!media.paused) media.pause(); " +
				"  }); " +
				"})();", null);
		}

		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (isHidden()) return;
		restoreWebViewSurface();
		if (!playOnResume) return;
		playOnResume = false;
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
			if (isHidden() || !isResumed()) return;
			PermataServiceUiBinder b = a.getMediaServiceBinder();
			if (YoutubeMediaEngine.isYoutubeItem(b.getCurrentItem())) {
				b.getMediaSessionCallback().onPlay();
			}
		});
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) {
			exitFullScreenIfNeeded();
			if ((getAddon() instanceof YoutubeAddon addon) && addon.pauseOnLeave()) {
				MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
					PermataServiceUiBinder b = a.getMediaServiceBinder();
					if (YoutubeMediaEngine.isYoutubeItem(b.getCurrentItem()) && b.isPlaying()) {
						b.getMediaSessionCallback().onPause();
						playOnResume = true;
					} else {
						playOnResume = false;
					}
				});
				PermataWebView v = getWebView();
				if (v != null) {
					v.evaluateJavascript(
						"(function() { " +
						"  document.querySelectorAll('video, audio').forEach(function(media) { " +
						"    if (!media.paused) media.pause(); " +
						"  }); " +
						"})();", null);
				}
			}
		} else {
			restoreWebViewSurface();
			if (playOnResume) {
				playOnResume = false;
				MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
					PermataServiceUiBinder b = a.getMediaServiceBinder();
					if (YoutubeMediaEngine.isYoutubeItem(b.getCurrentItem())) {
						b.getMediaSessionCallback().onPlay();
					}
				});
			}
		}
	}

	private boolean isProfileUrl(@Nullable String url) {
		return url != null && url.startsWith(PROFILE_URL);
	}

	@Override
	public boolean isRootPage() {
		PermataWebView v = getWebView();
		if ((v == null) || (v.getWebChromeClient() == null)) return true;
		if (v.getWebChromeClient().isFullScreen() || v.canGoBack()) return false;
		return isProfileUrl(v.getUrl());
	}

	@Override
	public boolean onBackPressed() {
		if (super.onBackPressed()) return true;
		PermataWebView v = getWebView();
		if (v != null && !isProfileUrl(v.getUrl())) {
			v.loadUrl(PROFILE_URL);
			return true;
		}
		return false;
	}

	public void loadUrl(String url) {
		PermataWebView v = getWebView();
		if (v != null) v.loadUrl(url);
	}

	@Override
	public void onPlayableChanged(MediaLib.PlayableItem oldItem, MediaLib.PlayableItem newItem) {
		if (YoutubeMediaEngine.isYoutubeItem(oldItem) && !YoutubeMediaEngine.isYoutubeItem(newItem)) {
			exitFullScreenIfNeeded();
			return;
		}
		if (isHidden()) return;

		if (YoutubeMediaEngine.isYoutubeItem(newItem)) {
			PermataWebView v = getWebView();
			if (v == null) return;

			PermataChromeClient chrome = v.getWebChromeClient();
			if (chrome == null) return;

			// Only while on screen: off screen, full screen would hide the page and pause the video
			if (isResumed() && !DEFAULT_URLS.contains(getUrl())) chrome.enterFullScreen();
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
	public YoutubeWebView getWebView() {
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