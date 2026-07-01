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
		Log.i((activity == null) ? "Media: " : "Activity: ", event);

		if (event.isCanceled()) {
			worker = null;
			return defaultHandler.apply(event.getKeyCode(), event);
		}

		// SURGICAL INTERCEPTION: Intercept hardware media wheel controls before lower structures consume them
		if (activity != null && event.getAction() == ACTION_DOWN) {
			int checkCode = event.getKeyCode();
			if (checkCode == KeyEvent.KEYCODE_MEDIA_NEXT || checkCode == KeyEvent.KEYCODE_NAVIGATE_NEXT ||
				checkCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || checkCode == KeyEvent.KEYCODE_NAVIGATE_PREVIOUS) {
				
				final android.content.Context ctx = activity.getContext();
				if (ctx instanceof androidx.fragment.app.FragmentActivity) {
					final androidx.fragment.app.FragmentManager fragmentManager = 
							((androidx.fragment.app.FragmentActivity) ctx).getSupportFragmentManager();
					
					final int targetBrowserId = ctx.getResources().getIdentifier(
							"browserWebView", "id", ctx.getPackageName());

					// Dynamic extraction across parent stacks, custom segments, and deep nested components
					final android.webkit.WebView targetWebView = scanFragmentsForWebView(fragmentManager.getFragments(), targetBrowserId);

					if (targetWebView != null) {
						// Guard verification evaluating current URL tracking to preserve native YouTube features
						final String currentUrl = targetWebView.getUrl();
						final String className = targetWebView.getClass().getName().toLowerCase();
						
						boolean isYoutube = (currentUrl != null && (currentUrl.contains("youtube.com") || currentUrl.contains("youtu.be")))
								|| className.contains("youtube");

						if (!isYoutube) {
							final boolean isDown = (checkCode == KeyEvent.KEYCODE_MEDIA_NEXT || checkCode == KeyEvent.KEYCODE_NAVIGATE_NEXT);
							
							// Multi-tiered execution handling touch interactions, layout snaps, and dynamic lazy loading
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
									"      return;" +
									"    }" +
									"    var scrollTarget = null;" +
									"    var elements = document.querySelectorAll('*');" +
									"    for (var i = 0; i < elements.length; i++) {" +
									"      var el = elements[i];" +
									"      var style = window.getComputedStyle(el);" +
									"      if ((style.overflowY === 'auto' || style.overflowY === 'scroll' || style.scrollSnapType !== 'none') && el.scrollHeight > el.clientHeight) {" +
									"        var rect = el.getBoundingClientRect();" +
									"        if (rect.width > window.innerWidth * 0.3 && rect.height > window.innerHeight * 0.3) {" +
									"          scrollTarget = el;" +
									"          break;" +
									"        }" +
									"      }" +
									"    }" +
									"    if (!scrollTarget) scrollTarget = document.querySelector('main') || document.body;" +
									"    var viewHeight = (scrollTarget === document.body) ? window.innerHeight : scrollTarget.clientHeight;" +
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
									"    var fall = isDown ? window.innerHeight : -window.innerHeight;" +
									"    window.scrollBy(0, fall);" +
									"  }" +
									"})();";

							targetWebView.post(new Runnable() {
								@Override
								public void run() {
									try {
										targetWebView.requestFocus();
										targetWebView.evaluateJavascript(jsScript, null);
									} catch (Exception ex) {
										Log.e("Error executing advanced robust web scroll payload", ex);
									}
								}
							});
							return true; // Key event cleanly consumed
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
	 * Iterates dynamically over nested active child fragments to fetch web targets safely.
	 */
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

	/**
	 * Recursively traces the view hierarchy tree to capture web nodes without relying on layout IDs.
	 */
	private static @Nullable android.webkit.WebView findWebViewInHierarchy(android.view.View view) {
		if (view instanceof android.webkit.WebView) {
			return (android.webkit.WebView) view;
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