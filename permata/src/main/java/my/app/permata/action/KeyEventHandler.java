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

	// === THE PERMANENT GLOBAL STYLESHEET INJECTION & GESTURE STEALER ===
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
			"}; " +
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
			"const attemptFS = function() { " +
			"  let player = document.querySelector('.xgplayer, video, main'); " +
			"  if (player && !document.fullscreenElement && !document.webkitFullscreenElement) { " +
			"      try { player.requestFullscreen(); } catch(e) {} " +
			"  } " +
			"}; " +
			"window.__permataTouchListener = function(e) { " +
			"  attemptFS(); " +
			"  window.removeEventListener('touchend', window.__permataTouchListener); " +
			"  window.removeEventListener('mouseup', window.__permataTouchListener); " +
			"}; " +
			"window.addEventListener('touchend', window.__permataTouchListener, {once:true}); " +
			"window.addEventListener('mouseup', window.__permataTouchListener, {once:true}); " +
			"const registry=[" +
			"    {name:\"douyin\",match:/douyin\\.com/,execute:function(){" +
			"      return 'DOUYIN: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"tiktok\",match:/tiktok\\.com/,execute:function(){" +
			"      var ttCss = ' [data-e2e=\"video-author-avatar\"], [data-e2e=\"nav-login\"], [class*=\"DivHeaderContainer\"], [class*=\"DivSideNavContainer\"], [class*=\"DivBottomContainer\"] { display: none !important; pointer-events: none !important; } '; " +
			"      return 'TIKTOK: ' + injectGlobalWipe() + ' | ' + injectSpecificWipe(ttCss, 'permata-tt-css'); " +
			"    }}," +
			"    {name:\"instagram\",match:/instagram\\.com/,execute:function(){" +
			"      var igCss = ' header, nav, [role=\"navigation\"] { display: none !important; pointer-events: none !important; opacity: 0 !important; visibility: hidden !important; } body, html { overflow: auto !important; touch-action: pan-y !important; } '; " +
			"      return 'INSTAGRAM: ' + injectSpecificWipe(igCss, 'permata-ig-css'); " +
			"    }}," +
			"    {name:\"youtube\",match:/(youtube\\.com|youtu\\.be)/,execute:function(){" +
			"      let ad=document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-skip-button');if(ad)ad.click();" +
			"      let dm=document.querySelectorAll('yt-button-renderer[id=\"dismiss-button\"],[aria-label=\"No thanks\"],[aria-label=\"Dismiss\"],.yt-spec-button-shape-next--text');" +
			"      dm.forEach(b=>{if(b.textContent&&(b.textContent.includes('No thanks')||b.textContent.includes('Skip')||b.textContent.includes('Dismiss')))b.click();});" +
			"      return 'YOUTUBE: Handled standard media skip.'; " +
			"    }}," +
			"    {name:\"facebook\",match:/facebook\\.com/,execute:function(){" +
			"      return 'FACEBOOK: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"bilibili\",match:/bilibili\\.com/,execute:function(){" +
			"      let pl=document.querySelector('.mplayer-play');if(pl&&pl.classList.contains('play'))pl.click();" +
			"      return 'BILIBILI: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"kuaishou\",match:/kuaishou\\.com/,execute:function(){" +
			"      return 'KUAISHOU: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"xiaohongshu\",match:/xiaohongshu\\.com/,execute:function(){" +
			"      return 'XIAOHONGSHU: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"reddit\",match:/reddit\\.com/,execute:function(){" +
			"      if(document.body&&window.getComputedStyle(document.body).overflow==='hidden') document.body.style.overflow='auto';" +
			"      return 'REDDIT: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"x\",match:/(twitter\\.com|x\\.com)/,execute:function(){" +
			"      return 'X/TWITTER: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"pinterest\",match:/pinterest\\.com/,execute:function(){" +
			"      if(document.body) document.body.style.overflow='auto';" +
			"      return 'PINTEREST: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"twitch\",match:/twitch\\.tv/,execute:function(){" +
			"      return 'TWITCH: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"weibo\",match:/weibo\\.(com|cn)/,execute:function(){" +
			"      return 'WEIBO: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"snapchat\",match:/snapchat\\.com/,execute:function(){" +
			"      return 'SNAPCHAT: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"likee\",match:/likee\\.video/,execute:function(){" +
			"      return 'LIKEE: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"moj\",match:/(mojapp\\.in|sharechat\\.com)/,execute:function(){" +
			"      if(document.body) document.body.style.overflow='auto';" +
			"      return 'MOJ: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"vk\",match:/vk\\.com/,execute:function(){" +
			"      return 'VK: ' + injectGlobalWipe(); " +
			"    }}," +
			"    {name:\"kwai\",match:/(kwai\\.com|snackvideo\\.com)/,execute:function(){" +
			"      return 'KWAI: ' + injectGlobalWipe(); " +
			"    }}" +
			"  ];" +
			"  window.__permataActive = registry.find(p=>p.match.test(window.location.hostname));" +
			"  if (window.__permataActive) { " +
			"    res = 'Discovery [Layer 2]: JS Registry Match Success -> ' + window.__permataActive.name; " +
			"    try { let execRes = window.__permataActive.execute(); res += ' || ' + execRes; } catch(e){} " +
			"  } else { " +
			"    res = 'Discovery [Layer 2]: JS Registry Miss -> Executing Common Webpage Fallback'; " +
			"    var commonCss = ' header, footer, nav, aside, #cookie-notice, .cookie-banner, [id*=\"cookie\"], [class*=\"cookie\"], [id*=\"popup\"], [class*=\"popup\"], .floating-action-button { display: none !important; } '; " +
			"    try { let execRes = injectSpecificWipe(commonCss, 'permata-common-css'); res += ' || COMMON: ' + execRes; } catch(e){} " +
			"  } " +
			"  return res + ' || Script Payload Concluded';" +
			"})();";

	// Resilient polling container
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
			Log.i("[CHECK] Status: Event is canceled. [EXIT] Returning to default handler.");
			worker = null;
			return defaultHandler.apply(event.getKeyCode(), event);
		}

		if (worker != null) {
			Log.i("[CHECK] Status: Active worker found. [ACTION] Delegating event to worker.");
			if (worker.handle(event)) {
				Log.i("[EXIT] Worker consumed the event.");
				return true;
			}
			Log.i("[ACTION] Worker rejected event. Clearing worker.");
			worker = null;
			return false;
		}

		var code = event.getKeyCode();

		// === CAR IHU TARGET RESOLUTION & SCROLL INJECTION ===
		MainActivityDelegate targetActivity = activity;
		if (targetActivity == null && cb != null) {
			if (cb.getAssistant() instanceof MainActivityDelegate) {
				targetActivity = (MainActivityDelegate) cb.getAssistant();
				Log.i("[CHECK] targetActivity resolved from MediaSessionCallback assistant.");
			}
		}

		if (targetActivity != null && event.getAction() == ACTION_DOWN) {
			Log.i("[CHECK] Condition: targetActivity is valid and action is ACTION_DOWN.");
			if (code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
				Log.i("[DETECTION] KeyCode matched MEDIA_NEXT or MEDIA_PREVIOUS.");
				
				ActivityFragment activeFragment = targetActivity.getActiveFragment();
				if (activeFragment != null) {
					String className = activeFragment.getClass().getName();
					Log.i("[CHECK] Active fragment class name detected: " + className);
					
					if (className.endsWith("WebBrowserFragment") && !className.endsWith("YoutubeFragment")) {
						Log.i("[CHECK] Status: WebBrowserFragment confirmed.");
						boolean isNext = (code == KeyEvent.KEYCODE_MEDIA_NEXT);
						
						Log.i("[ACTION] Posting targetActivity WebBrowser extraction to main thread.");
						targetActivity.post(() -> {
							Log.i("[ENTRY] targetActivity.post Runnable executing.");
							WebView webView = scanFragmentsForWebView(activeFragment);
							if (webView != null) {
								Log.i("[CHECK] Status: WebView successfully extracted.");
								String currentUrl = webView.getUrl();
								String host = "unknown";
								boolean isMediaHost = false;
								boolean isInstagram = false;
								
								if (currentUrl != null) {
									try {
										host = Uri.parse(currentUrl).getHost();
										if (host != null) {
											if (host.startsWith("www.")) host = host.substring(4);
											
											String h = host.toLowerCase();
											isInstagram = h.contains("instagram.com");
											isMediaHost = isInstagram || h.contains("douyin") || h.contains("tiktok") ||
													h.contains("youtube") || h.contains("youtu") || h.contains("facebook") ||
													h.contains("bilibili") || h.contains("kuaishou") || h.contains("xiaohongshu") ||
													h.contains("reddit") || h.contains("twitter") || h.contains("x.com") ||
													h.contains("pinterest") || h.contains("twitch") || h.contains("weibo") ||
													h.contains("snapchat") || h.contains("likee") || h.contains("mojapp") ||
													h.contains("sharechat") || h.contains("vk") || h.contains("kwai") || h.contains("snackvideo");
										}
									} catch (Exception ignored) {}
								}
								final String hostTag = "[Host: " + host + "] ";
								Log.i("[DETECTION] " + hostTag + "Resolved. isMediaHost: " + isMediaHost);

								View touchTargetView = webView;
								
								// IG TARGET LOCK: Prevent targeting the detached FullScreenView layer which causes the scroll freeze
								if (isInstagram) {
									Log.i("[CHECK] Instagram detected. Overriding Reflection layer to enforce immutable WebView target link.");
								} else {
									try {
										Log.i("[ACTION] Attempting reflection to identify FullScreenView.");
										Method getChromeClient = webView.getClass().getMethod("getWebChromeClient");
										Object chromeClient = getChromeClient.invoke(webView);
										if (chromeClient != null) {
											Method isFullScreenMethod = chromeClient.getClass().getMethod("isFullScreen");
											boolean isFullScreen = (Boolean) isFullScreenMethod.invoke(chromeClient);
											Log.i("[CHECK] Reflection isFullScreen status: " + isFullScreen);
											
											if (isFullScreen) {
												Method getFullScreenViewMethod = chromeClient.getClass().getMethod("getFullScreenView");
												View fullScreenView = (View) getFullScreenViewMethod.invoke(chromeClient);
												if (fullScreenView != null && fullScreenView.getVisibility() == View.VISIBLE) {
													touchTargetView = fullScreenView;
													Log.i("[REACTION] " + hostTag + "Discovery: Target Layout locked to FullScreenView.");
												}
											}
										}
									} catch (Exception e) {
										Log.e(e, "[REACTION] " + hostTag + "Discovery: Reflection failed, defaulting to base WebView.");
									}
								}

								Log.i("[ACTION] Triggering smartScrollWebView.");
								smartScrollWebView(webView, touchTargetView, !isNext, hostTag, isMediaHost);
							} else {
								Log.i("[CHECK] Status: WebView extraction returned null. Aborting intercept.");
							}
							Log.i("[EXIT] targetActivity.post Runnable finished.");
						});
						
						Log.i("[EXIT] Returning true. Key event fully intercepted by God Mode logic.");
						return true;
					} else {
						Log.i("[CHECK] Status: Fragment did not match WebBrowserFragment criteria.");
					}
				} else {
					Log.i("[CHECK] Status: activeFragment is null.");
				}
			}
		}
		// ====================================================

		var k = Key.get(code);
		if (k == null) {
			Log.i("[CHECK] Key definition not found. [EXIT] Returning default handler.");
			return defaultHandler.apply(code, event);
		}

		if (!k.isMedia() && (targetActivity != null) && (targetActivity.getCurrentFocus() instanceof EditText)) {
			Log.i("[CHECK] Editing text. [EXIT] Returning default handler.");
			return defaultHandler.apply(code, event);
		}

		var dblClickAction = k.getDblClickAction();
		if (dblClickAction == null) {
			Log.i("[CHECK] No Double Click Action mapped. [EXIT] Returning default handler.");
			return defaultHandler.apply(code, event);
		}

		var action = event.getAction();
		if (action == ACTION_MULTIPLE) {
			Log.i("[DETECTION] ACTION_MULTIPLE detected.");
			Log.i(k, " key double click");
			performAction(dblClickAction, cb, targetActivity, uptimeMillis());
			return true;
		}
		
		if (action != ACTION_DOWN) {
			Log.i("[CHECK] Action is not ACTION_DOWN. [EXIT] Returning default handler.");
			return defaultHandler.apply(code, event);
		}

		var clickAction = k.getClickAction();
		if (clickAction == null) {
			Log.i("[CHECK] No Click Action mapped. [EXIT] Returning default handler.");
			return defaultHandler.apply(code, event);
		}
		
		var longClickAction = k.getLongClickAction();
		if (longClickAction == null) {
			Log.i("[CHECK] No Long Click Action mapped. [EXIT] Returning default handler.");
			return defaultHandler.apply(code, event);
		}

		if (((clickAction == dblClickAction) && (clickAction == longClickAction)) ||
				((dblClickAction == Action.NONE) && (longClickAction == Action.NONE))) {
			Log.i("[ACTION] Immediate singular click logic triggered.");
			Log.i(k, " key click");
			performAction(clickAction, cb, targetActivity, uptimeMillis());
			return true;
		}

		Log.i("[ACTION] Spawning new Worker thread to monitor click sequence.");
		worker = new Worker(cb, targetActivity, k, clickAction, dblClickAction, longClickAction);
		Log.i("[EXIT] handleKeyEvent completed successfully.");
		return true;
	}

	private static WebView scanFragmentsForWebView(ActivityFragment activeFragment) {
		Log.i("[ENTRY] scanFragmentsForWebView execution started.");
		try {
			Method getWebViewMethod = activeFragment.getClass().getMethod("getWebView");
			Object result = getWebViewMethod.invoke(activeFragment);
			if (result instanceof WebView) {
				Log.i("[REACTION] Discovery [Layer 1]: Target Fragment (WebBrowserFragment) matched successfully.");
				return (WebView) result;
			}
		} catch (Exception e) {
			Log.e(e, "[REACTION] Discovery: Failed to scan for WebView.");
		}
		Log.i("[EXIT] scanFragmentsForWebView returned null.");
		return null;
	}

	private static void smartScrollWebView(final WebView wv, final View touchTarget, boolean up, final String hostTag, boolean isMediaHost) {
		Log.i("[ENTRY] smartScrollWebView execution started. Direction Up: " + up + " | isMediaHost: " + isMediaHost);
		if (wv == null || touchTarget == null || !touchTarget.isAttachedToWindow() || touchTarget.getWidth() <= 0 || touchTarget.getHeight() <= 0) {
			Log.i("[CHECK] Status: Invalid WebView or touchTarget dimensions. [EXIT] Aborting smart scroll.");
			return;
		}

		long now = android.os.SystemClock.uptimeMillis();
		Long lastClickTimeObj = scrollTimestamps.get(wv);
		long lastClickTime = (lastClickTimeObj != null) ? lastClickTimeObj : 0;
		
		if (now - lastClickTime < 250) {
			Log.w("[CHECK] " + hostTag + "Scroll [Anti-Spam]: Event dropped (Throttle window < 250ms). [IDLE] Ignored.");
			return;
		}
		scrollTimestamps.put(wv, now); 
		touchTarget.requestFocus();
		Log.i("[ACTION] Target view focus requested. Evaluating Javascript states...");

		if (wv.getSettings().getJavaScriptEnabled()) {
			
			Log.i("[ACTION] Injecting JS_UNIVERSAL_PAYLOAD...");
			wv.evaluateJavascript(JS_UNIVERSAL_PAYLOAD, value -> {
				if (value != null && !value.equals("null")) Log.i(hostTag + "[REACTION] [EXECUTION STATUS] " + value.replace("\"", ""));
			});

			if (isMediaHost) {
				Log.i("[ACTION] Media Host Detected: Injecting Multi-Vector Virtual Scroll JS Script...");
				
				// IG Specific Override: Defer entirely to Hardware Swipe to prevent snap-scroll fights
				String advancedJsScript = "(function() {" +
						"  try {" +
						"    if (window.location.hostname.indexOf('instagram.com') !== -1) {" +
						"      if(document.body) { document.body.style.overflow = 'auto'; }" +
						"      return 'Scroll: JS Scroll Deferred to Java Hardware Swipe for IG Snap-Scroll stability.';" +
						"    }" +
						"    var isDown = " + (!up) + ";" +
						"    var targetBtn = isDown ? document.querySelector('.xgplayer-playswitch-next, .slide-down-btn, [aria-label=\"Next video\"], [data-e2e=\"arrow-down\"]') : document.querySelector('.xgplayer-playswitch-prev, .slide-up-btn, [aria-label=\"Previous video\"], [data-e2e=\"arrow-up\"]');" +
						"    if (targetBtn) { targetBtn.click(); return 'Scroll: Programmatic Button Clicked'; }" +
						"    var amount = isDown ? window.innerHeight * 0.90 : -window.innerHeight * 0.90;" +
						"    window.scrollBy({ top: amount, behavior: 'smooth' });" +
						"    var activeNode = document.activeElement || document.body;" +
						"    try {" +
						"      var wheelEvt = new WheelEvent('wheel', { deltaY: amount, bubbles: true, cancelable: true });" +
						"      activeNode.dispatchEvent(wheelEvt);" +
						"    } catch(wErr) {}" +
						"    try {" +
						"      var keyStr = isDown ? 'ArrowDown' : 'ArrowUp';" +
						"      var keyCode = isDown ? 40 : 38;" +
						"      var kEvt = new KeyboardEvent('keydown', { key: keyStr, code: keyStr, keyCode: keyCode, which: keyCode, bubbles: true, cancelable: true });" +
						"      activeNode.dispatchEvent(kEvt);" +
						"    } catch(kErr) {}" +
						"    return 'Scroll: Virtual Web API & Force Event Scroll Executed.';" +
						"  } catch (err) { return 'Scroll Error: ' + err.message; }" +
						"})();";
						
				wv.evaluateJavascript(advancedJsScript, value -> {
					if (value != null && !value.equals("null")) Log.i(hostTag + "[REACTION] " + value.replace("\"", ""));
				});

				Log.i("[ACTION] Queuing JS_POLLING_PAYLOAD to run in 1.5s.");
				wv.postDelayed(() -> {
					if (wv.isAttachedToWindow()) {
						Log.i("[ACTION] Executing delayed JS_POLLING_PAYLOAD.");
						wv.evaluateJavascript(JS_POLLING_PAYLOAD, null);
					} else {
						Log.i("[CHECK] Status: WebView detached. [EXIT] Aborting JS_POLLING_PAYLOAD.");
					}
				}, 1500);

			} else {
				Log.i("[ACTION] General Webpage Detected: Injecting Safe Standard Scroll JS Script...");
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

			Log.i("[ACTION] Dispatching Fallback KeyEvent Scroll.");
			int backupKey = up ? KeyEvent.KEYCODE_PAGE_UP : KeyEvent.KEYCODE_PAGE_DOWN;
			wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, backupKey));
			wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, backupKey));

		} else {
			Log.w("[CHECK] Status: JavaScript is disabled on this WebView. Bypassing JS injection.");
		}

		if (isMediaHost) {
			// 5. Dispatch the Physical Hardware Swipe (Provides the User Activation Token for Fullscreen)
			final float actionX = touchTarget.getWidth() * 0.50f;
			final float centerY = touchTarget.getHeight() / 2f;
			float span = touchTarget.getHeight() * 0.60f; 
			final float yStart = up ? (centerY - span / 2f) : (centerY + span / 2f);
			final float yEnd = up ? (centerY + span / 2f) : (centerY - span / 2f);

			try {
				Log.i("[ACTION] " + hostTag + "Dispatching Hardware Swipe to fulfill Token Security & Media Scroll.");
				final long startTime = android.os.SystemClock.uptimeMillis();
				
				MotionEvent eventDown = MotionEvent.obtain(startTime, startTime, MotionEvent.ACTION_DOWN, actionX, yStart, 0);
				touchTarget.dispatchTouchEvent(eventDown);
				eventDown.recycle();
				Log.i("[REACTION] Dispatching ACTION_DOWN at Y: " + yStart);

				final int stepCount = 5;
				final long swipeDuration = 150; 
				
				for (int i = 1; i <= stepCount; i++) {
					final float fraction = (float) i / stepCount;
					final float currentY = yStart + (yEnd - yStart) * fraction;
					final long moveTime = startTime + (long) (swipeDuration * fraction);
					
					final int stepId = i;
					touchTarget.postDelayed(() -> {
						if (touchTarget.isAttachedToWindow()) {
							MotionEvent eventMove = MotionEvent.obtain(startTime, moveTime, MotionEvent.ACTION_MOVE, actionX, currentY, 0);
							touchTarget.dispatchTouchEvent(eventMove);
							eventMove.recycle();
							Log.i("[REACTION] Hardware Swipe Step " + stepId + " Dispatched at Y: " + currentY);
						}
					}, (long) (swipeDuration * fraction));
				}

				// The ACTION_UP event here triggers the JS 'touchend' listener, granting Fullscreen
				touchTarget.postDelayed(() -> {
					if (touchTarget.isAttachedToWindow()) {
						long endTime = startTime + swipeDuration + 10;
						MotionEvent eventUp = MotionEvent.obtain(startTime, endTime, MotionEvent.ACTION_UP, actionX, yEnd, 0);
						touchTarget.dispatchTouchEvent(eventUp);
						eventUp.recycle();
						Log.i("[REACTION] " + hostTag + "Hardware Swipe Concluded (ACTION_UP). Fullscreen Token Stealer Executed.");
					} else {
						Log.i("[CHECK] Status: Target detached before ACTION_UP. Hardware Swipe aborted.");
					}
				}, swipeDuration + 10);

			} catch (Exception e) {
				Log.e(e, "[REACTION] " + hostTag + "Hardware swipe failed with Exception.");
			}
		} else {
			Log.i("[CHECK] Status: General Webpage Detected. [EXIT] Bypassing hardware swipe to prevent accidental clicks.");
		}
		
		Log.i("[EXIT] smartScrollWebView execution complete. Awaiting postDelayed runnables.");
	}

	private static void performAction(Action action, MediaSessionCallback cb,
																		@Nullable MainActivityDelegate activity, long timestamp) {
		Log.i("[ENTRY] performAction execution started.");
		worker = null;
		Log.i("[ACTION] Performing action ", action);
		action.getHandler().handle(cb, activity, timestamp);
		Log.i("[EXIT] performAction execution completed.");
	}

	private static final class Worker implements Runnable {
		private final MediaSessionCallback cb;
		@Nullable
		private final MainActivityDelegate activity;
		private final Key key;
		private final Action clickAction;
		private final Action dblClickAction;
		private final Action longClickAction;
		private final long time;
		private long longClickTime;
		private boolean up;


		Worker(MediaSessionCallback cb, @Nullable MainActivityDelegate activity, Key key,
					 Action clickAction, Action dblClickAction, Action longClickAction) {
			Log.i("[ENTRY] Worker Initialization.");
			this.cb = cb;
			this.activity = activity;
			this.key = key;
			this.clickAction = clickAction;
			this.dblClickAction = dblClickAction;
			this.longClickAction = longClickAction;
			time = longClickTime = uptimeMillis();
			sched(DBL_CLICK_INTERVAL);
			Log.i("[STANDBY] Worker scheduled with DBL_CLICK_INTERVAL. Idling...");
		}

		@Override
		public void run() {
			Log.i("[ENTRY] Worker run() triggered.");
			if (worker != this) {
				Log.i("[CHECK] Status: worker instance mismatch. [EXIT] Returning early.");
				return;
			}
			if (up) {
				Log.i("[CHECK] Status: 'up' flag is true. [ACTION] Triggering clickAction.");
				Log.i(key, " key click");
				handle(clickAction);
				return;
			}

			long now = uptimeMillis();
			long diff = now - longClickTime;
			Log.i("[CHECK] Checking time diff: " + diff + "ms");

			if (diff < LONG_CLICK_INTERVAL) {
				Log.i("[STANDBY] Time diff insufficient. Rescheduling for " + (LONG_CLICK_INTERVAL - diff) + "ms. Idling...");
				sched(LONG_CLICK_INTERVAL - diff);
			} else if (diff > 15000) { 
				Log.i("[CHECK] Status: diff > 15s. Key UP not received. [ACTION] Clearing worker.");
				worker = null;
			} else {
				longClickTime = time;
				Log.i("[DETECTION] Long click thresholds met.");
				Log.i(key, " key long click");
				Log.i("[ACTION] Triggering longClickAction.");
				handle(longClickAction);
				worker = this;
				sched(LONG_CLICK_INTERVAL);
				Log.i("[STANDBY] Worker rescheduled with LONG_CLICK_INTERVAL. Idling...");
			}
		}

		boolean handle(KeyEvent e) {
			Log.i("[ENTRY] Worker handle(KeyEvent) triggered.");
			if (e.getKeyCode() != key.getCode()) {
				Log.i("[CHECK] KeyCode mismatch. [EXIT] Returning false.");
				return false;
			}

			switch (e.getAction()) {
				case ACTION_DOWN -> {
					Log.i("[DETECTION] Worker detected ACTION_DOWN.");
					if (!up) {
						if ((longClickAction == clickAction) || (longClickAction == Action.NONE)) {
							Log.i("[ACTION] Conditions met for standard key click inside ACTION_DOWN.");
							Log.i(key, " key click");
							handle(clickAction);
						}
					}
					return true;
				}
				case ACTION_UP -> {
					long holdTime = uptimeMillis() - time;
					Log.i("[DETECTION] Worker detected ACTION_UP. Hold time: " + holdTime + "ms");

					if (holdTime <= DBL_CLICK_INTERVAL) {
						if (up) {
							Log.i("[ACTION] Triggering dblClickAction.");
							Log.i(key, " key double click");
							handle(dblClickAction);
						} else if (dblClickAction == clickAction) {
							Log.i("[ACTION] Triggering clickAction.");
							Log.i(key, " key click");
							handle(clickAction);
						} else {
							Log.i("[ACTION] Setting 'up' flag to true. Awaiting next phase.");
							up = true;
						}
					} else if (holdTime >= LONG_CLICK_INTERVAL) {
						Log.i("[CHECK] Hold time exceeds LONG_CLICK_INTERVAL. [ACTION] Clearing worker.");
						worker = null;
					} else {
						Log.i("[ACTION] Clearing worker.");
						worker = null;
						if (longClickTime == time) {
							Log.i("[ACTION] Triggering clickAction based on longClickTime matching initial time.");
							Log.i(key, " key click");
							handle(clickAction);
						}
					}

					return true;
				}
				case ACTION_MULTIPLE -> {
					Log.i("[DETECTION] Worker detected ACTION_MULTIPLE.");
					Log.i("[ACTION] Triggering dblClickAction.");
					Log.i(key, " key double click");
					handle(dblClickAction);
					return true;
				}
			}
			Log.i("[EXIT] Worker handle(KeyEvent) fell through switch. Returning false.");
			return false;
		}

		private void handle(Action action) {
			Log.i("[ACTION] Worker internally delegating to performAction.");
			performAction(action, cb, activity, time);
		}

		private void sched(long delay) {
			Log.i("[IDLE] Worker schedule requested. Delay: " + delay + "ms");
			var handler = (activity == null) ? cb.getHandler() : activity.getHandler();
			handler.postDelayed(this, delay);
		}
	}
}