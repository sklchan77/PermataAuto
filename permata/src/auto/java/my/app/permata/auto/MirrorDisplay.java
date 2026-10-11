package my.app.permata.auto;

import static android.content.Context.POWER_SERVICE;
import static android.content.Context.WINDOW_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP;
import static android.os.SystemClock.uptimeMillis;
import static android.provider.Settings.System.ACCELEROMETER_ROTATION;
import static android.provider.Settings.System.USER_ROTATION;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
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
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Build.VERSION_CODES;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.car.app.SurfaceContainer;

import java.io.File;
import java.lang.ref.WeakReference;

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
			FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE | FLAG_WATCH_OUTSIDE_TOUCH | 
			FLAG_KEEP_SCREEN_ON | FLAG_DISMISS_KEYGUARD | FLAG_TURN_SCREEN_ON | FLAG_SHOW_WHEN_LOCKED;
	private static WeakReference<MirrorDisplay> ref;
	private final int[] loc = new int[2];
	private final Display defaultDisplay;
	private final float scaleDiff;
	private WakeLock wakeLock;
	private static int accel = -1;
	private int refCounter;
	private FutureSupplier<Session> session = Completed.cancelled();
	private SurfaceContainer sc;
	private Overlay overlay;
	private Metrics lMetrics;
	private Metrics pMetrics;
	private float dx;
	private float dy;
	private long lastInjectedTouchTime;
	private Runnable sessionStopListener;

	private MirrorDisplay() {
		var ctx = PermataApplication.get();
		var vm = (WindowManager) ctx.getSystemService(WINDOW_SERVICE);
		defaultDisplay = vm.getDefaultDisplay();
		var size = new Point();
		defaultDisplay.getRealSize(size);
		scaleDiff = Math.max(UiUtils.toPx(ctx, 20), Math.min(size.x, size.y) / 20f);
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
		md.refCounter = 0; 
		var sc = md.sc;
		md.cleanUp();
		if (sc != null) drawMsg(sc, R.string.app_name);
	}

	public void release() {
		if (--refCounter <= 0) {
			refCounter = 0;
			cleanUp();
		}
	}

	public void setSessionStopListener(Runnable listener) {
		this.sessionStopListener = listener;
	}

	public void setSurface(@NonNull SurfaceContainer sc) {
		var oldSc = this.sc;
		if (oldSc == sc) return;
		this.sc = sc;
		lMetrics = pMetrics = null;
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
		lMetrics = pMetrics = null;
		if (session.isDoneNotFailed()) {
			new Thread(() -> {
				try {
					Session s = session.getOrThrow();
					if (s != null && s.vd != null) {
						s.vd.setSurface(null);
					}
				} catch (Exception e) {
					Log.e(e, "Safe VirtualDisplay detachment failed.");
				}
			}, "Permata-Surface-Detach").start();
		}
		drawMsg(oldSc, R.string.app_name);
	}

	public synchronized void tap(float x, float y) {
		var d = translate(x, y);
		if (d != null) d.tap(dx, dy);
	}

	public synchronized void scale(float x, float y, boolean zoomIn) {
		var d = translate(x, y);
		if (d != null) d.scale(dx, dy, zoomIn ? scaleDiff : -scaleDiff);
	}

	private long downTime;

	public synchronized boolean motionEvent(MotionEvent e) {
		lastInjectedTouchTime = uptimeMillis();
		EventDispatcher d;
		var action = e.getAction();
		if (action == MotionEvent.ACTION_DOWN) downTime = uptimeMillis();
		var cnt = e.getPointerCount();
		if (cnt == 1) {
			d = translate(e.getX(), e.getY());
			if (d == null) return false;
			e = MotionEvent.obtain(downTime, uptimeMillis(), e.getAction(), dx, dy, 0);
			e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
		} else {
			d = dispatcher();
			var a = d.getActivity();
			var m = metrics(a);
			if (m == null) return false;
			if (a != null) a.getWindow().getDecorView().getLocationOnScreen(loc);
			var props = new MotionEvent.PointerProperties[cnt];
			var coords = new MotionEvent.PointerCoords[cnt];
			for (int i = 0; i < cnt; i++) {
				props[i] = new MotionEvent.PointerProperties();
				e.getPointerProperties(i, props[i]);
				var c = coords[i] = new MotionEvent.PointerCoords();
				e.getPointerCoords(i, c);
				if (a != null) {
					c.x = (c.x - m.x) * m.scale - loc[0];
					c.y = (c.y - m.y) * m.scale - loc[1];
				} else {
					c.x = (c.x - m.x) * m.scale;
					c.y = (c.y - m.y) * m.scale;
				}
			}
			e = MotionEvent.obtain(downTime, uptimeMillis(), action, cnt, props, coords,
					e.getMetaState(),
					e.getButtonState(), e.getXPrecision(), e.getYPrecision(), e.getDeviceId(),
					e.getEdgeFlags(), InputDevice.SOURCE_TOUCHSCREEN, e.getFlags());
		}
		return d.motionEvent(e);
	}

	public synchronized boolean motionEvent(long downTime, long eventTime, int action, float x, float y) {
		lastInjectedTouchTime = uptimeMillis();
		var d = translate(x, y);
		return (d != null) && d.motionEvent(downTime, eventTime, action, dx, dy);
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
					land ? SCREEN_ORIENTATION_LANDSCAPE : SCREEN_ORIENTATION_PORTRAIT);
		}

		try {
			var cr = ctx.getContentResolver();
			if (accel == -1) {
				var v = Settings.System.getInt(cr, ACCELEROMETER_ROTATION, -1);
				if (v != -1) accel = v;
			}
			Settings.System.putInt(cr, ACCELEROMETER_ROTATION, 0);
			Settings.System.putInt(cr, USER_ROTATION, land ? ROTATION_90 : ROTATION_0);
		} catch (Exception err) {
			Log.e(err);
		}
	}

	private static void restoreAccelRotation(Context ctx) {
		var a = EventDispatcher.get().getActivity();
		if (a != null) a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		if (accel == -1) return;
		try {
			Settings.System.putInt(ctx.getContentResolver(), ACCELEROMETER_ROTATION, accel);
		} catch (Exception err) {
			Log.e(err);
		}
		accel = -1;
	}

	private void setOverlayBrightness(float brightness) {
		if (overlay != null) {
			try {
				var app = PermataApplication.get();
				var wm = (WindowManager) app.getSystemService(WINDOW_SERVICE);
				var lp = (WindowManager.LayoutParams) overlay.getLayoutParams();
				lp.screenBrightness = brightness;
				wm.updateViewLayout(overlay, lp);
			} catch (Exception err) {
				Log.e(err, "Failed to update overlay brightness");
			}
		}
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

		disableAccelRotation(app);

		var pmg = (PowerManager) app.getSystemService(POWER_SERVICE);
		if (pmg != null) {
			//noinspection deprecation
			wakeLock = pmg.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP,
					"Permata:ScreenLock");
			if (wakeLock != null) wakeLock.acquire(24 * 3600000);
		}

		if ((overlay == null) && (SDK_INT >= VERSION_CODES.O)) {
			try {
				var wm = (WindowManager) app.getSystemService(WINDOW_SERVICE);
				var lp = new WindowManager.LayoutParams(1, 1, TYPE_APPLICATION_OVERLAY,
								OVERLAY_FLAGS, PixelFormat.TRANSPARENT);
				lp.gravity = Gravity.TOP | Gravity.START;
				
				if (!Build.MANUFACTURER.equalsIgnoreCase("Xiaomi")) {
					lp.screenBrightness = 0.01f;
				}
				var v = new Overlay(app);
				wm.addView(v, lp);
				this.overlay = v;
				
				v.dimAndRotate.schedule(10000);
			} catch (Exception err) {
				Log.e(err, "Failed to add overlay");
			}
		}

		try {
			app.startService(new Intent(app, XposedEventDispatcherService.class));
		} catch (Exception err) {
			Log.e(err, "Failed to start XposedEventDispatcherService");
		}
		
		setMirroringMode(app, mode);
		applyRootResolutionHack(true);
	}

	private void cleanUp() {
		noSession();
		applyRootResolutionHack(false);
		
		sc = null;
		lMetrics = pMetrics = null;
		var app = PermataApplication.get();
		if (overlay != null) {
			overlay.dimAndRotate.cancel();
			try {
				var wm = (WindowManager) app.getSystemService(WINDOW_SERVICE);
				wm.removeView(overlay);
			} catch (Exception ignored) {}
			overlay = null;
		}
		if (wakeLock != null) {
			try {
				wakeLock.release();
			} catch (Exception ignored) {}
			wakeLock = null;
		}
		setMirroringMode(app, 0);
		restoreAccelRotation(app);
		ProjectionService.stop();

		try {
			app.stopService(new Intent(app, XposedEventDispatcherService.class));
		} catch (Exception err) {
			Log.d(err, "Failed to stop XposedEventDispatcherService");
		}
		
		if (sessionStopListener != null) {
			new android.os.Handler(android.os.Looper.getMainLooper()).post(sessionStopListener);
		}
	}

	private boolean canExecuteSu() {
		try {
			Process p = new ProcessBuilder("which", "su").start();
			int exitCode = p.waitFor();
			p.destroy();
			return exitCode == 0;
		} catch (Exception e) {
			return false;
		}
	}

	private void applyRootResolutionHack(boolean active) {
		final int surfaceWidth = sc != null ? sc.getWidth() : 0;
		final int surfaceHeight = sc != null ? sc.getHeight() : 0;

		new Thread(() -> {
			Process p = null;
			try {
				File su1 = new File("/system/xbin/su");
				File su2 = new File("/system/bin/su");
				if (!su1.exists() && !su2.exists() && !canExecuteSu()) {
					Log.w("Root Hack skipped: Device is not rooted or 'su' is missing.");
					return;
				}

				if (active && surfaceWidth > 0 && surfaceHeight > 0) {
					Point size = new Point();
					defaultDisplay.getRealSize(size);
					
					int portW = Math.min(size.x, size.y);
					int carW = Math.max(surfaceWidth, surfaceHeight);
					int carH = Math.min(surfaceWidth, surfaceHeight);
					
					if (carH > 0) {
						int targetH = (int) ((float) portW * carW / carH);
						p = new ProcessBuilder("su", "-c", "wm size " + portW + "x" + targetH).start();
						p.waitFor();
						Log.i("Root Hack: Display forcefully resized to " + portW + "x" + targetH);
					}
				} else {
					p = new ProcessBuilder("su", "-c", "wm size reset").start();
					p.waitFor();
					Log.i("Root Hack: Display forcefully reset to native physical resolution.");
				}
			} catch (Exception e) {
				Log.e(e, "Root resolution hack failed.");
			} finally {
				if (p != null) p.destroy();
				PermataApplication.get().getHandler().post(() -> {
					lMetrics = pMetrics = null;
				});
			}
		}, "Permata-Resolution-Thread").start();
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
		if (sc == null) {
			noSession();
			return;
		}
		if (p.isDone()) return;
		ProjectionService.start().onCompletion((mp, err) -> {
			if (session != p) return;
			if (sc == null) {
				noSession();
				return;
			}
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
		if (sc == null) return;
		var surface = sc.getSurface();
		if (surface == null || !surface.isValid()) return;
		Canvas c = null;
		try {
			c = surface.lockCanvas(null);
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
		} catch (Exception err) {
			Log.d(err, "Failed to draw message on surface");
		} finally {
			if (c != null && surface.isValid()) {
				try {
					surface.unlockCanvasAndPost(c);
				} catch (Exception ignored) {}
			}
		}
	}

	private EventDispatcher dispatcher() {
		return EventDispatcher.get();
	}

	@Nullable
	private EventDispatcher translate(float x, float y) {
		var d = dispatcher();
		var a = d.getActivity();
		var m = metrics(a);
		if (m == null) return null;

		int rotation = defaultDisplay.getRotation();
		float targetX = x;
		float targetY = y;

		if (rotation == ROTATION_180) { 
			targetX = sc.getWidth() - x;
			targetY = sc.getHeight() - y;
		} else if (rotation == ROTATION_270) { 
			targetX = sc.getWidth() - x;
			targetY = sc.getHeight() - y;
		}

		if (a != null) {
			a.getWindow().getDecorView().getLocationOnScreen(loc);
			dx = (targetX - m.x) * m.scale - loc[0];
			dy = (targetY - m.y) * m.scale - loc[1];
		} else {
			dx = (targetX - m.x) * m.scale;
			dy = (targetY - m.y) * m.scale;
		}
		return d;
	}

	@Nullable
	@SuppressLint("SwitchIntDef")
	private Metrics metrics(@Nullable AppCompatActivity a) {
		if (sc == null) return null;
		
		boolean isCarLandscape = sc.getWidth() > sc.getHeight();
		
		var m = isCarLandscape ? lMetrics : pMetrics;
		if (m == null) {
			if (!session.isDoneNotFailed()) return null;
			final Point size = new Point();
			defaultDisplay.getRealSize(size);
			
			float phoneW = size.x;
			float phoneH = size.y;
			
			if (isCarLandscape && phoneW < phoneH) {
				phoneW = size.y;
				phoneH = size.x;
			} else if (!isCarLandscape && phoneW > phoneH) {
				phoneW = size.y;
				phoneH = size.x;
			}

			m = new Metrics(phoneW, phoneH, sc.getWidth(), sc.getHeight());
			if (isCarLandscape) lMetrics = m;
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

	private final class Overlay extends FrameLayout {
		final ReschedulableTask dimAndRotate = new ReschedulableTask() {
			@Override
			protected void perform() {
				setOverlayBrightness(0.01f);
			}
		};

		public Overlay(@NonNull Context context) {
			super(context);
		}

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
				if (Math.abs(uptimeMillis() - lastInjectedTouchTime) > 200) {
					setOverlayBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE);
					dimAndRotate.schedule(10000);
				}
			}
			return false;
		}
	}

	private static final class Session extends MediaProjection.Callback {
		final MediaProjection mp;
		final VirtualDisplay vd;
		final WeakReference<MirrorDisplay> mdRef;

		Session(MediaProjection mp, MirrorDisplay md) {
			this.mp = mp;
			this.mdRef = new WeakReference<>(md);
			var sc = md.sc;
			if (sc == null) throw new IllegalArgumentException("SurfaceContainer is null");
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

		Metrics(float dw, float dh, float sw, float sh) {
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
}