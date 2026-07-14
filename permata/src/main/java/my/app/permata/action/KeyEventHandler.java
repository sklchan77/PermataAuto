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

	// === ZERO-ALLOCATION JAVASCRIPT PAYLOADS ===
	// Storing these as static constants prevents the Garbage Collector from causing micro-stutters on IHUs
	private static final String JS_UNIVERSAL_PAYLOAD = "(function(){" +
			"let res = 'Discovery [Layer 2]: JS Registry Miss (No custom formatting applied)'; " +
			"if (!window.__permataDOMInit) {" +
			"  window.__permataDOMInit = true;" +
			"  const trigger = function(el) { if(!el) return; " +
			"    try { el.dispatchEvent(new TouchEvent('touchstart',{bubbles:true,cancelable:true})); el.dispatchEvent(new TouchEvent('touchend',{bubbles:true,cancelable:true})); } catch(e){} " +
			"    el.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true,view:window})); " +
			"    el.dispatchEvent(new MouseEvent('mouseup',{bubbles:true,cancelable:true,view:window})); " +
			"    el.click(); " +
			"  };" +
			"  const registry=[" +
			"    {name:\"douyin\",match:/douyin\\.com/,execute:function(){" +
			"      let fs=document.querySelector('xg-fullscreen, .xgplayer-fullscreen, [title*=\"全屏\"]');" +
			"      if(fs && !document.fullscreenElement && !fs.classList.contains('xgplayer-fullscreen-active') && fs.getAttribute('data-state') !== 'full') trigger(fs);" +
			"      let cl=document.querySelector('xg-clear-screen, .xgplayer-clearscreen, [title*=\"清屏\"]');" +
			"      if(cl && !cl.classList.contains('xgplayer-clearscreen-active') && cl.getAttribute('data-state') !== 'clear') trigger(cl);" +
			"      let lc=document.querySelector('.dy-account-close, .login-mask-enter-done .close, [class*=\"close-btn\"]');" +
			"      if(lc) trigger(lc);" +
			"    }}," +
			"    {name:\"tiktok\",match:/tiktok\\.com/,execute:function(){" +
			"      let cl=document.querySelector('[data-e2e=\"login-modal\"] button[class*=\"Close\"],div[class*=\"DivModalClose\"]');if(cl)trigger(cl);" +
			"      let mu=document.querySelector('[data-e2e=\"video-player-volume\"],[class*=\"volume\"] svg');" +
			"      if(mu&&(mu.innerHTML.includes('mute')||(mu.className.baseVal&&mu.className.baseVal.includes('mute')))){" +
			"        let vc=mu.closest('button')||mu.parentElement;if(vc)trigger(vc);" +
			"      }" +
			"      let th=document.querySelector('[data-e2e=\"browse-theatre-mode\"]');if(th)trigger(th);" +
			"    }}," +
			"    {name:\"instagram\",match:/instagram\\.com/,execute:function(){" +
			"      for(let b of document.querySelectorAll('button,div,span')){" +
			"        let t=b.textContent?b.textContent.trim():'';" +
			"        if(t==='Not Now'||t==='以后再说'||t==='Cancel')trigger(b);" +
			"      }" +
			"      let un=document.querySelectorAll('svg[aria-label*=\"Audio\"],svg[aria-label*=\"Mute\"]');" +
			"      un.forEach(s=>{" +
			"        let l=s.getAttribute('aria-label');" +
			"        if(l&&(l.includes('Mute')||l.includes('静音'))){let c=s.closest('button')||s.parentElement;if(c)trigger(c);}" +
			"      });" +
			"    }}," +
			"    {name:\"youtube\",match:/(youtube\\.com|youtu\\.be)/,execute:function(){" +
			"      let ad=document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-skip-button');if(ad)trigger(ad);" +
			"      let dm=document.querySelectorAll('yt-button-renderer[id=\"dismiss-button\"],[aria-label=\"No thanks\"],[aria-label=\"Dismiss\"],.yt-spec-button-shape-next--text');" +
			"      dm.forEach(b=>{if(b.textContent&&(b.textContent.includes('No thanks')||b.textContent.includes('Skip')||b.textContent.includes('Dismiss')))trigger(b);});" +
			"      let fs=document.querySelector('.ytp-fullscreen-button');" +
			"      if(fs&&fs.getAttribute('aria-label')&&fs.getAttribute('aria-label').includes('full screen'))trigger(fs);" +
			"    }}," +
			"    {name:\"facebook\",match:/facebook\\.com/,execute:function(){" +
			"      let co=document.querySelector('[data-cookiebanner=\"accept_button\"],[data-testid=\"cookie-policy-manage-dialog-accept\"]');if(co)trigger(co);" +
			"      let cd=document.querySelector('[aria-label=\"Close\"],[aria-label=\"关闭\"],[class*=\"layerCancel\"]');if(cd)trigger(cd);" +
			"    }}," +
			"    {name:\"bilibili\",match:/bilibili\\.com/,execute:function(){" +
			"      let fs=document.querySelector('.bilibili-player-video-btn-fullscreen,.sq-wrap,.m-bilibili-space-fullscreen,.mplayer-fullscreen');" +
			"      if(fs&&!fs.classList.contains('closed'))trigger(fs);" +
			"      let pl=document.querySelector('.mplayer-play');if(pl&&pl.classList.contains('play'))trigger(pl);" +
			"      let ab=document.querySelector('.m-home-float-openapp,.launch-app-btn,.open-app-btn');" +
			"      if(ab&&ab.parentElement)ab.parentElement.style.display='none';" +
			"    }}," +
			"    {name:\"kuaishou\",match:/kuaishou\\.com/,execute:function(){" +
			"      let fs=document.querySelector('[aria-label*=\"全屏\"],.fullscreen-icon');if(fs)trigger(fs);" +
			"      let cb=document.querySelector('.login-close,[class*=\"close-btn\"],[aria-label=\"关闭\"]');if(cb)trigger(cb);" +
			"    }}," +
			"    {name:\"xiaohongshu\",match:/xiaohongshu\\.com/,execute:function(){" +
			"      let ov=document.querySelectorAll('[class*=\"app-open\"],[class*=\"download-btn\"],[class*=\"login-box\"]');" +
			"      ov.forEach(el=>{el.style.display='none';});" +
			"      let cb=document.querySelector('.close-icon,[class*=\"close\"]');if(cb)trigger(cb);" +
			"    }}," +
			"    {name:\"reddit\",match:/reddit\\.com/,execute:function(){" +
			"      let pb=document.querySelectorAll('.XPromoPopup, [class*=\"bottom-bar\"], [class*=\"Prompt\"]');" +
			"      pb.forEach(el=>{el.style.display='none';});" +
			"      let xb=document.querySelector('button[aria-label=\"Close\"], button[aria-label=\"Dismiss\"]');if(xb)trigger(xb);" +
			"      if(document.body&&window.getComputedStyle(document.body).overflow==='hidden') document.body.style.overflow='auto';" +
			"    }}," +
			"    {name:\"x\",match:/(twitter\\.com|x\\.com)/,execute:function(){" +
			"      let bb=document.querySelector('[data-testid=\"BottomBar\"]');if(bb)bb.style.display='none';" +
			"      let cb=document.querySelector('[data-testid=\"app-bar-close\"]');if(cb)trigger(cb);" +
			"    }}," +
			"    {name:\"pinterest\",match:/pinterest\\.com/,execute:function(){" +
			"      let wb=document.querySelectorAll('[data-test-id=\"gift-wrap\"], .UnauthBanner, [data-test-id=\"signup-banner\"]');" +
			"      wb.forEach(el=>{el.style.display='none';});" +
			"      if(document.body) document.body.style.overflow='auto';" +
			"    }}," +
			"    {name:\"twitch\",match:/twitch\\.tv/,execute:function(){" +
			"      let ma=document.querySelector('[data-a-target=\"player-overlay-mature-accept\"]');if(ma)trigger(ma);" +
			"      let ap=document.querySelectorAll('.tw-bottom-0, .tw-fixed');" +
			"      ap.forEach(b=>{if(b.textContent&&b.textContent.includes('App'))b.style.display='none';});" +
			"    }}," +
			"    {name:\"weibo\",match:/weibo\\.(com|cn)/,execute:function(){" +
			"      let oa=document.querySelectorAll('.f-bg-toast, [class*=\"open-app\"], [class*=\"app-btn\"]');" +
			"      oa.forEach(el=>{el.style.display='none';});" +
			"    }}," +
			"    {name:\"snapchat\",match:/snapchat\\.com/,execute:function(){" +
			"      let ab=document.querySelector('.AppBanner, [class*=\"Banner\"], [class*=\"DownloadApp\"]');" +
			"      if(ab)ab.style.display='none';" +
			"      let ub=document.querySelector('[aria-label=\"Unmute\"], .unmute-icon');if(ub)trigger(ub);" +
			"    }}," +
			"    {name:\"likee\",match:/likee\\.video/,execute:function(){" +
			"      let dw=document.querySelector('.download-bar, [class*=\"Download\"], [class*=\"guide\"]');" +
			"      if(dw)dw.style.display='none';" +
			"      let cb=document.querySelector('.close-btn, [class*=\"close\"]');if(cb)trigger(cb);" +
			"    }}," +
			"    {name:\"moj\",match:/(mojapp\\.in|sharechat\\.com)/,execute:function(){" +
			"      let login=document.querySelector('.login-modal, [class*=\"LoginOverlay\"]');" +
			"      if(login)login.style.display='none';" +
			"      if(document.body) document.body.style.overflow='auto';" +
			"    }}," +
			"    {name:\"vk\",match:/vk\\.com/,execute:function(){" +
			"      let lb=document.querySelector('.UnauthBox, .box_layout, [id*=\"login\"]');" +
			"      if(lb)lb.style.display='none';" +
			"      let um=document.querySelector('.ShortsVideo__unmute, [class*=\"unmute\"]');if(um)trigger(um);" +
			"    }}," +
			"    {name:\"kwai\",match:/(kwai\\.com|snackvideo\\.com)/,execute:function(){" +
			"      let ob=document.querySelector('.open-app-bar, [class*=\"banner\"], .login-dialog');" +
			"      if(ob)ob.style.display='none';" +
			"    }}" +
			"  ];" +
			"  window.__permataActive = registry.find(p=>p.match.test(window.location.hostname));" +
			"  if (window.__permataActive) {" +
			"    const run = () => { try { window.__permataActive.execute(); } catch(e){} };" +
			"    let guard = null;" +
			"    const obs = new MutationObserver(() => { if(guard) clearTimeout(guard); guard = setTimeout(run, 100); });" +
			"    if(document.body) obs.observe(document.body, {childList:true, subtree:true});" +
			"    else window.addEventListener('DOMContentLoaded', () => obs.observe(document.body, {childList:true, subtree:true}));" +
			"  }" +
			"}" +
			"if (window.__permataActive) { " +
			"  res = 'Discovery [Layer 2]: JS Registry Match Success -> ' + window.__permataActive.name;" +
			"  try { window.__permataActive.execute(); } catch(e){} " +
			"}" +
			"return res;" +
			"})();";

	// Polls up to 8 times (4 seconds total) to catch Douyin resetting its UI on slow network buffering
	private static final String JS_POLLING_PAYLOAD = "try { " +
			"  if(window.__permataActive) { " +
			"    let attempts = 0; " +
			"    let interval = setInterval(() => { " +
			"      try { window.__permataActive.execute(); } catch(e){} " +
			"      attempts++; " +
			"      if(attempts >= 8) clearInterval(interval); " +
			"    }, 500); " +
			"  } " +
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
		Log.i((activity == null) ? "Media: " : "Activity: ", event);

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
								if (currentUrl != null) {
									try {
										host = Uri.parse(currentUrl).getHost();
										if (host != null && host.startsWith("www.")) host = host.substring(4);
									} catch (Exception ignored) {}
								}
								final String hostTag = "[Host: " + host + "] ";

								View targetView = webView;
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
												targetView = fullScreenView;
												Log.i(hostTag + "Discovery [Layer 3]: Target Layout is FullScreenView.");
											} else {
												Log.i(hostTag + "Discovery [Layer 3]: FullScreen detected but View is hidden/null. Targeting Normal WebView.");
											}
										} else {
											Log.i(hostTag + "Discovery [Layer 3]: Target Layout is Normal WebView.");
										}
									}
								} catch (Exception e) {
									Log.e(e, hostTag + "Discovery [Layer 3]: Reflection for FullScreenView failed, falling back to WebView.");
								}

								smartScrollWebView(webView, targetView, !isNext, -1f, -1f, hostTag);
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
			Log.i(k, " key double click");
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
			Log.i(k, " key click");
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
			if (result instanceof WebView) {
				Log.i("Discovery [Layer 1]: Target Fragment (WebBrowserFragment) matched successfully.");
				return (WebView) result;
			}
		} catch (Exception e) {
			Log.e(e, "Discovery [Layer 1]: Failed to scan for WebView via reflection.");
		}
		Log.w("Discovery [Layer 1]: WebBrowserFragment not found or no WebView returned.");
		return null;
	}

	private static void smartScrollWebView(final WebView wv, final View touchTarget, boolean up, float relativeX, float relativeY, final String hostTag) {
		if (wv == null || touchTarget == null || !touchTarget.isAttachedToWindow() || touchTarget.getWidth() <= 0 || touchTarget.getHeight() <= 0) {
			return;
		}

		long now = android.os.SystemClock.uptimeMillis();
		Long lastClickTimeObj = scrollTimestamps.get(touchTarget);
		long lastClickTime = (lastClickTimeObj != null) ? lastClickTimeObj : 0;
		
		if (now - lastClickTime < 250) {
			Log.w(hostTag + "Scroll [Anti-Spam]: Key event dropped to prevent ANR.");
			return;
		}
		scrollTimestamps.put(touchTarget, now); 
		
		touchTarget.requestFocus();

		if (wv.getSettings().getJavaScriptEnabled()) {
			wv.evaluateJavascript(JS_UNIVERSAL_PAYLOAD, value -> {
				if (value != null && !value.equals("null")) Log.i(hostTag + value.replace("\"", ""));
			});

			// Dynamic string building remains here because it depends on the "up" boolean direction
			String advancedJsScript = "(function() {" +
					"  try {" +
					"    var isDown = " + (!up) + ";" +
					"    var ihuWidth = window.innerWidth;" +
					"    var ihuHeight = window.innerHeight;" +
					"    var targetBtn = null;" +
					"    if (isDown) {" +
					"      targetBtn = document.querySelector('[data-e2e=\"arrow-down\"]') || " +
					"                  document.querySelector('.xgplayer-playswitch-next') || " +
					"                  document.querySelector('.slide-down-btn') || " +
					"                  document.querySelector('[aria-label=\"Next video\"]') || " +
					"                  document.querySelector('[aria-label=\"Next\"]');" +
					"    } else {" +
					"      targetBtn = document.querySelector('[data-e2e=\"arrow-up\"]') || " +
					"                  document.querySelector('.xgplayer-playswitch-prev') || " +
					"                  document.querySelector('.slide-up-btn') || " +
					"                  document.querySelector('[aria-label=\"Previous video\"]') || " +
					"                  document.querySelector('[aria-label=\"Go back\"]');" +
					"    }" +
					"    if (targetBtn) {" +
					"      targetBtn.click();" +
					"      return 'Scroll [Method 1]: Targeted Elements Programmatic Clicking Success.';" +
					"    }" +
					"    var scrollTarget = null;" +
					"    var elements = document.querySelectorAll('*');" +
					"    for (var i = 0; i < elements.length; i++) {" +
					"      var el = elements[i];" +
					"      var style = window.getComputedStyle(el);" +
					"      if ((style.overflowY === 'auto' || style.overflowY === 'scroll' || style.scrollSnapType !== 'none') && el.scrollHeight > el.clientHeight) {" +
					"        var rect = el.getBoundingClientRect();" +
					"        if (rect.width > ihuWidth * 0.3 && rect.height > ihuHeight * 0.3) {" +
					"          scrollTarget = el;" +
					"          break;" +
					"        }" +
					"      }" +
					"    }" +
					"    if (!scrollTarget) scrollTarget = document.querySelector('main') || document.body;" +
					"    var viewHeight = (scrollTarget === document.body) ? ihuHeight : scrollTarget.clientHeight;" +
					"    var amount = isDown ? (viewHeight * 0.90) : -(viewHeight * 0.90);" +
					"    var activeNode = document.activeElement || scrollTarget || document.body;" +
					"    try {" +
					"      var wheelEvt = new WheelEvent('wheel', { deltaY: amount, bubbles: true, cancelable: true });" +
					"      activeNode.dispatchEvent(wheelEvt);" +
					"    } catch(wErr) {}" +
					"    if (scrollTarget && scrollTarget.scrollBy) {" +
					"      scrollTarget.scrollBy({ top: amount, behavior: 'smooth' });" +
					"    } else {" +
					"      window.scrollBy({ top: amount, behavior: 'smooth' });" +
					"    }" +
					"    var keyStr = isDown ? 'ArrowDown' : 'ArrowUp';" +
					"    var keyCode = isDown ? 40 : 38;" +
					"    var kEvt = new KeyboardEvent('keydown', { key: keyStr, code: keyStr, keyCode: keyCode, window: window, bubbles: true, cancelable: true });" +
					"    activeNode.dispatchEvent(kEvt);" +
					"    return 'Scroll [Method 2/3]: Synthesized Virtual Input & Web API Scroll Executed.';" +
					"  } catch (err) {" +
					"    var fall = " + (!up) + " ? window.innerHeight : -window.innerHeight;" +
					"    window.scrollBy(0, fall);" +
					"    return 'Scroll [Method 3 Fallback]: JS Exception caught, executed global window.scrollBy.';" +
					"  }" +
					"})();";
					
			wv.evaluateJavascript(advancedJsScript, value -> {
				if (value != null && !value.equals("null")) Log.i(hostTag + value.replace("\"", ""));
			});
		}

		final float actionX = (relativeX >= 0) ? relativeX : (touchTarget.getWidth() * 0.50f);
		final float centerY = (relativeY >= 0) ? relativeY : (touchTarget.getHeight() / 2f);
		
		float span = touchTarget.getHeight() * 0.60f; 
		final float yStart = up ? (centerY - span / 2f) : (centerY + span / 2f);
		final float yEnd = up ? (centerY + span / 2f) : (centerY - span / 2f);

		try {
			Log.i(hostTag + "Scroll [Method 4]: Executing Hardware Touch Gesture Fallback (Multi-step simulated swipe).");
			final long startTime = android.os.SystemClock.uptimeMillis();
			
			MotionEvent eventDown = MotionEvent.obtain(startTime, startTime, MotionEvent.ACTION_DOWN, actionX, yStart, 0);
			touchTarget.dispatchTouchEvent(eventDown);
			eventDown.recycle();

			final int stepCount = 5;
			final long swipeDuration = 150; 
			
			for (int i = 1; i <= stepCount; i++) {
				final float fraction = (float) i / stepCount;
				final float currentY = yStart + (yEnd - yStart) * fraction;
				final long moveTime = startTime + (long) (swipeDuration * fraction);
				
				touchTarget.postDelayed(() -> {
					if (touchTarget.isAttachedToWindow()) {
						MotionEvent eventMove = MotionEvent.obtain(startTime, moveTime, MotionEvent.ACTION_MOVE, actionX, currentY, 0);
						touchTarget.dispatchTouchEvent(eventMove);
						eventMove.recycle();
					}
				}, (long) (swipeDuration * fraction));
			}

			touchTarget.postDelayed(() -> {
				if (touchTarget.isAttachedToWindow()) {
					long endTime = startTime + swipeDuration + 10;
					MotionEvent eventUp = MotionEvent.obtain(startTime, endTime, MotionEvent.ACTION_UP, actionX, yEnd, 0);
					touchTarget.dispatchTouchEvent(eventUp);
					eventUp.recycle();
				}
			}, swipeDuration + 10);

		} catch (Exception e) {
			Log.e(e, hostTag + "Scroll [Method 4]: Touch Gesture Failed. Executing KeyEvent PageUp/Down fallback.");
			int backupKey = up ? KeyEvent.KEYCODE_PAGE_UP : KeyEvent.KEYCODE_PAGE_DOWN;
			touchTarget.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, backupKey));
			touchTarget.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, backupKey));
		}

		Log.i(hostTag + "Scroll [Phase 4]: Dispatching Post-Scroll Formatting Polling (4.0s duration).");
		wv.postDelayed(() -> {
			if (wv.isAttachedToWindow() && wv.getSettings().getJavaScriptEnabled()) {
				wv.evaluateJavascript(JS_POLLING_PAYLOAD, null);
			}
		}, 400);
	}

	private static void performAction(Action action, MediaSessionCallback cb,
																		@Nullable MainActivityDelegate activity, long timestamp) {
		worker = null;
		Log.i("Performing action ", action);
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
				Log.i(key, " key click");
				handle(clickAction);
				return;
			}

			long now = uptimeMillis();
			long diff = now - longClickTime;

			if (diff < LONG_CLICK_INTERVAL) {
				sched(LONG_CLICK_INTERVAL - diff);
			} else if (diff > 15000) { // Key UP not received?
				worker = null;
			} else {
				longClickTime = time;
				Log.i(key, " key long click");
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
							Log.i(key, " key click");
							handle(clickAction);
						}
					}
					return true;
				}
				case ACTION_UP -> {
					long holdTime = uptimeMillis() - time;

					if (holdTime <= DBL_CLICK_INTERVAL) {
						if (up) {
							Log.i(key, " key double click");
							handle(dblClickAction);
						} else if (dblClickAction == clickAction) {
							Log.i(key, " key click");
							handle(clickAction);
						} else {
							up = true;
						}
					} else if (holdTime >= LONG_CLICK_INTERVAL) {
						worker = null;
					} else {
						worker = null;
						if (longClickTime == time) {
							Log.i(key, " key click");
							handle(clickAction);
						}
					}

					return true;
				}
				case ACTION_MULTIPLE -> {
					Log.i(key, " key double click");
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