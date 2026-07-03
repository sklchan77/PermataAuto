package my.app.permata.action;

import static android.os.SystemClock.uptimeMillis;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_MULTIPLE;
import static android.view.KeyEvent.ACTION_UP;

import android.view.KeyEvent;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import my.app.permata.media.service.MediaSessionCallback;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.function.IntObjectFunction;
import my.app.utils.log.Log;

/**
 * High-Performance Automotive Input Engine optimized for IHU Display Layers.
 * Zero reflection-lock footprints to eliminate any possibility of ANR or deadlocks.
 */
public class KeyEventHandler {
	private static final int DBL_CLICK_INTERVAL = 500;
	private static final int LONG_CLICK_INTERVAL = 1000;

	private static Worker worker;

	// Optimization: Lightweight WeakReference UI-Cache to protect Main Thread cycles
	private static java.lang.ref.WeakReference<android.webkit.WebView> cachedWebViewRef;

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

		if (event.getAction() == ACTION_DOWN) {
			int checkCode = event.getKeyCode();
			if (checkCode == KeyEvent.KEYCODE_MEDIA_NEXT || checkCode == KeyEvent.KEYCODE_NAVIGATE_NEXT ||
				checkCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || checkCode == KeyEvent.KEYCODE_NAVIGATE_PREVIOUS) {
				
				android.webkit.WebView targetWebView = null;

				// 1. Check WeakReference Cache availability
				if (cachedWebViewRef != null) {
					targetWebView = cachedWebViewRef.get();
					if (targetWebView != null && (!targetWebView.isAttachedToWindow() || !targetWebView.isShown())) {
						targetWebView = null; 
					}
				}

				// 2. Wireless Android Auto Projected Safe Hierarchy Scan
				if (targetWebView == null && activity != null) {
					try {
						// Universally query active layouts safely inside MainCarActivity on the IHU display
						Object activeFragObj = activity.getActiveFragment();
						if (activeFragObj instanceof my.app.utils.ui.fragment.ActivityFragment activeFrag) {
							if (activeFrag.getView() != null) {
								targetWebView = findWebViewInHierarchy(activeFrag.getView());
							}
						}
					} catch (Exception ignored) {}

					// Window content tree backup scan if fragment matching is delayed
					if (targetWebView == null && activity.getContext() instanceof android.app.Activity) {
						try {
							android.view.View rootContent = ((android.app.Activity) activity.getContext()).findViewById(android.R.id.content);
							if (rootContent != null) {
								targetWebView = findWebViewInHierarchy(rootContent);
							}
						} catch (Exception ignored) {}
					}
				}

				// 3. Handheld physical mobile screen standalone fallback
				if (targetWebView == null) {
					try {
						androidx.appcompat.app.AppCompatActivity activeApp = my.app.permata.ui.activity.MainActivity.getActiveInstance();
						if (activeApp != null) {
							android.view.View rootContent = activeApp.findViewById(android.R.id.content);
							if (rootContent != null) {
								targetWebView = findWebViewInHierarchy(rootContent);
							}
						}
					} catch (Exception ignored) {}
				}

				// If found a valid WebView, process automotive navigation routing mapping
				if (targetWebView != null) {
					cachedWebViewRef = new java.lang.ref.WeakReference<>(targetWebView);
					
					final String currentUrl = targetWebView.getUrl();
					final String className = targetWebView.getClass().getName().toLowerCase();
					
					boolean isYoutube = (currentUrl != null && (currentUrl.contains("youtube.com") || currentUrl.contains("youtu.be")))
							|| className.contains("youtube");

					// Handle non-YouTube browser lists/pages (e.g. TikTok, general scrolling webs)
					if (!isYoutube) {
						final boolean isDown = (checkCode == KeyEvent.KEYCODE_MEDIA_NEXT || checkCode == KeyEvent.KEYCODE_NAVIGATE_NEXT);
						
						final int viewWidth = targetWebView.getWidth();
						final int viewHeight = targetWebView.getHeight();

						final String jsScript = "(function() {" +
								"  try {" +
								"    var isDown = " + isDown + ";" +
								"    var ihuWidth = " + viewWidth + ";" +
								"    var ihuHeight = " + viewHeight + ";" +
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
								"      return 'btn_click';" + 
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
								"    return 'js_scroll';" +
								"  } catch (err) {" +
								"    var fall = isDown ? ihuHeight : -ihuHeight;" +
								"    window.scrollBy(0, fall);" +
								"    return 'fallback_scroll';" +
								"  }" +
								"})();";

						final android.webkit.WebView finalWebView = targetWebView;

						targetWebView.post(new Runnable() {
							@Override
							public void run() {
								try {
									if (!finalWebView.isAttachedToWindow()) return;
									finalWebView.requestFocus();
									finalWebView.evaluateJavascript(jsScript, new android.webkit.ValueCallback<String>() {
										@Override
										public void onReceiveValue(String value) {
											String token = (value != null) ? value.replace("\"", "") : "";
											if ("btn_click".equals(token) || "js_scroll".equals(token)) {
												return;
											}
											// Localized virtual display touch engine fallback
											executePacedSwipeGesture(finalWebView, viewWidth, viewHeight, isDown);
										}
									});

								} catch (Exception ex) {
									Log.e("Error scrolling web viewport via steering control", ex);
								}
							}
						});
						return true; 
					}
				}
			}
		}

		if (worker != null) {
			if (worker.handle(event)) return true;
			worker = null;
			return false;
		}

		var code = event.getKeyCode();
		var k = Key.get(code);
		if (k == null) return defaultHandler.apply(code, event);

		if (!k.isMedia() && (activity != null) && (activity.getCurrentFocus() instanceof EditText)) {
			return defaultHandler.apply(code, event);
		}

		var dblClickAction = k.getDblClickAction();
		if (dblClickAction == null) return defaultHandler.apply(code, event);

		var action = event.getAction();
		if (action == ACTION_MULTIPLE) {
			performAction(dblClickAction, cb, activity, uptimeMillis());
			return true;
		}
		if (action != ACTION_DOWN) return defaultHandler.apply(code, event);

		var clickAction = k.getClickAction();
		if (clickAction == null) return defaultHandler.apply(code, event);
		var longClickAction = k.getLongClickAction();
		if (longClickAction == null) return defaultHandler.apply(code, event);

		if (((clickAction == dblClickAction) && (clickAction == longClickAction)) ||
				((dblClickAction == Action.NONE) && (longClickAction == Action.NONE))) {
			performAction(clickAction, cb, activity, uptimeMillis());
			return true;
		}

		worker = new Worker(cb, activity, k, clickAction, dblClickAction, longClickAction);
		return true;
	}

	/**
	 * Dispatches simulated MotionEvents directly onto the targeted WebView view model layout bounds.
	 * Decoupled from window managers to prevent bleeding interactions outside the active virtual car display.
	 */
	private static void executePacedSwipeGesture(final android.webkit.WebView webView, final int viewWidth, final int viewHeight, final boolean isDown) {
		if (webView == null || !webView.isAttachedToWindow() || viewWidth <= 0 || viewHeight <= 0) return;

		final float centerX = viewWidth / 2.0f;
		final float startY = viewHeight * (isDown ? 0.82f : 0.18f);
		final float endY = viewHeight * (isDown ? 0.18f : 0.82f);

		final long downTime = uptimeMillis();
		
		try {
			android.view.MotionEvent downEvent = android.view.MotionEvent.obtain(downTime, downTime, android.view.MotionEvent.ACTION_DOWN, centerX, startY, 0);
			webView.dispatchTouchEvent(downEvent);
			downEvent.recycle();
		} catch (Exception ignored) {}

		final int totalSteps = 12;
		final long gestureDuration = 240; 
		final long stepDelay = gestureDuration / totalSteps;

		final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
		
		for (int i = 1; i <= totalSteps; i++) {
			final int step = i;
			mainHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (webView == null || !webView.isAttachedToWindow()) return;
					
					float alpha = (float) step / totalSteps;
					float easeAlpha = (alpha < 0.5f) 
							? (4.0f * alpha * alpha * alpha) 
							: (1.0f - (float) Math.pow(-2.0f * alpha + 2.0f, 3.0f) / 2.0f);
					
					float interpolatedY = startY + (endY - startY) * easeAlpha;
					long frameTime = downTime + (step * stepDelay);
					
					try {
						android.view.MotionEvent moveEvent = android.view.MotionEvent.obtain(downTime, frameTime, android.view.MotionEvent.ACTION_MOVE, centerX, interpolatedY, 0);
						webView.dispatchTouchEvent(moveEvent);
						moveEvent.recycle();
						
						if (step == totalSteps) {
							android.view.MotionEvent upEvent = android.view.MotionEvent.obtain(downTime, frameTime + stepDelay, android.view.MotionEvent.ACTION_UP, centerX, endY, 0);
							webView.dispatchTouchEvent(upEvent);
							upEvent.recycle();
						}
					} catch (Exception ignored) {}
				}
			}, step * stepDelay); 
		}
	}

	private static @Nullable android.webkit.WebView findWebViewInHierarchy(android.view.View view) {
		if (view == null) return null;
		if (view instanceof android.webkit.WebView) {
			android.webkit.WebView webView = (android.webkit.WebView) view;
			if (webView.isShown() && (webView.getWidth() == 0 || webView.getWidth() > 100)) {
				return webView;
			}
		}
		if (view instanceof android.view.ViewGroup) {
			android.view.ViewGroup group = (android.view.ViewGroup) view;
			for (int i = 0; i < group.getChildCount(); i++) {
				android.webkit.WebView deepFound = findWebViewInHierarchy(group.getChildAt(i));
				if (deepFound != null) return deepFound;
			}
		}
		return null;
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
			if (handler != null) {
				handler.postDelayed(this, delay);
			}
		}
	}
}
