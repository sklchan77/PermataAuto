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
						reflectionInitialized = true; 
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

					if (cachedWebViewRef != null) {
						targetWebView = cachedWebViewRef.get();
						if (targetWebView != null && (!targetWebView.isAttachedToWindow() || !targetWebView.isShown())) {
							targetWebView = null; 
						}
					}

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

							// Streamlined DOM Verification Payload focusing on direct interaction interception
							final String jsScript = "(function() {" +
									"  try {" +
									"    var isDown = " + isDown + ";" +
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
									"    return 'trigger_swipe';" +
									"  } catch (err) {" +
									"    return 'trigger_swipe';" +
									"  }" +
									"})();";

							final android.webkit.WebView finalWebView = targetWebView;

							targetWebView.post(new Runnable() {
								@Override
								public void run() {
									try {
										finalWebView.requestFocus();
										
										// Process JS Engine evaluation with string isolation feedback rules
										finalWebView.evaluateJavascript(jsScript, new android.webkit.ValueCallback<String>() {
											@Override
											public void onReceiveValue(String value) {
												String token = (value != null) ? value.replace("\"", "") : "";
												
												// If explicit DOM navigation button is handled, stop here to avoid dual input collision
												if ("btn_click".equals(token)) {
													Log.i("KeyEventHandler", "Navigation executed via direct DOM element click.");
													return;
												}

												// Cleanly execute the hardware paced swipe gesture sequence
												executePacedSwipeGesture(viewWidth, viewHeight, absoluteX, absoluteY, isDown);
											}
										});

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

	/**
	 * Dispatches continuous incremental MotionEvents across real clock frame intervals via Handler tasks.
	 */
	private static void executePacedSwipeGesture(final int viewWidth, final int viewHeight, 
																							 final int absoluteX, final int absoluteY, final boolean isDown) {
		final float centerX = absoluteX + (viewWidth / 2.0f);
		final float startY = absoluteY + (viewHeight * (isDown ? 0.82f : 0.18f));
		final float endY = absoluteY + (viewHeight * (isDown ? 0.18f : 0.82f));

		final long downTime = uptimeMillis();
		
		// 1. Dispatch structural touch start down anchor
		invokeMotionEvent(downTime, downTime, android.view.MotionEvent.ACTION_DOWN, centerX, startY);

		final int totalSteps = 12;
		final long gestureDuration = 240; 
		final long stepDelay = gestureDuration / totalSteps; // Delay coordinates by ~20ms frames

		final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
		
		for (int i = 1; i <= totalSteps; i++) {
			final int step = i;
			mainHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					float alpha = (float) step / totalSteps;
					
					// Smooth cubic ease curve metrics to mimic physical finger dragging acceleration patterns
					float easeAlpha = (alpha < 0.5f) 
							? (4.0f * alpha * alpha * alpha) 
							: (1.0f - (float) Math.pow(-2.0f * alpha + 2.0f, 3.0f) / 2.0f);
					
					float interpolatedY = startY + (endY - startY) * easeAlpha;
					long frameTime = downTime + (step * stepDelay);
					
					// 2. Stream incremental moves separated by true wall-clock time
					invokeMotionEvent(downTime, frameTime, android.view.MotionEvent.ACTION_MOVE, centerX, interpolatedY);
					
					// 3. Complete gesture event and release pointer layout contact to engage scrolling inertia
					if (step == totalSteps) {
						invokeMotionEvent(downTime, frameTime + stepDelay, android.view.MotionEvent.ACTION_UP, centerX, endY);
					}
				}
			}, step * stepDelay); 
		}
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

	private static @Nullable android.webkit.WebView findWebViewInHierarchy(android.view.View view) {
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
