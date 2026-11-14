package my.app.permata.auto;

import static android.os.SystemClock.uptimeMillis;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.MotionEvent.obtain;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.completedNull;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import my.app.permata.PermataApplication;
import my.app.permata.ui.activity.MainActivity;
import my.app.utils.async.FutureSupplier;
import my.app.utils.log.Log;

public abstract class EventDispatcher {

	public static void useSu(Su su) {
		SuDispatcher.instance = completed(new SuDispatcher(su));
	}

	public static EventDispatcher get() {
		int windowCount = AccessibilityEventDispatcherService.getWindowCount();
		if (windowCount > 1) {
			return AccessibilityDispatcher.get();
		}
		
		var d = ActivityDispatcher.get();
		return (d.getActiveInstance() == null) ? XposedDispatcher.get() : d;
	}

	public abstract boolean back();
	public abstract boolean directBack();
	public abstract boolean home();
	public abstract boolean recent();
	public abstract boolean directRecent();
	public abstract boolean tap(float x, float y);
	public abstract boolean motionEvent(MotionEvent e);
	public abstract boolean motionEvent(long downTime, long eventTime, int action, float x, float y);

	public boolean scale(float x, float y, float diff) {
		return AccessibilityDispatcher.get().scale(x, y, diff);
	}

	@Nullable
	AppCompatActivity getActivity() {
		return null;
	}

	private static final class XposedDispatcher extends EventDispatcher {
		static final XposedDispatcher instance = new XposedDispatcher();

		@NonNull
		public static XposedDispatcher get() {
			return instance;
		}

		@Override
		public boolean back() {
			return XposedEventDispatcherService.dispatchBackEvent() ||
					AccessibilityDispatcher.get().back();
		}

		@Override
		public boolean directBack() {
			return XposedEventDispatcherService.dispatchBackEvent() ||
					AccessibilityDispatcher.get().directBack();
		}

		@Override
		public boolean home() {
			return AccessibilityDispatcher.get().home();
		}

		@Override
		public boolean recent() {
			return AccessibilityDispatcher.get().recent();
		}

		@Override
		public boolean directRecent() {
			return AccessibilityDispatcher.get().directRecent();
		}

		@Override
		public boolean tap(float x, float y) {
			if (XposedEventDispatcherService.canDispatchEvent()) {
				var time = uptimeMillis();
				return motionEvent(time, time, ACTION_DOWN, x, y) &&
						motionEvent(time, time + 10, ACTION_UP, x, y);
			} else {
				return AccessibilityDispatcher.get().tap(x, y);
			}
		}

		@Override
		public boolean motionEvent(MotionEvent e) {
			return XposedEventDispatcherService.dispatchEvent(e) ||
					AccessibilityDispatcher.get().motionEvent(e);
		}

		@Override
		public boolean motionEvent(long downTime, long eventTime, int action, float x, float y) {
			if (XposedEventDispatcherService.canDispatchEvent()) {
				if (XposedEventDispatcherService.dispatchEvent(
						obtain(downTime, eventTime, action, x, y, 0))) return true;
			}
			return AccessibilityDispatcher.get().motionEvent(downTime, eventTime, action, x, y);
		}
	}

	private static final class ActivityDispatcher extends EventDispatcher {
		private static final ActivityDispatcher instance = new ActivityDispatcher();
		private InputMethodManager imm;

		@NonNull
		public static ActivityDispatcher get() {
			return instance;
		}

		@Override
		public boolean back() {
			var a = getActivity();
			if (a == null) return AccessibilityDispatcher.get().back();
			//noinspection deprecation
			Log.i("[OUTPUT_TELEMETRY]", "ActivityDispatcher injecting native onBackPressed()");
			a.onBackPressed();
			return true;
		}

		@Override
		public boolean directBack() {
			return AccessibilityDispatcher.get().directBack();
		}

		@Override
		public boolean home() {
			return AccessibilityDispatcher.get().home();
		}
		
		@Override
		public boolean recent() {
			return AccessibilityDispatcher.get().recent();
		}

		@Override
		public boolean directRecent() {
			return AccessibilityDispatcher.get().directRecent();
		}

		@Override
		public boolean tap(float x, float y) {
			AppCompatActivity a = getActivity();
			if (a == null) return AccessibilityDispatcher.get().tap(x, y);
			var time = uptimeMillis();
			Log.i("[OUTPUT_TELEMETRY]", "ActivityDispatcher injecting local touch sequence at X:" + x + " Y:" + y);
			var e = obtain(time, time, ACTION_DOWN, x, y, 0);
			a.dispatchTouchEvent(e);
			e.recycle();
			e = obtain(time, time + 10, ACTION_UP, x, y, 0);
			a.dispatchTouchEvent(e);
			e.recycle();
			return true;
		}

		@Override
		public boolean motionEvent(MotionEvent e) {
			var a = getActivity();
			if (a == null) return AccessibilityDispatcher.get().motionEvent(e);
			Log.i("[OUTPUT_TELEMETRY]", "ActivityDispatcher injecting local MotionEvent: " + e.getActionMasked());
			a.dispatchTouchEvent(e);
			return true;
		}

		@Override
		public boolean motionEvent(long downTime, long eventTime, int action, float x, float y) {
			var a = getActivity();
			if (a == null)
				return AccessibilityDispatcher.get().motionEvent(downTime, eventTime, action, x, y);
			var e = obtain(downTime, eventTime, action, x, y, 0);
			Log.i("[OUTPUT_TELEMETRY]", "ActivityDispatcher injecting local single touch: " + action + " at X:" + x + " Y:" + y);
			a.dispatchTouchEvent(e);
			e.recycle();
			return true;
		}

		@Override
		AppCompatActivity getActivity() {
			var cnt = AccessibilityEventDispatcherService.getWindowCount();
			if (cnt == -1) {
				if (imm == null) {
					imm = (InputMethodManager) PermataApplication.get()
							.getSystemService(Context.INPUT_METHOD_SERVICE);
				}
				if (imm.isAcceptingText()) return null;
			} else if (cnt > 1) {
				return null;
			}
			return getActiveInstance();
		}

		private AppCompatActivity getActiveInstance() {
			AppCompatActivity a = LauncherActivity.getActiveInstance();
			if (a == null) a = MainActivity.getActiveInstance();
			return (a == null) || ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) &&
					(a.isInMultiWindowMode() || a.isInPictureInPictureMode())) ? null : a;
		}
	}

	private static final class AccessibilityDispatcher extends EventDispatcher {
		private static final AccessibilityDispatcher instance = new AccessibilityDispatcher();

		@NonNull
		public static AccessibilityDispatcher get() {
			return instance;
		}

		@Override
		public boolean back() {
			return directBack();
		}

		@Override
		public boolean directBack() {
			if (AccessibilityEventDispatcherService.dispatchUniversalBack()) return true;
			
			var a = getActivity();
			if (a != null) {
				Log.i("[OUTPUT_TELEMETRY]", "AccessibilityDispatcher injecting local KeyEvent.KEYCODE_BACK");
				a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
				a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
				return true;
			}
			
			var ctx = PermataApplication.get();
			if (ctx != null) {
				Log.i("[OUTPUT_TELEMETRY]", "AccessibilityDispatcher broadcasting Global ACTION_GLOBAL_BACK");
				Intent intent = new Intent("my.app.permata.ACTION_GLOBAL_BACK");
				intent.setPackage(ctx.getPackageName()); 
				ctx.sendBroadcast(intent);
				return true;
			}
			
			var d = SuDispatcher.get();
			return (d != null) && d.directBack();
		}

		@Override
		public boolean home() {
			if (AccessibilityEventDispatcherService.dispatchHome()) return true;
			
			var ctx = PermataApplication.get();
			if (ctx != null) {
				Log.i("[OUTPUT_TELEMETRY]", "AccessibilityDispatcher broadcasting Global ACTION_GLOBAL_HOME");
				Intent intent = new Intent("my.app.permata.ACTION_GLOBAL_HOME");
				intent.setPackage(ctx.getPackageName()); 
				ctx.sendBroadcast(intent);
				return true;
			}
			
			var d = SuDispatcher.get();
			return (d != null) && d.home();
		}
		
		@Override
		public boolean recent() {
			return directRecent();
		}

		@Override
		public boolean directRecent() {
			if (AccessibilityEventDispatcherService.dispatchRecent()) return true;
			
			var ctx = PermataApplication.get();
			if (ctx != null) {
				Log.i("[OUTPUT_TELEMETRY]", "AccessibilityDispatcher broadcasting Global ACTION_GLOBAL_RECENT");
				Intent intent = new Intent("my.app.permata.ACTION_GLOBAL_RECENT");
				intent.setPackage(ctx.getPackageName()); 
				ctx.sendBroadcast(intent);
				return true;
			}
			
			var d = SuDispatcher.get();
			return (d != null) && d.recent();
		}

		@Override
		public boolean tap(float x, float y) {
			if (AccessibilityEventDispatcherService.dispatchTap(x, y)) return true;
			var d = SuDispatcher.get();
			return (d != null) && d.tap(x, y);
		}

		@Override
		public boolean scale(float x, float y, float diff) {
			return AccessibilityEventDispatcherService.dispatchScale(x, y, diff);
		}

		@Override
		public boolean motionEvent(MotionEvent e) {
			if (AccessibilityEventDispatcherService.dispatchMotionEvent(e)) return true;
			var d = SuDispatcher.get();
			return (d != null) && d.motionEvent(e);
		}

		@Override
		public boolean motionEvent(long downTime, long eventTime, int action, float x, float y) {
			if (AccessibilityEventDispatcherService.dispatchMotionEvent(downTime, eventTime, action, x,
					y)) return true;
			var d = SuDispatcher.get();
			return (d != null) && d.motionEvent(downTime, eventTime, action, x, y);
		}
	}

	private static final class SuDispatcher extends EventDispatcher {
		static FutureSupplier<SuDispatcher> instance = completedNull();
		private final Su su;

		SuDispatcher(Su su) {this.su = su;}

		@Nullable
		public static SuDispatcher get() {
			return instance.peek();
		}

		@Override
		public boolean back() {
			return keyEvent(KeyEvent.KEYCODE_BACK);
		}

		@Override
		public boolean directBack() {
			return keyEvent(KeyEvent.KEYCODE_BACK);
		}

		@Override
		public boolean home() {
			return keyEvent(KeyEvent.KEYCODE_HOME);
		}
		
		@Override
		public boolean recent() {
			return keyEvent(KeyEvent.KEYCODE_APP_SWITCH); 
		}

		@Override
		public boolean directRecent() {
			return keyEvent(KeyEvent.KEYCODE_APP_SWITCH);
		}

		private boolean keyEvent(int code) {
			try {
				Log.i("[OUTPUT_TELEMETRY]", "SuDispatcher injecting shell cmd input keyevent " + code);
				su.exec("cmd input keyevent " + code);
				return true;
			} catch (Exception e) {
				return false;
			}
		}

		@Override
		public boolean tap(float x, float y) {
			Log.i("[OUTPUT_TELEMETRY]", "SuDispatcher injecting shell cmd input tap " + (int) x + " " + (int) y);
			su.exec("cmd input tap " + (int) x + " " + (int) y);
			return true;
		}

		@Override
		public boolean motionEvent(MotionEvent e) {
			return motionEvent(e.getDownTime(), e.getEventTime(), e.getAction(), e.getX(), e.getY());
		}

		@Override
		public boolean motionEvent(long downTime, long eventTime, int action, float x, float y) {
			String a;
			switch (action) {
				case ACTION_DOWN -> a = "DOWN ";
				case ACTION_UP -> a = "UP ";
				case ACTION_MOVE -> a = "MOVE ";
				default -> {
					return false;
				}
			}
			Log.i("[OUTPUT_TELEMETRY]", "SuDispatcher injecting shell cmd input motionevent " + a + (int) x + " " + (int) y);
			su.exec("cmd input motionevent " + a + (int) x + " " + (int) y);
			return true;
		}
	}
}