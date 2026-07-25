package my.app.permata.action;

import static android.os.SystemClock.uptimeMillis;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_MULTIPLE;
import static android.view.KeyEvent.ACTION_UP;

import android.net.Uri;
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
	
	// Enterprise Hardening: Synchronized map prevents ConcurrentModificationException across threads
	private static final Map<View, Long> scrollTimestamps = Collections.synchronizedMap(new WeakHashMap<>());

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
			"window.__attemptFS = function() { " +
			"  if (window.location.hostname.indexOf('instagram.com') !== -1) return; " +
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
			"  window.__attemptFS(); " +
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
			"      let dyWipe = injectGlobalWipe(); " +
			"      if (typeof window.__attemptFS === 'function') window.__attemptFS(); " +
			"      return 'DOUYIN FS Attempted | ' + dyWipe; " +
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

		// === DYNAMIC FOCUS GATEKEEPER ===
		if (targetActivity != null && event.getAction() == ACTION_DOWN) {
			if (code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
				boolean isNext = (code == KeyEvent.KEYCODE_MEDIA_NEXT);
				
				ActivityFragment activeFragment = targetActivity.getActiveFragment();
				if (activeFragment != null) {
					String className = activeFragment.getClass().getName();
					
					// Pre-qualification check ensures we only hijack if the intent is to browse the web
					if (className.endsWith("WebBrowserFragment") && !className.endsWith("YoutubeFragment")) {
						
						// Enterprise Hardening: All View Hierarchy access is posted safely to the Main UI Thread
						targetActivity.post(() -> {
							WebView resolvedWebView = scanFragmentsForWebView(activeFragment);
							boolean isExplicitWebFragment = (resolvedWebView != null);

							// Fallback: Scan the Window DecorView for any visible WebViews (Android Auto Fragment fixes)
							if (resolvedWebView == null || !resolvedWebView.isShown()) {
								resolvedWebView = findTopVisibleWebView(targetActivity);
							}

							// Focus Verification: Ensure we only hijack if the user is ACTUALLY interacting with it
							if (resolvedWebView != null && resolvedWebView.isShown()) {
								boolean hasTouchFocus = resolvedWebView.hasFocus();
								boolean isFullScreenSize = false;
								
								if (targetActivity.getWindow() != null && targetActivity.getWindow().getDecorView() != null) {
									int screenHeight = targetActivity.getWindow().getDecorView().getHeight();
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
									
									if (currentUrl != null) {
										try {
											host = Uri.parse(currentUrl).getHost();
											if (host != null) {
												if (host.startsWith("www.")) host = host.substring(4);
												
												String h = host.toLowerCase();
												isInstagram = h.contains("instagram.com");
												isTikTok = h.contains("tiktok");
												
												isSnapFeedHost = h.contains("youtube") || h.contains("youtu") || h.contains("facebook") ||
														h.contains("kuaishou") || h.contains("xiaohongshu") ||
														h.contains("likee") || h.contains("kwai") || h.contains("snackvideo") ||
														h.contains("mojapp") || h.contains("sharechat");
														
												isMediaHost = isInstagram || isSnapFeedHost || h.contains("bilibili") || 
														h.contains("reddit") || h.contains("twitter") || h.contains("x.com") || 
														h.contains("pinterest") || h.contains("twitch") || h.contains("weibo") || 
														h.contains("snapchat") || h.contains("vk") || h.contains("douyin") || isTikTok;
											}
										} catch (Exception ignored) {}
									}
									final String hostTag = "[Host: " + host + "] ";
									Log.i("[DETECTION] " + hostTag + "Resolved. isMediaHost: " + isMediaHost + " | isSnapFeedHost: " + isSnapFeedHost + " | isTikTok: " + isTikTok);

									View touchTargetView = resolvedWebView;
									
									if (isInstagram) {
										Log.i("[CHECK] Instagram detected. Overriding Reflection layer.");
									} else {
										try {
											Method getChromeClient = resolvedWebView.getClass().getMethod("getWebChromeClient");
											Object chromeClient = getChromeClient.invoke(resolvedWebView);
											if (chromeClient != null) {
												Method isFullScreenMethod = chromeClient.getClass().getMethod("isFullScreen");
												boolean isFullScreen = (Boolean) isFullScreenMethod.invoke(chromeClient);
												
												if (isFullScreen) {
													Method getFullScreenViewMethod = chromeClient.getClass().getMethod("getFullScreenView");
													View fullScreenView = (View) getFullScreenViewMethod.invoke(chromeClient);
													if (fullScreenView != null && fullScreenView.getVisibility() == View.VISIBLE) {
														touchTargetView = fullScreenView;
														Log.i("[REACTION] " + hostTag + "Target Layout locked to FullScreenView.");
													}
												}
											}
										} catch (Exception e) {
											Log.e(e, "[REACTION] " + hostTag + "Reflection failed, defaulting to base WebView.");
										}
									}

									smartScrollWebView(resolvedWebView, touchTargetView, !isNext, hostTag, isMediaHost, isInstagram, isSnapFeedHost, isTikTok);
								} else {
									Log.i("[DETECTION] WebBrowser is hidden or out of focus. Allowing default media propagation.");
								}
							}
						});
						
						return true; // We consume the event immediately to protect the UI Thread
					}
				}
			}
		}

		var k = Key.get(code);
		if (k == null) return defaultHandler.apply(code, event);

		if (!k.isMedia() && (targetActivity != null) && (targetActivity.getCurrentFocus() instanceof EditText)) {
			return defaultHandler.apply(code, event);
		}

		var dblClickAction = k.getDblClickAction();
		if (dblClickAction == null) return defaultHandler.apply(code, event);

		var action = event.getAction();
		if (action == ACTION_MULTIPLE) {
			performAction(dblClickAction, cb, targetActivity, uptimeMillis());
			return true;
		}
		
		if (action != ACTION_DOWN) return defaultHandler.apply(code, event);

		var clickAction = k.getClickAction();
		if (clickAction == null) return defaultHandler.apply(code, event);
		
		var longClickAction = k.getLongClickAction();
		if (longClickAction == null) return defaultHandler.apply(code, event);

		if (((clickAction == dblClickAction) && (clickAction == longClickAction)) ||
				((dblClickAction == Action.NONE) && (longClickAction == Action.NONE))) {
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

			if (isTikTok) {
				// TikTok exclusively relies on the High-Velocity Hardware Swipe to prevent the Zoom glitch.
				Log.i(hostTag + "[ACTION] TikTok Exclusive Detected: Bypassing JS Scroll entirely. Relying purely on Hardware Swipe.");
			} else if (isMediaHost && !isInstagram) {
				// Lock in Douyin and other standard media hosts
				if (!isSnapFeedHost) {
					Log.i("[ACTION] Injecting Virtual Scroll JS Script...");
					String advancedJsScript = "(function() {" +
							"  try {" +
							"    var isDown = " + (!up) + ";" +
							"    var targetBtn = isDown ? document.querySelector('.xgplayer-playswitch-next, .slide-down-btn, [aria-label=\"Next video\"]') : document.querySelector('.xgplayer-playswitch-prev, .slide-up-btn, [aria-label=\"Previous video\"]');" +
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
				} else {
					Log.i(hostTag + "[ACTION] Snap-Feed Host Detected: Bypassing JS Virtual Scroll. Using God Mode Swipe.");
				}

				wv.postDelayed(() -> {
					if (wv.isAttachedToWindow()) wv.evaluateJavascript(JS_POLLING_PAYLOAD, null);
				}, 1500);

			} else if (isInstagram) {
				// Lock in Instagram
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

			if (!isSnapFeedHost && !isTikTok) {
				int backupKey = up ? KeyEvent.KEYCODE_PAGE_UP : KeyEvent.KEYCODE_PAGE_DOWN;
				wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, backupKey));
				wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, backupKey));
			}
		}

		// === THE HIGH-VELOCITY FLING ALGORITHM (Pure Gestural Swipe Engine) ===
		// TikTok gets this block exclusively to prevent glitching. Douyin gets both. Instagram is bypassed.
		if (isMediaHost && !isInstagram) {
			final float actionX = touchTarget.getWidth() * 0.50f;
			final float centerY = touchTarget.getHeight() / 2f;
			
			float span = touchTarget.getHeight() * 0.65f; 
			final float yStart = up ? (centerY - span / 2f) : (centerY + span / 2f);
			final float yEnd = up ? (centerY + span / 2f) : (centerY - span / 2f);

			try {
				final long startTime = android.os.SystemClock.uptimeMillis();
				
				MotionEvent eventDown = MotionEvent.obtain(startTime, startTime, MotionEvent.ACTION_DOWN, actionX, yStart, 0);
				touchTarget.dispatchTouchEvent(eventDown);
				eventDown.recycle();

				wv.evaluateJavascript("if(typeof window.__attemptFS === 'function') window.__attemptFS();", null);

				final int stepCount = 10;
				final long swipeDuration = 120; 
				
				for (int i = 1; i <= stepCount; i++) {
					final float linearT = (float) i / stepCount;
					
					final float currentY = yStart + (yEnd - yStart) * linearT;
					final long moveTime = startTime + (long) (swipeDuration * linearT);
					
					touchTarget.postDelayed(() -> {
						if (touchTarget.isAttachedToWindow()) {
							MotionEvent eventMove = MotionEvent.obtain(startTime, moveTime, MotionEvent.ACTION_MOVE, actionX, currentY, 0);
							touchTarget.dispatchTouchEvent(eventMove);
							eventMove.recycle();
						}
					}, (long) (swipeDuration * linearT));
				}

				touchTarget.postDelayed(() -> {
					if (touchTarget.isAttachedToWindow()) {
						long endTime = startTime + swipeDuration + 10;
						MotionEvent eventUp = MotionEvent.obtain(startTime, endTime, MotionEvent.ACTION_UP, actionX, yEnd, 0);
						touchTarget.dispatchTouchEvent(eventUp);
						eventUp.recycle();
						Log.i("[REACTION] " + hostTag + "Hardware Swipe Concluded (ACTION_UP).");
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
			this.cb = cb;
			this.activity = activity;
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
			performAction(action, cb, activity, time);
		}

		private void sched(long delay) {
			var handler = (activity == null) ? cb.getHandler() : activity.getHandler();
			handler.postDelayed(this, delay);
		}
	}
}