package my.app.permata.action;

import static android.os.SystemClock.uptimeMillis;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_MULTIPLE;
import static android.view.KeyEvent.ACTION_UP;

import android.view.KeyEvent;
import android.widget.EditText;

import androidx.annotation.Nullable;

import my.app.permata.media.service.MediaSessionCallback;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.function.IntObjectFunction;
import my.app.utils.log.Log;

/**
 * High-Performance Media Event Controller optimized for physical automotive control rings.
 * Fully compatible with package-private access rules and aggressive ProGuard configurations.
 * * @author sklchan77
 */
public class KeyEventHandler {
	private static final int DBL_CLICK_INTERVAL = 500;
	private static final int LONG_CLICK_INTERVAL = 1000;

	private static Worker worker;

	// Optimization: Thread-Safe Double-Checked Reflection Fields
	private static Object cachedDispatcherInstance;
	private static java.lang.reflect.Method cachedMotionEventMethod;
	private static volatile boolean reflectionInitialized = false;

	// Optimization: Lightweight WeakReference UI-Cache to protect Main Thread cycles
	private static java.lang.ref.WeakReference<android.webkit.WebView> cachedWebViewRef;

	private static void invokeMotionEvent(long downTime, long eventTime, int action, float x, float y) {
		if (!reflectionInitialized) {
			synchronized (KeyEventHandler.class) {
				if (!reflectionInitialized) {
					try {
						Class<?> clazz = Class.forName("my.app.permata.auto.EventDispatcher");
						java.lang.reflect.Method getMethod = clazz.getDeclaredMethod("get");
						getMethod.setAccessible(true);
						cachedDispatcherInstance = getMethod.invoke(null);

						cachedMotionEventMethod = clazz.getDeclaredMethod("motionEvent", long.class, long.class, int.class, float.class, float.class);
						cachedMotionEventMethod.setAccessible(true);
					} catch (Exception e) {
						Log.e("Failed to bind to package-private EventDispatcher", e);
					} finally {
						reflectionInitialized = true; // Safe fallback guard to prevent repeating lookups on failure
					}
				}
			}
		}

		if (cachedMotionEventMethod != null && cachedDispatcherInstance != null) {
			try {
				cachedMotionEventMethod.invoke(cachedDispatcherInstance, downTime, eventTime, action, x, y);
			} catch (Exception e) {
				Log.e("Failed to execute remote motionEvent injection sequence", e);
			}
		}
	}

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

		// SURGICAL INTERCEPTION: Catch hardware wheel controls before structural views discard them
		if (event.getAction() == ACTION_DOWN) {
			int checkCode = event.getKeyCode();
			if (checkCode == KeyEvent.KEYCODE_MEDIA_NEXT || checkCode == KeyEvent.KEYCODE_NAVIGATE_NEXT ||
				checkCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || checkCode == KeyEvent.KEYCODE_NAVIGATE_PREVIOUS) {
				
				androidx.fragment.app.FragmentActivity targetActivity = null;
				if (activity != null && activity.getContext() instanceof androidx.fragment.app.FragmentActivity) {
					targetActivity = (androidx.fragment.app.FragmentActivity) activity.getContext();
				} else {
					androidx.appcompat.app.AppCompatActivity activeApp = my.app.permata.ui.activity.MainActivity.getActiveInstance();
					if (activeApp instanceof androidx.fragment.app.FragmentActivity) {
						targetActivity = (androidx.fragment.app.FragmentActivity) activeApp;
					}
				}

				if (targetActivity != null) {
					android.webkit.WebView targetWebView = null;

					// Optimization: Try recycling the cached view reference to maintain zero latency
					if (cachedWebViewRef != null) {
						targetWebView = cachedWebViewRef.get();
						if (targetWebView != null && (!targetWebView.isAttachedToWindow() || !targetWebView.isShown())) {
							targetWebView = null; // Clear if the cached component was detached or hidden
						}
					}

					// Cache miss: Execute your friend's robust 3-Tier Scan Strategy
					if (targetWebView == null) {
						final androidx.fragment.app.FragmentManager fragmentManager = targetActivity.getSupportFragmentManager();
						final int targetBrowserId = targetActivity.getResources().getIdentifier(
								"browserWebView", "id", targetActivity.getPackageName());

						targetWebView = scanFragmentsForWebView(fragmentManager.getFragments(), targetBrowserId);
						if (targetWebView != null) {
							cachedWebViewRef = new java.lang.ref.WeakReference<>(targetWebView);
						}
					}

					if (targetWebView != null) {
						final String currentUrl = targetWebView.getUrl();
						final String className = targetWebView.getClass().getName().toLowerCase();
						
						boolean isYoutube = (currentUrl != null && (currentUrl.contains("youtube.com") || currentUrl.contains("youtu.be")))
								|| className.contains("youtube");

						if (!isYoutube) {
							final boolean isDown = (checkCode == KeyEvent.KEYCODE_MEDIA_NEXT || checkCode == KeyEvent.KEYCODE_NAVIGATE_NEXT);
							
							final int viewWidth = targetWebView.getWidth();
							final int viewHeight = targetWebView.getHeight();
							final int[] screenLocation = new int[2];
							targetWebView.getLocationOnScreen(screenLocation);
							final int absoluteX = screenLocation[0];
							final int absoluteY = screenLocation[1];

							// Real-time Contextual JS Scrolling Engine
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
									"                  document.querySelector('.xgplayer-playswitch-prev']') || " +
									"                  document.querySelector('.slide-up-btn') || " +
									"                  document.querySelector('[aria-label=\"Previous video\"]') || " +
									"                  document.querySelector('[aria-label=\"Go back\"]');" +
									"    }" +
									"    if (targetBtn) {" +
									"      targetBtn.click();" +
									"      return;" +
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
									"  } catch (err) {" +
									"    var fall = isDown ? ihuHeight : -ihuHeight;" +
									"    window.scrollBy(0, fall);" +
									"  }" +
									"})();";

							targetWebView.post(new Runnable() {
								@Override
								public void run() {
									try {
										targetWebView.requestFocus();
										targetWebView.evaluateJavascript(jsScript, null);

										float centerX = absoluteX + (viewWidth / 2.0f);
										float startY = absoluteY + (viewHeight * (isDown ? 0.82f : 0.18f));
										float endY = absoluteY + (viewHeight * (isDown ? 0.18f : 0.82f));

										long downTime = uptimeMillis();
										invokeMotionEvent(downTime, downTime, android.view.MotionEvent.ACTION_DOWN, centerX, startY);

										int totalSteps = 10;
										long gestureDuration = 220; 
										
										for (int i = 1; i <= totalSteps; i++) {
											float alpha = (float) i / totalSteps;
											float easeAlpha = (alpha < 0.5f) ? (4.0f * alpha * alpha * alpha) : (1.0f - (float) Math.pow(-2.0f * alpha + 2.0f, 3.0f) / 2.0f);
											float interpolatedY = startY + (endY - startY) * easeAlpha;
											long frameTime = downTime + (long) (gestureDuration * alpha);
											
											invokeMotionEvent(downTime, frameTime, android.view.MotionEvent.ACTION_MOVE, centerX, interpolatedY);
										}

										invokeMotionEvent(downTime, downTime + gestureDuration + 10, android.view.MotionEvent.ACTION_UP, centerX, endY);

									} catch (Exception ex) {
										Log.e("Error executing advanced robust web scroll payload", ex);
									}
								}
							});
							return true; 
						}
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
			Log.i(k, " key double click");
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
			Log.i(k, " key click");
			performAction(clickAction, cb, activity, uptimeMillis());
			return true;
		}

		worker = new Worker(cb, activity, k, clickAction, dblClickAction, longClickAction);
		return true;
	}

	private static @Nullable android.webkit.WebView scanFragmentsForWebView(@Nullable java.util.List<androidx.fragment.app.Fragment> fragments, int targetBrowserId) {
		if (fragments == null) return null;
		
		for (androidx.fragment.app.Fragment f : fragments) {
			if (f != null && f.isAdded() && f.isVisible()) {
				android.view.View root = f.getView();
				if (root != null) {
					android.webkit.WebView matchedView = null;
					
					if (targetBrowserId != 0) {
						android.view.View found = root.findViewById(targetBrowserId);
						if (found instanceof android.webkit.WebView) {
							matchedView = (android.webkit.WebView) found;
						}
					}
					
					if (matchedView == null) {
						matchedView = findWebViewInHierarchy(root);
					}
					
					if (matchedView != null) {
						return matchedView;
					}
				}
				try {
					android.webkit.WebView nestedView = scanFragmentsForWebView(f.getChildFragmentManager().getFragments(), targetBrowserId);
					if (nestedView != null) return nestedView;
				} catch (Exception ignored) {}
			}
		}
		return null;
	}

	// Optimization: Size metrics filter applied to avoid hijacked selections on web tracking structures
	private static @Nullable android.webkit.WebView findWebViewInHierarchy(android.view.View view) {
		if (view instanceof android.webkit.WebView) {
			android.webkit.WebView webView = (android.webkit.WebView) view;
			// Ignore zero-dimension script containers, invisible trackers, or hidden side banners
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
				Log.i(key, " key click");
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