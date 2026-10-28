package my.app.permata.auto;

import static android.content.Context.POWER_SERVICE;
import static android.content.Context.WINDOW_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP;
import static android.os.SystemClock.uptimeMillis;
import static android.provider.Settings.System.ACCELEROMETER_ROTATION;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE;
import static android.provider.Settings.System.USER_ROTATION;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
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
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioManager;
import android.media.projection.MediaProjection;
import android.os.Build;
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
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.car.app.SurfaceContainer;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.permata.ui.activity.MainActivity;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.async.Completed;
import my.app.utils.async.FutureSupplier;
import my.app.utils.async.Promise;
import my.app.utils.concurrent.ReschedulableTask;
import my.app.utils.log.Log;
import my.app.utils.ui.UiUtils;

public class MirrorDisplay {
	private static final int OVERLAY_FLAGS =
			FLAG_NOT_FOCUSABLE | FLAG_KEEP_SCREEN_ON | FLAG_DISMISS_KEYGUARD | FLAG_TURN_SCREEN_ON |
					FLAG_SHOW_WHEN_LOCKED | FLAG_NOT_TOUCHABLE | FLAG_WATCH_OUTSIDE_TOUCH;
	private static WeakReference<MirrorDisplay> ref;
	private final int[] loc = new int[2];
	private final Display defaultDisplay;
	private final float scaleDiff;
	private final AudioFocusRequestCompat audioFocusReq;
	private WakeLock wakeLock;
	
	// GUARANTEE: Eliminates Thread-Thrashing by queuing IPC operations
	private static final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
	private static final AtomicBoolean isTearingDown = new AtomicBoolean(false);
	
	// IPC System State Caches
	private static volatile boolean isRotationLocked = false;
	private static volatile boolean isDimmed = false;
	private static volatile int accel = -1;
	private static volatile int brightnessMode = -1; 
	private volatile int brightness = -1;
	private volatile long lastInjectedTouchTime; 
	
	// Micro-locking for multi-touch concurrency
	private final Object touchLock = new Object();
	
	private int refCounter;
	private FutureSupplier<Session> session = Completed.cancelled();
	private SurfaceContainer sc;
	private Overlay overlay;
	private Metrics lMetrics;
	private Metrics pMetrics;
	
	private MotionEvent.PointerProperties[] reusableProps;
	private MotionEvent.PointerCoords[] reusableCoords;
	private volatile boolean cacheValid = false;
	
	private float dx;
	private float dy;
	private long downTime;

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
		MirrorDisplay md;
		if ((ref == null) || ((md = ref.get()) == null)) {
			ref = new WeakReference<>(md = new MirrorDisplay());
		}
		md.refCounter++;
		return md;
	}

	public static void close() {
		MirrorDisplay md;
		if ((ref == null) || ((md = ref.get()) == null)) return;
		var sc = md.sc;
		md.cleanUp();
		if (sc != null) drawMsg(sc, R.string.app_name);
	}

	public void release() {
		if (--refCounter == 0) cleanUp();
	}

	public void setSurface(@NonNull SurfaceContainer sc) {
		var oldSc = this.sc;
		if (oldSc == sc) return;
		
		// Guard against Async VirtualDisplay Race Conditions
		if (isTearingDown.get()) {
			Log.w("Ignoring surface set; teardown in progress.");
			return;
		}
		
		this.sc = sc;
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

	public void releaseSurface(@NonNull SurfaceContainer sc) {
		var oldSc = this.sc;
		if (oldSc != sc) return;
		this.sc = null;
		
		if (session.isDoneNotFailed()) {
			isTearingDown.set(true);
			bgExecutor.execute(() -> {
				try {
					Session s = session.getOrThrow();
					if (s != null && s.vd != null) s.vd.setSurface(null);
				} catch (Exception e) {
					Log.e(e, "Safe VirtualDisplay detachment failed.");
				} finally {
					isTearingDown.set(false);
				}
			});
		}
		drawMsg(oldSc, R.string.app_name);
	}

	public void tap(float x, float y) {
		cacheValid = false;
		var d = translate(x, y, MotionEvent.ACTION_DOWN);
		if (d != null) d.tap(dx, dy);
		cacheValid = false;
	}

	public void scale(float x, float y, boolean zoomIn) {
		cacheValid = false;
		var d = translate(x, y, MotionEvent.ACTION_DOWN);
		if (d != null) d.scale(dx, dy, zoomIn ? scaleDiff : -scaleDiff);
		cacheValid = false;
	}

	public boolean motionEvent(MotionEvent e) {
		lastInjectedTouchTime = uptimeMillis(); 
		EventDispatcher d = null; 
		var actionMasked = e.getActionMasked();
		
		if (actionMasked == MotionEvent.ACTION_DOWN) {
			downTime = uptimeMillis();
			cacheValid = false; 
		}
		
		var cnt = e.getPointerCount();
		
		MotionEvent clonedEvent = null;
		boolean result = false;

		synchronized (touchLock) {
			if (cnt > reusableProps.length) {
				reusableProps = new MotionEvent.PointerProperties[cnt];
				reusableCoords = new MotionEvent.PointerCoords[cnt];
				for (int i = 0; i < cnt; i++) {
					reusableProps[i] = new MotionEvent.PointerProperties();
					reusableCoords[i] = new MotionEvent.PointerCoords();
				}
			}

			try {
				if (cnt == 1) {
					d = translate(e.getX(), e.getY(), actionMasked);
					if (d == null) return false;
					
					e.getPointerProperties(0, reusableProps[0]);
					e.getPointerCoords(0, reusableCoords[0]);
					
					reusableProps[0].id = 10; 
					reusableProps[0].toolType = MotionEvent.TOOL_TYPE_FINGER;
					reusableCoords[0].x = dx;
					reusableCoords[0].y = dy;
					if (reusableCoords[0].pressure <= 0.01f) reusableCoords[0].pressure = 1.0f;
					if (reusableCoords[0].size <= 0.01f) reusableCoords[0].size = 1.0f;
					
					clonedEvent = MotionEvent.obtain(downTime, uptimeMillis(), e.getAction(), 1, reusableProps, reusableCoords,
							e.getMetaState(), e.getButtonState(), e.getXPrecision(), e.getYPrecision(), e.getDeviceId(),
							e.getEdgeFlags(), InputDevice.SOURCE_TOUCHSCREEN, e.getFlags());
				} else {
					d = dispatcher();
					var a = d.getActivity();
					var m = metrics(a);
					if (m == null) return false;
					
					if (actionMasked == MotionEvent.ACTION_DOWN || actionMasked == MotionEvent.ACTION_POINTER_DOWN || !cacheValid) {
						if (a != null) a.getWindow().getDecorView().getLocationOnScreen(loc);
						else { loc[0] = 0; loc[1] = 0; }
						cacheValid = true;
					}

					for (int i = 0; i < cnt; i++) {
						e.getPointerProperties(i, reusableProps[i]);
						e.getPointerCoords(i, reusableCoords[i]);
						
						reusableProps[i].id = 10 + i; 
						reusableProps[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
						
						if (a != null) {
							reusableCoords[i].x = (reusableCoords[i].x - m.x) * m.scale - loc[0];
							reusableCoords[i].y = (reusableCoords[i].y - m.y) * m.scale - loc[1];
						} else {
							reusableCoords[i].x = (reusableCoords[i].x - m.x) * m.scale;
							reusableCoords[i].y = (reusableCoords[i].y - m.y) * m.scale;
						}
						
						if (reusableCoords[i].pressure <= 0.01f) reusableCoords[i].pressure = 1.0f;
						if (reusableCoords[i].size <= 0.01f) reusableCoords[i].size = 1.0f;
					}
					clonedEvent = MotionEvent.obtain(downTime, uptimeMillis(), e.getAction(), cnt, reusableProps, reusableCoords,
							e.getMetaState(), e.getButtonState(), e.getXPrecision(), e.getYPrecision(), e.getDeviceId(),
							e.getEdgeFlags(), InputDevice.SOURCE_TOUCHSCREEN, e.getFlags());
				}
			} catch (Exception ex) {
				Log.e(ex, "Touch clone failed.");
			}
		}
		
		if (clonedEvent != null && d != null) {
			try {
				result = d.motionEvent(clonedEvent);
			} finally {
				clonedEvent.recycle();
				if (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL) {
					cacheValid = false;
				}
			}
		}
		return result;
	}

	public boolean motionEvent(long downTime, long eventTime, int action, float x, float y) {
		lastInjectedTouchTime = uptimeMillis(); 
		if (action == MotionEvent.ACTION_DOWN) cacheValid = false;
		
		var d = translate(x, y, action);
		if (d == null) return false;
		
		boolean result = false;
		MotionEvent e = null;

		synchronized (touchLock) {
			try {
				reusableProps[0].clear();
				reusableProps[0].id = 10; 
				reusableProps[0].toolType = MotionEvent.TOOL_TYPE_FINGER;
				
				reusableCoords[0].clear();
				reusableCoords[0].x = dx;
				reusableCoords[0].y = dy;
				reusableCoords[0].pressure = 1.0f;
				reusableCoords[0].size = 1.0f;
				
				e = MotionEvent.obtain(downTime, eventTime, action, 1, reusableProps, reusableCoords,
						0, 0, 1.0f, 1.0f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
						
			} catch (Exception ex) {
				Log.e(ex, "Single touch motion builder failed.");
			}
		}

		if (e != null) {
			try {
				result = d.motionEvent(e);
			} finally {
				e.recycle();
				if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
					cacheValid = false;
				}
			}
		}
		
		return result;
	}

	@Nullable
	private EventDispatcher translate(float x, float y, int action) {
		var d = dispatcher();
		var a = d.getActivity();
		var m = metrics(a);
		if (m == null) return null;

		if (action == MotionEvent.ACTION_DOWN || !cacheValid) {
			if (a != null) {
				a.getWindow().getDecorView().getLocationOnScreen(loc);
			} else {
				loc[0] = 0;
				loc[1] = 0;
			}
			cacheValid = true;
		}

		if (a != null) {
			dx = (x - m.x) * m.scale - loc[0];
			dy = (y - m.y) * m.scale - loc[1];
		} else {
			dx = (x - m.x) * m.scale;
			dy = (y - m.y) * m.scale;
		}
		
		return d;
	}

	public static void disableAccelRotation() {
		var app = PermataApplication.get();
		app.getHandler().postDelayed(() -> disableAccelRotation(app), 3000);
	}

	private static void disableAccelRotation(Context ctx) {
		var a = EventDispatcher.get().getActivity();
		var land = PermataApplication.get().isMirroringLandscape();
		if (a != null) {
			a.setRequestedOrientation(
					land ? SCREEN_ORIENTATION_SENSOR_LANDSCAPE : SCREEN_ORIENTATION_SENSOR_PORTRAIT);
		}

		bgExecutor.execute(() -> {
			if (isRotationLocked) return;
			try {
				var cr = ctx.getContentResolver();
				if (accel == -1) {
					var v = Settings.System.getInt(cr, ACCELEROMETER_ROTATION, -1);
					if (v != -1) accel = v;
				}
				Settings.System.putInt(cr, ACCELEROMETER_ROTATION, 0);
				Settings.System.putInt(cr, USER_ROTATION, land ? ROTATION_90 : ROTATION_0);
				isRotationLocked = true;
			} catch (Exception err) {
				Log.e(err);
			}
		});
	}

	private static void restoreAccelRotation(Context ctx) {
		var a = EventDispatcher.get().getActivity();
		if (a != null) a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		
		bgExecutor.execute(() -> {
			if (!isRotationLocked) return;
			try {
				if (accel != -1) {
					Settings.System.putInt(ctx.getContentResolver(), ACCELEROMETER_ROTATION, accel);
					accel = -1;
				}
				isRotationLocked = false;
			} catch (Exception err) {
				Log.e(err);
			}
		});
	}

	private void dimScreen(Context ctx) {
		bgExecutor.execute(() -> {
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
				if (!Build.MANUFACTURER.equalsIgnoreCase("Xiaomi")) {
					Settings.System.putInt(cr, SCREEN_BRIGHTNESS, 0);
				}
				isDimmed = true;
			} catch (Exception err) {
				Log.e(err);
			}
		});
	}

	private void restoreBrightness(Context ctx) {
		bgExecutor.execute(() -> {
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
			} catch (Exception err) {
				Log.e(err);
			}
		});
	}

	@Override
	protected void finalize() {
		if ((ref == null) || (ref.get() == null) || (ref.get() == this)) {
			cleanUp();
		}
	}

	private void started() {
		if (sc == null) return;
		var app = PermataApplication.get();
		var mode = sc.getWidth() > sc.getHeight() ? 1 : 2;

		if (app.isMirroringMode()) {
			app.setMirroringMode(mode);
			return;
		}

		dimScreen(app);
		disableAccelRotation(app);

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

		if ((overlay == null) && (SDK_INT >= VERSION_CODES.O)) {
			try {
				var wm = (WindowManager) app.getSystemService(WINDOW_SERVICE);
				var lp =
						new WindowManager.LayoutParams(MATCH_PARENT, MATCH_PARENT, TYPE_APPLICATION_OVERLAY,
								OVERLAY_FLAGS, PixelFormat.TRANSPARENT);
				var overlay = new Overlay(app);
				wm.addView(overlay, lp);
				this.overlay = overlay;
				
				overlay.dimAndRotate.schedule(15000);
			} catch (Exception err) {
				Log.e(err, "Failed to add overlay");
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

	private void cleanUp() {
		noSession();
		var app = PermataApplication.get();
		
		if (overlay != null) {
			overlay.dimAndRotate.cancel();
			final Overlay oldOverlay = overlay;
			new Handler(Looper.getMainLooper()).post(() -> {
				try {
					var wm = (WindowManager) app.getSystemService(WINDOW_SERVICE);
					wm.removeView(oldOverlay);
				} catch (Exception ignored) {}
			});
			overlay = null;
		}
		
		if (wakeLock != null) {
			try {
				if (wakeLock.isHeld()) wakeLock.release();
			} catch (Exception ignored) {}
			wakeLock = null;
		}
		
		setMirroringMode(app, 0);
		restoreBrightness(app);
		restoreAccelRotation(app);
		ProjectionService.stop();

		try {
			app.stopService(new Intent(app, XposedEventDispatcherService.class));
		} catch (Exception err) {
			Log.d(err, "Failed to stop XposedEventDispatcherService");
		}

		var amgr = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
		if (amgr != null) AudioManagerCompat.abandonAudioFocusRequest(amgr, audioFocusReq);
		
		sc = null;
		lMetrics = pMetrics = null;
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
			Log.i("Session created: ", s);
			session = Completed.completed(s);
			started();
		});
		PermataApplication.get().getHandler().schedule(() -> {
			if (!session.isDone()) drawMsg(R.string.unlock_phone_and_grant);
		}, 500);
	}

	private void createSession(Promise<Session> p) {
		if (session != p) return;
		if (sc == null) noSession();
		if (p.isDone()) return; 
		ProjectionService.start().onCompletion((mp, err) -> {
			if (session != p) return;
			if (sc == null) noSession();
			if (p.isDone()) return;
			if (err != null) {
				if (isCancellation(err)) {
					drawMsg(R.string.screen_capture_rejected);
					cleanUp();
					return;
				}
				Log.e(err, "Failed to create media projection");
				retryCreateSession(p);
			} else if (mp == null) {
				Log.e("Failed to create media projection");
				retryCreateSession(p);
			} else {
				try {
					p.complete(new Session(mp, this));
				} catch (Exception ex) {
					Log.e(ex, "Failed to create media projection");
					retryCreateSession(p);
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
			if (surface == null) return;
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
	@SuppressLint("SwitchIntDef")
	private Metrics metrics(@Nullable AppCompatActivity a) {
		if (sc == null) return null;
		
		// FIX: Never trust PiP or Floating Window configurations.
		// Rely 100% on the absolute physical hardware rotation.
		boolean landscape = switch (defaultDisplay.getRotation()) {
			case ROTATION_90, ROTATION_270 -> true;
			default -> false;
		};
		
		var m = landscape ? lMetrics : pMetrics;
		
		if (m != null && (m.sw != sc.getWidth() || m.sh != sc.getHeight())) {
			m = null; 
		}
		
		if (m == null) {
			if (!session.isDoneNotFailed()) return null;
			final Point size = new Point();
			defaultDisplay.getRealSize(size);
			
			float realWidth = size.x;
			float realHeight = size.y;
			
			if (landscape && realWidth < realHeight) {
				realWidth = size.y;
				realHeight = size.x;
			} else if (!landscape && realWidth > realHeight) {
				realWidth = size.y;
				realHeight = size.x;
			}

			m = new Metrics(realWidth, realHeight, sc.getWidth(), sc.getHeight());
			if (landscape) lMetrics = m;
			else pMetrics = m;
		}
		return m;
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
		final WeakReference<MirrorDisplay> mdRef;

		Session(MediaProjection mp, MirrorDisplay md) {
			this.mp = mp;
			this.mdRef = new WeakReference<>(md);
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
			var md = mdRef.get();
			if (md == null) return;
			Log.i("Media projection stopped");
			md.cleanUp();
		}

		void close() {
			mp.unregisterCallback(this);
			vd.release();
			mp.stop();
		}
	}

	private static final class Metrics {
		final float x;
		final float y;
		final float scale;
		final float sw; 
		final float sh; 

		Metrics(float dw, float dh, float sw, float sh) {
			this.sw = sw;
			this.sh = sh;
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
		final ReschedulableTask dimAndRotate = new ReschedulableTask() {
			@Override
			protected void perform() {
				var ctx = getContext();
				dimScreen(ctx);
				disableAccelRotation(ctx);
			}
		};

		public Overlay(@NonNull Context context) {
			super(context);
		}

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			if (Math.abs(uptimeMillis() - lastInjectedTouchTime) < 200) return false;
			
			if (event.getAction() == MotionEvent.ACTION_OUTSIDE || event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
				Log.d("Temporary restoring brightness and rotation due to physical touch event ", event);
				dimAndRotate.schedule(15000);
				var ctx = getContext();
				restoreBrightness(ctx);
				restoreAccelRotation(ctx);
			}
			return false;
		}
	}
}