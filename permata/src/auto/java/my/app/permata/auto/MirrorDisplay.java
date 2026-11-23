package my.app.permata.auto;

import static android.content.Context.POWER_SERVICE;
import static android.content.Context.WINDOW_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP;
import static android.os.SystemClock.uptimeMillis;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static my.app.utils.function.ResultConsumer.Cancel.isCancellation;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.car.app.SurfaceContainer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.permata.media.service.PermataMediaService;
import my.app.permata.ui.activity.MainActivity;
import my.app.utils.async.Completed;
import my.app.utils.async.FutureSupplier;
import my.app.utils.async.Promise;
import my.app.utils.log.Log;
import my.app.utils.ui.UiUtils;

public class MirrorDisplay {
	private static final int OVERLAY_FLAGS = FLAG_NOT_FOCUSABLE | FLAG_KEEP_SCREEN_ON | FLAG_DISMISS_KEYGUARD | FLAG_TURN_SCREEN_ON | FLAG_SHOW_WHEN_LOCKED | FLAG_NOT_TOUCHABLE | FLAG_WATCH_OUTSIDE_TOUCH;
	private static final int ANCHOR_FLAGS = FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_KEEP_SCREEN_ON | FLAG_DISMISS_KEYGUARD | FLAG_SHOW_WHEN_LOCKED | FLAG_ALT_FOCUSABLE_IM;
	private static final int MAX_VD_LONG_EDGE = 1920;

	private static MirrorDisplay instance;
	private final Display defaultDisplay;
	private final float scaleDiff;
	private WakeLock wakeLock;

	private static final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
	private static final ExecutorService touchExecutor = Executors.newSingleThreadExecutor();

	private static final Object lifecycleLock = new Object();
	private static final Object settingsLock = new Object();

	private static volatile boolean isDimmed = false;
	private static volatile boolean isFillModeFS = false;
	private static volatile boolean isFillModeActivity = false;
	private static int originalAccelState = -1; // [SURGERY] Track original accelerometer state

	private volatile long lastInjectedTouchTime = 0;
	private final Object touchLock = new Object();

	private int refCounter = 0;
	private boolean isDestroyed = false;

	private FutureSupplier<Session> session = Completed.cancelled();
	private SurfaceContainer sc;

	private Overlay overlay;
	private View rotationAnchor;
	private Metrics currentMetrics;

	private MotionEvent.PointerProperties[] reusableProps;
	private MotionEvent.PointerCoords[] reusableCoords;

	public static final String ACTION_KILL_WEBVIEW = "my.app.permata.action.KILL_WEBVIEW";

	private final Handler mainHandler = new Handler(Looper.getMainLooper());

	private final Runnable idleDimRunnable = () -> {
		Log.i("[MIRROR][DECISION] Idle Timer Expired (10s) | action=RE_DIM_AND_LOCK_ORIENTATION");
		dimScreen(PermataApplication.get());
	};

	private BroadcastReceiver unlockReceiver;

	private MirrorDisplay() {
		var ctx = PermataApplication.get();
		var vm = (WindowManager) ctx.getSystemService(WINDOW_SERVICE);
		defaultDisplay = vm.getDefaultDisplay();
		var size = new Point();
		defaultDisplay.getRealSize(size);
		scaleDiff = Math.max(UiUtils.toPx(ctx, 20), Math.min(size.x, size.y) / 20f);

		reusableProps = new MotionEvent.PointerProperties[10];
		reusableCoords = new MotionEvent.PointerCoords[10];
		for (int i = 0; i < 10; i++) {
			reusableProps[i] = new MotionEvent.PointerProperties();
			reusableCoords[i] = new MotionEvent.PointerCoords();
		}
	}

	public static boolean isFillMode(boolean isFS) {
		return isFS ? isFillModeFS : isFillModeActivity;
	}

	public static boolean toggleFillMode(boolean isFS) {
		synchronized (lifecycleLock) {
			boolean newState;
			if (isFS) {
				isFillModeFS = !isFillModeFS;
				newState = isFillModeFS;
			} else {
				isFillModeActivity = !isFillModeActivity;
				newState = isFillModeActivity;
			}
			Log.i("[MIRROR][GL] Scale Mode Changed | surface=" + (isFS ? "FS" : "ACTIVITY")
					+ ", fillMode=" + newState);

			if (instance != null && !instance.isDestroyed && instance.isCurrentSurfaceFS() == isFS) {
				instance.currentMetrics = null;
				if (instance.session.isDoneNotFailed()) {
					try {
						Session s = instance.session.getOrThrow();
						if (s != null && s.renderer != null) {
							s.renderer.setFillMode(newState);
						}
					} catch (Exception ignored) {}
				}
			}
			return newState;
		}
	}

	private boolean isCurrentSurfaceFS() {
		return sc != null && sc == MirrorServiceFS.sc;
	}

	private boolean isActiveFillMode() {
		return isCurrentSurfaceFS() ? isFillModeFS : isFillModeActivity;
	}

	public static MirrorDisplay get() {
		synchronized (lifecycleLock) {
			if (instance == null) {
				instance = new MirrorDisplay();
			}
			instance.refCounter++;
			return instance;
		}
	}

	public static void close() {
		synchronized (lifecycleLock) {
			if (instance == null) return;
			Log.i("[MIRROR][LIFECYCLE] MirrorDisplay.close() called");
			var scRef = instance.sc;
			instance.cleanUpLocked();
			if (scRef != null) drawMsg(scRef, R.string.app_name);
		}
	}

	public static void shutdownForCarMedia() {
		var app = PermataApplication.get();
		if (app == null || !app.isMirroringMode()) return;
		Log.i("[MIRROR][LIFECYCLE] Auto-Shutdown Triggered | reason=CAR_MEDIA_STARTED");
		close();
		try {
			Intent goHome = new Intent(Intent.ACTION_MAIN);
			goHome.addCategory(Intent.CATEGORY_HOME);
			goHome.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
			app.startActivity(goHome);
		} catch (Exception err) {
			Log.e(err, "Failed to send phone to Home screen after mirroring auto-shutdown.");
		}
	}

	public boolean isDestroyed() {
		synchronized (lifecycleLock) {
			return isDestroyed;
		}
	}

	public void release() {
		synchronized (lifecycleLock) {
			if (isDestroyed) return;
			if (--refCounter <= 0) {
				refCounter = 0;
				Log.i("[MIRROR][LIFECYCLE] MirrorDisplay refCounter reached 0 | action=CLEANUP");
				cleanUpLocked();
			}
		}
	}

	public void setSurface(@NonNull SurfaceContainer sc) {
		synchronized (lifecycleLock) {
			if (isDestroyed) return;
			var oldSc = this.sc;
			if (oldSc == sc) return;

			this.sc = sc;
			currentMetrics = null;
			boolean activeFill = isActiveFillMode();
			String orient = sc.getWidth() > sc.getHeight() ? "LANDSCAPE" : "PORTRAIT";
			Log.i("[MIRROR][SURFACE] setSurface | size=" + sc.getWidth() + "x" + sc.getHeight()
					+ ", dpi=" + sc.getDpi() + ", orientation=" + orient
					+ ", fillMode=" + activeFill
					+ ", sessionActive=" + session.isDoneNotFailed());

			dimScreen(PermataApplication.get());
			disableAccelRotation(PermataApplication.get());

			if (session.isDoneNotFailed()) {
				try {
					var s = this.session.getOrThrow();
					if (oldSc != null) {
						s.renderer.detachOutputSurfaceSync();
						drawMsg(oldSc, R.string.app_name);
					}
					int[] vdSize = computeVirtualDisplaySize();
					s.vd.resize(vdSize[0], vdSize[1], sc.getDpi());
					s.renderer.setInputSize(vdSize[0], vdSize[1]);
					s.renderer.setFillMode(activeFill);
					s.renderer.setOutputSurface(sc.getSurface(), sc.getWidth(), sc.getHeight());
					started();
				} catch (Throwable err) {
					Log.d(err);
					noSession();
					createSession();
				}
			} else {
				createSession();
				if (oldSc != null) drawMsg(oldSc, R.string.app_name);
			}
		}
	}

	public void releaseSurface(@NonNull SurfaceContainer sc) {
		synchronized (lifecycleLock) {
			if (isDestroyed) return;
			var oldSc = this.sc;
			if (oldSc != sc) return;
			Log.i("[MIRROR][SURFACE] releaseSurface | size=" + sc.getWidth() + "x" + sc.getHeight());
			this.sc = null;

			restoreBrightness(PermataApplication.get(), true);
			restoreAccelRotation(PermataApplication.get(), true);

			if (session.isDoneNotFailed()) {
				try {
					Session s = session.getOrThrow();
					if (s != null && s.renderer != null) {
						s.renderer.detachOutputSurfaceSync();
					}
				} catch (Exception ignored) {}
			}
			drawMsg(oldSc, R.string.app_name);
		}
	}

	private int[] computeVirtualDisplaySize() {
		Point size = new Point();
		defaultDisplay.getRealSize(size);
		float dw = size.x;
		float dh = size.y;
		boolean isLandscape = PermataApplication.get().isMirroringLandscape();
		if (isLandscape && dw < dh) {
			float tmp = dw; dw = dh; dh = tmp;
		} else if (!isLandscape && dw > dh) {
			float tmp = dw; dw = dh; dh = tmp;
		}

		float longEdge = Math.max(dw, dh);
		if (longEdge > MAX_VD_LONG_EDGE) {
			float factor = MAX_VD_LONG_EDGE / longEdge;
			dw *= factor;
			dh *= factor;
		}
		int w = Math.max(2, ((int) Math.round(dw)) & ~1);
		int h = Math.max(2, ((int) Math.round(dh)) & ~1);
		return new int[]{w, h};
	}

	private void resetIdleTimer() {
		mainHandler.removeCallbacks(idleDimRunnable);
		mainHandler.postDelayed(idleDimRunnable, 10000);
	}

	public static void notifyInjectedTouch() {
		var md = instance;
		if (md != null) {
			md.lastInjectedTouchTime = uptimeMillis();
		}
	}

	public void tap(float x, float y) {
		lastInjectedTouchTime = uptimeMillis();
		var m = metrics();
		if (m == null) return;

		touchExecutor.execute(() -> {
			var d = dispatcher();
			if (d != null) {
				d.tap((x - m.x) * m.scale, (y - m.y) * m.scale);
			}
		});
	}

	public void scale(float x, float y, boolean zoomIn) {
		lastInjectedTouchTime = uptimeMillis();
		var m = metrics();
		if (m == null) return;

		touchExecutor.execute(() -> {
			var d = dispatcher();
			if (d != null) {
				d.scale((x - m.x) * m.scale, (y - m.y) * m.scale, zoomIn ? scaleDiff : -scaleDiff);
			}
		});
	}

	public boolean motionEvent(MotionEvent e) {
		lastInjectedTouchTime = uptimeMillis();

		MotionEvent clonedEvent = null;

		synchronized (touchLock) {
			var cnt = e.getPointerCount();
			if (cnt > reusableProps.length) {
				reusableProps = new MotionEvent.PointerProperties[cnt];
				reusableCoords = new MotionEvent.PointerCoords[cnt];
				for (int i = 0; i < cnt; i++) {
					reusableProps[i] = new MotionEvent.PointerProperties();
					reusableCoords[i] = new MotionEvent.PointerCoords();
				}
			}

			try {
				var m = metrics();
				if (m == null) return false;

				for (int i = 0; i < cnt; i++) {
					e.getPointerProperties(i, reusableProps[i]);
					e.getPointerCoords(i, reusableCoords[i]);
					reusableProps[i].id = 10 + i;
					reusableProps[i].toolType = MotionEvent.TOOL_TYPE_FINGER;

					reusableCoords[i].x = (reusableCoords[i].x - m.x) * m.scale;
					reusableCoords[i].y = (reusableCoords[i].y - m.y) * m.scale;

					if (reusableCoords[i].pressure <= 0.01f) reusableCoords[i].pressure = 1.0f;
					if (reusableCoords[i].size <= 0.01f) reusableCoords[i].size = 1.0f;
				}

				clonedEvent = MotionEvent.obtain(e.getDownTime(), e.getEventTime(), e.getAction(), cnt, reusableProps, reusableCoords,
						e.getMetaState(), e.getButtonState(), e.getXPrecision(), e.getYPrecision(), e.getDeviceId(),
						e.getEdgeFlags(), InputDevice.SOURCE_TOUCHSCREEN, e.getFlags());

			} catch (Exception ex) {
				Log.e(ex, "Touch clone failed.");
			}
		}

		if (clonedEvent != null) {
			final MotionEvent finalEvent = clonedEvent;
			try {
				touchExecutor.execute(() -> {
					try {
						EventDispatcher d = dispatcher();
						if (d != null) d.motionEvent(finalEvent);
					} catch (Exception ex) {}
				});
			} catch (Exception rejectedException) {}
		}

		return true;
	}

	public boolean motionEvent(long downTime, long eventTime, int action, float x, float y) {
		lastInjectedTouchTime = uptimeMillis();

		MotionEvent clonedEvent = null;

		synchronized (touchLock) {
			try {
				var m = metrics();
				if (m == null) return false;

				reusableProps[0].clear();
				reusableProps[0].id = 10;
				reusableProps[0].toolType = MotionEvent.TOOL_TYPE_FINGER;

				reusableCoords[0].clear();

				reusableCoords[0].x = (x - m.x) * m.scale;
				reusableCoords[0].y = (y - m.y) * m.scale;

				reusableCoords[0].pressure = 1.0f;
				reusableCoords[0].size = 1.0f;

				clonedEvent = MotionEvent.obtain(downTime, eventTime, action, 1, reusableProps, reusableCoords,
						0, 0, 1.0f, 1.0f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);

			} catch (Exception ex) {}
		}

		if (clonedEvent != null) {
			final MotionEvent finalEvent = clonedEvent;
			try {
				touchExecutor.execute(() -> {
					try {
						EventDispatcher d = dispatcher();
						if (d != null) d.motionEvent(finalEvent);
					} catch (Exception ex) {}
				});
			} catch (Exception rejectedException) {}
		}

		return true;
	}

	private void updateOverlayAttributes(boolean forceCarPriority) {
		try {
			var wm = (WindowManager) PermataApplication.get().getSystemService(WINDOW_SERVICE);
			boolean land = PermataApplication.get().isMirroringLandscape();
			int targetOrientation = forceCarPriority
					? (land ? SCREEN_ORIENTATION_LANDSCAPE : SCREEN_ORIENTATION_PORTRAIT)
					: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;

			if (rotationAnchor != null) {
				var anchorLp = (WindowManager.LayoutParams) rotationAnchor.getLayoutParams();
				if (anchorLp != null) {
					anchorLp.screenOrientation = targetOrientation;
					wm.updateViewLayout(rotationAnchor, anchorLp);
				}
			}

			if (overlay != null) {
				var overlayLp = (WindowManager.LayoutParams) overlay.getLayoutParams();
				if (overlayLp != null) {
					overlayLp.screenOrientation = targetOrientation;
					overlayLp.screenBrightness = isDimmed ? 0.0f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
					wm.updateViewLayout(overlay, overlayLp);
				}
			}
		} catch (Exception err) {}
	}

	public static void disableAccelRotation() {
		var app = PermataApplication.get();
		app.getHandler().postDelayed(() -> disableAccelRotation(app), 0);
	}

	private static void disableAccelRotation(Context ctx) {
		synchronized (lifecycleLock) {
			if (instance == null || instance.sc == null || instance.isDestroyed) return;
		}

		var a = EventDispatcher.get().getActivity();
		boolean land = PermataApplication.get().isMirroringLandscape();

		if (a != null) {
			a.setRequestedOrientation(land ? SCREEN_ORIENTATION_LANDSCAPE : SCREEN_ORIENTATION_PORTRAIT);
		}

		try {
			android.content.ContentResolver cr = ctx.getContentResolver();
			if (originalAccelState == -1) {
				int v = android.provider.Settings.System.getInt(cr, android.provider.Settings.System.ACCELEROMETER_ROTATION, -1);
				if (v != -1) originalAccelState = v;
			}
			android.provider.Settings.System.putInt(cr, android.provider.Settings.System.ACCELEROMETER_ROTATION, 0);
			android.provider.Settings.System.putInt(cr, android.provider.Settings.System.USER_ROTATION,
					land ? android.view.Surface.ROTATION_90 : android.view.Surface.ROTATION_0);
			Log.i("[MIRROR][DECISION] Rotation Lock Engaged | orientation=" + (land ? "LANDSCAPE" : "PORTRAIT")
					+ ", savedAccel=" + originalAccelState);
		} catch (Exception err) {
			Log.e(err, "[ROTATION_LOCK] Failed to write system settings.");
		}
	}

	private static void restoreAccelRotation(Context ctx, boolean synchronous) {
		var a = EventDispatcher.get().getActivity();
		if (a != null) a.setRequestedOrientation(SCREEN_ORIENTATION_UNSPECIFIED);

		if (originalAccelState != -1) {
			try {
				android.provider.Settings.System.putInt(ctx.getContentResolver(),
						android.provider.Settings.System.ACCELEROMETER_ROTATION, originalAccelState);
				Log.i("[MIRROR][DECISION] Rotation Lock Released | restoredAccel=" + originalAccelState);
			} catch (Exception err) {}
			originalAccelState = -1;
		}
	}

	private void dimScreen(Context ctx) {
		if (isDestroyed) return;
		synchronized (settingsLock) {
			if (isDimmed) return;
			isDimmed = true;
			Log.i("[MIRROR][DECISION] Screen Dimmed | brightness=0.0");
			mainHandler.post(() -> {
				updateOverlayAttributes(true);
				disableAccelRotation(PermataApplication.get());
			});
		}
	}

	private void restoreBrightness(Context ctx, boolean synchronous) {
		Runnable restoreTask = () -> {
			synchronized (settingsLock) {
				if (!isDimmed) return;
				isDimmed = false;
				Log.i("[MIRROR][DECISION] Screen Brightness Restored | sync=" + synchronous);
				updateOverlayAttributes(false);
			}
		};

		if (synchronous) {
			restoreTask.run();
		} else {
			mainHandler.post(restoreTask);
		}
	}

	@SuppressLint("UnspecifiedRegisterReceiverFlag")
	private void started() {
		if (sc == null || isDestroyed) return;
		var app = PermataApplication.get();
		var mode = sc.getWidth() > sc.getHeight() ? 1 : 2;
		String layoutType = (mode == 1) ? "LANDSCAPE" : "PORTRAIT";

		if (app.isMirroringMode()) {
			Log.i("[MIRROR][LIFECYCLE] Mirroring Resized/Updated | size=" + sc.getWidth() + "x" + sc.getHeight()
					+ ", orientation=" + layoutType);
			app.setMirroringMode(mode);
			updateOverlayAttributes(true);
			return;
		}

		Log.i("[MIRROR][LIFECYCLE] Mirroring Session Started | size=" + sc.getWidth() + "x" + sc.getHeight()
				+ ", dpi=" + sc.getDpi() + ", orientation=" + layoutType);

		disableAccelRotation(app);
		resetIdleTimer();

		if (unlockReceiver == null) {
			unlockReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					if (Intent.ACTION_USER_PRESENT.equals(intent.getAction()) && !isDestroyed) {
						Log.i("[MIRROR][DECISION] Phone Unlocked (ACTION_USER_PRESENT) | action=IMMEDIATE_DIM_AND_LOCK");
						mainHandler.removeCallbacks(idleDimRunnable);
						mainHandler.postDelayed(() -> {
							if (isDestroyed || sc == null) return;
							synchronized (settingsLock) {
								isDimmed = true;
							}
							updateOverlayAttributes(true);
							disableAccelRotation(PermataApplication.get());
						}, 300);
					}
				}
			};
			IntentFilter unlockFilter = new IntentFilter(Intent.ACTION_USER_PRESENT);
			if (SDK_INT >= VERSION_CODES.TIRAMISU) {
				app.registerReceiver(unlockReceiver, unlockFilter, Context.RECEIVER_NOT_EXPORTED);
			} else {
				app.registerReceiver(unlockReceiver, unlockFilter);
			}
		}

		var pmg = (PowerManager) app.getSystemService(POWER_SERVICE);
		if (pmg != null) {
			if (wakeLock != null && wakeLock.isHeld()) {
				wakeLock.release();
				wakeLock = null;
			}

			//noinspection deprecation
			wakeLock = pmg.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP,
					"Permata:ScreenLock");
			if (wakeLock != null) wakeLock.acquire(24 * 3600000);
		}

		if (SDK_INT >= VERSION_CODES.O) {
			try {
				var wm = (WindowManager) app.getSystemService(WINDOW_SERVICE);
				boolean land = (mode == 1);

				if (rotationAnchor == null) {
					var anchorLp = new WindowManager.LayoutParams(
							1, 1, TYPE_APPLICATION_OVERLAY, ANCHOR_FLAGS, PixelFormat.TRANSPARENT);
					anchorLp.gravity = Gravity.TOP | Gravity.START;
					anchorLp.screenOrientation = land ? SCREEN_ORIENTATION_LANDSCAPE : SCREEN_ORIENTATION_PORTRAIT;

					rotationAnchor = new View(app);
					wm.addView(rotationAnchor, anchorLp);
				}

				if (overlay == null) {
					var overlayLp = new WindowManager.LayoutParams(
							MATCH_PARENT, MATCH_PARENT, TYPE_APPLICATION_OVERLAY,
							OVERLAY_FLAGS, PixelFormat.TRANSLUCENT);
					overlayLp.gravity = Gravity.TOP | Gravity.START;
					overlayLp.screenOrientation = land ? SCREEN_ORIENTATION_LANDSCAPE : SCREEN_ORIENTATION_PORTRAIT;
					overlayLp.alpha = 0.05f;
					overlayLp.screenBrightness = 0.0f;

					synchronized (settingsLock) {
						isDimmed = true;
					}
					overlay = new Overlay(app);
					wm.addView(overlay, overlayLp);
					disableAccelRotation(app);
				}
			} catch (Exception err) {
				Log.e(err, "Failed to inject overlay hierarchy");
			}
		}

		try {
			app.startService(new Intent(app, XposedEventDispatcherService.class));
		} catch (IllegalStateException ise) {
		} catch (Exception err) {}

		PermataMediaService.requestFocusAndAnchor(app);

		setMirroringMode(app, mode);
	}

	private void cleanUpLocked() {
		if (isDestroyed) return;
		isDestroyed = true;
		Log.i("[MIRROR][LIFECYCLE] Mirroring Session Closing | action=FULL_CLEANUP");

		AccessibilityEventDispatcherService.abortActiveGesture();
		noSession();
		var app = PermataApplication.get();

		mainHandler.removeCallbacks(idleDimRunnable);
		if (unlockReceiver != null) {
			try {
				app.unregisterReceiver(unlockReceiver);
			} catch (Exception ignored) {}
			unlockReceiver = null;
		}
		app.sendBroadcast(new Intent(ACTION_KILL_WEBVIEW));

		restoreBrightness(app, true);
		restoreAccelRotation(app, true);

		var a = EventDispatcher.get().getActivity();
		if (a != null) a.setRequestedOrientation(SCREEN_ORIENTATION_UNSPECIFIED);

		if (overlay != null || rotationAnchor != null) {
			final Overlay oldOverlay = overlay;
			final View oldAnchor = rotationAnchor;

			mainHandler.post(() -> {
				try {
					var wm = (WindowManager) app.getSystemService(WINDOW_SERVICE);
					if (oldOverlay != null && oldOverlay.isAttachedToWindow()) wm.removeView(oldOverlay);
					if (oldAnchor != null && oldAnchor.isAttachedToWindow()) wm.removeView(oldAnchor);
				} catch (IllegalArgumentException iae) {
				} catch (Exception ignored) {}
			});

			overlay = null;
			rotationAnchor = null;
		}

		if (wakeLock != null) {
			try {
				if (wakeLock.isHeld()) wakeLock.release();
			} catch (Exception ignored) {}
			wakeLock = null;
		}

		setMirroringMode(app, 0);
		ProjectionService.stop();

		try {
			app.stopService(new Intent(app, XposedEventDispatcherService.class));
		} catch (Exception err) {}

		sc = null;
		currentMetrics = null;
		instance = null;
	}

	private void noSession() {
		if (session.isDoneNotFailed()) session.getOrThrow().close();
		else session.cancel();
		session = Completed.cancelled();
	}

	private void createSession() {
		if (!session.isDone()) return;
		var p = new Promise<Session>();
		session = p;
		createSession(p);
		p.onSuccess(s -> {
			synchronized (lifecycleLock) {
				if (isDestroyed) {
					s.close();
					return;
				}
				session = Completed.completed(s);
				started();
			}
		});
		PermataApplication.get().getHandler().schedule(() -> {
			if (!session.isDone()) drawMsg(R.string.unlock_phone_and_grant);
		}, 500);
	}

	private void createSession(Promise<Session> p) {
		if (session != p) return;
		if (sc == null) {
			noSession();
			return;
		}

		if (session.isDoneNotFailed()) {
			try {
				Session oldSession = session.getOrThrow();
				if (oldSession != null) oldSession.close();
			} catch (Exception e) {}
			session = Completed.cancelled();
		}

		if (p.isDone()) return;

		ProjectionService.start().onCompletion((mp, err) -> {
			synchronized (lifecycleLock) {
				if (isDestroyed || session != p) {
					if (mp != null) {
						try { mp.stop(); } catch (Exception ignored) {}
					}
					return;
				}

				if (sc == null) {
					noSession();
					return;
				}
				if (p.isDone()) return;

				if (err != null) {
					if (isCancellation(err)) {
						Log.i("[MIRROR][LIFECYCLE] Screen Capture Rejected by User | action=CLEANUP");
						drawMsg(R.string.screen_capture_rejected);
						cleanUpLocked();
						return;
					}
					retryCreateSession(p);
				} else if (mp == null) {
					retryCreateSession(p);
				} else {
					try {
						p.complete(new Session(mp, MirrorDisplay.this));
					} catch (Exception ex) {
						Log.e(ex, "[MIRROR][LIFECYCLE] Session creation failed, retrying");
						retryCreateSession(p);
					}
				}
			}
		});
	}

	private void retryCreateSession(Promise<Session> p) {
		PermataApplication.get().getHandler().schedule(() -> {
			if (p.isDone()) return;
			createSession(p);
		}, 3000);
	}

	private void drawMsg(@StringRes int msg) {
		if (sc != null) drawMsg(sc, msg);
	}

	private static void drawMsg(SurfaceContainer sc, @StringRes int msg) {
		try {
			var surface = sc.getSurface();
			if (surface == null || !surface.isValid()) return;
			var c = surface.lockCanvas(null);
			if (c == null) return;
			var w = sc.getWidth();
			var h = sc.getHeight();
			TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
			paint.setColor(Color.WHITE);
			paint.setTypeface(Typeface.DEFAULT);
			paint.setElegantTextHeight(true);
			paint.setTextAlign(Paint.Align.CENTER);
			paint.setTextSize(h / 10f);
			var text = PermataApplication.get().getString(msg);
			var sl = StaticLayout.Builder.obtain(text, 0, text.length(), paint, w).setMaxLines(5)
					.setEllipsize(TextUtils.TruncateAt.END).setIncludePad(true).build();
			c.drawColor(Color.BLACK);
			c.translate(w / 2f, h / 2f - sl.getHeight() / 2f);
			sl.draw(c);
			surface.unlockCanvasAndPost(c);
		} catch (Exception err) {}
	}

	private EventDispatcher dispatcher() {
		return EventDispatcher.get();
	}

	@Nullable
	private Metrics metrics() {
		if (sc == null || !session.isDoneNotFailed() || isDestroyed) return null;

		final Point size = new Point();
		defaultDisplay.getRealSize(size);

		float dw = size.x;
		float dh = size.y;
		float sw = sc.getWidth();
		float sh = sc.getHeight();

		boolean isLandscape = PermataApplication.get().isMirroringLandscape();

		if (isLandscape && dw < dh) {
			float temp = dw; dw = dh; dh = temp;
		} else if (!isLandscape && dw > dh) {
			float temp = dw; dw = dh; dh = temp;
		}

		boolean fill = isActiveFillMode();
		if (currentMetrics != null &&
			currentMetrics.fillMode == fill &&
			currentMetrics.sw == sw && currentMetrics.sh == sh &&
			currentMetrics.dw == dw && currentMetrics.dh == dh) {
			return currentMetrics;
		}

		currentMetrics = new Metrics(dw, dh, sw, sh, fill);
		return currentMetrics;
	}

	private void setMirroringMode(PermataApplication app, int mode) {
		app.setMirroringMode(mode);

		mainHandler.post(() -> {
			MainActivity activeMain = MainActivity.getActiveInstance();
			if (activeMain != null && !activeMain.isFinishing()) {
				Log.i("[MIRROR][DECISION] Finishing Active Phone MainActivity | mode=" + mode);
				activeMain.finish();
			}
		});

		if (mode != 0 && MainCarActivity.activeInstance == null) {
			Log.i("[MIRROR][DECISION] Launching Phone LauncherActivity | mode=" + mode);
			var intent = new Intent(app, LauncherActivity.class);
			intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
			app.startActivity(intent);
		} else if (mode == 0) {
			mainHandler.post(() -> {
				LauncherActivity activeLauncher = LauncherActivity.getActiveInstance();
				if (activeLauncher != null && !activeLauncher.isFinishing()) {
					Log.i("[MIRROR][DECISION] Finishing Phone LauncherActivity | mode=0");
					activeLauncher.finish();
				}
			});
		}
	}

	private static final class Session extends MediaProjection.Callback {
		final MediaProjection mp;
		final VirtualDisplay vd;
		final GlMirrorRenderer renderer;

		Session(MediaProjection mp, MirrorDisplay md) {
			this.mp = mp;
			var sc = md.sc;
			var app = PermataApplication.get();
			var name = app.getString(R.string.mirror_service_name);
			int[] vdSize = md.computeVirtualDisplaySize();

			renderer = new GlMirrorRenderer(vdSize[0], vdSize[1], md.isActiveFillMode());
			renderer.setOutputSurface(sc.getSurface(), sc.getWidth(), sc.getHeight());

			mp.registerCallback(this, app.getHandler());
			vd = mp.createVirtualDisplay(name, vdSize[0], vdSize[1], sc.getDpi(),
					DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
					renderer.getInputSurface(), null, null);
			if (vd == null) {
				renderer.release();
				throw new RuntimeException("Failed to create VirtualDisplay");
			}
		}

		@Override
		public void onStop() {
			Log.i("[MIRROR][LIFECYCLE] MediaProjection.Callback.onStop() | action=CLEANUP");
			close();
			synchronized (lifecycleLock) {
				MirrorDisplay md = MirrorDisplay.instance;
				if (md == null) return;
				md.cleanUpLocked();
			}
		}

		void close() {
			try { mp.unregisterCallback(this); } catch (Exception ignored) {}
			if (vd != null) {
				try { vd.release(); } catch (Exception ignored) {}
			}
			if (renderer != null) {
				renderer.release();
			}
			if (mp != null) {
				try { mp.stop(); } catch (Exception ignored) {}
			}
		}
	}

	private static final class Metrics {
		final float x;
		final float y;
		final float scale;
		final float sw;
		final float sh;
		final float dw;
		final float dh;
		final boolean fillMode;

		Metrics(float dw, float dh, float sw, float sh, boolean fillMode) {
			this.sw = sw;
			this.sh = sh;
			this.dw = dw;
			this.dh = dh;
			this.fillMode = fillMode;

			float scaleW = dw / sw;
			float scaleH = dh / sh;

			if (fillMode) {
				if (scaleW >= scaleH) {
					// Phone is wider than car: match height, crop left/right overhang (x <= 0)
					this.scale = scaleH;
					this.y = 0f;
					this.x = (sw - (dw / this.scale)) / 2f;
				} else {
					// Phone is taller than car: match width, crop top/bottom overhang (y <= 0)
					this.scale = scaleW;
					this.x = 0f;
					this.y = (sh - (dh / this.scale)) / 2f;
				}
			} else {
				if (scaleW >= scaleH) {
					// Fit: match width, letterbox top/bottom (y >= 0)
					this.scale = scaleW;
					this.x = 0f;
					this.y = (sh - (dh / this.scale)) / 2f;
				} else {
					// Fit: match height, pillarbox left/right (x >= 0)
					this.scale = scaleH;
					this.y = 0f;
					this.x = (sw - (dw / this.scale)) / 2f;
				}
			}
		}
	}

	private final class Overlay extends FrameLayout {

		public Overlay(@NonNull Context context) {
			super(context);
		}

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			int action = event.getActionMasked();
			if (action == MotionEvent.ACTION_OUTSIDE) {
				long deltaMs = Math.abs(uptimeMillis() - lastInjectedTouchTime);
				if (deltaMs > 500) {
					resetIdleTimer();
					if (isDimmed) {
						Log.i("[MIRROR][DECISION] Physical Phone Touch Detected | deltaMs=" + deltaMs
								+ " -> action=WAKE_AND_RESTORE_ROTATION");
						restoreBrightness(getContext(), false);
						restoreAccelRotation(getContext(), false);
					}
				}
			}
			return false;
		}
	}
}