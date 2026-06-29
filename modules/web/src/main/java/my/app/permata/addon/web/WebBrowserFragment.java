package my.app.permata.addon.web;

import static android.os.Build.*;
import static my.app.permata.addon.web.PermataWebClient.isYoutubeUri;
import static my.app.permata.util.Utils.dynCtx;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import my.app.permata.BuildConfig;
import my.app.permata.addon.AddonManager;
import my.app.permata.addon.web.yt.YoutubeFragment;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.activity.MainActivityListener;
import my.app.permata.ui.activity.VoiceCommand;
import my.app.permata.ui.fragment.MainActivityFragment;
import my.app.utils.function.BooleanConsumer;
import my.app.utils.function.Supplier;
import my.app.utils.log.Log;
import my.app.utils.pref.BasicPreferenceStore;
import my.app.utils.pref.PreferenceSet;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.ui.UiUtils;
import my.app.utils.ui.menu.OverlayMenu;
import my.app.utils.ui.menu.OverlayMenuItem;
import my.app.utils.ui.view.ToolBarView;

/**
 * @author sklchan77
 */
@Keep
@SuppressWarnings("unused")
public class WebBrowserFragment extends MainActivityFragment
		implements OverlayMenu.SelectionHandler, MainActivityListener {
	private boolean fullScreenOnResume;

	@Override
	public int getFragmentId() {
		return my.app.permata.R.id.web_browser_fragment;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		dynCtx(requireContext());
		return inflater.inflate(R.layout.browser, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		WebBrowserAddon addon = getAddon();
		if (addon == null) return;

		Context ctx = view.getContext();
		PermataWebView webView = view.findViewById(R.id.browserWebView);
		ViewGroup fullScreenView = view.findViewById(R.id.browserFullScreenView);
		PermataWebClient webClient = new PermataWebClient();
		PermataChromeClient chromeClient = new PermataChromeClient(webView, fullScreenView);
		webView.init(addon, webClient, chromeClient);
		webView.loadUrl(addon.getLastUrl());
		MainActivityDelegate.getActivityDelegate(ctx).onSuccess(this::registerListeners);
	}

	@Override
	public void onDestroyView() {
		MainActivityDelegate.getActivityDelegate(requireContext()).onSuccess(this::unregisterListeners);
		super.onDestroyView();
	}

	@Override
	public void onRefresh(BooleanConsumer refreshing) {
		PermataWebView v = getWebView();
		if (v != null) {
			PermataWebClient c = v.getWebViewClient();
			if (c != null) {
				c.loading = refreshing;
				v.reload();
			}
		}
	}

		@Override
	public void onPause() {
		super.onPause();
		// --- RELEASE FAKE PLAYBACK FOCUS ON EXIT ---
		try {
			MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(delegate -> {
				var cb = delegate.getMediaSessionCallback();
				if (cb != null) {
					try {
						// Universally search all fields in your callback class to locate the MediaSession instance
						for (java.lang.reflect.Field field : cb.getClass().getDeclaredFields()) {
							if (field.getType().getName().equals("android.media.session.MediaSession") || 
									field.getType().getName().equals("android.support.v4.media.session.MediaSessionCompat")) {
								field.setAccessible(true);
								Object session = field.get(cb);
								if (session != null) {
									// Safely call setActive(false) via reflection
									java.lang.reflect.Method setActiveMethod = session.getClass().getMethod("setActive", boolean.class);
									setActiveMethod.invoke(session, false);
									break;
								}
							}
						}
					} catch (Exception reflectiveEx) {
						Log.e(reflectiveEx, "Reflection failed inside onPause");
					}
				}
			});
		} catch (Exception e) {
			// Fail-safe protection hook
		}
		// --------------------------------------------

		if (!BuildConfig.AUTO) return;
		PermataWebView v = getWebView();
		if (v == null) return;
		PermataChromeClient chrome = v.getWebChromeClient();
		if (chrome != null) {
			if (chrome.isFullScreen()) {
				chrome.exitFullScreen();
				fullScreenOnResume = true;
			} else {
				fullScreenOnResume = false;
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		// --- FORCE CAR ROUTING FOCUS TO BROWSER ---
		try {
			MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(delegate -> {
				var cb = delegate.getMediaSessionCallback();
				if (cb != null) {
					try {
						// Universally search all fields in your callback class to locate the MediaSession instance
						for (java.lang.reflect.Field field : cb.getClass().getDeclaredFields()) {
							if (field.getType().getName().equals("android.media.session.MediaSession") || 
									field.getType().getName().equals("android.support.v4.media.session.MediaSessionCompat")) {
								field.setAccessible(true);
								Object session = field.get(cb);
								if (session != null) {
									Class<?> sessionClass = session.getClass();
									
									// 1. Force the active media session to spin up online
									java.lang.reflect.Method setActiveMethod = sessionClass.getMethod("setActive", boolean.class);
									setActiveMethod.invoke(session, true);

									// 2. Build a native framework PlaybackState object
									var state = new android.media.session.PlaybackState.Builder()
											.setActions(android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT | 
															android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)
											.setState(android.media.session.PlaybackState.STATE_PLAYING, 0, 1.0f)
											.build();

									// 3. Apply the state to the session object
									java.lang.reflect.Method setPlaybackStateMethod = sessionClass.getMethod("setPlaybackState", android.media.session.PlaybackState.class);
									setPlaybackStateMethod.invoke(session, state);
									break;
								}
							}
						}
					} catch (Exception reflectiveEx) {
						Log.e(reflectiveEx, "Reflection failed inside onResume");
					}
				}
			});
		} catch (Exception e) {
			my.app.utils.log.Log.e("BrowserFocus", "Failed to force car media session focus", e);
		}
		// ------------------------------------------

		if (!BuildConfig.AUTO || !fullScreenOnResume) return;
		PermataWebView v = getWebView();
		if (v == null) return;
		// Calling here onResume makes the video to not get freezed
		// when you switch to another app and go back to Permata
		v.onResume();
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> a.post(() -> {
			PermataChromeClient chrome = v.getWebChromeClient();
			if (chrome != null) chrome.enterFullScreen();
		}));
	}



	protected void registerListeners(MainActivityDelegate a) {
		a.addBroadcastListener(this, MainActivityListener.ACTIVITY_DESTROY);
	}

	protected void unregisterListeners(MainActivityDelegate a) {
		PermataWebView v = getWebView();
		WebBrowserAddon addon = getAddon();
		a.removeBroadcastListener(this);
		if ((addon != null) && (v != null)) addon.getPreferenceStore().removeBroadcastListener(v);
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (e == ACTIVITY_DESTROY) unregisterListeners(a);
	}

	@Override
	public void setInput(Object input) {
		loadUrl(input.toString());
	}

	public void loadUrl(String url) {
		if (Uri.parse(url).getScheme() == null) {
			url = getSearchUrl() + url;
		}

		PermataWebView v = getWebView();

		if (v != null) {
			if (!(this instanceof YoutubeFragment) && isYoutubeUri(Uri.parse(url)) &&
					AddonManager.get().hasAddon(my.app.permata.R.id.youtube_fragment)) {
				String u = url;
				MainActivityDelegate.getActivityDelegate(requireContext()).onSuccess(a -> {
					if (a.showFragment(my.app.permata.R.id.youtube_fragment) instanceof YoutubeFragment f)
						f.loadUrl(u);
				});
			} else {
				v.loadUrl(url);
			}
		} else {
			WebBrowserAddon addon = AddonManager.get().getAddon(WebBrowserAddon.class);
			if (addon != null) addon.setLastUrl(url);
		}
	}

	@Nullable
	public String getUrl() {
		WebView v = getWebView();
		return (v == null) ? null : v.getUrl();
	}

	@Override
	public boolean isRootPage() {
		PermataWebView v = getWebView();
		if ((v == null) || (v.getWebChromeClient() == null)) return true;
		return !v.getWebChromeClient().isFullScreen() && !v.canGoBack();
	}

	@Override
	public boolean onBackPressed() {
		PermataWebView v = getWebView();
		if (v == null) return false;
		PermataChromeClient chrome = v.getWebChromeClient();

		if ((chrome != null) && chrome.isFullScreen()) {
			chrome.exitFullScreen();
			return true;
		}

		if (v.canGoBack()) {
			v.goBack();
			return true;
		}

		return false;
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return WebToolBarMediator.getInstance();
	}

	@Nullable
	protected WebBrowserAddon getAddon() {
		return AddonManager.get().getAddon(WebBrowserAddon.class);
	}

	@Nullable
	protected PermataWebView getWebView() {
		View v = getView();
		return (v != null) ? v.findViewById(R.id.browserWebView) : null;
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder b) {
		WebBrowserAddon a = getAddon();
		PermataWebView v = getWebView();
		if ((a == null) || (v == null)) return;

		Context ctx = dynCtx(requireContext());
		Resources res = ctx.getResources();
		Resources.Theme theme = ctx.getTheme();
		b.addItem(my.app.permata.R.id.refresh,
				ResourcesCompat.getDrawable(res, my.app.permata.R.drawable.refresh, theme),
				res.getString(my.app.permata.R.string.refresh)).setHandler(this);

		if (isDesktopVersionSupported()) {
			b.addItem(R.id.desktop_version,
							ResourcesCompat.getDrawable(res, R.drawable.desktop, theme),
							res.getString(R.string.desktop_version)).setChecked(a.isDesktopVersion())
					.setHandler(this);
		}

		PermataChromeClient chrome = v.getWebChromeClient();
		if (chrome == null) return;

		if (!chrome.isFullScreen()) {
			if (chrome.canEnterFullScreen()) {
				b.addItem(R.id.fullscreen,
						ResourcesCompat.getDrawable(res, R.drawable.fullscreen, theme),
						res.getString(R.string.full_screen)).setHandler(this);
			}
		} else {
			b.addItem(R.id.fullscreen_exit,
					ResourcesCompat.getDrawable(res, R.drawable.fullscreen_exit, theme),
					res.getString(R.string.full_screen_exit)).setHandler(this);
		}

		b.addItem(my.app.permata.R.id.bookmarks,
				ResourcesCompat.getDrawable(res, my.app.permata.R.drawable.bookmark_filled, theme),
				res.getText(my.app.permata.R.string.bookmarks)).setSubmenu(this::bookmarksMenu);
	}

	protected boolean isDesktopVersionSupported() {
		return true;
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem item) {
		PermataWebView v = getWebView();
		if (v == null) return false;

		int id = item.getItemId();

		if (id == my.app.permata.R.id.refresh) {
			v.reload();
			return true;
		} else if (id == R.id.desktop_version) {
			WebBrowserAddon addon = getAddon();
			if (addon != null) addon.setDesktopVersion(!addon.isDesktopVersion());
			return true;
		} else if (id == R.id.fullscreen || id == R.id.fullscreen_exit) {
			PermataChromeClient chrome = v.getWebChromeClient();
			if (chrome == null) return false;
			if (id == R.id.fullscreen) chrome.enterFullScreen();
			else chrome.exitFullScreen();
			return true;
		}

		return false;
	}

	public void bookmarksMenu(OverlayMenu.Builder b) {
		WebBrowserAddon a = getAddon();
		if (a == null) return;

		b.addItem(my.app.permata.R.id.bookmark_create, my.app.permata.R.string.create_bookmark)
				.setSubmenu(this::createBookmark);
		int i = 0;

		for (Map.Entry<String, String> e : a.getBookmarks().entrySet()) {
			b.addItem(UiUtils.getArrayItemId(i++), e.getValue()).setData(e.getKey())
					.setHandler(this::bookmarkSelected);
		}
	}

	private void createBookmark(OverlayMenu.Builder b) {
		PermataWebView v = getWebView();
		if (v == null) return;
		PreferenceStore store = new BasicPreferenceStore();
		PreferenceStore.Pref<Supplier<String>> name = PreferenceStore.Pref.s("name", v.getTitle());
		PreferenceStore.Pref<Supplier<String>> url = PreferenceStore.Pref.s("url", v.getUrl());

		PreferenceSet set = new PreferenceSet();
		set.addStringPref(o -> {
			o.store = store;
			o.pref = name;
			o.title = my.app.permata.R.string.bookmark_name;
		});
		set.addStringPref(o -> {
			o.store = store;
			o.pref = url;
			o.title = R.string.url;
		});

		set.addToMenu(b, true);
		b.setCloseHandlerHandler(m -> {
			WebBrowserAddon a = getAddon();
			if (a != null) a.addBookmark(store.getStringPref(name), store.getStringPref(url));
		});
	}

	private boolean bookmarkSelected(OverlayMenuItem item) {
		if (item.isLongClick()) {
			String url = item.getData();
			item.getMenu().show(b ->
					b.addItem(my.app.permata.R.id.bookmark_remove, my.app.permata.R.string.remove_bookmark)
							.setHandler(i -> {
								WebBrowserAddon a = getAddon();
								if (a != null) a.removeBookmark(url);
								return true;
							})
			);
		} else {
			loadUrl(item.getData());
		}

		return true;
	}

	@Override
	public boolean isVoiceCommandsSupported() {
		return true;
	}

	@Override
	public void voiceCommand(VoiceCommand cmd) {
		String q = cmd.getQuery();

		if (cmd.isOpen()) {
			WebBrowserAddon a = getAddon();
			if (a != null) {
				for (Map.Entry<String, String> e : a.getBookmarks().entrySet()) {
					if (q.equalsIgnoreCase(e.getValue())) {
						loadUrl(e.getKey());
						return;
					}
				}
			}
		}

		try {
			var encoded =
					(VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) ? URLEncoder.encode(q,
							StandardCharsets.UTF_8) :
							URLEncoder.encode(q, "UTF-8");
			var u = getSearchUrl() + encoded;
			loadUrl(u);
		} catch (UnsupportedEncodingException ex) {
			Log.e(ex, "Failed to encode query ", q);
		}
	}

	protected String getSearchUrl() {
		return "https://www.google.com/search?q=";
	}
}
