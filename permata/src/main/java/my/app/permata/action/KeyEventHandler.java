package my.app.permata.action;

import static android.os.SystemClock.uptimeMillis;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_MULTIPLE;
import static android.view.KeyEvent.ACTION_UP;

import android.net.Uri;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

import my.app.permata.media.service.MediaSessionCallback;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.function.IntObjectFunction;
import my.app.utils.log.Log;
import my.app.utils.ui.fragment.ActivityFragment;

/**
 * @author sklchan77
 */
public class KeyEventHandler {
	private static final int DBL_CLICK_INTERVAL = 500;
	private static final int LONG_CLICK_INTERVAL = 1000;

	private static Worker worker;
	
	// Tracks scroll timestamps to prevent spamming and ANRs
	private static final Map<View, Long> scrollTimestamps = new WeakHashMap<>();

	// === ENTERPRISE HARDENED: GLOBAL CSS PAYLOAD ===
	private static final String JS_UNIVERSAL_PAYLOAD = "(function(){" +
			"var res = 'Discovery [Layer 2]: JS Registry Miss'; " +
			"var injectGlobalWipe = function() { " +
			"  if(!document.getElementById('permata-god-mode-css')) { " +
			"    var style = document.createElement('style'); " +
			"    style.id = 'permata-god-mode-css'; " +
			"    style.innerHTML = ' " +
			"      xg-controls, .xgplayer-controls, .xg-right-bar, .xg-left-bar, " +
			"      .video-info-container, .right-container, .bottom-container, .xgplayer-bottom, " +
			"      [class*=\"sidebar\"], [class*=\"video-info\"], [class*=\"action-bar\"], [class*=\"author-info\"], " +
			"      [class*=\"comment\"], .account-info, .danmaku-container, .login-mask-enter-done, .dy-account-close, " +
			"      [class*=\"bottom-bar\"], [class*=\"Prompt\"], [class*=\"download-btn\"], [class*=\"app-open\"], " +
			"      .XPromoPopup, .AppBanner, [class*=\"Banner\"], .UnauthBox, .box_layout, [id*=\"login\"], " +
			"      .open-app-bar, .login-dialog { display: none !important; opacity: 0 !important; visibility: hidden !important; pointer-events: none !important; height: 0 !important; width: 0 !important; } '; " +
			"    document.head.appendChild(style); " +
			"    return 'Global CSS Injected.'; " +
			"  } " +
			"  return 'CSS Exists.'; " +
			"}; " +
			"var injectSpecificWipe = function(customCss, id) { " +
			"  if(!document.getElementById(id)) { " +
			"    var style = document.createElement('style'); " +
			"    style.id = id; " +
			"    style.innerHTML = customCss; " +
			"    document.head.appendChild(style); " +
			"    return 'CSS (' + id + ') Injected.'; " +
			"  } " +
			"  return 'CSS (' + id + ') Exists.'; " +
			"}; " +
			"window.__attemptFS = function() { " +
			"  var player = document.querySelector('.xgplayer, video, main'); " +
			"  if (player && !document.fullscreenElement && !document.webkitFullscreenElement) { " +
			"      try { player.requestFullscreen(); } catch(e) {} " +
			"  } " +
			"}; " +
			"if (!window.__permataArmed) { " +
			"  window.addEventListener('touchend', window.__attemptFS, {capture:true}); " +
			"  window.addEventListener('mouseup', window.__attemptFS, {capture:true}); " +
			"  window.__permataArmed = true; " +
			"  setInterval(function(){ if(window.__permataActive) { window.__permataActive.execute(); } window.__attemptFS(); }, 1500); " +
			"} " +
			"var registry=[" +
			"    {name:\"douyin\",match:/douyin\\.com/,execute:function(){ return 'DOUYIN: ' + injectGlobalWipe(); }}," +
			"    {name:\"tiktok\",match:/tiktok\\.com/,execute:function(){ var ttCss = ' [data-e2e=\"video-author-avatar\"], [data-e2e=\"nav-login\"], [class*=\"DivHeaderContainer\"], [class*=\"DivSideNavContainer\"], [class*=\"DivBottomContainer\"] { display: none !important; pointer-events: none !important; } '; return 'TIKTOK: ' + injectGlobalWipe() + ' | ' + injectSpecificWipe(ttCss, 'permata-tt-css'); }}," +
			"    {name:\"instagram\",match:/instagram\\.com/,execute:function(){ var igCss = ' header, nav, [role=\"navigation\"], [role=\"dialog\"], [class*=\"x1qjc9v5\"], div._a9-z, div._a9_1 { display: none !important; pointer-events: none !important; opacity: 0 !important; visibility: hidden !important; height: 0 !important; } body, html, div, section, main { overflow: auto !important; overflow-y: auto !important; touch-action: pan-y !important; overscroll-behavior-y: auto !important; } '; try { var traps = document.querySelectorAll(\"[role='dialog'], div._a9-z\"); for(var i=0; i<traps.length; i++) { traps[i].remove(); } } catch(e){} return 'INSTAGRAM: ' + injectSpecificWipe(igCss, 'permata-ig-css'); }}," +
			"    {name:\"youtube\",match:/(youtube\\.com|youtu\\.be)/,execute:function(){ var ad=document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-skip-button'); if(ad)ad.click(); return 'YOUTUBE: Handled.'; }}," +
			"  ];" +
			"  window.__permataActive = null; " +
			"  for(var i=0; i<registry.length; i++) { if(registry[i].match.test(window.location.hostname)) { window.__permataActive = registry[i]; break; } } " +
			"  if (window.__permataActive) { res = 'Registry Match: ' + window.__permataActive.name; try { res += ' || ' + window.__permataActive.execute(); } catch(e){} } " +
			"  else { var commonCss = ' header, footer, nav, aside, #cookie-notice, .cookie-banner, [id*=\"cookie\"], [class*=\"cookie\"], [id*=\"popup\"], [class*=\"popup\"], .floating-action-button { display: none !important; } '; try { res += ' || COMMON: ' + injectSpecificWipe(commonCss, 'permata-common-css'); } catch(e){} } " +
			"  return res + ' || Enforcer Active'; " +
			"})();";

	private static final String JS_POLLING_PAYLOAD = "try { if(window.__permataActive) { window.__permataActive.execute(); } } catch(e){}";

	public static boolean handleKeyEvent(MediaSessionCallback cb, KeyEvent event, IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		return handleKeyEvent(cb, null, event, defaultHandler);
	}

	public static boolean handleKeyEvent(MainActivityDelegate activity, KeyEvent event, IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		return handleKeyEvent(activity.getMediaSessionCallback(), activity, event, defaultHandler);
	}

	private static boolean handleKeyEvent(MediaSessionCallback cb, @Nullable MainActivityDelegate activity, KeyEvent event, IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		if (event.isCanceled()) { worker = null; return defaultHandler.apply(event.getKeyCode(), event); }
		if (worker != null) { if (worker.handle(event)) return true; worker = null; return false; }
		var code = event.getKeyCode();
		MainActivityDelegate targetActivity = activity;
		if (targetActivity == null && cb != null && cb.getAssistant() instanceof MainActivityDelegate) { targetActivity = (MainActivityDelegate) cb.getAssistant(); }

		if (targetActivity != null && event.getAction() == ACTION_DOWN) {
			if (code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
				ActivityFragment activeFragment = targetActivity.getActiveFragment();
				if (activeFragment != null && activeFragment.getClass().getName().endsWith("WebBrowserFragment") && !activeFragment.getClass().getName().endsWith("YoutubeFragment")) {
					boolean isNext = (code == KeyEvent.KEYCODE_MEDIA_NEXT);
					targetActivity.post(() -> {
						WebView webView = scanFragmentsForWebView(activeFragment);
						if (webView != null) {
							String host = "";
							try { host = Uri.parse(webView.getUrl()).getHost(); } catch (Exception ignored) {}
							boolean isInstagram = host != null && host.contains("instagram.com");
							boolean isMediaHost = isInstagram || (host != null && (host.contains("douyin") || host.contains("tiktok")));
							
							// FORCE IG TARGETING TO BASE WEBVIEW TO FIX SNAP-SCROLL LOCKUP
							View touchTargetView = webView;
							if (!isInstagram) {
								try {
									Method getChromeClient = webView.getClass().getMethod("getWebChromeClient");
									Object chromeClient = getChromeClient.invoke(webView);
									if (chromeClient != null) {
										Method isFullScreenMethod = chromeClient.getClass().getMethod("isFullScreen");
										if ((Boolean) isFullScreenMethod.invoke(chromeClient)) {
											Method getFullScreenViewMethod = chromeClient.getClass().getMethod("getFullScreenView");
											View fullScreenView = (View) getFullScreenViewMethod.invoke(chromeClient);
											if (fullScreenView != null && fullScreenView.getVisibility() == View.VISIBLE) touchTargetView = fullScreenView;
										}
									}
								} catch (Exception ignored) {}
							}
							smartScrollWebView(webView, touchTargetView, !isNext, host, isMediaHost, isInstagram);
						}
					});
					return true;
				}
			}
		}

		var k = Key.get(code);
		if (k == null) return defaultHandler.apply(code, event);
		if (!k.isMedia() && (targetActivity != null) && (targetActivity.getCurrentFocus() instanceof EditText)) return defaultHandler.apply(code, event);
		var dblClickAction = k.getDblClickAction();
		if (dblClickAction == null) return defaultHandler.apply(code, event);
		var action = event.getAction();
		if (action == ACTION_MULTIPLE) { performAction(dblClickAction, cb, targetActivity, uptimeMillis()); return true; }
		if (action != ACTION_DOWN) return defaultHandler.apply(code, event);
		var clickAction = k.getClickAction();
		var longClickAction = k.getLongClickAction();
		if (clickAction == null || longClickAction == null) return defaultHandler.apply(code, event);
		if (((clickAction == dblClickAction) && (clickAction == longClickAction)) || ((dblClickAction == Action.NONE) && (longClickAction == Action.NONE))) {
			performAction(clickAction, cb, targetActivity, uptimeMillis());
			return true;
		}
		worker = new Worker(cb, targetActivity, k, clickAction, dblClickAction, longClickAction);
		return true;
	}

	private static WebView scanFragmentsForWebView(ActivityFragment activeFragment) {
		try {
			Method getWebViewMethod = activeFragment.getClass().getMethod("getWebView");
			Object result = getWebViewMethod.invoke(activeFragment);
			return (result instanceof WebView) ? (WebView) result : null;
		} catch (Exception e) { return null; }
	}

	private static void smartScrollWebView(final WebView wv, final View touchTarget, boolean up, final String host, boolean isMediaHost, boolean isInstagram) {
		if (wv == null || touchTarget == null || !touchTarget.isAttachedToWindow() || touchTarget.getWidth() <= 0 || touchTarget.getHeight() <= 0) return;
		long now = android.os.SystemClock.uptimeMillis();
		Long lastClickTimeObj = scrollTimestamps.get(wv);
		long lastClickTime = (lastClickTimeObj != null) ? lastClickTimeObj : 0;
		if (now - lastClickTime < (isMediaHost ? 500 : 250)) return;
		scrollTimestamps.put(wv, now); 
		touchTarget.requestFocus();

		if (wv.getSettings().getJavaScriptEnabled()) {
			wv.evaluateJavascript(JS_UNIVERSAL_PAYLOAD, null);
			if (isMediaHost && !isInstagram) {
				String advancedJsScript = "(function() {" +
						"  try {" +
						"    var isDown = " + (!up) + ";" +
						"    var targetBtn = isDown ? document.querySelector('.xgplayer-playswitch-next, .slide-down-btn, [aria-label=\"Next video\"], [data-e2e=\"arrow-down\"]') : document.querySelector('.xgplayer-playswitch-prev, .slide-up-btn, [aria-label=\"Previous video\"], [data-e2e=\"arrow-up\"]');" +
						"    if (targetBtn) { targetBtn.click(); }" +
						"    var amount = isDown ? window.innerHeight * 0.90 : -window.innerHeight * 0.90;" +
						"    window.scrollBy({ top: amount, behavior: 'smooth' });" +
						"    var activeNode = document.activeElement || document.body;" +
						"    try { var wheelEvt = new WheelEvent('wheel', { deltaY: amount, bubbles: true, cancelable: true }); activeNode.dispatchEvent(wheelEvt); } catch(wErr) {}" +
						"    return 'Scroll Executed.';" +
						"  } catch (err) { return 'Scroll Error'; }" +
						"})();";
				wv.evaluateJavascript(advancedJsScript, null);
				wv.postDelayed(() -> wv.evaluateJavascript(JS_POLLING_PAYLOAD, null), 1500);
			} else if (!isMediaHost) {
				String generalJsScript = "(function() { window.scrollBy({ top: " + (!up ? "window.innerHeight * 0.85" : "-window.innerHeight * 0.85") + ", behavior: 'smooth' }); })();";
				wv.evaluateJavascript(generalJsScript, null);
			}
			int backupKey = up ? KeyEvent.KEYCODE_PAGE_UP : KeyEvent.KEYCODE_PAGE_DOWN;
			wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, backupKey));
			wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, backupKey));
		}

		if (isMediaHost) {
			final float actionX = touchTarget.getWidth() * 0.50f;
			final float centerY = touchTarget.getHeight() / 2f;
			float span = touchTarget.getHeight() * 0.60f; 
			final float yStart = up ? (centerY - span / 2f) : (centerY + span / 2f);
			final float yEnd = up ? (centerY + span / 2f) : (centerY - span / 2f);
			final long startTime = android.os.SystemClock.uptimeMillis();
			
			// WAKE-UP TAP
			MotionEvent wakeDown = MotionEvent.obtain(startTime, startTime, MotionEvent.ACTION_DOWN, 10f, 10f, 0);
			touchTarget.dispatchTouchEvent(wakeDown); wakeDown.recycle();
			MotionEvent wakeUp = MotionEvent.obtain(startTime, startTime + 10, MotionEvent.ACTION_UP, 10f, 10f, 0);
			touchTarget.dispatchTouchEvent(wakeUp); wakeUp.recycle();
			
			final long swipeStartTime = startTime + 20;
			MotionEvent eventDown = MotionEvent.obtain(swipeStartTime, swipeStartTime, MotionEvent.ACTION_DOWN, actionX, yStart, 0);
			touchTarget.dispatchTouchEvent(eventDown); eventDown.recycle();

			final int stepCount = 5;
			final long swipeDuration = 80; 
			for (int i = 1; i <= stepCount; i++) {
				final float fraction = (float) i / stepCount;
				final float currentY = yStart + (yEnd - yStart) * fraction;
				final long moveTime = swipeStartTime + (long) (swipeDuration * fraction);
				touchTarget.postDelayed(() -> {
					if (touchTarget.isAttachedToWindow()) {
						MotionEvent eventMove = MotionEvent.obtain(swipeStartTime, moveTime, MotionEvent.ACTION_MOVE, actionX, currentY, 0);
						touchTarget.dispatchTouchEvent(eventMove); eventMove.recycle();
					}
				}, (long) (swipeDuration * fraction) + 20);
			}

			touchTarget.postDelayed(() -> {
				if (touchTarget.isAttachedToWindow()) {
					MotionEvent eventUp = MotionEvent.obtain(swipeStartTime, swipeStartTime + swipeDuration + 10, MotionEvent.ACTION_UP, actionX, yEnd, 0);
					touchTarget.dispatchTouchEvent(eventUp); eventUp.recycle();
				}
			}, swipeDuration + 30);
		}
	}

	private static void performAction(Action action, MediaSessionCallback cb, @Nullable MainActivityDelegate activity, long timestamp) {
		worker = null;
		action.getHandler().handle(cb, activity, timestamp);
	}

	private static final class Worker implements Runnable {
		private final MediaSessionCallback cb;
		@Nullable private final MainActivityDelegate activity;
		private final Key key;
		private final Action clickAction;
		private final Action dblClickAction;
		private final Action longClickAction;
		private final long time;
		private long longClickTime;
		private boolean up;

		Worker(MediaSessionCallback cb, @Nullable MainActivityDelegate activity, Key key, Action clickAction, Action dblClickAction, Action longClickAction) {
			this.cb = cb; this.activity = activity; this.key = key; this.clickAction = clickAction; this.dblClickAction = dblClickAction; this.longClickAction = longClickAction;
			time = longClickTime = uptimeMillis(); sched(DBL_CLICK_INTERVAL);
		}

		@Override public void run() {
			if (worker != this) return;
			if (up) { handle(clickAction); return; }
			long now = uptimeMillis();
			long diff = now - longClickTime;
			if (diff < LONG_CLICK_INTERVAL) sched(LONG_CLICK_INTERVAL - diff);
			else if (diff > 15000) worker = null;
			else { longClickTime = time; handle(longClickAction); worker = this; sched(LONG_CLICK_INTERVAL); }
		}

		boolean handle(KeyEvent e) {
			if (e.getKeyCode() != key.getCode()) return false;
			switch (e.getAction()) {
				case ACTION_DOWN -> { if (!up && ((longClickAction == clickAction) || (longClickAction == Action.NONE))) handle(clickAction); return true; }
				case ACTION_UP -> {
					long holdTime = uptimeMillis() - time;
					if (holdTime <= DBL_CLICK_INTERVAL) {
						if (up) handle(dblClickAction);
						else if (dblClickAction == clickAction) handle(clickAction);
						else up = true;
					} else if (holdTime >= LONG_CLICK_INTERVAL) worker = null;
					else { worker = null; if (longClickTime == time) handle(clickAction); }
					return true;
				}
				case ACTION_MULTIPLE -> { handle(dblClickAction); return true; }
			}
			return false;
		}

		private void handle(Action action) { performAction(action, cb, activity, time); }
		private void sched(long delay) { var handler = (activity == null) ? cb.getHandler() : activity.getHandler(); handler.postDelayed(this, delay); }
	}
}