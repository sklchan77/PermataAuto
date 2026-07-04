package my.app.permata.auto;

import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_DPAD_UP_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP_RIGHT;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.failed;
import static my.app.utils.ui.UiUtils.showAlert;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.OperationCanceledException;
import android.os.SystemClock;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView.OnEditorActionListener;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarUiController;

import my.app.permata.R;
import my.app.permata.media.service.PermataMediaServiceConnection;
import my.app.permata.ui.activity.PermataActivity;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.view.MediaItemListView;
import my.app.permata.ui.view.VideoView;
import my.app.utils.async.FutureSupplier;
import my.app.utils.function.Cancellable;
import my.app.utils.function.Supplier;
import my.app.utils.log.Log;
import my.app.utils.ui.activity.ActivityDelegate;
import my.app.utils.ui.fragment.ActivityFragment;
import my.app.utils.ui.menu.OverlayMenu;

/**
 * @author sklchan77
 */
public class MainCarActivity extends CarActivity implements PermataActivity {

	// APPROACH 2: Safe runtime ID generation completely isolated from external view tag collisions
	private static final int SMART_SCROLL_TIMESTAMP_ID = View.generateViewId();

	static PermataMediaServiceConnection service;
	@SuppressWarnings("unchecked")
	@NonNull
	private FutureSupplier<MainActivityDelegate> delegate =
			(FutureSupplier<MainActivityDelegate>) NO_DELEGATE;
	private CarEditText editText;
	private TextWatcher textWatcher;

	@NonNull
	@Override
	public FutureSupplier<MainActivityDelegate> getActivityDelegate() {
		return delegate;
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(MainActivityDelegate.attachBaseContext(base));
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		MainActivityDelegate.setTheme(this, true);
		super.onCreate(savedInstanceState);
		initCarActivity(this);
		PermataMediaServiceConnection s = service;

		if ((s != null) && s.isConnected()) {
			onCreate(savedInstanceState, s);
		} else {
			delegate = PermataMediaServiceConnection.connect(this).main()
					.onFailure(err -> showAlert(getContext(), String.valueOf(err))).map(c -> {
						service = c;
						return onCreate(savedInstanceState, c);
					});
		}
	}

	static void initCarActivity(CarActivity a) {
		a.setIgnoreConfigChanges(0xFFFFFFFF);
		CarUiController ctrl = a.getCarUiController();
		ctrl.getStatusBarController().hideAppHeader();
		ctrl.getMenuController().hideMenuButton();
	}

	private MainActivityDelegate onCreate(Bundle state, PermataMediaServiceConnection s) {
		MainActivityDelegate d = new MainActivityDelegate(this, s.createBinder());
		ActivityDelegate.setContextToDelegate(ctx -> d);
		delegate = completed(d);
		d.onActivityCreate(state);
		return d;
	}

	@Override
	public void onResume() {
		super.onResume();
		getActivityDelegate().onSuccess(MainActivityDelegate::onActivityResume);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onDestroy() {
		super.onDestroy();
		getActivityDelegate().onSuccess(MainActivityDelegate::onActivityDestroy)
				.thenRun(() -> ActivityDelegate.setContextToDelegate(null));
		delegate = (FutureSupplier<MainActivityDelegate>) NO_DELEGATE;
	}

	@Override
	public void onConfigurationChanged(Configuration configuration) {
		Log.i("Configuration changed: ", configuration);
		super.onConfigurationChanged(configuration);
	}

	@Override
	@SuppressWarnings("unchecked")
	public View findViewById(int i) {
		return super.findViewById(i);
	}

	@NonNull
	@Override
	public FragmentManager getSupportFragmentManager() {
		return super.getSupportFragmentManager();
	}

	@Override
	public View getCurrentFocus() {
		return null;
	}

	public boolean isCarActivity() {
		return true;
	}

	@Override
	public void setRequestedOrientation(int requestedOrientation) {
	}

	public void recreate() {
	}

	public void finish() {
		getActivityDelegate().onSuccess(MainActivityDelegate::onActivityFinish);
	}

	@Override
	public FutureSupplier<Intent> startActivityForResult(Supplier<Intent> intent) {
		return failed(new UnsupportedOperationException());
	}

	public FutureSupplier<int[]> checkPermissions(String... perms) {
		return failed(new UnsupportedOperationException());
	}

	@Override
	public Window getWindow() {
		return c();
	}

	@Override
	public EditText startInput(TextWatcher w) {
		if (editText == null) editText = new CarEditText(this);
		if (textWatcher != null) editText.removeTextChangedListener(textWatcher);
		editText.addTextChangedListener(w);
		textWatcher = w;
		getActivityDelegate().onSuccess(a -> {
			if (a.getPrefs().getVoiceControlEnabledPref()) {
				a.startSpeechRecognizer(null, true).onCompletion((q, err) -> {
					stopInput();
					if (err instanceof OperationCanceledException) {
						textWatcher = w;
						editText.removeTextChangedListener(w);
						editText.addTextChangedListener(w);
						if (w instanceof OnEditorActionListener)
							editText.setOnEditorActionListener((OnEditorActionListener) w);
						a().startInput(editText);
					} else if ((q != null) && !q.isEmpty()) {
						editText.setText(q.get(0));
						w.afterTextChanged(editText.getText());
					} else {
						stopInput();
					}
				});
			} else {
				a().startInput(editText);
			}
		});
		return editText;
	}

	public void stopInput() {
		if (editText != null) {
			if (textWatcher != null) editText.removeTextChangedListener(textWatcher);
			editText.setOnEditorActionListener(null);
		}

		a().stopInput();
	}

	public boolean isInputActive() {
		return a().isInputActive();
	}

	public EditText createEditText(Context ctx) {
		CarEditText et = new CarEditText(ctx);
		et.setOnClickListener(v -> {
			if (!a().isInputActive()) a().startInput(et);
		});
		return et;
	}

	@Override
	public boolean setTextInput(String text) {
		if ((editText == null) || !isInputActive()) return false;
		editText.setText(text);
		stopInput();
		return true;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
		Log.i(keyEvent);
		MainActivityDelegate d = delegate.peek();
		if (d == null) return super.onKeyUp(keyCode, keyEvent);

		if (d.getPrefs().useDpadCursor(d)) {
			switch (keyCode) {
				case KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_LEFT,
						KEYCODE_DPAD_UP_RIGHT, KEYCODE_DPAD_DOWN_LEFT, KEYCODE_DPAD_DOWN_RIGHT -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if (c != null) c.delayedHide();
					return true;
				}
				case KEYCODE_BACK -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if ((c != null) && c.ignoreBack) {
						c.ignoreBack = false;
						return true;
					}
				}
				case KEYCODE_DPAD_CENTER -> {
					Cursor c = (Cursor) findViewById(R.id.cursor);
					if ((c != null) && c.isFocused()) return c.click();
				}
			}
		}

		return d.onKeyUp(keyCode, keyEvent, super::onKeyDown);
	}

	private boolean performFragmentScroll(boolean up, MainActivityDelegate d) {
		if (d == null) return false;
		ActivityFragment f = d.getActiveFragment();
		if (f == null) return false;
		View root = f.getView();
		if (root instanceof ViewGroup vg) return performViewScroll(up, vg);
		return false;
	}

	private boolean performViewScroll(boolean up, View v) {
		if (v instanceof RecyclerView rv) {
			LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
			if (lm == null) return false;
			int pos = lm.findFirstVisibleItemPosition();
			if (up) {
				if (pos > 0) lm.scrollToPositionWithOffset(pos - 1, 0);
			} else {
				if (pos < lm.getItemCount() - 1) lm.scrollToPositionWithOffset(pos + 1, 0);
			}
			return true;
		} else if (v instanceof WebView wv) {
			// Integrated Multi-Layered Fail-Safe Web Scrolling Engine
			return smartScrollWebView(wv, up);
		} else if (v instanceof ViewGroup vg) {
			for (int i = 0, n = vg.getChildCount(); i < n; i++) {
				if (performViewScroll(up, vg.getChildAt(i))) return true;
			}
		}
		return false;
	}



	// THE PRODUCTION-READY SPAM ENGINE: Powered by Main-Thread Decoupling & Numerical Tokens
	private static boolean smartScrollWebView(WebView wv, boolean up) {
		if (wv == null) return false;

		// --- SPAM DETECTOR GATE (Using Approach 2 Inline Dynamic ID) ---
		long now = android.os.SystemClock.uptimeMillis();
		Object lastClickTag = wv.getTag(SMART_SCROLL_TIMESTAMP_ID);
		long lastClickTime = (lastClickTag instanceof Long) ? (Long) lastClickTag : 0;
		wv.setTag(SMART_SCROLL_TIMESTAMP_ID, now); 
		
		boolean isSpamming = (now - lastClickTime < 250);

		if (wv.getSettings().getJavaScriptEnabled()) {
			// Optimized JS payload returning exact integers (1 / 0) to avoid JSON string quote variations
			String jsScript = "(function(isUp, isSpam) {" +
					"  var currentTime = Date.now();" +
					"  var target = window.__smartScrollTarget;" +
					"  " +
					"  /* DOM CACHE GATE: Reuses target element for 3s to save CPU overhead on spam streams */" +
					"  if (!target || !document.contains(target) || (currentTime - (window.__lastScrollScan || 0) > 3000)) {" +
					"    var doc = document.documentElement, body = document.body;" +
					"    if (isUp) {" +
					"      if (doc.scrollTop > 5 || body.scrollTop > 5 || window.pageYOffset > 5) target = window;" +
					"    } else {" +
					"      var total = Math.max(doc.scrollHeight, body.scrollHeight, doc.offsetHeight, body.offsetHeight);" +
					"      var current = window.pageYOffset || doc.scrollTop || body.scrollTop;" +
					"      if (total - current > window.innerHeight + 5) target = window;" +
					"    }" +
					"    if (!target || target === window) {" +
					"      var nodes = document.querySelectorAll('*'), bestNode = null, maxArea = 0;" +
					"      for (var i = 0; i < nodes.length; i++) {" +
					"        var el = nodes[i], s = window.getComputedStyle(el);" +
					"        var overflow = s.overflowY || s.overflow || '';" +
					"        if (overflow.indexOf('auto') !== -1 || overflow.indexOf('scroll') !== -1 || overflow.indexOf('overlay') !== -1 || el.scrollHeight > el.clientHeight) {" +
					"          var canScroll = isUp ? el.scrollTop > 5 : (el.scrollHeight - el.scrollTop > el.clientHeight + 5);" +
					"          if (canScroll) {" +
					"            var r = el.getBoundingClientRect();" +
					"            var area = r.width * r.height;" +
					"            if (area > maxArea && r.width > 40 && r.height > 40) { maxArea = area; bestNode = el; }" +
					"          }" +
					"        }" +
					"      }" +
					"      target = bestNode || window;" +
					"    }" +
					"    window.__smartScrollTarget = target;" +
					"    window.__lastScrollScan = currentTime;" +
					"  }" +
					"  " +
					"  if (!target) return 0;" +
					"  var step = window.innerHeight * 0.75;" +
					"  if (isUp) step = -step;" +
					"  " +
					"  /* SMOOTH SMOOTHING BYPASS: Drops down to instant frame rendering if user clicks aggressively */" +
					"  var behavior = isSpam ? 'auto' : 'smooth';" +
					"  try {" +
					"    target.scrollBy({ top: step, behavior: behavior });" +
					"  } catch(e) {" +
					"    if (target === window) window.scrollBy(0, step);" +
					"    else target.scrollTop += step;" +
					"  }" +
					"  try {" +
					"    var wheelEvt = new WheelEvent('wheel', { deltaY: step, bubbles: true });" +
					"    (target === window ? document.body : target).dispatchEvent(wheelEvt);" +
					"  } catch(err) {}" +
					"  return 1;" +
					"})(" + up + ", " + isSpamming + ");";

			wv.evaluateJavascript(jsScript, result -> {
				// FIX: Safely route the token evaluation and UI updates back onto the Main Thread loop
				if (result == null || "0".equals(result)) {
					wv.post(() -> executeNativeScrollFallback(wv, up, isSpamming));
				}
			});
			return true; 
		} else {
			return executeNativeScrollFallback(wv, up, isSpamming);
		}
	}

	// ISOLATED NATIVE PIPELINE WITH GESTURE COLLISION ARRAYS
	private static boolean executeNativeScrollFallback(WebView wv, boolean up, boolean isSpamming) {
		// Layer 2 Fallback: Native API frame translations
		boolean systemScrolled = up ? wv.pageUp(false) : wv.pageDown(false);
		if (systemScrolled) return true;

		// Layer 3 Fallback: Hardware Event Tunneling
		int key = up ? KeyEvent.KEYCODE_PAGE_UP : KeyEvent.KEYCODE_PAGE_DOWN;
		wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
		wv.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));

		// Layer 4 Fallback Protection: Skip heavy layout drag coordinates if user is spam-clicking
		if (isSpamming) return true;

		int touchSlop = android.view.ViewConfiguration.get(wv.getContext()).getScaledTouchSlop();
		long downTime = android.os.SystemClock.uptimeMillis();
		float midX = wv.getWidth() / 2f;
		
		float dragDistance = Math.max(wv.getHeight() * 0.5f, touchSlop * 4f);
		float centerY = wv.getHeight() / 2f;
		
		float yStart = up ? (centerY - dragDistance / 2f) : (centerY + dragDistance / 2f);
		float yEnd = up ? (centerY + dragDistance / 2f) : (centerY - dragDistance / 2f);

		wv.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, midX, yStart, 0));
		
		int steps = 5;
		for (int i = 1; i <= steps; i++) {
			float alpha = (float) i / steps;
			float currentY = yStart + alpha * (yEnd - yStart);
			long moveTime = downTime + (i * 25);
			wv.dispatchTouchEvent(MotionEvent.obtain(downTime, moveTime, MotionEvent.ACTION_MOVE, midX, currentY, 0));
		}
		
		long upTime = downTime + (steps * 25) + 20;
		wv.dispatchTouchEvent(MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, midX, yEnd, 0));

		return true;
	}



	@Override
	public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
		Log.i(keyEvent);
		MainActivityDelegate d = delegate.peek();
		if (d == null) return super.onKeyDown(keyCode, keyEvent);

		// Global Intercept: Handle physical steering wheel keys or media knobs for web/list surfaces
		if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
			if (performFragmentScroll(false, d)) return true;
		} else if (keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
			if (performFragmentScroll(true, d)) return true;
		}

		if (!d.getPrefs().useDpadCursor(d)) return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);

		float x = 0;
		float y = 0;
		View screen = null;
		Cursor cursor = null;

		switch (keyCode) {
			case KEYCODE_DPAD_UP -> {
				OverlayMenu m = d.getActiveMenu();
				if (m instanceof View v) {
					View f = v.focusSearch(View.FOCUS_UP);
					if (f != null) {
						f.requestFocus();
						return true;
					}
				}
				y = -1;
			}
			case KEYCODE_DPAD_DOWN -> {
				OverlayMenu m = d.getActiveMenu();
				if (m instanceof View v) {
					View f = v.focusSearch(View.FOCUS_DOWN);
					if (f != null) {
						f.requestFocus();
						return true;
					}
				}
				y = 1;
			}
			case KEYCODE_DPAD_LEFT -> x = -1;
			case KEYCODE_DPAD_RIGHT -> x = 1;
			case KEYCODE_DPAD_UP_LEFT -> {
				y = -1;
				x = -1;
			}
			case KEYCODE_DPAD_UP_RIGHT -> {
				y = -1;
				x = 1;
			}
			case KEYCODE_DPAD_DOWN_LEFT -> {
				y = 1;
				x = -1;
			}
			case KEYCODE_DPAD_DOWN_RIGHT -> {
				y = 1;
				x = 1;
			}
			case KEYCODE_BACK -> {
				screen = findViewById(R.id.main_activity);
				cursor = screen.findViewById(R.id.cursor);
				if ((cursor == null) || cursor.isFocused())
					return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);
				cursor.ignoreBack = true;
			}
			case KEYCODE_DPAD_CENTER -> {
				screen = findViewById(R.id.main_activity);
				cursor = screen.findViewById(R.id.cursor);
				if (cursor == null) return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);
			}
			default -> {return d.onKeyDown(keyCode, keyEvent, super::onKeyDown);}
		}

		if (screen == null) {
			screen = findViewById(R.id.main_activity);
			cursor = screen.findViewById(R.id.cursor);
		}

		int w = screen.getWidth();
		int h = screen.getHeight();

		if (cursor == null) {
			cursor = new Cursor(d, (int) (Math.min(w, h) * 0.05f));
			((ViewGroup) screen).addView(cursor);
			cursor.show(w / 2f, h / 2f);
		} else if (!cursor.isVisible()) {
			cursor.show(cursor.getX(), cursor.getY());
		} else {
			int cursorSize = (int) (Math.min(w, h) * 0.05f);
			cursor.move(w, h, x, y, cursorSize / 3f, keyCode);
		}

		return true;
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent keyEvent) {
		MainActivityDelegate d = delegate.peek();
		return (d != null) ? d.onKeyLongPress(keyCode, keyEvent, super::onKeyLongPress) :
				super.onKeyLongPress(keyCode, keyEvent);
	}

	private static final class Cursor extends AppCompatImageView
			implements View.OnClickListener, View.OnLongClickListener {
		private final MainActivityDelegate activity;
		private Cancellable move = Cancellable.CANCELED;
		private Cancellable hide = Cancellable.CANCELED;
		private Cancellable resetAccel = Cancellable.CANCELED;
		boolean ignoreBack;
		int accel = 1;

		Cursor(MainActivityDelegate d, int size) {
			super(d.getContext());
			activity = d;
			setId(R.id.cursor);
			setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
			setLayoutParams(new ConstraintLayout.LayoutParams(size, size));
			setImageDrawable(AppCompatResources.getDrawable(d.getContext(), R.drawable.cursor));
			setOnClickListener(this);
			setOnLongClickListener(this);

			Drawable transparent = new ColorDrawable(Color.TRANSPARENT);
			StateListDrawable bg = new StateListDrawable();
			bg.addState(new int[]{android.R.attr.state_focused}, transparent);
			bg.addState(new int[]{}, transparent);
			setBackground(bg);
		}

		boolean isVisible() {
			return getVisibility() == VISIBLE;
		}

		@Override
		public void onClick(View v) {
			click();
		}

		boolean click() {
			delayedHide();
			float x = getX();
			float y = getY();
			OverlayMenu m = activity.getActiveMenu();
			if ((m instanceof ViewGroup v) && click(v, x - v.getX(), y - v.getY())) return true;
			click(screen(), x, y);
			return true;
		}

		private boolean click(ViewGroup parent, float cursorX, float cursorY) {
			for (int i = 0, n = parent.getChildCount(); i < n; i++) {
				View v = parent.getChildAt(i);
				if (v.getVisibility() != VISIBLE) continue;
				float x = cursorX - v.getX();
				if ((x > 0) && (x < v.getWidth())) {
					float y = cursorY - v.getY();
					if ((y >= 0) && (y < v.getHeight())) {
						if (v instanceof WebView) {
							touch(v, x, y, true);
							return true;
						}
						if (v instanceof VideoView) {
							touch(v, x, y, false);
							return true;
						}
						if (v.isClickable()) {
							v.performClick();
							v.requestFocus();
							activity.post(this::requestFocus);
							return true;
						}

						if ((v instanceof ViewGroup vg) && click(vg, x, y)) return true;
						touch(v, x, y, false);
						return true;
					}
				}
			}
			return false;
		}

		private void touch(View v, float x, float y, boolean clearFocus) {
			long time = SystemClock.uptimeMillis();
			MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
			if (clearFocus) {
				clearFocus();
				setVisibility(GONE);
			}
			v.dispatchTouchEvent(down);
			down.recycle();
			activity.postDelayed(() -> {
				MotionEvent up = MotionEvent.obtain(time, time + 100, MotionEvent.ACTION_UP, x, y, 0);
				v.dispatchTouchEvent(up);
				up.recycle();
				if (!clearFocus) activity.post(this::requestFocus);
			}, 100);
		}

		@Override
		public boolean onLongClick(View v) {
			delayedHide();
			longClick(screen(), getX(), getY());
			return true;
		}

		private void longClick(ViewGroup parent, float cursorX, float cursorY) {
			for (int i = 0, n = parent.getChildCount(); i < n; i++) {
				View v = parent.getChildAt(i);
				if (v.getVisibility() != VISIBLE) continue;
				float x = cursorX - v.getX();
				if ((x > 0) && (x < v.getWidth())) {
					float y = cursorY - v.getY();
					if ((y >= 0) && (y < v.getHeight())) {
						if (v.isLongClickable()) {
							v.performLongClick();
							activity.post(this::requestFocus);
							return;
						}
						if (v instanceof ViewGroup vg) longClick(vg, x, y);
						return;
					}
				}
			}
		}

		void delayedHide() {
			move.cancel();
			move = Cancellable.CANCELED;
			hide.cancel();
			hide = activity.postDelayed(() -> {
				hide = Cancellable.CANCELED;
				clearFocus();
				setVisibility(GONE);
				MediaItemListView.focusActive(activity.getContext(), null);
			}, 5000);
		}

		void show(float cursorX, float cursorY) {
			setVisibility(VISIBLE);
			animate().x(cursorX).y(cursorY).setDuration(0).start();
		}

		void move(int w, int h, float dx, float dy, float step, int keyCode) {
			move.cancel();
			move = activity.postDelayed(() -> move(w, h, dx, dy, step, keyCode), 50);

			float cursorX = getX() + (step * dx * accel);
			float cursorY = getY() + (step * dy * accel);
			cursorX = Math.max(0, Math.min(w - step, cursorX));
			cursorY = Math.max(0, Math.min(h - step, cursorY));

			if (resetAccel.cancel()) accel += 1;
			resetAccel = activity.postDelayed(() -> {
				resetAccel = Cancellable.CANCELED;
				accel = 1;
			}, 200);

			if ((keyCode == KEYCODE_DPAD_UP) && (getY() == 0)) {
				scroll(true);
			} else if ((keyCode == KEYCODE_DPAD_DOWN) && (getY() >= screen().getHeight() - getHeight())) {
				scroll(false);
			}

			if (!focusFb(screen(), cursorX, cursorY)) requestFocus();
			animate().x(cursorX).y(cursorY).setDuration(0).start();
		}

		private boolean focusFb(ViewGroup screen, float cursorX, float cursorY) {
			View fb = screen.findViewById(R.id.floating_button);
			float fbX = fb.getX();
			float fbY = fb.getY();
			if ((cursorX >= fbX) && (cursorX < fbX + fb.getWidth()) && (cursorY >= fbY) &&
					(cursorY < fbY + fb.getHeight())) {
				fb.requestFocus();
				return true;
			}
			return false;
		}

		private void scroll(boolean up) {
			ActivityFragment f = activity.getActiveFragment();
			if (f == null) return;
			View root = f.getView();
			if (root instanceof ViewGroup vg) scroll(up, vg);
		}

		private boolean scroll(boolean up, View v) {
			if (v instanceof RecyclerView rv) {
				LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
				if (lm == null) return false;
				int pos = lm.findFirstVisibleItemPosition();
				if (up) {
					if (pos > 0) lm.scrollToPositionWithOffset(pos - 1, 0);
				} else {
					if (pos < lm.getItemCount() - 1) lm.scrollToPositionWithOffset(pos + 1, 0);
				}
				return true;
			} else if (v instanceof WebView wv) {
				// Linked Cursor Navigation logic to the integrated Universal Engine as well
				return MainCarActivity.smartScrollWebView(wv, up);
			} else if (v instanceof ViewGroup vg) {
				for (int i = 0, n = vg.getChildCount(); i < n; i++) {
					if (scroll(up, vg.getChildAt(i))) return true;
				}
			}
			return false;
		}

		private ViewGroup screen() {
			return (ViewGroup) getParent();
		}
	}
}
