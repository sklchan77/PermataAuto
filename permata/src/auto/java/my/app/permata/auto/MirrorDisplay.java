package my.app.permata.auto;

import static android.content.Context.POWER_SERVICE;
import static android.content.Context.WINDOW_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP;
import static android.os.SystemClock.uptimeMillis;
import static android.provider.Settings.System.ACCELEROMETER_ROTATION;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE;
import static android.provider.Settings.System.USER_ROTATION;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_90;
import static android.view.Surface.ROTATION_270;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static my.app.utils.function.ResultConsumer.Cancel.isCancellation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioManager;
import android.media.projection.MediaProjection;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings;
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
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.permata.ui.activity.MainActivity;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.async.Completed;
import my.app.utils.async.FutureSupplier;
import my.app.utils.async.Promise;
import my.app.utils.log.Log;
import my.app.utils.ui.UiUtils;

public class MirrorDisplay {
	private static final int OVERLAY_FLAGS =
			FLAG_NOT_FOCUSABLE | FLAG_KEEP_SCREEN_ON | FLAG_DISMISS_KEYGUARD | FLAG_TURN_SCREEN_ON |
					FLAG_SHOW_WHEN_LOCKED | FLAG_NOT_TOUCH_MODAL | FLAG_WATCH_OUTSIDE_TOUCH;
					
	private static final int ANCHOR_FLAGS = 
			FLAG_NOT_TOUCHABLE | FLAG_KEEP_SCREEN_ON | FLAG_DISMISS_KEYGUARD | FLAG_SHOW_WHEN_LOCKED | FLAG_ALT_FOCUSABLE_IM;
			
	private static MirrorDisplay instance;
	private final Display defaultDisplay;
	private final float scaleDiff;
	private final AudioFocusRequestCompat audioFocusReq;
	private WakeLock wakeLock;
	
	private static final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
	private static final ExecutorService touchExecutor = Executors.newSingleThreadExecutor();
	
	private static final Object lifecycleLock = new Object(); 
	private static final Object settingsLock = new Object();
	
	private static volatile boolean isRotationLocked = false;
	private static volatile boolean isDimmed = false;
	private static volatile int accel = -1;
	private static volatile int originalUserRotation = -1; 
	private static volatile int brightnessMode = -1; 
	private static volatile int brightness = -1;
	
	private volatile long lastInjectedTouchTime; 
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
		Log.d("Idle timeout reached. Dimming screen.");
		dimScreen(PermataApplication.get());
	};

	private final Runnable orientationHeartbeat = new Runnable() {
		@Override
		public void run() {
			if (!isDimmed || sc == null || isDestroyed) return;
			
			try {
				boolean wantLandscape = PermataApplication.get().isMirroringLandscape();
				int rotation = defaultDisplay.getRotation();
				boolean isCurrentlyLandscape = (rotation == ROTATION_90 || rotation == ROTATION_270);
				
				if (wantLandscape != isCurrentlyLandscape) {
					Log.w("Real rogue app hijacked physical rotation. Forcing self-healing correction.");
					updateOverlayOrientation(true);
					disableAccelRotation(PermataApplication.get());
				}
			} catch (Exception err) {
				Log.e(err, "Orientation heartbeat check failed.");
			}
			
			if (isDimmed && !isDestroyed) {
				mainHandler.postDelayed(this, 3000); // 3 second heartbeat during idle
			}
		}
	};

	private MirrorDisplay() {
		var ctx = PermataApplication.get();
		var vm = (WindowManager) ctx.getSystemService(WINDOW_SERVICE);
		defaultDisplay = vm.getDefaultDisplay();
		var size = new Point();
		defaultDisplay.getRealSize(size);
		scaleDiff = Math.max(UiUtils.toPx(ctx, 20), Math.min(size.x, size.y) / 20f);
		
		audioFocusReq =
				new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).setAudioAttributes(
								new AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA)
										.setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build())
						.setWillPauseWhenDucked(false).setOnAudioFocusChangeListener(focusChange -> {}).build();
		
		reusableProps = new MotionEvent.PointerProperties[10];
		reusableCoords = new MotionEvent.PointerCoords[10];
		for (int i = 0; i < 10; i++) {
			reusableProps[i] = new MotionEvent.PointerProperties();
			reusableCoords[i] = new MotionEvent.PointerCoords();
		}
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
			var scRef = instance.sc;
			instance.cleanUpLocked();
			if (scRef != null) drawMsg(scRef, R.string.app_name);
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
			
			if (session.isDoneNotFailed()) {
				try {
					var vd = this.session.getOrThrow().vd;
					vd.setSurface(sc.getSurface());
					vd.resize(sc.getWidth(), sc.getHeight(), sc.getDpi());
					started();
				} catch (Throwable err) {
					Log.d(err);
					noSession();
					createSession();
				}
			} else {
				createSession();
			}
			if (oldSc != null) drawMsg(oldSc, R.string.app_name);
		}
	}

	public void releaseSurface(@NonNull SurfaceContainer sc) {
		synchronized (lifecycleLock) {
			if (isDestroyed) return;
			var oldSc = this.sc;
			if (oldSc != sc) return;
			this.sc = null;
			
			if (session.isDoneNotFailed()) {
				try {
					Session s = session.getOrThrow();
					if (s != null && s.vd != null) s.vd.setSurface(null);
				} catch (Exception e) {
					Log.e(e, "VirtualDisplay detachment failed.");
				}
			}
			drawMsg(oldSc, R.string.app_name);
		}
	}

	private void resetIdleTimer() {
		mainHandler.removeCallbacks(idleDimRunnable);
		mainHandler.postDelayed(idleDimRunnable, 6000);
	}

	public void tap(float x, float y) {
		resetIdleTimer();
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
		resetIdleTimer();
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
		resetIdleTimer();
		
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
					} catch (Exception ex) {
						Log.e(ex, "Async touch injection failed.");
					}
				});
			} catch (Exception rejectedException) {
				Log.e(rejectedException, "Touch thread rejected execution.");
			}
		}
		
		return true;
	}

	public boolean motionEvent(long downTime, long eventTime, int action, float x, float y) {
		lastInjectedTouchTime = uptimeMillis(); 
		resetIdleTimer();
		
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
						
			} catch (Exception ex) {
				Log.e(ex, "Single touch motion builder failed.");
			}
		}

		if (clonedEvent != null) {
			final MotionEvent finalEvent = clonedEvent;
			try {
				touchExecutor.execute(() -> {
					try {
						EventDispatcher d = dispatcher();
						if (d != null) d.motionEvent(finalEvent);
					} catch (Exception ex) {
						Log.e(ex, "Async single touch injection failed.");
					}
				});
			} catch (Exception rejectedException) {
				Log.e(rejectedException, "Touch thread rejected execution.");
			}
		}
		
		return true;
	}

	private void updateOverlayOrientation(boolean forceCarPriority) {
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
					wm.updateViewLayout(overlay, overlayLp);
				}
			}
		} catch (Exception err) {
			Log.e(err, "Failed to update overlay orientation hierarchy");
		}
	}

	public static void disableAccelRotation() {
		var app = PermataApplication.get();
		app.getHandler().postDelayed(() -> disableAccelRotation(app), 3000);
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
			bgExecutor.execute(() -> {
				synchronized (settingsLock) {
					if (isRotationLocked) return;
					try {
						var cr = ctx.getContentResolver();
						if (accel == -1) {
							var v = Settings.System.getInt(cr, ACCELEROMETER_ROTATION, -1);
							if (v != -1) accel = v;
						}
						
						if (originalUserRotation == -1) {
							var ur = Settings.System.getInt(cr, USER_ROTATION, -1);
							if (ur != -1) originalUserRotation = ur;
						}
						
						Settings.System.putInt(cr, ACCELEROMETER_ROTATION, 0);
						Settings.System.putInt(cr, USER_ROTATION, land ? ROTATION_90 : ROTATION_0);
						isRotationLocked = true;
					} catch (Exception err) {
						Log.e(err);
					}
				}
			});
		} catch (Exception ex) {
			Log.e(ex, "Background thread rejected rotation lock instruction");
		}
	}

	private static void restoreAccelRotation(Context ctx, boolean synchronous) {
		var a = EventDispatcher.get().getActivity();
		if (a != null) a.setRequestedOrientation(SCREEN_ORIENTATION_UNSPECIFIED);

		Runnable task = () -> {
			synchronized (settingsLock) {
				if (!isRotationLocked) return;
				try {
					var cr = ctx.getContentResolver();
					if (accel != -1) {
						Settings.System.putInt(cr, ACCELEROMETER_ROTATION, accel);
						accel = -1;
					}
					
					if (originalUserRotation != -1) {
						Settings.System.putInt(cr, USER_ROTATION, originalUserRotation);
						originalUserRotation = -1;
					}
					isRotationLocked = false;
				} catch (Exception err) {
					Log.e(err, "Settings Provider lagged or failed during rotation restore.");
				}
			}
		};

		if (synchronous) {
			task.run();
		} else {
			try {
				bgExecutor.execute(task);
			} catch (Exception ex) {
				Log.e(ex, "Background thread rejected rotation restore instruction");
			}
		}
	}

	private void dimScreen(Context ctx) {
		if (isDestroyed) return;
		try {
			bgExecutor.execute(() -> {
				if (sc == null || isDestroyed) return; 
				
				synchronized (settingsLock) {
					if (isDimmed) return;
					try {
						var cr = ctx.getContentResolver();
						
						if (brightnessMode == -1) {
							var mode = Settings.System.getInt(cr, SCREEN_BRIGHTNESS_MODE, -1);
							if (mode != -1) brightnessMode = mode;
						}
						Settings.System.putInt(cr, SCREEN_BRIGHTNESS_MODE, 0); 
						
						if (brightness == -1) {
							var br = Settings.System.getInt(cr, SCREEN_BRIGHTNESS, -1);
							brightness = (br > 0) ? br : 200;
						}
						
						Settings.System.putInt(cr, SCREEN_BRIGHTNESS, 1);
						isDimmed = true;
						
						mainHandler.post(() -> {
							mainHandler.removeCallbacks(orientationHeartbeat);
							// Immediately trigger heartbeat lock, then cycle every 3s
							orientationHeartbeat.run(); 
						});
					} catch (Exception err) {
						Log.e(err);
					}
				}
			});
		} catch (Exception ex) {
			Log.e(ex, "Background thread rejected screen dim instruction");
		}
	}

	private void restoreBrightness(Context ctx, boolean synchronous) {
		Runnable restoreTask = () -> {
			synchronized (settingsLock) {
				if (!isDimmed) return;
				try {
					var cr = ctx.getContentResolver();
					if (brightness > 0) {
						Settings.System.putInt(cr, SCREEN_BRIGHTNESS, brightness);
					}
					if (brightnessMode != -1) {
						Settings.System.putInt(cr, SCREEN_BRIGHTNESS_MODE, brightnessMode);
						brightnessMode = -1;
					}
					isDimmed = false;
					mainHandler.post(() -> mainHandler.removeCallbacks(orientationHeartbeat));
				} catch (Exception err) {
					Log.e(err, "Settings Provider lagged or failed during brightness restore.");
				}
			}
		};

		if (synchronous) {
			restoreTask.run();
		} else {
			try {
				bgExecutor.execute(restoreTask);
			} catch (Exception ex) {
				Log.e(ex, "Background thread rejected brightness restore instruction");
			}
		}
	}

	private void started() {
		if (sc == null || isDestroyed) return;
		var app = PermataApplication.get();
		var mode = sc.getWidth() > sc.getHeight() ? 1 : 2;

		if (app.isMirroringMode()) {
			app.setMirroringMode(mode);
			updateOverlayOrientation(true);
			return;
		}

		dimScreen(app);
		disableAccelRotation(app);
		resetIdleTimer();

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
					overlayLp.alpha = 0.01f;
					
					overlay = new Overlay(app);
					wm.addView(overlay, overlayLp);
				}
			} catch (Exception err) {
				Log.e(err, "Failed to inject overlay hierarchy");
			}
		}

		try {
			app.startService(new Intent(app, XposedEventDispatcherService.class));
		} catch (IllegalStateException ise) {
			Log.w("Background Service restrictions active, ignoring Xposed Service startup.");
		} catch (Exception err) {
			Log.e(err, "Failed to start XposedEventDispatcherService");
		}

		var amgr = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
		if (amgr != null) AudioManagerCompat.requestAudioFocus(amgr, audioFocusReq);
		
		setMirroringMode(app, mode);
	}

	private void cleanUpLocked() {
		if (isDestroyed) return;
		isDestroyed = true;
		
		noSession();
		var app = PermataApplication.get();
		
		mainHandler.removeCallbacks(idleDimRunnable);
		mainHandler.removeCallbacks(orientationHeartbeat);
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
					Log.w("Zombie view prevented during cleanup execution.");
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
		} catch (Exception err) {
			Log.d(err, "Failed to stop XposedEventDispatcherService");
		}

		var amgr = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
		if (amgr != null) AudioManagerCompat.abandonAudioFocusRequest(amgr, audioFocusReq);
		
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
				Log.i("Session created: ", s);
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
			} catch (Exception e) {
				Log.e(e, "Failed to guillotine previous session.");
			}
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
						drawMsg(R.string.screen_capture_rejected);
						cleanUpLocked();
						return;
					}
					Log.e(err, "Failed to create media projection");
					retryCreateSession(p);
				} else if (mp == null) {
					Log.e("Failed to create media projection");
					retryCreateSession(p);
				} else {
					try {
						p.complete(new Session(mp, MirrorDisplay.this));
					} catch (Exception ex) {
						Log.e(ex, "Failed to create VirtualDisplay");
						retryCreateSession(p);
					}
				}
			}
		});
	}

	private void retryCreateSession(Promise<Session> p) {
		PermataApplication.get().getHandler().schedule(() -> {
			if (p.isDone()) return;
			Log.i("Retrying to create media projection");
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
		} catch (Exception err) {
			Log.d(err, "Failed to draw message on surface");
		}
	}

	private EventDispatcher dispatcher() {
		var d = EventDispatcher.get();
		Log.d(d);
		return d;
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
		
		if (currentMetrics != null && 
			currentMetrics.sw == sw && currentMetrics.sh == sh && 
			currentMetrics.dw == dw && currentMetrics.dh == dh) {
			return currentMetrics;
		}
		
		currentMetrics = new Metrics(dw, dh, sw, sh);
		return currentMetrics;
	}

	private void setMirroringMode(PermataApplication app, int mode) {
		app.setMirroringMode(mode);
		var intent = new Intent(app, MainActivity.class);
		intent.setAction(MainActivityDelegate.INTENT_ACTION_FINISH);
		intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
		app.startActivity(intent);

		intent = new Intent(app, LauncherActivity.class);
		intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
		if (mode == 0) intent.setAction(MainActivityDelegate.INTENT_ACTION_FINISH);
		app.startActivity(intent);
	}

	private static final class Session extends MediaProjection.Callback {
		final MediaProjection mp;
		final VirtualDisplay vd;

		Session(MediaProjection mp, MirrorDisplay md) {
			this.mp = mp;
			var sc = md.sc;
			var app = PermataApplication.get();
			var name = app.getString(R.string.mirror_service_name);
			mp.registerCallback(this, app.getHandler());
			vd = mp.createVirtualDisplay(name, sc.getWidth(), sc.getHeight(), sc.getDpi(),
					DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY, sc.getSurface(), null, null);
			if (vd == null) throw new RuntimeException("Failed to create VirtualDisplay");
			Log.i("VirtualDisplay created");
		}

		@Override
		public void onStop() {
			close();
			synchronized (lifecycleLock) {
				MirrorDisplay md = MirrorDisplay.instance;
				if (md == null) return;
				Log.i("Media projection stopped");
				md.cleanUpLocked();
			}
		}

		void close() {
			mp.unregisterCallback(this);
			if (vd != null) vd.release();
			if (mp != null) mp.stop();
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

		Metrics(float dw, float dh, float sw, float sh) {
			this.sw = sw;
			this.sh = sh;
			this.dw = dw;
			this.dh = dh;
			
			var scale = dw / sw;
			var scaleH = dh / scale;
			if (scaleH <= sh) {
				x = 0f;
				y = (sh - scaleH) / 2f;
				assert y >= 0f;
			} else {
				scale = dh / sh;
				y = 0f;
				x = (sw - (dw / scale)) / 2;
				assert x >= 0f;
			}
			this.scale = scale;
		}
	}

	private final class Overlay extends FrameLayout {
		
		public Overlay(@NonNull Context context) {
			super(context);
		}

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			// Check if touch came from the physical phone display (id 0) vs car virtual display
			if (event.getDisplayId() != Display.DEFAULT_DISPLAY) {
				return false;
			}
			
			int action = event.getActionMasked();
			if (action == MotionEvent.ACTION_OUTSIDE || action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
				Log.d("True physical touch on phone detected. Restoring brightness and natural orientation.");
				
				resetIdleTimer();
				var ctx = getContext();
				restoreBrightness(ctx, false);
				restoreAccelRotation(ctx, false); 
				updateOverlayOrientation(false); 
			}
			return false; 
		}
	}
}