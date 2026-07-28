package my.app.permata.action;

import static android.os.SystemClock.uptimeMillis;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_MULTIPLE;
import static android.view.KeyEvent.ACTION_UP;

import android.net.Uri;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Collections;
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
	
	// =========================================================================================
	// MASTER TOGGLE: UNIVERSAL WEB TELEMETRY PROBE
	// Set to TRUE to arm the DOM inspector for debugging complex web layouts (Scroll/Fullscreen).
	// Set to FALSE for zero-overhead production builds.
	// =========================================================================================
	public static final boolean ENABLE_WEB_PROBE = true;

	private static final int DBL_CLICK_INTERVAL = 500;
	private static final int LONG_CLICK_INTERVAL = 1000;

	private static Worker worker;
	
	// Global timestamp to prevent CPU overload from button spamming before reflection/traversal
	private static long lastGlobalActionTime = 0L;

	// Enterprise Hardening: Synchronized map prevents ConcurrentModificationException across threads
	private static final Map<View, Long> scrollTimestamps = Collections.synchronizedMap(new WeakHashMap<>());

	// The Dedicated Universal Telemetry Probe Agent (Injected only when ENABLE_WEB_PROBE is true)
	private static final String JS_TELEMETRY_PROBE = "(function() { " +
			"  if (window.__permataProbeActive) return; " +
			"  window.__permataProbeActive = true; " +
			"  window.__permataLastTouch = 0; " + 
			"  const recordEvent = function(e) { " +
			"      try { " +
			"          let now = Date.now(); " +
			"          if (now - window.__permataLastTouch < 50) return; " + 
			"          window.__permataLastTouch = now; " +
			"          " +
			"          let touch = e.touches ? e.touches[0] : e; " +
			"          let el = (e.composedPath && e.composedPath().length > 0) ? e.composedPath()[0] : e.target; " +
			"          let dpr = window.devicePixelRatio || 1; " +
			"          let vWidth = window.innerWidth; let vHeight = window.innerHeight; " +
			"          let isIframe = (window !== window.top) ? '[IFRAME] ' : ''; " +
			"          let nodeInfo = el ? el.tagName.toLowerCase() : 'unknown'; " +
			"          if (el && el.id) nodeInfo += '#' + el.id; " +
			"          if (el && typeof el.className === 'string' && el.className.trim()) nodeInfo += '.' + el.className.trim().split(/\\s+/).join('.'); " +
			"          " +
			"          let actionTarget = el && el.closest ? (el.closest('button, a, [role=\"button\"]') || el) : el; " +
			"          let actionInfo = (actionTarget !== el) ? (actionTarget.tagName.toLowerCase() + (actionTarget.id ? '#' + actionTarget.id : '')) : 'same'; " +
			"          " +
			"          let hiddenAttrs = Array.from(actionTarget.attributes || []).filter(a => a.name.startsWith('data-') || a.name.startsWith('aria-')).map(a => a.name + '=' + a.value).join(' '); " +
			"          " +
			"          let getPath = function(n) { let p=[]; while(n && n.nodeType===1 && n.tagName!=='BODY' && n.tagName!=='HTML'){ let s=n.tagName.toLowerCase(); if(n.id) { s+='#'+n.id; p.unshift(s); break; } else if(n.className && typeof n.className==='string') { s+='.'+n.className.trim().split(/\\s+/).join('.'); } p.unshift(s); n=n.parentNode; } return p.join(' > '); }; " +
			"          let cssPath = getPath(actionTarget); " +
			"          " +
			"          let textInfo = el && el.innerText ? el.innerText.trim().substring(0, 25).replace(/\\n/g, ' ') : ''; " +
			"          let rect = el ? el.getBoundingClientRect() : {width:0, height:0, top:0, left:0}; " +
			"          " +
			"          let canvasMath = ''; " +
			"          if (nodeInfo.includes('canvas')) { canvasMath = ' [CanvasRel: ' + ((touch.clientX - rect.left) / rect.width).toFixed(3) + 'x, ' + ((touch.clientY - rect.top) / rect.height).toFixed(3) + 'y]'; } " +
			"          " +
			"          let elStyle = el ? window.getComputedStyle(el) : null; " +
			"          let isVis = (el && el.checkVisibility) ? el.checkVisibility() : true; " +
			"          let cssInfo = elStyle ? ('[pos:' + elStyle.position + '|z:' + elStyle.zIndex + '|pe:' + elStyle.pointerEvents + '|cur:' + elStyle.cursor + '|vis:' + isVis + ']') : ''; " +
			"          " +
			"          let scrollTarget = el; " +
			"          while (scrollTarget && scrollTarget !== document.body && scrollTarget !== document.documentElement) { " +
			"              let overflowY = window.getComputedStyle(scrollTarget).overflowY; " +
			"              if (overflowY === 'auto' || overflowY === 'scroll') break; " +
			"              scrollTarget = scrollTarget.parentElement; " +
			"          } " +
			"          let scrollInfo = 'window/body'; let scrollTransform = 'none'; " +
			"          if (scrollTarget && scrollTarget !== document.body && scrollTarget !== document.documentElement) { " +
			"              scrollInfo = scrollTarget.tagName.toLowerCase(); " +
			"              if (scrollTarget.id) scrollInfo += '#' + scrollTarget.id; " +
			"              if (scrollTarget.className && typeof scrollTarget.className === 'string') scrollInfo += '.' + scrollTarget.className.trim().split(/\\s+/).join('.'); " +
			"              scrollTransform = window.getComputedStyle(scrollTarget).transform; " +
			"          } " +
			"          " +
			"          let vids = document.querySelectorAll('video'); " +
			"          let vidStats = vids.length + ' vid(s).'; " +
			"          if(vids.length > 0) { " +
			"              try { " +
			"                  let v = vids[0]; let vRect = v.getBoundingClientRect(); " +
			"                  vidStats += ' Main size: ' + Math.round(vRect.width) + 'x' + Math.round(vRect.height) + " +
			"                              ' at X:' + Math.round(vRect.left) + ' Y:' + Math.round(vRect.top) + " +
			"                              ' [Play:' + (!v.paused) + '|Vol:' + v.volume + '|Time:' + Math.round(v.currentTime) + '/' + Math.round(v.duration) + 's]'; " +
			"              } catch(mediaErr) { vidStats += ' [CORS Blocked/Iframe]'; } " +
			"          } " +
			"          " +
			"          let msg = '[Host: ' + isIframe + window.location.hostname + '] ' + " +
			"                    'Touch: Viewport(' + Math.round(touch.clientX) + ',' + Math.round(touch.clientY) + ') Page(' + Math.round(touch.pageX) + ',' + Math.round(touch.pageY) + ') DPR:' + dpr + canvasMath + ' | ' + " +
			"                    'Target: ' + nodeInfo + ' (ActionBtn: ' + actionInfo + ') ' + cssInfo + ' Hitbox[' + Math.round(rect.width) + 'x' + Math.round(rect.height) + ' at X:' + Math.round(rect.left) + ' Y:' + Math.round(rect.top) + '] | ' + " +
			"                    'HiddenAttrs: {' + hiddenAttrs + '} | ' + " +
			"                    'SelectorPath: ' + cssPath + ' | ' + " +
			"                    'Text: [' + textInfo + '] | ' + " +
			"                    'ScrollContainer: ' + scrollInfo + ' (Transform: ' + scrollTransform + ') | ' + " +
			"                    'Media: ' + vidStats; " +
			"          if (window.PermataInspector && window.PermataInspector.recordTouch) { window.PermataInspector.recordTouch(msg); } " +
			"      } catch(ex) {} " +
			"  }; " +
			"  window.addEventListener('pointerdown', recordEvent, {capture: true, passive: true}); " +
			"  window.addEventListener('touchstart', recordEvent, {capture: true, passive: true}); " +
			"  window.addEventListener('mousedown', recordEvent, {capture: true, passive: true}); " +
			"})();";

	// The standard UI execution payload
	private static final String JS_UNIVERSAL_PAYLOAD = "(function(){" +
			"let res = 'Discovery [Layer 2]: JS Registry Miss (No custom formatting applied)'; " +
			"const injectGlobalWipe = function() { " +
			"  if(!document.getElementById('permata-god-mode-css')) { " +
			"    const style = document.createElement('style'); " +
			"    style.id = 'permata-god-mode-css'; " +
			"    style.innerHTML = ` " +
			"      xg-controls, .xgplayer-controls, " +
			"      .xg-right-bar, .xg-left-bar, " +
			"      .video-info-container, .right-container, " +
			"      .bottom-container, .xgplayer-bottom, " +
			"      [class*=\"sidebar\"], [class*=\"video-info\"], " +
			"      [class*=\"action-bar\"], [class*=\"author-info\"], " +
			"      [class*=\"comment\"], .account-info, .danmaku-container, " +
			"      .login-mask-enter-done, .dy-account-close, " +
			"      [class*=\"bottom-bar\"], [class*=\"Prompt\"], " +
			"      [class*=\"download-btn\"], [class*=\"app-open\"], " +
			"      .XPromoPopup, .AppBanner, [class*=\"Banner\"], " +
			"      .UnauthBox, .box_layout, [id*=\"login\"], " +
			"      .open-app-bar, .login-dialog { " +
			"          display: none !important; " +
			"          opacity: 0 !important; " +
			"          visibility: hidden !important; " +
			"          pointer-events: none !important; " +
			"          height: 0 !important; " +
			"          width: 0 !important; " +
			"      } " +
			"    `; " +
			"    document.head.appendChild(style); " +
			"    return 'Global CSS Stylesheet Injected Successfully.'; " +
			"  } " +
			"  return 'Global CSS Stylesheet Already Exists.'; " +
			"  }; " +
			"const injectSpecificWipe = function(customCss, id) { " +
			"  if(!document.getElementById(id)) { " +
			"    const style = document.createElement('style'); " +
			"    style.id = id; " +
			"    style.innerHTML = customCss; " +
			"    document.head.appendChild(style); " +
			"    return 'Custom CSS (' + id + ') Injected.'; " +
			"  } " +
			"  return 'Custom CSS (' + id + ') Already Exists.'; " +
			"}; " +
			"window.__attemptFS = function() { " +
			"  let h = window.location.hostname; " +
			"  if (h.indexOf('instagram') !== -1 || h.indexOf('tiktok') !== -1 || h.indexOf('douyin') !== -1) return; " +
			"  if (document.fullscreenElement || document.webkitFullscreenElement) return; " +
			"  let vid = document.querySelector('video'); " +
			"  if (vid) { " +
			"      try { if (vid.webkitEnterFullscreen) { vid.webkitEnterFullscreen(); return; } } catch(e) {} " +
			"      try { vid.requestFullscreen(); } catch(e) {} " +
			"  } " +
			"  let fsBtn = document.querySelector('.xgplayer-fullscreen, .xg-fullscreen, .xgplayer-pagefull, [class*=\"fullscreen\"], .css-1vvdg2q'); " +
			"  if (fsBtn) { try { fsBtn.click(); } catch(e){} } " +
			"}; " +
			"window.__permataTouchListener = function(e) { " +
			"  try { window.focus(); } catch(err) {} " +
			"  let h = window.location.hostname; " +
			"  if (h.indexOf('tiktok') === -1 && h.indexOf('instagram') === -1 && h.indexOf('douyin') === -1) { " +
			"    window.__attemptFS(); " + 
			"  } " +
			"}; " +
			"window.addEventListener('touchstart', window.__permataTouchListener, {passive: true}); " +
			"window.addEventListener('touchend', window.__permataTouchListener, {passive: true}); " +
			"window.addEventListener('mouseup', window.__permataTouchListener, {passive: true}); " +
			"if (!window.__permataMediaCapturerBound) { " +
			"  window.__permataMediaCapturerBound = true; " +
			"  document.addEventListener('play', function(e) { " +
			"    if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) { " +
			"      if (window.AndroidMediaBridge && window.AndroidMediaBridge.onMediaPlay) { " +
			"        window.AndroidMediaBridge.onMediaPlay(); " +
			"      } " +
			"    } " +
			"  }, true); " +
			"  document.addEventListener('pause', function(e) { " +
			"    if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) { " +
			"      if (window.AndroidMediaBridge && window.AndroidMediaBridge.onMediaPause) { " +
			"        window.AndroidMediaBridge.onMediaPause(); " +
			"      } " +
			"    } " +
			"  }, true); " +
			"} " +
			"const registry=[" +
			"    {name:\"douyin\",match:/douyin\\.com/,execute:function(){" +
			"      return 'DOUYIN: Fullscreen and Global Wipe Disabled for Native Feed Layout'; " +
			"    }}," +
			"    {name:\"tiktok\",match:/tiktok\\.com/,execute:function(){" +
			"      var ttCss = ' [data-e2e=\"video-author-avatar\"], [data-e2e=\"nav-login\"], [class*=\"DivHeaderContainer\"], [class*=\"DivSideNavContainer\"], [class*=\"DivBottomContainer\"] { display: none !important; pointer-events: none !important; } '; " +
			"      return 'TIKTOK: ' + injectGlobalWipe() + ' | ' + injectSpecificWipe(ttCss, 'permata-tt-css'); " +
			"    }}," +
			"    {name:\"instagram\",match:/instagram/,execute:function(){" +
			"      var igCss = ' header, nav, [role=\"navigation\"] { display: none !important; pointer-events: none !important; opacity: 0 !important; visibility: hidden !important; } body, html { overflow: auto !important; touch-action: pan-y !important; } '; " +
			"      return 'INSTAGRAM: ' + injectSpecificWipe(igCss, 'permata-ig-css'); " +
			"    }}," +
			"    {name:\"youtube\",match:/(youtube\\.com|youtu\\.be)/,execute:function(){" +
			"      let ad=document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-skip-button');if(ad)ad.click();" +
			"      let dm=document.querySelectorAll('yt-button-renderer[id=\"dismiss-button\"],[aria-label=\"No thanks\"],[aria-label=\"Dismiss\"],.yt-spec-button-shape-next--text');" +
			"      dm.forEach(b=>{if(b.textContent&&(b.textContent.includes('No thanks')||b.textContent.includes('Skip')||b.textContent.includes('Dismiss')))b.click();});" +
			"      return 'YOUTUBE: Handled standard media skip.'; " +
			"    }}," +
			"    {name:\"bilibili\",match:/bilibili\\.com/,execute:function(){" +
			"      let pl=document.querySelector('.mplayer-play');if(pl&&pl.classList.contains('play'))pl.click();" +
			"      return 'BILIBILI: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"reddit\",match:/reddit\\.com/,execute:function(){" +
			"      if(document.body&&window.getComputedStyle(document.body).overflow==='hidden') document.body.style.overflow='auto';" +
			"      return 'REDDIT: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"moj\",match:/(mojapp\\.in|sharechat\\.com)/,execute:function(){" +
			"      if(document.body) document.body.style.overflow='auto';" +
			"      return 'MOJ: ' + injectGlobalWipe(); " +
			"    }}" +
			"  ];" +
			"  window.__permataActive = registry.find(p=>p.match.test(window.location.hostname));" +
			"  if (window.__permataActive) { " +
			"    res = 'Discovery [Layer 2]: Match -> ' + window.__permataActive.name; " +
			"    try { res += ' || ' + window.__permataActive.execute(); } catch(e){} " +
			"  } else { " +
			"    res = 'Discovery [Layer 2]: Generic Host'; " +
			"    var commonCss = ' header, footer, nav, aside, #cookie-notice, .cookie-banner, [id*=\"cookie\"], [class*=\"cookie\"], [id*=\"popup\"], [class*=\"popup\"], .floating-action-button { display: none !important; } '; " +
			"    try { let execRes = injectSpecificWipe(commonCss, 'permata-common-css'); res += ' || COMMON: ' + execRes; } catch(e){} " +
			"  } " +
			"  return res;" +
			"})();";

	private static final String JS_POLLING_PAYLOAD = "try { " +
			"  if(window.__permataActive) { window.__permataActive.execute(); } " +
			"} catch(e){}";

	public static boolean handleKeyEvent(MediaSessionCallback cb, KeyEvent event,
																			 IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		return handleKeyEvent(cb, null, event, defaultHandler);
	}

	public static boolean handleKeyEvent(MainActivityDelegate activity, KeyEvent event,
																			 IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		return handleKeyEvent(activity.getMediaSessionCallback(), activity, event, defaultHandler);
	}

	private static boolean handleKeyEvent(MediaSessionCallback cb,
																				@Nullable MainActivityDelegate activity, KeyEvent event,
																				IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		
		Log.i("[ENTRY] [Code Start] KeyEventHandler.handleKeyEvent triggered. KeyCode: " + event.getKeyCode() + ", Action: " + event.getAction());

		if (event.isCanceled()) {
			worker = null;
			return defaultHandler.apply(event.getKeyCode(), event);
		}

		if (worker != null) {
			if (worker.handle(event)) return true;
			worker = null;
			return false;
		}

		var code = event.getKeyCode();

		MainActivityDelegate targetActivity = activity;
		if (targetActivity == null && cb != null) {
			if (cb.getAssistant() instanceof MainActivityDelegate) {
				targetActivity = (MainActivityDelegate) cb.getAssistant();
			}
		}

		final MainActivityDelegate finalTargetActivity = targetActivity;

		if (finalTargetActivity != null && event.getAction() == ACTION_DOWN) {
			if (code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
				
				// THE FIX: Catch button spam BEFORE doing heavy reflection or view traversal
				long currentUptime = android.os.SystemClock.uptimeMillis();
				if (currentUptime - lastGlobalActionTime < 250) {
					Log.w("[CHECK] Input dropped: Button spam detected (< 250ms)");
					return true; // Consume event but do nothing
				}
				lastGlobalActionTime = currentUptime;

				boolean isNext = (code == KeyEvent.KEYCODE_MEDIA_NEXT);
				
				ActivityFragment activeFragment = finalTargetActivity.getActiveFragment();
				if (activeFragment != null) {
					String className = activeFragment.getClass().getName();
					
					if (className.endsWith("WebBrowserFragment") && !className.endsWith("YoutubeFragment")) {
						
						finalTargetActivity.post(() -> {
							WebView resolvedWebView = scanFragmentsForWebView(activeFragment);
							boolean isExplicitWebFragment = (resolvedWebView != null);

							if (resolvedWebView == null || !resolvedWebView.isShown()) {
								resolvedWebView = findTopVisibleWebView(finalTargetActivity);
							}

							if (resolvedWebView != null && resolvedWebView.isShown()) {
								boolean hasTouchFocus = resolvedWebView.hasFocus();
								boolean isFullScreenSize = false;
								
								if (finalTargetActivity.getWindow() != null && finalTargetActivity.getWindow().getDecorView() != null) {
									int screenHeight = finalTargetActivity.getWindow().getDecorView().getHeight();
									if (screenHeight > 0) {
										isFullScreenSize = resolvedWebView.getHeight() > (screenHeight * 0.5);
									}
								}

								if (isExplicitWebFragment || hasTouchFocus || isFullScreenSize) {
									String currentUrl = resolvedWebView.getUrl();
									String host = "unknown";
									boolean isMediaHost = false;
									boolean isInstagram = false;
									boolean isSnapFeedHost = false;
									boolean isTikTok = false;
									boolean isDouyin = false;
									
									if (currentUrl != null) {
										try {
											host = Uri.parse(currentUrl).getHost();
											if (host != null) {
												if (host.startsWith("www.")) host = host.substring(4);
												
												String h = host.toLowerCase();
												isInstagram = h.contains("instagram");
												isTikTok = h.contains("tiktok");
												isDouyin = h.contains("douyin");
												
												isSnapFeedHost = h.contains("youtube") || h.contains("youtu") || h.contains("facebook") ||
														h.contains("kuaishou") || h.contains("xiaohongshu") ||
														h.contains("likee") || h.contains("kwai") || h.contains("snackvideo") ||
														h.contains("mojapp") || h.contains("sharechat");
														
												isMediaHost = isInstagram || isSnapFeedHost || h.contains("bilibili") || 
														h.contains("reddit") || h.contains("twitter") || h.contains("x.com") || 
														h.contains("pinterest") || h.contains("twitch") || h.contains("weibo") || 
														h.contains("snapchat") || h.contains("vk") || isDouyin || isTikTok;
											}
										} catch (Exception ignored) {}
									}
									final String hostTag = "[Host: " + host + "] ";
									Log.i("[DETECTION] " + hostTag + "Resolved. isMediaHost: " + isMediaHost + " | isSnapFeedHost: " + isSnapFeedHost + " | isTikTok: " + isTikTok);

									// -------------------------------------------------------------------------
									// PROBE INJECTION: Arms the Universal Web Probe if the master toggle is true
									// -------------------------------------------------------------------------
									if (ENABLE_WEB_PROBE) {
										try {
											resolvedWebView.addJavascriptInterface(new PermataInspectorBridge(resolvedWebView), "PermataInspector");
											resolvedWebView.evaluateJavascript(JS_TELEMETRY_PROBE, null);
											Log.w("[WEB_PROBE] Agent Armed successfully on: " + resolvedWebView.getClass().getSimpleName());
										} catch (Exception e) {
											Log.e(e, "[WEB_PROBE] Failed to arm Javascript Agent.");
										}
									}
									
									// -------------------------------------------------------------------------
									// SURGICAL GOD-MODE: Traverse native Android hierarchy to find the topmost overlay layer
									// -------------------------------------------------------------------------
									View touchTargetView = findTopmostTouchTarget(finalTargetActivity, resolvedWebView);
									Log.i("[SURGERY] Target locked to topmost layer: " + touchTargetView.getClass().getSimpleName() 
											+ " [Width: " + touchTargetView.getWidth() + "x" + touchTargetView.getHeight() + "]");

									smartScrollWebView(resolvedWebView, touchTargetView, !isNext, hostTag, isMediaHost, isInstagram, isSnapFeedHost, isTikTok);
								} else {
									Log.i("[DETECTION] WebBrowser is hidden or out of focus. Allowing default media propagation.");
								}
							}
						});
						
						return true; 
					}
				}
			}
		}

		var k = Key.get(code);
		if (k == null) return defaultHandler.apply(code, event);

		if (!k.isMedia() && (finalTargetActivity != null) && (finalTargetActivity.getCurrentFocus() instanceof EditText)) {
			return defaultHandler.apply(code, event);
		}

		var dblClickAction = k.getDblClickAction();
		if (dblClickAction == null) return defaultHandler.apply(code, event);

		var action = event.getAction();
		if (action == ACTION_MULTIPLE) {
			performAction(dblClickAction, cb, finalTargetActivity, uptimeMillis());
			return true;
		}
		
		if (action != ACTION_DOWN) return defaultHandler.apply(code, event);

		var clickAction = k.getClickAction();
		if (clickAction == null) return defaultHandler.apply(code, event);
		
		var longClickAction = k.getLongClickAction();
		if (longClickAction == null) return defaultHandler.apply(code, event);

		if (((clickAction == dblClickAction) && (clickAction == longClickAction)) ||
				((dblClickAction == Action.NONE) && (longClickAction == Action.NONE))) {
			performAction(clickAction, cb, finalTargetActivity, uptimeMillis());
			return true;
		}

		worker = new Worker(cb, finalTargetActivity, k, clickAction, dblClickAction, longClickAction);
		return true;
	}

	// =========================================================================================
	// VIEW TRAVERSAL ENGINE
	// =========================================================================================

	private static WebView scanFragmentsForWebView(ActivityFragment activeFragment) {
		try {
			Method getWebViewMethod = activeFragment.getClass().getMethod("getWebView");
			Object result = getWebViewMethod.invoke(activeFragment);
			if (result instanceof WebView) return (WebView) result;
		} catch (Exception e) {
			Log.e(e, "[REACTION] Discovery: Failed to scan for WebView.");
		}
		return null;
	}

	private static WebView findTopVisibleWebView(MainActivityDelegate activity) {
		if (activity == null || activity.getWindow() == null) return null;
		View decorView = activity.getWindow().getDecorView();
		return findWebViewRecursively(decorView);
	}

	private static WebView findWebViewRecursively(View view) {
		if (view instanceof WebView && view.isShown() && view.getVisibility() == View.VISIBLE && view.getWidth() > 0) {
			if (view.getAlpha() > 0.1f) {
				return (WebView) view;
			}
		}
		if (view instanceof ViewGroup) {
			ViewGroup vg = (ViewGroup) view;
			for (int i = vg.getChildCount() - 1; i >= 0; i--) { 
				WebView res = findWebViewRecursively(vg.getChildAt(i));
				if (res != null) return res;
			}
		}
		return null;
	}

	/**
	 * Surgically scans the decor view hierarchy to locate the topmost, highest z-index,
	 * fully visible View that is large enough to be the primary interactive surface.
	 */
	private static View findTopmostTouchTarget(MainActivityDelegate activity, WebView baseWebView) {
		if (activity == null || activity.getWindow() == null) return baseWebView;
		
		View decorView = activity.getWindow().getDecorView();
		int screenWidth = decorView.getWidth();
		int screenHeight = decorView.getHeight();
		
		if (screenWidth == 0 || screenHeight == 0) return baseWebView;
		
		long totalScreenArea = (long) screenWidth * screenHeight;
		
		// The 20% Automotive Rule: Drops the threshold to catch 9:16 vertical video on ultra-wide screens.
		long minValidArea = (long) (totalScreenArea * 0.20f);
		// The Height Rule: Feed containers almost always span > 80% of the screen height.
		int minValidHeight = (int) (screenHeight * 0.80f);
		
		View topCandidate = scanHighestVisibleChild(decorView, minValidArea, minValidHeight);
		
		return (topCandidate != null && topCandidate.isShown()) 
				? topCandidate 
				: baseWebView;
	}

	private static View scanHighestVisibleChild(View parent, long minValidArea, int minValidHeight) {
		// Opacity & Visibility Check
		if (parent == null || !parent.isShown() || parent.getVisibility() != View.VISIBLE || parent.getAlpha() < 0.1f) {
			return null;
		}
		
		// Traversal (Reverse order for highest z-index)
		if (parent instanceof ViewGroup) {
			ViewGroup vg = (ViewGroup) parent;
			for (int i = vg.getChildCount() - 1; i >= 0; i--) {
				View child = vg.getChildAt(i);
				View target = scanHighestVisibleChild(child, minValidArea, minValidHeight);
				
				if (target != null) {
					return target;
				}
			}
		}
		
		// Geometry Filter: Must be at least 20% of the screen area OR span 80% of the screen height.
		// (The 'OR' guarantees we catch extremely skinny vertical videos on extremely wide screens).
		long viewArea = (long) parent.getWidth() * parent.getHeight();
		if (viewArea >= minValidArea || parent.getHeight() >= minValidHeight) {
			return parent;
		}
		
		return null;
	}

	// =========================================================================================
	// HARDWARE SWIPE ENGINE
	// =========================================================================================

	private static void smartScrollWebView(final WebView wv, final View touchTarget, boolean up, final String hostTag, boolean isMediaHost, boolean isInstagram, boolean isSnapFeedHost, boolean isTikTok) {
		if (wv == null || touchTarget == null || !touchTarget.isAttachedToWindow() || touchTarget.getWidth() <= 0 || touchTarget.getHeight() <= 0) return;

		long now = android.os.SystemClock.uptimeMillis();
		Long lastClickTime = scrollTimestamps.get(wv);
		if (lastClickTime == null) lastClickTime = 0L;
		
		if (now - lastClickTime < 250) {
			Log.w("[CHECK] " + hostTag + "Scroll [Anti-Spam]: Event dropped (Throttle window < 250ms).");
			return;
		}
		scrollTimestamps.put(wv, now); 
		touchTarget.requestFocus();

		if (wv.getSettings().getJavaScriptEnabled()) {
			
			wv.evaluateJavascript(JS_UNIVERSAL_PAYLOAD, value -> {
				if (value != null && !value.equals("null")) Log.i(hostTag + "[REACTION] " + value.replace("\"", ""));
			});

			if (isMediaHost && !isInstagram) {
				Log.i(hostTag + "[ACTION] Media Host Detected: Bypassing JS Scroll entirely. Relying purely on God-Mode Hardware Swipe.");
			} else if (isInstagram) {
				String igJsScript = "(function() {" +
						"  try {" +
						"    var isDown = " + (!up) + ";" +
						"    var amount = isDown ? window.innerHeight * 0.85 : -window.innerHeight * 0.85;" +
						"    var containers = [document.documentElement, document.body, document.querySelector('main'), document.querySelector('main[role=\"main\"]') ? document.querySelector('main[role=\"main\"]').parentElement : null, document.querySelector('article')];" +
						"    containers.forEach(function(c) { if(c) { try { c.scrollBy({ top: amount, behavior: 'smooth' }); } catch(e){} } });" +
						"    window.scrollBy({ top: amount, behavior: 'smooth' });" +
						"    var activeNode = document.activeElement || document.body;" +
						"    try { var wheelEvt = new WheelEvent('wheel', { deltaY: amount, bubbles: true, cancelable: true }); activeNode.dispatchEvent(wheelEvt); } catch(wErr) {}" +
						"    try { var keyStr = isDown ? 'ArrowDown' : 'ArrowUp'; var keyCode = isDown ? 40 : 38; var kEvt = new KeyboardEvent('keydown', { key: keyStr, code: keyStr, keyCode: keyCode, which: keyCode, bubbles: true, cancelable: true }); activeNode.dispatchEvent(kEvt); } catch(kErr) {}" +
						"    return 'Scroll: IG Specific JS Scroll Executed on multiple containers.';" +
						"  } catch (err) { return 'Scroll Error: ' + err.message; }" +
						"})();";
				wv.evaluateJavascript(igJsScript, value -> {
					if (value != null && !value.equals("null")) Log.i(hostTag + "[REACTION] " + value.replace("\"", ""));
				});
			} else {
				String generalJsScript = "(function() {" +
						"  try {" +
						"    var amount = " + (!up) + " ? window.innerHeight * 0.85 : -window.innerHeight * 0.85;" +
						"    window.scrollBy({ top: amount, behavior: 'smooth' });" +
						"    return 'Scroll: General Webpage Smooth Scroll Executed.';" +
						"  } catch (err) { return 'Scroll Error: ' + err.message; }" +
						"})();";
				wv.evaluateJavascript(generalJsScript, value -> {
					if (value != null && !value.equals("null")) Log.i(hostTag + "[REACTION] " + value.replace("\"", ""));
				});
			}

			// ONLY dispatch backup keys if we are NOT using the Hardware Swipe (prevents double-scroll everywhere)
			if (!isMediaHost) {
				int backupKey = up ? KeyEvent.KEYCODE_PAGE_UP : KeyEvent.KEYCODE_PAGE_DOWN;
				wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, backupKey));
				wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, backupKey));
			}
		}

		// EXCLUSIVE God-Mode Hardware Swipe for Media Hosts (Bilibili, Reddit, Douyin, TikTok, etc.)
		if (isMediaHost && !isInstagram) {
			final float actionX = touchTarget.getWidth() * 0.60f; // Adjusted to 60%
			final float centerY = touchTarget.getHeight() / 2f;
			
			float span = touchTarget.getHeight() * 0.60f; // Adjusted to 60%
			final float yStart = up ? (centerY - span / 2f) : (centerY + span / 2f);
			final float yEnd = up ? (centerY + span / 2f) : (centerY - span / 2f);

			try {
				final long startTime = android.os.SystemClock.uptimeMillis();
				
				MotionEvent eventDown = MotionEvent.obtain(startTime, startTime, MotionEvent.ACTION_DOWN, actionX, yStart, 0);
				eventDown.setSource(InputDevice.SOURCE_TOUCHSCREEN); // Anti-Bot: Force hardware to report as human finger
				touchTarget.dispatchTouchEvent(eventDown);
				eventDown.recycle();

				final int stepCount = 13; // Refined for 60% distance
				final long swipeDuration = 170; // Refined for 60% distance
				
				for (int i = 1; i <= stepCount; i++) {
					final float linearT = (float) i / stepCount;
					
					final float currentY = yStart + (yEnd - yStart) * linearT;
					final long moveTime = startTime + (long) (swipeDuration * linearT);
					
					touchTarget.postDelayed(() -> {
						if (touchTarget.isAttachedToWindow() && touchTarget.getVisibility() == View.VISIBLE) {
							MotionEvent eventMove = MotionEvent.obtain(startTime, moveTime, MotionEvent.ACTION_MOVE, actionX, currentY, 0);
							eventMove.setSource(InputDevice.SOURCE_TOUCHSCREEN); // Anti-Bot evasion
							touchTarget.dispatchTouchEvent(eventMove);
							eventMove.recycle();
						}
					}, (long) (swipeDuration * linearT));
				}

				touchTarget.postDelayed(() -> {
					if (touchTarget.isAttachedToWindow() && touchTarget.getVisibility() == View.VISIBLE) {
						long endTime = startTime + swipeDuration + 10;
						MotionEvent eventUp = MotionEvent.obtain(startTime, endTime, MotionEvent.ACTION_UP, actionX, yEnd, 0);
						eventUp.setSource(InputDevice.SOURCE_TOUCHSCREEN); // Anti-Bot evasion
						touchTarget.dispatchTouchEvent(eventUp);
						eventUp.recycle();
						Log.i("[REACTION] " + hostTag + "God-Mode Hardware Swipe Concluded (ACTION_UP). Duration: " + swipeDuration + "ms | Steps: " + stepCount);
					}
				}, swipeDuration + 10);

			} catch (Exception e) {
				Log.e(e, "[REACTION] " + hostTag + "Hardware swipe failed with Exception.");
			}
		}
	}

	private static void performAction(Action action, MediaSessionCallback cb,
																		@Nullable MainActivityDelegate activity, long timestamp) {
		worker = null;
		action.getHandler().handle(cb, activity, timestamp);
	}
	
	// Dedicated Javascript Interface to safely pipe web touch coordinates/elements back to Java Log
	public static class PermataInspectorBridge {
		private final String webViewIdentifier;
		
		public PermataInspectorBridge(WebView webView) {
			this.webViewIdentifier = webView.getClass().getSimpleName() + " (Hash: " + Integer.toHexString(webView.hashCode()) + ")";
		}

		@android.webkit.JavascriptInterface
		public void recordTouch(String logData) {
			Log.w("[WEB_PROBE] [Android View: " + webViewIdentifier + "] " + logData);
		}
	}

	private static final class Worker implements Runnable {
		private final MediaSessionCallback cb;
		private final WeakReference<MainActivityDelegate> activityRef;
		private final Key key;
		private final Action clickAction;
		private final Action dblClickAction;
		private final Action longClickAction;
		private final long time;
		private long longClickTime;
		private boolean up;

		Worker(MediaSessionCallback cb, @Nullable MainActivityDelegate activity, Key key,
					 Action clickAction, Action dblClickAction, Action longClickAction) {
			this.cb = cb;
			this.activityRef = new WeakReference<>(activity);
			this.key = key;
			this.clickAction = clickAction;
			this.dblClickAction = dblClickAction;
			this.longClickAction = longClickAction;
			time = longClickTime = uptimeMillis();
			sched(DBL_CLICK_INTERVAL);
		}

		@Override
		public void run() {
			if (worker != this) return;
			if (up) {
				handle(clickAction);
				return;
			}

			long now = uptimeMillis();
			long diff = now - longClickTime;

			if (diff < LONG_CLICK_INTERVAL) {
				sched(LONG_CLICK_INTERVAL - diff);
			} else if (diff > 15000) { 
				worker = null;
			} else {
				longClickTime = time;
				handle(longClickAction);
				worker = this;
				sched(LONG_CLICK_INTERVAL);
			}
		}

		boolean handle(KeyEvent e) {
			if (e.getKeyCode() != key.getCode()) return false;

			switch (e.getAction()) {
				case ACTION_DOWN -> {
					if (!up) {
						if ((longClickAction == clickAction) || (longClickAction == Action.NONE)) {
							handle(clickAction);
						}
					}
					return true;
				}
				case ACTION_UP -> {
					long holdTime = uptimeMillis() - time;

					if (holdTime <= DBL_CLICK_INTERVAL) {
						if (up) {
							handle(dblClickAction);
						} else if (dblClickAction == clickAction) {
							handle(clickAction);
						} else {
							up = true;
						}
					} else if (holdTime >= LONG_CLICK_INTERVAL) {
						worker = null;
					} else {
						worker = null;
						if (longClickTime == time) {
							handle(clickAction);
						}
					}
					return true;
				}
				case ACTION_MULTIPLE -> {
					handle(dblClickAction);
					return true;
				}
			}
			return false;
		}

		private void handle(Action action) {
			MainActivityDelegate activity = activityRef.get();
			performAction(action, cb, activity, time);
		}

		private void sched(long delay) {
			MainActivityDelegate activity = activityRef.get();
			var handler = (activity == null) ? cb.getHandler() : activity.getHandler();
			handler.postDelayed(this, delay);
		}
	}
}