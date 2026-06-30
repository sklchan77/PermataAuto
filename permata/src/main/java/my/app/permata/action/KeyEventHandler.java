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








	private static void performAction(Action action, MediaSessionCallback cb,
												@Nullable MainActivityDelegate activity, long timestamp) {
		worker = null;
		Log.i("Performing action ", action);

		// Surgical context check to route steering wheel commands exclusively to the pure browser
		if (activity != null && (action == Action.NEXT || action == Action.PREV)) {
			final android.content.Context ctx = activity.getContext();
			if (ctx instanceof androidx.fragment.app.FragmentActivity) {
				final androidx.fragment.app.FragmentManager fragmentManager = 
						((androidx.fragment.app.FragmentActivity) ctx).getSupportFragmentManager();
				final java.util.List<androidx.fragment.app.Fragment> fragments = fragmentManager.getFragments();
				
				if (fragments != null) {
					for (androidx.fragment.app.Fragment f : fragments) {
						// Ensure the fragment is initialized, currently active, and visible to the driver
						if (f != null && f.isAdded() && f.isVisible()) {
							
							// Hard verification: Eliminate any module related to YouTube fragments
							final String checkName = f.getClass().getName();
							if (checkName.contains(".yt.") || checkName.contains("Youtube")) {
								continue; 
							}

							try {
								java.lang.reflect.Method getWebViewMethod = null;
								
								// Scan method layouts to find getWebView() safely without ProGuard name dependencies
								for (java.lang.reflect.Method m : f.getClass().getMethods()) {
									if (m.getParameterTypes().length == 0 && 
											android.webkit.WebView.class.isAssignableFrom(m.getReturnType())) {
										getWebViewMethod = m;
										break;
									}
								}

								if (getWebViewMethod != null) {
									final Object webViewInstance = getWebViewMethod.invoke(f);
									
									if (webViewInstance instanceof android.view.View) {
										// Safe cast to native SDK View bypassing modern Android Reflection security limits
										final android.view.View castedView = (android.view.View) webViewInstance;
										final int viewId = castedView.getId();
										
										// This dynamically looks up your exact "browserWebView" resource mapping identifier
										final int targetBrowserId = ctx.getResources().getIdentifier(
												"browserWebView", "id", ctx.getPackageName());
										
										// If the ID matches youtube.xml or anything else, skip it immediately!
										if (viewId != targetBrowserId || targetBrowserId == 0) {
											continue;
										}

										final boolean isDown = (action == Action.NEXT);
										
										// The Omni-Versatile Roadblock-Proof Automation Core Script (String concatenation fixed)
										final String jsScript = "(function() {" +
												"  try {" +
												"    var isDown = " + isDown + ";" +
												"    var targetBtn = null;" +
												"    var targetEl = document.querySelector('main') || " +
												"                   document.querySelector('article') || " +
												"                   document.querySelector('[role=\"main\"]') || " +
												"                   document.body;" +
												"    " +
												"    // TIER 1: Native Element Selector Mapping (Desktop & Mobile DOM targets)" +
												"    if (isDown) {" +
												"      targetBtn = document.querySelector('[data-e2e=\"arrow-down\"]') || " +
												"                  document.querySelector('.xgplayer-playswitch-next') || " +
												"                  document.querySelector('.slide-down-btn') || " +
												"                  document.querySelector('.nav-btn-down') || " +
												"                  document.querySelector('[aria-label=\"Next video\"]') || " +
												"                  document.querySelector('[aria-label=\"Next\"]');" +
												"    } else {" +
												"      targetBtn = document.querySelector('[data-e2e=\"arrow-up\"]') || " +
												"                  document.querySelector('.xgplayer-playswitch-prev') || " +
												"                  document.querySelector('.slide-up-btn') || " +
												"                  document.querySelector('.nav-btn-up') || " +
												"                  document.querySelector('[aria-label=\"Previous video\"]') || " +
												"                  document.querySelector('[aria-label=\"Go back\"]');" +
												"    }" +
												"    if (targetBtn) {" +
												"      targetBtn.click();" +
												"      return;" +
												"    }" +
												"    " +
												"    // TIER 2: Keyboard Event Simulation (Fallback for explicit Desktop environments)" +
												"    var key = isDown ? 'ArrowDown' : 'ArrowUp';" +
												"    var keyCode = isDown ? 40 : 38;" +
												"    var activeNode = document.activeElement || targetEl || document.body;" +
												"    try {" +
												"      var kEvt = new KeyboardEvent('keydown', { key: key, code: key, keyCode: keyCode, which: keyCode, bubbles: true, cancelable: true });" +
												"      activeNode.dispatchEvent(kEvt);" +
												"    } catch(kErr) {" +
												"      if (document.createEvent) {" +
												"        var oldKeyEvt = document.createEvent('KeyboardEvent');" +
												"        (oldKeyEvt.initKeyboardEvent || oldKeyEvt.initKeyEvent)('keydown', true, true, window, key, 0, '', false, '');" +
												"        activeNode.dispatchEvent(oldKeyEvt);" +
												"      }" +
												"    }" +
												"    " +
												"    // TIER 3: Universal Touch-Swipe Macro Simulator (Fallback for explicit Mobile environments)" +
												"    if (targetEl) {" +
												"      var rect = targetEl.getBoundingClientRect();" +
												"      var startX = rect.left + (rect.width / 2);" +
												"      var startY = rect.top + (rect.height / 2);" +
												"      var travelDistance = rect.height * 0.80;" +
												"      var endY = isDown ? (startY - travelDistance) : (startY + travelDistance);" +
												"      " +
												"      var dispatchTouch = function(type, x, y) {" +
												"        try {" +
												"          var touchObj = new Touch({ identifier: Date.now(), target: targetEl, clientX: x, clientY: y, screenX: x, screenY: y, pageX: x, pageY: y });" +
												"          var touchEvent = new TouchEvent(type, { bubbles: true, cancelable: true, touches: [touchObj], targetTouches: [touchObj], changedTouches: [touchObj] });" +
												"          targetEl.dispatchEvent(touchEvent);" +
												"        } catch(tObjErr) {" +
												"          if (document.createEvent) {" +
												"            var oldTouchEvt = document.createEvent('TouchEvent');" +
												"            oldTouchEvt.initTouchEvent(type, true, true);" +
												"            targetEl.dispatchEvent(oldTouchEvt);" +
												"          }" +
												"        }" +
												"      };" +
												"      dispatchTouch('touchstart', startX, startY);" +
												"      dispatchTouch('touchmove', startX, (startY + endY) / 2);" +
												"      dispatchTouch('touchmove', startX, endY);" +
												"      dispatchTouch('touchend', startX, endY);" +
												"    }" +
												"    " +
												"    // TIER 4 & 5: Wheel Event Dispatcher & Viewport Height Metrics Shift" +
												"    var pageHeight = window.innerHeight || document.documentElement.clientHeight || 600;" +
												"    var scrollAmount = isDown ? (pageHeight * 0.95) : -(pageHeight * 0.95);" +
												"    try {" +
												"      var wheelEvt = new WheelEvent('wheel', { deltaY: scrollAmount, bubbles: true, cancelable: true });" +
												"      activeNode.dispatchEvent(wheelEvt);" +
												"    } catch(wErr) {}" +
												"    " +
												"    window.scrollBy({ top: scrollAmount, behavior: 'smooth' });" +
												"    " +
												"  } catch (globalErr) {" +
												"    // TIER 6: Last Resort Structural Fallback" +
												"    var fallbackHeight = window.innerHeight || 500;" +
												"    var finalScroll = isDown ? fallbackHeight : -fallbackHeight;" +
												"    window.scrollBy(0, finalScroll);" +
												"  }" +
												"})();";
												
										// Pull base WebView framework method signature to completely bypass obfuscation
										final java.lang.reflect.Method evaluateJsMethod = android.webkit.WebView.class.getMethod(
												"evaluateJavascript", String.class, android.webkit.ValueCallback.class);
										
										// Safely push script evaluation to the main system UI thread loop using View's native post
										castedView.post(new Runnable() {
											@Override
											public void run() {
												try {
													evaluateJsMethod.invoke(castedView, jsScript, null);
												} catch (Exception ex) {
													Log.e("Error executing isolated JS web scroll payload", ex);
												}
											}
										});
										return; // Interception successful. Consume event and bypass default music skipping.
									}
								}
							} catch (Exception e) {
								Log.e("Error during absolute isolated browser scroll reflection routing", e);
							}
						}
					}
				}
			}
		}

		// 100% Native Fallback Channel: Preserves all existing player/system actions perfectly
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
