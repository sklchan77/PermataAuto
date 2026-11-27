package my.app.permata.addon.web;

import static android.content.res.Configuration.UI_MODE_NIGHT_MASK;
import static android.content.res.Configuration.UI_MODE_NIGHT_YES;
import static android.os.Build.VERSION;
import static android.os.Build.VERSION_CODES;
import static android.view.MotionEvent.ACTION_UP;
import static androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING;
import static androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT;
import static androidx.webkit.WebViewFeature.FORCE_DARK;
import static androidx.webkit.WebViewFeature.FORCE_DARK_STRATEGY;
import static androidx.webkit.WebViewFeature.USER_AGENT_METADATA;
import static java.util.Objects.requireNonNull;
import static my.app.permata.addon.web.PermataJsInterface.JS_EDIT;
import static my.app.permata.addon.web.PermataJsInterface.JS_EVENT;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.ScriptHandler;
import androidx.webkit.UserAgentMetadata;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import my.app.permata.BuildConfig;
import my.app.permata.ui.activity.PermataActivity;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.activity.MainActivityListener;
import my.app.utils.async.FutureSupplier;
import my.app.utils.log.Log;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.text.SharedTextBuilder;
import my.app.utils.ui.UiUtils;
import my.app.utils.ui.fragment.ActivityFragment;
import my.app.utils.ui.view.TextChangedListener;
import my.app.utils.ui.view.ToolBarView;

/**
 * @author sklchan77
 */
public class PermataWebView extends WebView
		implements TextChangedListener, TextView.OnEditorActionListener, PreferenceStore.Listener,
		MainActivityListener {
	private static final Pattern CHROME_VER_PATTERN = Pattern.compile("Chrome/(([0-9]+)\\.[0-9.]+)");
	private final boolean isCar;
	private WebBrowserAddon addon;
	private PermataWebClient webClient;
	private PermataChromeClient chrome;
	private boolean keyNavigation;
	private UserAgentMetadata defaultUaMetadata;
	private boolean defaultUaMetadataCaptured;
	private boolean uaMetadataModified;
	private ScriptHandler uaScriptHandler;

	public PermataWebView(Context context) {
		this(context, null);
	}

	public PermataWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
		isCar = BuildConfig.AUTO && MainActivityDelegate.get(context).isCarActivityNotMirror();
	}

	public PermataWebView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		isCar = BuildConfig.AUTO && MainActivityDelegate.get(context).isCarActivityNotMirror();
	}

	@SuppressLint("SetJavaScriptEnabled")
	public void init(WebBrowserAddon addon, PermataWebClient webClient,
									 PermataChromeClient chromeClient) {
		this.addon = addon;
		this.webClient = webClient;
		setWebViewClient(webClient);
		setWebChromeClient(chromeClient);
		WebSettings s = getSettings();
		s.setSupportZoom(true);
		s.setBuiltInZoomControls(true);
		s.setDisplayZoomControls(false);
		s.setDatabaseEnabled(true);
		s.setDomStorageEnabled(true);
		s.setAllowFileAccess(true);
		s.setLoadWithOverviewMode(true);
		s.setJavaScriptEnabled(true);
		s.setMediaPlaybackRequiresUserGesture(false);
		s.setJavaScriptCanOpenWindowsAutomatically(true);

		addJavascriptInterface(createJsInterface(), PermataJsInterface.NAME);
		CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);

		addon.getPreferenceStore().addBroadcastListener(this);
		getActivity().onSuccess(a -> a.addBroadcastListener(this));

		captureDefaultUaMetadata(s);
		setDesktopMode(addon, false);
		setForceDark(addon, false);
	}

	@Override
	protected void onWindowVisibilityChanged(int visibility) {
		if (!BuildConfig.AUTO) super.onWindowVisibilityChanged(visibility);
		else if (visibility != View.GONE) super.onWindowVisibilityChanged(View.VISIBLE);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		WebBrowserAddon a = getAddon();
		if (a == null) return;

		if (prefs.contains(a.getDesktopVersionPref())) {
			setDesktopMode(a, true);
		} else if (prefs.contains(a.getUserAgentPref())) {
			UserAgent.ua = null;
			setDesktopMode(a, true);
		} else if (prefs.contains(a.getUserAgentDesktopPref())) {
			UserAgent.uaDesktop = null;
			setDesktopMode(a, true);
		} else if (prefs.contains(a.getForceDarkPref())) {
			setForceDark(addon, true);
		}
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (handleActivityDestroyEvent(a, e)) {
			getAddon().getPreferenceStore().removeBroadcastListener(this);
		}
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		PermataChromeClient c = getWebChromeClient();
		if ((c != null) && c.isFullScreen()) getActivity().onSuccess(a -> c.setFullScreen(a, true));
	}

	private void setDesktopMode(WebBrowserAddon a, boolean reload) {
		if (getClass() != PermataWebView.class) return;

		WebSettings s = getSettings();
		boolean v = a.getPreferenceStore().getBooleanPref(a.getDesktopVersionPref());
		String ua = v ? UserAgent.getUaDesktop(s, a) : UserAgent.getUa(s, a);
		s.setUseWideViewPort(v);

		try {
			Log.d("Setting User-Agent to " + ua);
			s.setUserAgentString(ua);
			syncClientIdentityWithUa(s, ua);
		} catch (Exception ex) {
			Log.e(ex, "Invalid User-Agent: ", ua);
			String msg = ex.getLocalizedMessage();
			if (msg == null) msg = "Invalid User-Agent: " + ua;
			UiUtils.showAlert(getContext(), msg);
		}

		if (reload) reload();
	}

	private void captureDefaultUaMetadata(WebSettings s) {
		if (defaultUaMetadataCaptured || (getClass() != PermataWebView.class)) return;
		defaultUaMetadataCaptured = true;
		if (WebViewFeature.isFeatureSupported(USER_AGENT_METADATA)) {
			try {
				defaultUaMetadata = WebSettingsCompat.getUserAgentMetadata(s);
			} catch (Throwable ex) {
				Log.w(ex, "Failed to snapshot default UserAgentMetadata");
			}
		}
	}

	private void removeUaScript() {
		if (uaScriptHandler != null) {
			if (WebViewFeature.isFeatureSupported(DOCUMENT_START_SCRIPT)) {
				try {
					uaScriptHandler.remove();
				} catch (Throwable ex) {
					Log.w(ex, "Failed to remove UA document-start script");
				}
			}
			uaScriptHandler = null;
		}
	}

	private void syncClientIdentityWithUa(WebSettings s, String ua) {
		removeUaScript();

		// 1. Mobile / Android UA: restore factory Client Hints if previously modified and inject zero JS
		if (ua == null || ua.contains("Android") || ua.contains("iPhone") || ua.contains("iPad")) {
			if (uaMetadataModified && defaultUaMetadata != null &&
					WebViewFeature.isFeatureSupported(USER_AGENT_METADATA)) {
				try {
					WebSettingsCompat.setUserAgentMetadata(s, defaultUaMetadata);
					uaMetadataModified = false;
				} catch (Throwable ex) {
					Log.w(ex, "Failed to restore default UserAgentMetadata");
				}
			}
			return;
		}

		captureDefaultUaMetadata(s);

		// 2. Auto-detect Desktop OS profile from resolved UA (supports manual custom UAs)
		String chPlatform;
		String chPlatformVersion;
		String chArch = (ua.contains("aarch64") || ua.contains("arm64")) ? "arm" : "x86";
		String navPlatform;

		if (ua.contains("Windows")) {
			chPlatform = "Windows";
			chPlatformVersion = "15.0.0";
			navPlatform = "Win32";
		} else if (ua.contains("Macintosh") || ua.contains("Mac OS X")) {
			chPlatform = "macOS";
			chPlatformVersion = "14.5.0";
			navPlatform = "MacIntel";
		} else if (ua.contains("CrOS")) {
			chPlatform = "Chrome OS";
			chPlatformVersion = "14541.0.0";
			navPlatform = "arm".equals(chArch) ? "Linux aarch64" : "Linux x86_64";
		} else {
			// Matches Permata's default Desktop UA: X11; Linux x86_64
			chPlatform = "Linux";
			chPlatformVersion = "6.5.0";
			navPlatform = "arm".equals(chArch) ? "Linux aarch64" : "Linux x86_64";
		}

		// 3. Sync Sec-CH-UA* HTTP headers & navigator.userAgentData
		if (WebViewFeature.isFeatureSupported(USER_AGENT_METADATA)) {
			try {
				Matcher cm = CHROME_VER_PATTERN.matcher(ua);
				if (cm.find()) {
					String fullVer = requireNonNull(cm.group(1));
					String majorVer = requireNonNull(cm.group(2));

					UserAgentMetadata metadata = new UserAgentMetadata.Builder()
							.setBrandVersionList(buildDesktopBrands(majorVer, fullVer))
							.setFullVersion(fullVer)
							.setPlatform(chPlatform)
							.setPlatformVersion(chPlatformVersion)
							.setArchitecture(chArch)
							.setBitness(64)
							.setMobile(false)
							.setModel("")
							.setWow64(false)
							.build();
					WebSettingsCompat.setUserAgentMetadata(s, metadata);
					uaMetadataModified = true;
				} else if (defaultUaMetadata != null) {
					UserAgentMetadata metadata = new UserAgentMetadata.Builder(defaultUaMetadata)
							.setPlatform(chPlatform)
							.setPlatformVersion(chPlatformVersion)
							.setArchitecture(chArch)
							.setBitness(64)
							.setMobile(false)
							.setModel("")
							.setWow64(false)
							.build();
					WebSettingsCompat.setUserAgentMetadata(s, metadata);
					uaMetadataModified = true;
				}
			} catch (Throwable ex) {
				Log.w(ex, "Failed to apply desktop UserAgentMetadata");
			}
		}

		// 4. Override Navigator.prototype.platform & vendor at document-start (before site JS runs)
		if (WebViewFeature.isFeatureSupported(DOCUMENT_START_SCRIPT)) {
			String navVendor = ua.contains("Firefox/") ? ""
					: (ua.contains("Safari/") && !ua.contains("Chrome/")) ? "Apple Computer, Inc."
					: "Google Inc.";

			String script = "(function(){" +
					"try{" +
					"Object.defineProperty(Navigator.prototype,'platform',{get:function(){return '" + navPlatform + "';},configurable:true});" +
					"Object.defineProperty(Navigator.prototype,'vendor',{get:function(){return '" + navVendor + "';},configurable:true});" +
					"}catch(e){}" +
					"})();";
			try {
				uaScriptHandler = WebViewCompat.addDocumentStartJavaScript(
						this, script, Collections.singleton("*"));
			} catch (Throwable ex) {
				Log.w(ex, "Failed to register document-start navigator override");
			}
		}
	}

	private List<UserAgentMetadata.BrandVersion> buildDesktopBrands(String majorVer, String fullVer) {
		List<UserAgentMetadata.BrandVersion> result = new ArrayList<>(3);
		boolean hasChrome = false;
		boolean hasChromium = false;

		if (defaultUaMetadata != null) {
			for (UserAgentMetadata.BrandVersion bv : defaultUaMetadata.getBrandVersionList()) {
				String brand = bv.getBrand();
				if (brand.contains("WebView")) {
					result.add(new UserAgentMetadata.BrandVersion.Builder()
							.setBrand("Google Chrome")
							.setMajorVersion(majorVer)
							.setFullVersion(fullVer)
							.build());
					hasChrome = true;
				} else if ("Chromium".equalsIgnoreCase(brand)) {
					result.add(new UserAgentMetadata.BrandVersion.Builder()
							.setBrand("Chromium")
							.setMajorVersion(majorVer)
							.setFullVersion(fullVer)
							.build());
					hasChromium = true;
				} else {
					result.add(bv);
				}
			}
		}

		if (result.isEmpty()) {
			result.add(new UserAgentMetadata.BrandVersion.Builder()
					.setBrand("Not(A:Brand")
					.setMajorVersion("99")
					.setFullVersion("99.0.0.0")
					.build());
		}
		if (!hasChromium) {
			result.add(new UserAgentMetadata.BrandVersion.Builder()
					.setBrand("Chromium")
					.setMajorVersion(majorVer)
					.setFullVersion(fullVer)
					.build());
		}
		if (!hasChrome) {
			result.add(new UserAgentMetadata.BrandVersion.Builder()
					.setBrand("Google Chrome")
					.setMajorVersion(majorVer)
					.setFullVersion(fullVer)
					.build());
		}
		return result;
	}

	@SuppressWarnings("deprecation")
	private void setForceDark(WebBrowserAddon a, boolean reload) {
		if ((VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) &&
				(WebViewFeature.isFeatureSupported(ALGORITHMIC_DARKENING))) {
			boolean dark = a.isForceDark() || (isDarkPhoneTheme() && a.isAutoDark());
			WebSettingsCompat.setAlgorithmicDarkeningAllowed(getSettings(), dark);
			if (reload) reload();
		} else if (WebViewFeature.isFeatureSupported(FORCE_DARK)) {
			int force;
			int strategy;
			if (a.isForceDark() || (isDarkPhoneTheme() && a.isAutoDark())) {
				force = WebSettingsCompat.FORCE_DARK_ON;
				strategy = WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING;
			} else {
				force = WebSettingsCompat.FORCE_DARK_OFF;
				strategy = WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY;
			}
			WebSettingsCompat.setForceDark(getSettings(), force);
			if (WebViewFeature.isFeatureSupported(FORCE_DARK_STRATEGY))
				WebSettingsCompat.setForceDarkStrategy(getSettings(), strategy);
			if (reload) reload();
		}
	}

	private boolean isDarkPhoneTheme() {
		int mode = getResources().getConfiguration().uiMode;
		return (mode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES;
	}

	protected PermataJsInterface createJsInterface() {
		return new PermataJsInterface(this);
	}

	protected boolean isCar() {
		return BuildConfig.AUTO && isCar;
	}

	public WebBrowserAddon getAddon() {
		return addon;
	}

	@NonNull
	@Override
	public PermataWebClient getWebViewClient() {
		return webClient;
	}

	public void setWebChromeClient(PermataChromeClient chrome) {
		this.chrome = chrome;
		super.setWebChromeClient(chrome);
	}

	@Nullable
	@Override
	public PermataChromeClient getWebChromeClient() {
		return chrome;
	}

	protected void urlChanged(String url) {
	}

	protected void pageLoaded(String uri) {
		addFocusHighlight();
		getAddon().setLastUrl(uri);
		getActivity().onSuccess(a -> {
			ActivityFragment f = a.getActiveFragment();
			if (f == null) return;

			ToolBarView.Mediator m = f.getToolBarMediator();

			if (m instanceof WebToolBarMediator wm) {
				ToolBarView tb = a.getToolBar();
				wm.setAddress(tb, uri);
				wm.setButtonsVisibility(tb, canGoBack(), canGoForward());
			}

			CookieManager.getInstance().flush();
		});
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if ((event.getAction() == KeyEvent.ACTION_DOWN) && isNavigationKey(event.getKeyCode())) {
			onKeyNavigation();
		}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public boolean dispatchGenericMotionEvent(MotionEvent event) {
		// Some rotary controllers turn as scroll events rather than keys
		if ((event.getAction() == MotionEvent.ACTION_SCROLL) &&
				event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
			onKeyNavigation();
		}
		return super.dispatchGenericMotionEvent(event);
	}

	private static boolean isNavigationKey(int code) {
		return switch (code) {
			case KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
					KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_TAB,
					KeyEvent.KEYCODE_NAVIGATE_NEXT, KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> true;
			default -> false;
		};
	}

	// The highlight is for key navigation (D-pad, rotary controller): add it on the first such input.
	// It then stays for this view's lifetime, as the same controller keeps being used.
	private void onKeyNavigation() {
		if (keyNavigation) return;
		keyNavigation = true;
		addFocusHighlight();
	}

	protected void addFocusHighlight() {
		if (!keyNavigation) return;
		evaluateJavascript("""
				(function() {
				  var style = document.createElement('style');
				  style.id = 'permata-focus';
				  style.textContent = ':focus-visible {outline: 2px solid blue !important; border-radius: 5px;}';
				  (document.head || document.documentElement).appendChild(style);
				})()""", null);
	}

	protected boolean requestFullScreen() {
		return false;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (getClass() == PermataWebView.class && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
			my.app.permata.action.KeyEventHandler.manualTouchFocus = my.app.permata.action.KeyEventHandler.TARGET_WEB;
			Log.i("[INPUT_TELEMETRY]", "PermataWebView touched (Action DOWN). Touch-Focus locked to TARGET_WEB.");
		}

		if (isCar()) {
			if (event.getAction() == ACTION_UP) checkTextInput();
		}
		return super.onTouchEvent(event);
	}

	private void checkTextInput() {
		if (!BuildConfig.AUTO || isKeyboardActive()) return;

		Log.d("checkTextInput");
		loadUrl("javascript:\n" + "function checkInput() {\n" + "  var e =  document.activeElement;" +
				"\n" + "  if (e == null) return;\n" + "  if (e instanceof HTMLInputElement) {\n" + "    " +
				JS_EVENT + '(' + JS_EDIT + ", e.value);\n" +
				"  } else if(e.getAttribute('contenteditable') == 'true') {\n" + "    " + JS_EVENT + '(' +
				JS_EDIT + ", e.innerText);\n" + "  }\n" + "}\n" + "setTimeout(checkInput, 500);");
	}

	private void setTextInput(CharSequence text) {
		if (!BuildConfig.AUTO) return;

		Log.d(text);
		loadUrl(
				"javascript:\n" + "var e =  document.activeElement;\n" + "var text = '" + text + "';\n" +
						"if (e.isContentEditable) e.innerText = text;\n" + "else e.value = text;\n" +
						"e.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));\n" +
						"e.dispatchEvent(new KeyboardEvent('keypress', { bubbles: true }));\n" +
						"e.dispatchEvent(new InputEvent('input', { bubbles: true, data: text, inputType: " +
						"'insertText' }));\n" +
						"e.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));\n" +
						"e.dispatchEvent(new Event('change', { bubbles: true }));");
	}


	protected void submitForm() {
		if (!BuildConfig.AUTO) return;
		loadUrl("""
				javascript:
				var ae = document.activeElement;
				if (ae.form != null) {
				  ae.form.submit();
				} else {
				  var e = new KeyboardEvent('keydown',
				  { code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });
				  ae.dispatchEvent(e);
				  e = new KeyboardEvent('keyup',
				  { code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });
				  ae.dispatchEvent(e);
				}""");
	}

	public void showKeyboard(String text) {
		if (!BuildConfig.AUTO) return;

		getActivity().onSuccess(a -> {
			EditText et = a.getAppActivity().startInput(this);
			if (et == null) return;

			if (text != null) {
				et.setText(text);
				et.setSelection(et.getText().length());
			}

			et.setOnEditorActionListener(this);
		});
	}

	public void hideKeyboard() {
		if (!BuildConfig.AUTO) return;
		getActivity().onSuccess(a -> a.getAppActivity().stopInput());
	}

	private boolean isKeyboardActive() {
		if (!BuildConfig.AUTO) return false;

		PermataActivity a = getActivity().map(MainActivityDelegate::getAppActivity).peek();
		return (a != null) && a.isInputActive();
	}

	@Override
	public void afterTextChanged(Editable s) {
		if (BuildConfig.AUTO) setTextInput(s);
	}

	@Override
	public boolean onEditorAction(TextView v, int actionId, @Nullable KeyEvent event) {
		if (!BuildConfig.AUTO) return false;

		switch (actionId) {
			case EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_SEND,
					EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE -> {
				submitForm();
				hideKeyboard();
			}
		}

		return false;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		PermataChromeClient chrome = getWebChromeClient();

		if ((chrome != null) && chrome.isFullScreen()) {
			chrome.onTouchEvent(this, ev);
		} else if (BuildConfig.AUTO) {
			PermataActivity a = getActivity().map(MainActivityDelegate::getAppActivity).peek();

			if ((a != null) && a.isInputActive()) {
				a.stopInput();
				return true;
			}
		}

		return super.onInterceptTouchEvent(ev);
	}

	@Override
	public void destroy() {
		removeUaScript();
		super.destroy();
	}

	private FutureSupplier<MainActivityDelegate> getActivity() {
		return MainActivityDelegate.getActivityDelegate(getContext());
	}

	static final class UserAgent {
		private static final Pattern pattern =
				Pattern.compile(".+ AppleWebKit/(\\S+) .+ Chrome/(\\S+) .+");
		static String ua;
		static String uaDesktop;

		static String getUa(WebSettings s, WebBrowserAddon a) {
			if (ua != null) return ua;

			String ua = s.getUserAgentString();
			Matcher m = pattern.matcher(ua);

			if (m.matches()) {
				String av;
				if (VERSION.SDK_INT >= VERSION_CODES.R) av = VERSION.RELEASE_OR_CODENAME;
				else av = VERSION.RELEASE;
				String wv = m.group(1);
				String cv = m.group(2);
				UserAgent.ua = a.getUserAgent().replace("{ANDROID_VERSION}", av)
						.replace("{WEBKIT_VERSION}", requireNonNull(wv))
						.replace("{CHROME_VERSION}", requireNonNull(cv));
				UserAgent.ua = normalize(UserAgent.ua);
				if (UserAgent.ua.isEmpty()) UserAgent.ua = ua;
			} else {
				Log.w("User-Agent does not match the pattern ", pattern, ": " + ua);
				UserAgent.ua = ua;
			}

			return UserAgent.ua;
		}

		static String getUaDesktop(WebSettings s, WebBrowserAddon a) {
			if (uaDesktop != null) return uaDesktop;

			String ua = s.getUserAgentString();
			Matcher m = pattern.matcher(ua);

			if (m.matches()) {
				String wv = m.group(1);
				String cv = m.group(2);
				uaDesktop = a.getUserAgentDesktop().replace("{WEBKIT_VERSION}", requireNonNull(wv))
						.replace("{CHROME_VERSION}", requireNonNull(cv));
			} else {
				Log.w("User-Agent does not match the pattern ", pattern, ": " + ua);
				int i1 = ua.indexOf('(') + 1;
				int i2 = ua.indexOf(')', i1);
				uaDesktop = ua.substring(0, i1) + "X11; Linux x86_64" +
						ua.substring(i2).replace(" Mobile ", " ").replaceFirst(" Version/\\d+\\.\\d+ ", " ");
			}

			return uaDesktop = normalize(uaDesktop);
		}

		private static String normalize(String ua) {
			try (SharedTextBuilder b = SharedTextBuilder.get()) {
				int cut = 0;
				boolean changed = false;

				for (int i = 0, n = ua.length(); i < n; i++) {
					char c = ua.charAt(i);

					if (c <= ' ') {
						if ((b.length() == 0) || (ua.charAt(i - 1) == ' ')) {
							changed = true;
							continue;
						} else if (c != ' ') {
							b.append(' ');
							changed = true;
							continue;
						}
					}

					b.append(c);
				}

				for (int i = b.length() - 1; i >= 0; i--) {
					if (b.charAt(i) == ' ') cut++;
					else break;
				}

				if (cut != 0) {
					changed = true;
					b.setLength(b.length() - cut);
				}

				return changed ? b.toString() : ua;
			}
		}
	}
}