package my.app.permata.action;

import static android.os.SystemClock.uptimeMillis;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_MULTIPLE;
import static android.view.KeyEvent.ACTION_UP;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

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

	private static volatile Worker worker;
	private static volatile long lastGlobalActionTime = 0L;
	private static volatile long lastAudioFlushTime = 0L;
	
	// ENTERPRISE HARDENING: Thread-safe global session tracker for the Kill Switch
	private static final AtomicLong swipeSessionId = new AtomicLong(0);
	
	// ENTERPRISE HARDENING: Reflection cache to prevent UI thread jank during rapid swiping
	private static final Map<Class<?>, Method> webViewMethodCache = new ConcurrentHashMap<>();
	
	private static final Map<View, Long> scrollTimestamps = Collections.synchronizedMap(new WeakHashMap<>());
	private static final ExecutorService audioResetExecutor = Executors.newSingleThreadExecutor();

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
			"    {name:\"douyin\",match:/douyin/,execute:function(){" +
			"      var dyFullscreenCss = ' video, xg-video-container, xg-video-wrapper { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 999999 !important; object-fit: cover !important; background-color: black !important; pointer-events: none !important; } '; " +
			"      return 'DOUYIN: ' + injectGlobalWipe() + ' | ' + injectSpecificWipe(dyFullscreenCss, 'permata-dy-fullscreen'); " +
			"    }}," +
			"    {name:\"tiktok\",match:/tiktok/,execute:function(){" +
			"      var ttCss = ' [data-e2e=\"video-author-avatar\"], [data-e2e=\"nav-login\"], [class*=\"DivHeaderContainer\"], [class*=\"DivSideNavContainer\"], [class*=\"DivBottomContainer\"] { display: none !important; } [class*=\"DivMediaCardOverlay\"], [class*=\"DivOverlayBottomContent\"], [class*=\"DivCreatorInfoContainer\"], [class*=\"BasePlayerContainer\"]::after { pointer-events: none !important; } '; " +
			"      return 'TIKTOK: ' + injectGlobalWipe() + ' | ' + injectSpecificWipe(ttCss, 'permata-tt-css'); " +
			"    }}," +
			"    {name:\"instagram\",match:/instagram/,execute:function(){" +
			"      var igCss = ' header, nav, [role=\"navigation\"] { display: none !important; pointer-events: none !important; opacity: 0 !important; visibility: hidden !important; } body, html { overflow: auto !important; touch-action: pan-y !important; } '; " +
			"      return 'INSTAGRAM: ' + injectSpecificWipe(igCss, 'permata-ig-css'); " +
			"    }}," +
			"    {name:\"youtube\",match:/(youtube|youtu)/,execute:function(){" +
			"      let ad=document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-skip-button');if(ad)ad.click();" +
			"      let dm=document.querySelectorAll('yt-button-renderer[id=\"dismiss-button\"],[aria-label=\"No thanks\"],[aria-label=\"Dismiss\"],.yt-spec-button-shape-next--text');" +
			"      dm.forEach(b=>{if(b.textContent&&(b.textContent.includes('No thanks')||b.textContent.includes('Skip')||b.textContent.includes('Dismiss')))b.click();});" +
			"      return 'YOUTUBE: Handled standard media skip.'; " +
			"    }}," +
			"    {name:\"bilibili\",match:/bilibili/,execute:function(){" +
			"      let pl=document.querySelector('.mplayer-play');if(pl&&pl.classList.contains('play'))pl.click();" +
			"      return 'BILIBILI: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"reddit\",match:/reddit/,execute:function(){" +
			"      if(document.body&&window.getComputedStyle(document.body).overflow==='hidden') document.body.style.overflow='auto';" +
			"      return 'REDDIT: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"moj\",match:/(mojapp|sharechat)/,execute:function(){" +
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
				
				// DSP Reset - Retained safely on a background thread to prevent car AudioFlinger deadlocks
				if (finalTargetActivity.getWindow() != null) {
					if (currentUptime - lastAudioFlushTime > 5000) {
						lastAudioFlushTime = currentUptime;
						flushAudioHardwareAsync(finalTargetActivity.getWindow().getContext().getApplicationContext(), "[MediaKey] ");
					} else {
						Log.i("[AUDIO_FLUSH] Hardware reset skipped (DSP cooldown active to prevent hardware crash).");
					}
				}

				boolean isNext = (code == KeyEvent.KEYCODE_MEDIA_NEXT);
				
				ActivityFragment activeFragment = finalTargetActivity.getActiveFragment();
				if (activeFragment != null) {
					String className = activeFragment.getClass().getName();
					
					if (className.endsWith("WebBrowserFragment") && !className.endsWith("YoutubeFragment")) {
						
						finalTargetActivity.post(() -> {
							
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
									Log.i("[DETECTION] " + hostTag + "Resolved. isMediaHost: " + isMediaHost + " | isSnapFeedHost: " + isSnapFeedHost + " | isTikTok: " + isTikTok + " | isDouyin: " + isDouyin);

									View touchTargetView = findTopmostTouchTarget(finalTargetActivity, resolvedWebView);
									smartScrollWebView(resolvedWebView, touchTargetView, !isNext, hostTag, isMediaHost, isInstagram, isSnapFeedHost, isTikTok, isDouyin);
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

	private static void flushAudioHardwareAsync(final Context applicationContext, final String hostTag) {
		if (applicationContext == null) return;
		
		audioResetExecutor.execute(() -> {
			try {
				Intent stopIntent = new Intent("my.app.permata.ACTION_STOP_SILENT_ANCHOR");
				stopIntent.setPackage(applicationContext.getPackageName());
				applicationContext.sendBroadcast(stopIntent);
				
				// 3000ms delay: Gives Android OS double the time to properly close and flush the AudioFlinger.
				Thread.sleep(3000);
				
				Intent startIntent = new Intent("my.app.permata.ACTION_START_SILENT_ANCHOR");
				startIntent.setPackage(applicationContext.getPackageName());
				applicationContext.sendBroadcast(startIntent);
				
				Log.i("[AUDIO_FLUSH] " + hostTag + "DSP audio buffer flushed successfully. Deadlocks avoided.");
				
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				Log.w("[AUDIO_FLUSH] DSP flush cycle interrupted.");
			} catch (Exception e) {
				Log.e(e, "[AUDIO_FLUSH] Failed to safely cycle audio DSP.");
			}
		});
	}

	private static WebView scanFragmentsForWebView(ActivityFragment activeFragment) {
		try {
			Class<?> fragClass = activeFragment.getClass();
			Method getWebViewMethod = webViewMethodCache.get(fragClass);
			
			if (getWebViewMethod == null) {
				getWebViewMethod = fragClass.getMethod("getWebView");
				webViewMethodCache.put(fragClass, getWebViewMethod);
			}
			
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

	private static void smartScrollWebView(final WebView wv, final View touchTarget, boolean up, final String hostTag, boolean isMediaHost, boolean isInstagram, boolean isSnapFeedHost, boolean isTikTok, boolean isDouyin) {
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
		
		// Generate a unique ID for this specific swipe event
		final long currentSessionId = swipeSessionId.incrementAndGet();

		if (wv.getSettings().getJavaScriptEnabled()) {
			
			wv.postDelayed(() -> {
				wv.evaluateJavascript(JS_UNIVERSAL_PAYLOAD, value -> {
					if (value != null && !value.equals("null")) Log.i(hostTag + "[REACTION] " + value.replace("\"", ""));
				});
			}, 220); 

			// =========================================================================================
			// [INVERSION OF CONTROL] 3000ms SUPPRESSION FIELD & DYNAMIC UNMUTE WATCHDOG
			// Splits payload: "Targeted Unmute" for Instagram, "Blanket Unmute" for Douyin & other hosts
			// =========================================================================================
			if (isMediaHost) {
				wv.postDelayed(() -> {
					if (swipeSessionId.get() != currentSessionId) return;

					String fireAndForgetJs;
					
					if (isInstagram) {
						// INSTAGRAM SPECIFIC: Only unmute the video with the largest on-screen bounding box area
						fireAndForgetJs = "(function() {" +
								"  try {" +
								"    window.__permataSwipeId = " + currentSessionId + ";" +
								"    var watcher = setInterval(function() {" +
								"      if (window.__permataSwipeId !== " + currentSessionId + ") {" +
								"        clearInterval(watcher);" +
								"        return;" + 
								"      }" +
								"      var v = document.getElementsByTagName('video');" +
								"      for(var i=0; i<v.length; i++) {" +
								"        v[i].playbackRate = 0.0;" + 
								"        v[i].muted = true;" + // AUDIO LOCK: Mute absolutely everything
								"        v[i].pause();" + 
								"      }" +
								"    }, 10);" + // SUPPRESSION FIELD
								"    setTimeout(function() {" +
								"      if (window.__permataSwipeId !== " + currentSessionId + ") return;" +
								"      clearInterval(watcher);" + 
								"      var v = document.getElementsByTagName('video');" +
								"      var activeVid = null;" +
								"      var maxArea = 0;" +
								"      for(var i=0; i<v.length; i++) {" +
								"        var rect = v[i].getBoundingClientRect();" +
								"        var vH = Math.min(rect.bottom, window.innerHeight) - Math.max(rect.top, 0);" +
								"        var vW = Math.min(rect.right, window.innerWidth) - Math.max(rect.left, 0);" +
								"        var area = (vH > 0 && vW > 0) ? (vH * vW) : 0;" +
								"        if (area > maxArea) {" +
								"          maxArea = area;" +
								"          activeVid = v[i];" + // Lock targeting onto the most visible video
								"        }" +
								"      }" +
								"      if (activeVid) {" +
								"        activeVid.playbackRate = 1.0;" + 
								"        try { activeVid.currentTime = activeVid.currentTime + 0.1; } catch(err) {}" + 
								"        var playPromise = activeVid.play();" + 
								"        if (playPromise !== undefined) { playPromise.catch(function(e){}); }" + 
								"        setTimeout(function() {" +
								"           if (window.__permataSwipeId !== " + currentSessionId + ") return;" +
								"           var unmuteChecks = 0;" +
								"           var unmuteWatchdog = setInterval(function() {" + // IG TARGETED UNMUTE WATCHDOG
								"               if (window.__permataSwipeId !== " + currentSessionId + " || unmuteChecks >= 30) {" +
								"                   clearInterval(unmuteWatchdog);" + // Self-destruct safely after 3 seconds
								"                   return;" +
								"               }" +
								"               if (activeVid.muted) activeVid.muted = false;" + // ONLY unmute the targeted video
								"               unmuteChecks++;" +
								"           }, 100);" + // Check every 100ms
								"        }, 200);" + // 200ms Audio Mute Shield
								"      }" +
								"    }, 3000);" + // DSP REBUILD MATCH
								"    return 'IG Targeted Suppression Field Injected';" +
								"  } catch(e) { return 'ERROR: ' + e.message; }" +
								"})();";
					} else if (isDouyin) {
						// DOUYIN STANDALONE: Blanket Play & Blanket Unmute to exploit native IntersectionObserver
						fireAndForgetJs = "(function() {" +
								"  try {" +
								"    window.__permataSwipeId = " + currentSessionId + ";" +
								"    var watcher = setInterval(function() {" +
								"      if (window.__permataSwipeId !== " + currentSessionId + ") {" +
								"        clearInterval(watcher);" +
								"        return;" + 
								"      }" +
								"      var v = document.getElementsByTagName('video');" +
								"      for(var i=0; i<v.length; i++) {" +
								"        v[i].playbackRate = 0.0;" + 
								"        v[i].muted = true;" + // AUDIO LOCK: Mute absolutely everything
								"        v[i].pause();" + 
								"      }" +
								"    }, 10);" + // SUPPRESSION FIELD
								"    setTimeout(function() {" +
								"      if (window.__permataSwipeId !== " + currentSessionId + ") return;" +
								"      clearInterval(watcher);" + 
								"      var v = document.getElementsByTagName('video');" +
								"      for(var i=0; i<v.length; i++) {" +
								"        v[i].playbackRate = 1.0;" + 
								"        try { v[i].currentTime = v[i].currentTime + 0.1; } catch(err) {}" + // BLANKET NUKE
								"        var playPromise = v[i].play();" + // BLANKET SLAM
								"        if (playPromise !== undefined) { playPromise.catch(function(e){}); }" + 
								"      }" +
								"      setTimeout(function() {" +
								"         if (window.__permataSwipeId !== " + currentSessionId + ") return;" +
								"         var unmuteChecks = 0;" +
								"         var unmuteWatchdog = setInterval(function() {" + // BLANKET UNMUTE WATCHDOG
								"             if (window.__permataSwipeId !== " + currentSessionId + " || unmuteChecks >= 30) {" +
								"                 clearInterval(unmuteWatchdog);" + // Self-destruct safely after 3 seconds
								"                 return;" +
								"             }" +
								"             var vids = document.getElementsByTagName('video');" +
								"             for(var j=0; j<vids.length; j++) {" +
								"                 if (vids[j].muted) vids[j].muted = false;" + // FORCE UNMUTE ALL
								"             }" +
								"             unmuteChecks++;" +
								"         }, 100);" + // Check every 100ms
								"      }, 200);" + // 200ms Audio Mute Shield
								"    }, 3000);" + // DSP REBUILD MATCH
								"    return 'Douyin Blanket Suppression Field Injected';" +
								"  } catch(e) { return 'ERROR: ' + e.message; }" +
								"})();";
					} else {
						// GLOBAL MEDIA HOST: Blanket Play & Blanket Unmute to exploit native IntersectionObserver
						fireAndForgetJs = "(function() {" +
								"  try {" +
								"    window.__permataSwipeId = " + currentSessionId + ";" +
								"    var watcher = setInterval(function() {" +
								"      if (window.__permataSwipeId !== " + currentSessionId + ") {" +
								"        clearInterval(watcher);" +
								"        return;" + 
								"      }" +
								"      var v = document.getElementsByTagName('video');" +
								"      for(var i=0; i<v.length; i++) {" +
								"        v[i].playbackRate = 0.0;" + 
								"        v[i].muted = true;" + // AUDIO LOCK: Mute absolutely everything
								"        v[i].pause();" + 
								"      }" +
								"    }, 10);" + // SUPPRESSION FIELD
								"    setTimeout(function() {" +
								"      if (window.__permataSwipeId !== " + currentSessionId + ") return;" +
								"      clearInterval(watcher);" + 
								"      var v = document.getElementsByTagName('video');" +
								"      for(var i=0; i<v.length; i++) {" +
								"        v[i].playbackRate = 1.0;" + 
								"        try { v[i].currentTime = v[i].currentTime + 0.1; } catch(err) {}" + // BLANKET NUKE
								"        var playPromise = v[i].play();" + // BLANKET SLAM
								"        if (playPromise !== undefined) { playPromise.catch(function(e){}); }" + 
								"      }" +
								"      setTimeout(function() {" +
								"         if (window.__permataSwipeId !== " + currentSessionId + ") return;" +
								"         var unmuteChecks = 0;" +
								"         var unmuteWatchdog = setInterval(function() {" + // GLOBAL BLANKET UNMUTE WATCHDOG
								"             if (window.__permataSwipeId !== " + currentSessionId + " || unmuteChecks >= 30) {" +
								"                 clearInterval(unmuteWatchdog);" + // Self-destruct safely after 3 seconds
								"                 return;" +
								"             }" +
								"             var vids = document.getElementsByTagName('video');" +
								"             for(var j=0; j<vids.length; j++) {" +
								"                 if (vids[j].muted) vids[j].muted = false;" + // FORCE UNMUTE ALL
								"             }" +
								"             unmuteChecks++;" +
								"         }, 100);" + // Check every 100ms
								"      }, 200);" + // 200ms Audio Mute Shield
								"    }, 3000);" + // DSP REBUILD MATCH
								"    return 'Global Blanket Suppression Field Injected';" +
								"  } catch(e) { return 'ERROR: ' + e.message; }" +
								"})();";
					}

					wv.evaluateJavascript(fireAndForgetJs, value -> {
						if (value != null) {
							Log.i(hostTag + "[AUDIO_RESYNC] JS Suppression Field Deployed (Session " + currentSessionId + "). Response: " + value.replace("\"", ""));
						}
					});
				}, 10); // BLEEDING EDGE INJECTION: Inject at exactly 10ms
			}
			// =========================================================================================

			if (isDouyin) {
				Log.i(hostTag + "[ACTION] Douyin Host Detected: Bypassing JS Scroll entirely. Relying purely on God-Mode Hardware Swipe.");
			} else if (isMediaHost && !isInstagram) {
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

		if (isDouyin) {
			// ---------------------------------------------------------
			// DOUYIN STANDALONE: God-Mode Hardware Swipe
			// ---------------------------------------------------------
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
						Log.i("[REACTION] " + hostTag + "Douyin Standalone Hardware Swipe Concluded (ACTION_UP). Duration: " + swipeDuration + "ms | Steps: " + stepCount);
					}
				}, swipeDuration + 10);

			} catch (Exception e) {
				Log.e(e, "[REACTION] " + hostTag + "Douyin standalone hardware swipe failed with Exception.");
			}
		} else if (isMediaHost && !isInstagram) {
			// ---------------------------------------------------------
			// GLOBAL MEDIA HOST: God-Mode Hardware Swipe
			// ---------------------------------------------------------
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
						Log.i("[REACTION] " + hostTag + "Global Hardware Swipe Concluded (ACTION_UP). Duration: " + swipeDuration + "ms | Steps: " + stepCount);
					}
				}, swipeDuration + 10);

			} catch (Exception e) {
				Log.e(e, "[REACTION] " + hostTag + "Global hardware swipe failed with Exception.");
			}
		}
	}

	private static void performAction(Action action, MediaSessionCallback cb,
																		@Nullable MainActivityDelegate activity, long timestamp) {
		worker = null;
		action.getHandler().handle(cb, activity, timestamp);
	}

	private static final class Worker implements Runnable {
		private final MediaSessionCallback cb;
		private final java.lang.ref.WeakReference<MainActivityDelegate> activityRef;
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
			this.activityRef = new java.lang.ref.WeakReference<>(activity);
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