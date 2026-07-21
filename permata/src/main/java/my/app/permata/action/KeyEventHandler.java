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

	// === THE SHRINK-WRAPPED GLOBAL STYLESHEET & UNIVERSAL MEDIA CAPTURER ===
	private static final String JS_UNIVERSAL_PAYLOAD = "(function(){" +
			"let res = 'Discovery [Layer 2]: JS Registry Miss'; " +
			"const injectGlobalWipe = function() { " +
			"  if(!document.getElementById('permata-god-mode-css')) { " +
			"    const style = document.createElement('style'); " +
			"    style.id = 'permata-god-mode-css'; " +
			"    style.innerHTML = ` " +
			"      [class*=\"login\" i], [id*=\"login\" i], [class*=\"unauth\" i], " +
			"      [class*=\"app-open\" i], [class*=\"download\" i], [class*=\"promo\" i], " +
			"      [class*=\"banner\" i], [class*=\"popup\" i], [class*=\"sidebar\" i], " +
			"      [class*=\"overlay\" i], [class*=\"bottom\" i], [class*=\"action-bar\" i], " +
			"      [class*=\"comment\" i], [class*=\"account\" i], [class*=\"danmaku\" i], " +
			"      xg-controls, [class*=\"xgplayer-controls\"] { " +
			"          display: none !important; " +
			"          opacity: 0 !important; " +
			"          visibility: hidden !important; " +
			"          pointer-events: none !important; " +
			"      } " +
			"    `; " +
			"    document.head.appendChild(style); " +
			"    return 'Universal Wildcard CSS Injected.'; " +
			"  } return 'Universal Wildcard CSS Exists.'; " +
			"}; " +
			"const injectSpecificWipe = function(customCss, id) { " +
			"  if(!document.getElementById(id)) { " +
			"    const style = document.createElement('style'); " +
			"    style.id = id; style.innerHTML = customCss; " +
			"    document.head.appendChild(style); " +
			"    return 'Custom CSS (' + id + ') Injected.'; " +
			"  } return 'Custom CSS (' + id + ') Exists.'; " +
			"}; " +
			"window.__attemptFS = function() { " +
			"  if (window.location.hostname.indexOf('instagram.com') !== -1) return; " +
			"  let player = document.querySelector('.xgplayer, video, main'); " +
			"  if (player && !document.fullscreenElement && !document.webkitFullscreenElement) { " +
			"      try { player.requestFullscreen(); } catch(e) {} " +
			"  } " +
			"}; " +
			"window.__enforceFS = function() { " +
			"  if (window.location.hostname.indexOf('instagram.com') !== -1) return; " +
			"  let attempts = 0; " +
			"  let iv = setInterval(function() { " +
			"      let player = document.querySelector('.xgplayer, video, main'); " +
			"      if (player && !document.fullscreenElement && !document.webkitFullscreenElement) { " +
			"          try { player.requestFullscreen(); } catch(e) {} " +
			"      } else if (document.fullscreenElement || document.webkitFullscreenElement) { " +
			"          clearInterval(iv); " +
			"      } " +
			"      if (++attempts > 10) clearInterval(iv); " +
			"  }, 500); " +
			"}; " +
			"window.__permataTouchListener = function(e) { " +
			"  window.__attemptFS(); " +
			"  window.removeEventListener('touchend', window.__permataTouchListener); " +
			"  window.removeEventListener('mouseup', window.__permataTouchListener); " +
			"}; " +
			"window.addEventListener('touchend', window.__permataTouchListener, {once:true}); " +
			"window.addEventListener('mouseup', window.__permataTouchListener, {once:true}); " +
			"if (!window.__permataMediaCapturerBound) { " +
			"  window.__permataMediaCapturerBound = true; " +
			"  document.addEventListener('play', function(e) { " +
			"    if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) { " +
			"      if (window.AndroidMediaBridge && window.AndroidMediaBridge.onMediaPlay) window.AndroidMediaBridge.onMediaPlay(); " +
			"    } " +
			"  }, true); " +
			"  document.addEventListener('pause', function(e) { " +
			"    if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) { " +
			"      if (window.AndroidMediaBridge && window.AndroidMediaBridge.onMediaPause) window.AndroidMediaBridge.onMediaPause(); " +
			"    } " +
			"  }, true); " +
			"} " +
			"const registry=[" +
			"    {name:\"instagram\",match:/instagram\\.com/,execute:function(){" +
			"      var igCss = ' header, nav, [role=\"navigation\"] { display: none !important; pointer-events: none !important; opacity: 0 !important; visibility: hidden !important; } body, html { overflow: auto !important; touch-action: pan-y !important; } '; " +
			"      return 'INSTAGRAM: ' + injectSpecificWipe(igCss, 'permata-ig-css'); " +
			"    }}," +
			"    {name:\"youtube\",match:/(youtube\\.com|youtu\\.be)/,execute:function(){" +
			"      let ad=document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-skip-button');if(ad)ad.click();" +
			"      let dm=document.querySelectorAll('yt-button-renderer[id=\"dismiss-button\"],[aria-label=\"No thanks\"],[aria-label=\"Dismiss\"],.yt-spec-button-shape-next--text');" +
			"      dm.forEach(b=>{if(b.textContent&&(b.textContent.includes('No thanks')||b.textContent.includes('Skip')||b.textContent.includes('Dismiss')))b.click();});" +
			"      return 'YOUTUBE: ' + injectGlobalWipe(); " +
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
			"    try { res += ' || COMMON: ' + injectGlobalWipe(); } catch(e){} " +
			"  } " +
			"  return res;" +
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
			worker = null;
			return defaultHandler.apply(event.getKeyCode(), event);
		}

		if (worker != null) {
			if (worker.handle(event)) return true;
			worker = null;
			return false;
		}

		var code = event.getKeyCode();

		// === CAR IHU TARGET RESOLUTION & SCROLL INJECTION ===
		MainActivityDelegate targetActivity = activity;
		if (targetActivity == null && cb != null) {
			if (cb.getAssistant() instanceof MainActivityDelegate) {
				targetActivity = (MainActivityDelegate) cb.getAssistant();
			}
		}

		if (targetActivity != null && event.getAction() == ACTION_DOWN) {
			if (code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
				
				ActivityFragment activeFragment = targetActivity.getActiveFragment();
				if (activeFragment != null) {
					String className = activeFragment.getClass().getName();
					
					if (className.endsWith("WebBrowserFragment") && !className.endsWith("YoutubeFragment")) {
						boolean isNext = (code == KeyEvent.KEYCODE_MEDIA_NEXT);
						
						targetActivity.post(() -> {
							WebView webView = scanFragmentsForWebView(activeFragment);
							if (webView != null) {
								String currentUrl = webView.getUrl();
								String host = "unknown";
								boolean isMediaHost = false;
								boolean isInstagram = false;
								boolean isSnapFeedHost = false;
								
								if (currentUrl != null) {
									try {
										host = Uri.parse(currentUrl).getHost();
										if (host != null) {
											if (host.startsWith("www.")) host = host.substring(4);
											
											String h = host.toLowerCase();
											isInstagram = h.contains("instagram.com");
											
											// IDENTIFY HIGH-RISK DOUBLE SCROLL PLATFORMS
											isSnapFeedHost = h.contains("douyin") || h.contains("tiktok") ||
													h.contains("youtube") || h.contains("youtu") || h.contains("facebook") ||
													h.contains("kuaishou") || h.contains("xiaohongshu") ||
													h.contains("likee") || h.contains("kwai") || h.contains("snackvideo") ||
													h.contains("mojapp") || h.contains("sharechat");
													
											// IDENTIFY GENERAL MEDIA PLATFORMS (INCLUDING FEED HOSTS)
											isMediaHost = isInstagram || isSnapFeedHost || h.contains("bilibili") || 
													h.contains("reddit") || h.contains("twitter") || h.contains("x.com") || 
													h.contains("pinterest") || h.contains("twitch") || h.contains("weibo") || 
													h.contains("snapchat") || h.contains("vk");
										}
									} catch (Exception ignored) {}
								}
								final String hostTag = "[Host: " + host + "] ";
								Log.i("[DETECTION] " + hostTag + "Resolved. isMediaHost: " + isMediaHost + " | isSnapFeedHost: " + isSnapFeedHost);

								View touchTargetView = webView;
								
								if (isInstagram) {
									Log.i("[CHECK] Instagram detected. Overriding Reflection layer.");
								} else {
									try {
										Method getChromeClient = webView.getClass().getMethod("getWebChromeClient");
										Object chromeClient = getChromeClient.invoke(webView);
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

								smartScrollWebView(webView, touchTargetView, !isNext, hostTag, isMediaHost, isInstagram, isSnapFeedHost);
							}
						});
						
						return true;
					}
				}
			}
		}
		// ====================================================

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

	private static void smartScrollWebView(final WebView wv, final View touchTarget, boolean up, final String hostTag, boolean isMediaHost, boolean isInstagram, boolean isSnapFeedHost) {
		if (wv == null || touchTarget == null || !touchTarget.isAttachedToWindow() || touchTarget.getWidth() <= 0 || touchTarget.getHeight() <= 0) return;

		long now = android.os.SystemClock.uptimeMillis();
		Long lastClickTimeObj = scrollTimestamps.get(wv);
		long lastClickTime = (lastClickTimeObj != null) ? lastClickTimeObj : 0;
		
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
				
				wv.evaluateJavascript("if(typeof window.__attemptFS === 'function') window.__attemptFS();", null);
				
				if (!isSnapFeedHost) {
					Log.i("[ACTION] Injecting Wildcard Virtual Scroll JS Script...");
					String advancedJsScript = "(function() {" +
							"  try {" +
							"    var isDown = " + (!up) + ";" +
							"    var targetBtn = isDown ? " +
							"        document.querySelector('[class*=\"next\" i], [class*=\"down\" i], [aria-label*=\"next\" i]') : " +
							"        document.querySelector('[class*=\"prev\" i], [class*=\"up\" i], [aria-label*=\"prev\" i]');" +
							"    if (targetBtn) { targetBtn.click(); return 'Scroll: Wildcard Button Clicked'; }" +
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
				String igJsScript = "(function() {" +
						"  try {" +
						"    var isDown = " + (!up) + ";" +
						"    var amount = isDown ? window.innerHeight * 0.85 : -window.innerHeight * 0.85;" +
						"    window.scrollBy({ top: amount, behavior: 'smooth' });" +
						"    var activeNode = document.activeElement || document.body;" +
						"    try { var wheelEvt = new WheelEvent('wheel', { deltaY: amount, bubbles: true, cancelable: true }); activeNode.dispatchEvent(wheelEvt); } catch(wErr) {}" +
						"    try { var keyStr = isDown ? 'ArrowDown' : 'ArrowUp'; var keyCode = isDown ? 40 : 38; var kEvt = new KeyboardEvent('keydown', { key: keyStr, code: keyStr, keyCode: keyCode, which: keyCode, bubbles: true, cancelable: true }); activeNode.dispatchEvent(kEvt); } catch(kErr) {}" +
						"    return 'Scroll: IG Specific JS Scroll Executed.';" +
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

			int backupKey = up ? KeyEvent.KEYCODE_PAGE_UP : KeyEvent.KEYCODE_PAGE_DOWN;
			wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, backupKey));
			wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, backupKey));

		}

		// === THE HUMANIZED BIOMETRIC GOD-MODE SWIPE ENGINE ===
		if (isMediaHost && !isInstagram) {
			final float actionX = touchTarget.getWidth() * 0.50f;
			final float centerY = touchTarget.getHeight() / 2f;
			float span = touchTarget.getHeight() * 0.60f; 
			final float yStart = up ? (centerY - span / 2f) : (centerY + span / 2f);
			final float yEnd = up ? (centerY + span / 2f) : (centerY - span / 2f);

			try {
				final long startTime = android.os.SystemClock.uptimeMillis();
				
				MotionEvent eventDown = MotionEvent.obtain(startTime, startTime, MotionEvent.ACTION_DOWN, actionX, yStart, 0);
				touchTarget.dispatchTouchEvent(eventDown);
				eventDown.recycle();

				final int stepCount = 12;
				final long swipeDuration = 200; 
				
				for (int i = 1; i <= stepCount; i++) {
					final float linearT = (float) i / stepCount;
					final float easeOutT = 1f - (float) Math.pow(1f - linearT, 3);
					
					final float currentY = yStart + (yEnd - yStart) * easeOutT;
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
						
						wv.evaluateJavascript("if(typeof window.__enforceFS === 'function') window.__enforceFS();", null);
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