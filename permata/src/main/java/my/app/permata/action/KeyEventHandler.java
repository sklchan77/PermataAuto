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
		// --- CRITICAL AT THE TOP: FAIL-SAFE FRAGMENT-BASED VIEW INTERCEPTOR ---
		if (activity != null && event != null) {
			var code = event.getKeyCode();
			if (code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
				try {
					var manager = activity.getSupportFragmentManager();
					if (manager != null && manager.getFragments() != null && !manager.getFragments().isEmpty()) {
						var activeContext = manager.getFragments().get(0).getContext();
						var resources = activeContext != null ? activeContext.getResources() : null;

						if (resources != null) {
							int fragmentId = resources.getIdentifier("web_browser_fragment", "id", "my.app.permata.addon.web");
							if (fragmentId == 0) fragmentId = resources.getIdentifier("web_browser_fragment", "id", "my.app.permata");

							if (fragmentId != 0) {
								var webFrag = manager.findFragmentById(fragmentId);
								if (webFrag != null && webFrag.isVisible()) {
									int webViewId = resources.getIdentifier("browserWebView", "id", "my.app.permata.addon.web");
									if (webViewId == 0) webViewId = resources.getIdentifier("browserWebView", "id", "my.app.permata");

									var view = webFrag.getView();
									var liveWebView = (view != null && webViewId != 0) ? view.findViewById(webViewId) : null;
									
									// MULTI-VECTOR PLATFORM GATE: Must be rendered and holding focus
									if (liveWebView instanceof android.webkit.WebView v && liveWebView.isShown() && liveWebView.hasFocus()) {
										if (event.getAction() == ACTION_DOWN) {
											if (code == KeyEvent.KEYCODE_MEDIA_NEXT) {
												v.evaluateJavascript("window.scrollBy({ top: window.innerHeight, behavior: 'smooth' });", null);
											} else {
												v.evaluateJavascript("window.scrollBy({ top: -window.innerHeight, behavior: 'smooth' });", null);
											}
										}
										return true; // SAFE SHORT-CIRCUIT: Consumes DOWN and UP cycles completely
									}
								}
							}
						}
					}
				} catch (Exception e) {
					Log.e("KeyEventHandler fail-safe validation crash protected", e);
				}
			}
		}
		// --- END OF BROWSER INTERCEPTOR ---

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

		var targetCode = event.getKeyCode();
		var k = Key.get(targetCode);
		if (k == null) return defaultHandler.apply(targetCode, event);

		if (!k.isMedia() && (activity != null) && (activity.getCurrentFocus() instanceof EditText)) {
			return defaultHandler.apply(targetCode, event);
		}

		var dblClickAction = k.getDblClickAction();
		if (dblClickAction == null) return defaultHandler.apply(targetCode, event);

		var action = event.getAction();
		if (action == ACTION_MULTIPLE) {
			Log.i(k, " key double click");
			performAction(dblClickAction, cb, activity, uptimeMillis());
			return true;
		}
		if (action != ACTION_DOWN) return defaultHandler.apply(targetCode, event);

		var clickAction = k.getClickAction();
		if (clickAction == null) return defaultHandler.apply(targetCode, event);
		var longClickAction = k.getLongClickAction();
		if (longClickAction == null) return defaultHandler.apply(targetCode, event);

		if (((clickAction == dblClickAction) && (clickAction == longClickAction)) ||
				((dblClickAction == Action.NONE) && (longClickAction == Action.NONE))) {
			Log.i(k, " key click");
			performAction(clickAction, cb, activity, uptimeMillis());
			return true;
		}

		worker = new Worker(cb, activity, k, clickAction, dblClickAction, longClickAction);
		return true;
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
