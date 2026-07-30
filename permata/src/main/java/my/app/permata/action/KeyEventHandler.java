package my.app.permata.action;

import static android.os.SystemClock.uptimeMillis;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_MULTIPLE;
import static android.view.KeyEvent.ACTION_UP;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
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
	
	// Set to TRUE to arm the DOM inspector & DOM Scanner for debugging web layouts.
	public static final boolean ENABLE_WEB_PROBE = true;

	private static final int DBL_CLICK_INTERVAL = 500;
	private static final int LONG_CLICK_INTERVAL = 1000;

	private static volatile Worker worker;
	private static volatile long lastGlobalActionTime = 0L;
	private static final Map<View, Long> scrollTimestamps = Collections.synchronizedMap(new WeakHashMap<>());

	// The Dedicated Universal Telemetry Probe & Web Page Investigator
	private static final String JS_TELEMETRY_PROBE = "(function() { " +
			"  if (window.__permataProbeActive) return; " +
			"  window.__permataProbeActive = true; " +
			"  window.__permataLastTouch = 0; " + 
			"  window.__permataScanDone = false; " +
			"  const scanDOMControls = function() { " +
			"      try { " +
			"          let elements = document.querySelectorAll('button, [role=\"button\"], [class*=\"btn\"], [class*=\"button\"], [class*=\"control\"], [class*=\"fullscreen\"], [class*=\"wide\"], [class*=\"clear\"], [class*=\"pure\"], [class*=\"screen\"], [id*=\"btn\"], [id*=\"control\"], xg-icon, [tagName^=\"XG-\"], [class*=\"play\"]'); " +
			"          let found = []; " +
			"          for (let i = 0; i < elements.length; i++) { " +
			"              let el = elements[i]; " +
			"              let rect = el.getBoundingClientRect(); " +
			"              if (rect.width > 0 || rect.height > 0 || window.getComputedStyle(el).opacity === '0') { " +
			"                  let tag = el.tagName.toLowerCase(); " +
			"                  let id = el.id ? '#' + el.id : ''; " +
			"                  let cls = (el.className && typeof el.className === 'string') ? '.' + el.className.trim().split(/\\s+/).join('.') : ''; " +
			"                  let aria = el.getAttribute('aria-label') || el.getAttribute('data-tip') || ''; " +
			"                  let text = el.innerText ? el.innerText.trim().substring(0, 15).replace(/\\n/g, '') : ''; " +
			"                  let info = tag + id + cls; " +
			"                  if (aria) info += ' [aria: ' + aria + ']'; " +
			"                  if (text) info += ' [text: ' + text + ']'; " +
			"                  found.push(info); " +
			"              } " +
			"          } " +
			"          let unique = Array.from(new Set(found)); " +
			"          if (window.PermataGodMode && window.PermataGodMode.recordTouch) { " +
			"              window.PermataGodMode.recordTouch('[DOM_SCAN] Extracted ' + unique.length + ' potential controls: ' + unique.join(' || ')); " +
			"          } " +
			"      } catch(e) {} " +
			"  }; " +
			"  const recordEvent = function(e) { " +
			"      try { " +
			"          if (!window.__permataScanDone) { " +
			"              window.__permataScanDone = true; " +
			"              setTimeout(scanDOMControls, 1500); " + 
			"          } " +
			"          let now = Date.now(); " +
			"          if (now - window.__permataLastTouch < 50) return; " + 
			"          window.__permataLastTouch = now; " +
			"          " +
			"          let touch = e.touches ? e.touches[0] : e; " +
			"          let el = (e.composedPath && e.composedPath().length > 0) ? e.composedPath()[0] : e.target; " +
			"          let dpr = window.devicePixelRatio || 1; " +
			"          let nodeInfo = el ? el.tagName.toLowerCase() : 'unknown'; " +
			"          if (el && el.id) nodeInfo += '#' + el.id; " +
			"          if (el && typeof el.className === 'string' && el.className.trim()) nodeInfo += '.' + el.className.trim().split(/\\s+/).join('.'); " +
			"          " +
			"          let actionTarget = el && el.closest ? (el.closest('button, a, [role=\"button\"]') || el) : el; " +
			"          let actionInfo = (actionTarget !== el) ? (actionTarget.tagName.toLowerCase() + (actionTarget.id ? '#' + actionTarget.id : '')) : 'same'; " +
			"          " +
			"          let getPath = function(n) { let p=[]; while(n && n.nodeType===1 && n.tagName!=='BODY' && n.tagName!=='HTML'){ let s=n.tagName.toLowerCase(); if(n.id) { s+='#'+n.id; p.unshift(s); break; } else if(n.className && typeof n.className==='string') { s+='.'+n.className.trim().split(/\\s+/).join('.'); } p.unshift(s); n=n.parentNode; } return p.join(' > '); }; " +
			"          let cssPath = getPath(actionTarget); " +
			"          " +
			"          let textInfo = el && el.innerText ? el.innerText.trim().substring(0, 25).replace(/\\n/g, ' ') : ''; " +
			"          let rect = el ? el.getBoundingClientRect() : {width:0, height:0, top:0, left:0}; " +
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
			"          let msg = '[Host: ' + window.location.hostname + '] ' + " +
			"                    'Touch: Viewport(' + Math.round(touch.clientX) + ',' + Math.round(touch.clientY) + ') Page(' + Math.round(touch.pageX) + ',' + Math.round(touch.pageY) + ') DPR:' + dpr + ' | ' + " +
			"                    'Target: ' + nodeInfo + ' (ActionBtn: ' + actionInfo + ') Hitbox[' + Math.round(rect.width) + 'x' + Math.round(rect.height) + ' at X:' + Math.round(rect.left) + ' Y:' + Math.round(rect.top) + '] | ' + " +
			"                    'SelectorPath: ' + cssPath + ' | ' + " +
			"                    'Text: [' + textInfo + '] | ' + " +
			"                    'Media: ' + vidStats; " +
			"          if (window.PermataGodMode && window.PermataGodMode.recordTouch) { window.PermataGodMode.recordTouch(msg); } " +
			"      } catch(ex) {} " +
			"  }; " +
			"  window.addEventListener('pointerdown', recordEvent, {capture: true, passive: true}); " +
			"  window.addEventListener('touchstart', recordEvent, {capture: true, passive: true}); " +
			"  window.addEventListener('mousedown', recordEvent, {capture: true, passive: true}); " +
			"})();";

	// The standard UI execution payload with Real-Time Wide/Fullscreen Status Checks
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
			"const registry=[" +
			"    {name:\"douyin\",match:/douyin\\.com/,execute:function(){" +
			"      let player = document.querySelector('.xgplayer'); " +
			"      let btn = document.querySelector('xg-cssfullscreen, xg-fullscreen, .xgplayer-pagefull'); " +
			"      if (player && btn) { " +
			"        let isFs = player.classList.contains('xgplayer-pagefull-active') || " +
			"                   player.classList.contains('xgplayer-is-cssfullscreen') || " +
			"                   player.classList.contains('xgplayer-is-fullscreen') || " +
			"                   player.classList.contains('xgplayer-fullscreen-active') || " +
			"                   document.querySelector('.xgplayer-pagefull-active, .xgplayer-is-cssfullscreen, .xgplayer-is-fullscreen'); " +
			"        " +
			"        if (!isFs) { " +
			"          player.classList.remove('xgplayer-inactive'); " +
			"          player.classList.add('xgplayer-active'); " +
			"          let controls = document.querySelector('xg-controls'); " +
			"          if (controls) { controls.style.opacity = '1'; controls.style.pointerEvents = 'auto'; } " +
			"          " +
			"          let rect = btn.getBoundingClientRect(); " +
			"          if (rect.width > 0 && rect.height > 0) { " +
			"            let dpr = window.devicePixelRatio || 1; " +
			"            let x = (rect.left + (rect.width / 2)) * dpr; " +
			"            let y = (rect.top + (rect.height / 2)) * dpr; " +
			"            if (window.PermataGodMode) { " +
			"              window.PermataGodMode.requestHardwareTap(x, y); " +
			"              return 'DOUYIN: Wide/Fullscreen inactive -> Re-applied God-Mode Tap at X:' + Math.round(x) + ' Y:' + Math.round(y); " +
			"            } " +
			"          } " +
			"        } else { " +
			"          return 'DOUYIN: Status Check -> Wide/Fullscreen already active.'; " +
			"        } " +
			"      } " +
			"      return 'DOUYIN: Native Feed Layout Active'; " +
			"    }}," +
			"    {name:\"tiktok\",match:/tiktok\\.com/,execute:function(){" +
			"      var ttCss = ' [data-e2e=\"video-author-avatar\"], [data-e2e=\"nav-login\"], [class*=\"DivHeaderContainer\"], [class*=\"DivSideNavContainer\"], [class*=\"DivBottomContainer\"] { display: none !important; } [class*=\"DivMediaCardOverlay\"], [class*=\"DivOverlayBottomContent\"], [class*=\"DivCreatorInfoContainer\"], [class*=\"BasePlayerContainer\"]::after { pointer-events: none !important; } '; " +
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
				
				long currentUptime = android.os.SystemClock.uptimeMillis();
				if (currentUptime - lastGlobalActionTime < 250) {
					Log.w("[CHECK] Input dropped: Button spam detected (< 250ms)");
					return true;
				}
				lastGlobalActionTime = currentUptime;

				boolean isNext = (code == KeyEvent.KEYCODE_MEDIA_NEXT);
				
				ActivityFragment activeFragment = finalTargetActivity.getActiveFragment();
				if (activeFragment != null) {
					String className = activeFragment.getClass().getName();
					
					if (className.endsWith("WebBrowserFragment") && !className.endsWith("YoutubeFragment")) {
						
						finalTargetActivity.post(() -> {
							
							// ANR Defense: Deep Context Unwrap to verify Activity Lifecycle safely
							if (finalTargetActivity.getWindow() != null) {
								Context ctx = finalTargetActivity.getWindow().getContext();
								while (ctx instanceof ContextWrapper) {
									if (ctx instanceof Activity) {
										break;
									}
									ctx = ((ContextWrapper) ctx).getBaseContext();
								}
								if (ctx instanceof Activity) {
									Activity act = (Activity) ctx;
									if (act.isFinishing() || act.isDestroyed()) return;
								}
							}

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

									View touchTargetView = findTopmostTouchTarget(finalTargetActivity, resolvedWebView);

									try {
										resolvedWebView.addJavascriptInterface(new PermataGodModeBridge(touchTargetView), "PermataGodMode");
										if (ENABLE_WEB_PROBE) {
											resolvedWebView.evaluateJavascript(JS_TELEMETRY_PROBE, null);
											Log.w("[WEB_PROBE]", "Agent Armed successfully on: " + resolvedWebView.getClass().getSimpleName());
										}
									} catch (Exception e) {
										Log.e(e, "[WEB_PROBE] Failed to arm Javascript Agent.");
									}

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

	private static View findTopmostTouchTarget(MainActivityDelegate activity, WebView baseWebView) {
		if (activity == null || activity.getWindow() == null) return baseWebView;
		
		View decorView = activity.getWindow().getDecorView();
		int screenWidth = decorView.getWidth();
		int screenHeight = decorView.getHeight();
		
		if (screenWidth == 0 || screenHeight == 0) return baseWebView;
		
		long totalScreenArea = (long) screenWidth * screenHeight;
		long minValidArea = (long) (totalScreenArea * 0.20f);
		int minValidHeight = (int) (screenHeight * 0.80f);
		
		View topCandidate = scanHighestVisibleChild(decorView, minValidArea, minValidHeight);
		
		if (topCandidate != null && topCandidate.isShown()) {
			long viewArea = (long) topCandidate.getWidth() * topCandidate.getHeight();
			float areaPercentage = ((float) viewArea / totalScreenArea) * 100f;
			
			String layerName = topCandidate.getClass().getName();
			if (layerName.startsWith("android.widget.") || layerName.startsWith("android.view.")) {
				layerName = topCandidate.getClass().getSimpleName();
			}
			
			Log.i("[SURGERY] Target Locked -> Layer: [" + layerName + "]" + 
					" | Size: " + topCandidate.getWidth() + "x" + topCandidate.getHeight() +
					" | Area: " + String.format("%.1f", areaPercentage) + "%" +
					" | Alpha: " + topCandidate.getAlpha());
			
			return topCandidate;
		}
		
		Log.i("[SURGERY] Target Locked -> Layer: [Fallback Base WebView] | No overlay passed the filters.");
		return baseWebView;
	}

	private static View scanHighestVisibleChild(View parent, long minValidArea, int minValidHeight) {
		if (parent == null || !parent.isShown() || parent.getVisibility() != View.VISIBLE || parent.getAlpha() < 0.1f) {
			return null;
		}
		
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
		
		long viewArea = (long) parent.getWidth() * parent.getHeight();
		if (viewArea >= minValidArea || parent.getHeight() >= minValidHeight) {
			String className = parent.getClass().getSimpleName();
			boolean isGenericWrapper = className.equals("FrameLayout") || 
									   className.equals("LinearLayout") || 
									   className.equals("RelativeLayout") || 
									   className.equals("DecorView") ||
									   className.equals("ViewGroup") ||
									   className.equals("ViewStub");
									   
			if (!isGenericWrapper) {
				return parent;
			}
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

			if (!isMediaHost) {
				int backupKey = up ? KeyEvent.KEYCODE_PAGE_UP : KeyEvent.KEYCODE_PAGE_DOWN;
				wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, backupKey));
				wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, backupKey));
			}
		}

		if (isMediaHost && !isInstagram) {
			final float actionX = touchTarget.getWidth() * 0.50f; 
			final float centerY = touchTarget.getHeight() / 2f;
			
			float span = touchTarget.getHeight() * 0.60f; 
			final float yStart = up ? (centerY - span / 2f) : (centerY + span / 2f);
			final float yEnd = up ? (centerY + span / 2f) : (centerY - span / 2f);

			try {
				final long startTime = android.os.SystemClock.uptimeMillis();
				
				MotionEvent eventDown = MotionEvent.obtain(startTime, startTime, MotionEvent.ACTION_DOWN, actionX, yStart, 0);
				eventDown.setSource(InputDevice.SOURCE_TOUCHSCREEN); 
				touchTarget.dispatchTouchEvent(eventDown);
				eventDown.recycle();

				final int stepCount = 13; 
				final long swipeDuration = 170; 
				
				for (int i = 1; i <= stepCount; i++) {
					final float linearT = (float) i / stepCount;
					
					final float currentY = yStart + (yEnd - yStart) * linearT;
					final long moveTime = startTime + (long) (swipeDuration * linearT);
					
					touchTarget.postDelayed(() -> {
						if (touchTarget.isAttachedToWindow() && touchTarget.getVisibility() == View.VISIBLE) {
							MotionEvent eventMove = MotionEvent.obtain(startTime, moveTime, MotionEvent.ACTION_MOVE, actionX, currentY, 0);
							eventMove.setSource(InputDevice.SOURCE_TOUCHSCREEN); 
							touchTarget.dispatchTouchEvent(eventMove);
							eventMove.recycle();
						}
					}, (long) (swipeDuration * linearT));
				}

				touchTarget.postDelayed(() -> {
					if (touchTarget.isAttachedToWindow() && touchTarget.getVisibility() == View.VISIBLE) {
						long endTime = startTime + swipeDuration + 10;
						MotionEvent eventUp = MotionEvent.obtain(startTime, endTime, MotionEvent.ACTION_UP, actionX, yEnd, 0);
						eventUp.setSource(InputDevice.SOURCE_TOUCHSCREEN); 
						touchTarget.dispatchTouchEvent(eventUp);
						eventUp.recycle();
						Log.i("[REACTION] " + hostTag + "God-Mode Hardware Swipe Concluded (ACTION_UP). Duration: " + swipeDuration + "ms | Steps: " + stepCount);
					}
				}, swipeDuration + 10);

				touchTarget.postDelayed(() -> {
					if (wv != null && wv.getSettings().getJavaScriptEnabled()) {
						String resumeJs = "(function() { " +
								"  try { " +
								"    let vids = document.querySelectorAll('video'); " +
								"    vids.forEach(function(v) { " +
								"      if (v && v.paused) { " +
								"        let p = v.play(); " +
								"        if (p && p.catch) p.catch(function(e){}); " +
								"      } " +
								"    }); " +
								"  } catch(e) {} " +
								"})();";
						wv.evaluateJavascript(resumeJs, null);
					}
				}, swipeDuration + 300);

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
	
	public static class PermataGodModeBridge {
		private final WeakReference<View> targetViewRef;
		
		public PermataGodModeBridge(View targetView) {
			this.targetViewRef = new WeakReference<>(targetView);
		}

		@android.webkit.JavascriptInterface
		public void requestHardwareTap(float x, float y) {
			View target = targetViewRef.get();
			if (target == null) return;
			
			target.post(() -> {
				long now = android.os.SystemClock.uptimeMillis();
				
				MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
				down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
				target.dispatchTouchEvent(down);
				down.recycle();

				MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0);
				up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
				target.dispatchTouchEvent(up);
				up.recycle();
				
				Log.i("[SURGERY]", "Executed God-Mode Auto-Tap for Fullscreen at Physical Coordinates -> X:" + x + " Y:" + y);
			});
		}

		@android.webkit.JavascriptInterface
		public void recordTouch(String logData) {
			Log.w("[WEB_PROBE]", logData);
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