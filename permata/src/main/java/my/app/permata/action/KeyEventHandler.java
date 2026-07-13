package my.app.permata.action;

import static android.os.SystemClock.uptimeMillis;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_MULTIPLE;
import static android.view.KeyEvent.ACTION_UP;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.annotation.Nullable;

import java.lang.reflect.Method;

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
				WebView webView = scanFragmentsForWebView(targetActivity);
				if (webView != null) {
					boolean isNext = (code == KeyEvent.KEYCODE_MEDIA_NEXT);
					
					// Dynamically target the FullScreenView if active, otherwise use normal WebView
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
									Log.i("Steering Wheel Scroll Intercepted. Targeting FullScreenView. Next: " + isNext);
								}
							} else {
								Log.i("Steering Wheel Scroll Intercepted. Targeting Normal WebView. Next: " + isNext);
							}
						}
					} catch (Exception e) {
						Log.e(e, "Reflection for FullScreenView failed, falling back to WebView.");
					}

					float centerX = targetView.getWidth() / 2f;
					float startY = isNext ? (targetView.getHeight() * 0.8f) : (targetView.getHeight() * 0.2f);
					float endY = isNext ? (targetView.getHeight() * 0.2f) : (targetView.getHeight() * 0.8f);

					long downTime = uptimeMillis();
					
					MotionEvent downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, centerX, startY, 0);
					targetView.dispatchTouchEvent(downEvent);
					downEvent.recycle();

					long moveTime = downTime + 30;
					MotionEvent moveEvent = MotionEvent.obtain(downTime, moveTime, MotionEvent.ACTION_MOVE, centerX, endY, 0);
					targetView.dispatchTouchEvent(moveEvent);
					moveEvent.recycle();

					long upTime = moveTime + 30;
					MotionEvent upEvent = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, centerX, endY, 0);
					targetView.dispatchTouchEvent(upEvent);
					upEvent.recycle();

					return true;
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

	private static WebView scanFragmentsForWebView(MainActivityDelegate activity) {
		try {
			ActivityFragment activeFragment = activity.getActiveFragment();
			if (activeFragment != null) {
				String className = activeFragment.getClass().getName();
				if (className.endsWith("WebBrowserFragment") && !className.endsWith("YoutubeFragment")) {
					Method getWebViewMethod = activeFragment.getClass().getMethod("getWebView");
					Object result = getWebViewMethod.invoke(activeFragment);
					if (result instanceof WebView) return (WebView) result;
				}
			}
		} catch (Exception e) {
			Log.e(e, "Failed to scan for WebView");
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