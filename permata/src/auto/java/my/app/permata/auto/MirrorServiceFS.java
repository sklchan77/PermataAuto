package my.app.permata.auto;

import static android.os.SystemClock.uptimeMillis;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static androidx.core.graphics.drawable.IconCompat.createWithResource;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarAppService;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.SessionInfo;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.validation.HostValidator;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import java.util.Locale;

import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.permata.media.service.PermataMediaService;
import my.app.utils.concurrent.HandlerExecutor;
import my.app.utils.function.Cancellable;
import my.app.utils.log.Log;

public class MirrorServiceFS extends CarAppService {
	private static final long RESUME_REBIND_DELAY_MS = 150L;

	private MirrorDisplay md;
	static SurfaceContainer sc;
	static volatile boolean isScreenResumed = false;

	@Override
	public void onCreate() {
		super.onCreate();
		Log.i("[MIRROR_FS][LIFECYCLE] MirrorServiceFS.onCreate | type=FULLSCREEN_CAR_APP_SERVICE");
		md = MirrorDisplay.get();
	}

	@Override
	public void onDestroy() {
		Log.i("[MIRROR_FS][LIFECYCLE] MirrorServiceFS.onDestroy");
		isScreenResumed = false;
		AccessibilityEventDispatcherService.abortActiveGesture();
		if (md != null) {
			md.release();
			md = null;
		}
		sc = null;
		super.onDestroy();
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
		boolean mirroring = PermataApplication.get().isMirroringMode();
		boolean autoConnected = PermataApplication.get().isConnectedToAuto();
		Log.i("[MIRROR_FS][LIFECYCLE] MirrorServiceFS.onTaskRemoved | mirroring=" + mirroring
				+ ", autoConnected=" + autoConnected);

		AccessibilityEventDispatcherService.abortActiveGesture();

		if (mirroring) {
			MirrorDisplay.close();
		} else if (autoConnected) {
			Log.i("[MIRROR_FS][DECISION] Preserving MirrorServiceFS | reason=AUTO_STILL_CONNECTED");
			return;
		}

		isScreenResumed = false;
		if (md != null) {
			md.release();
			md = null;
		}
		sc = null;
		stopSelf();
	}

	@NonNull
	@Override
	public Session onCreateSession(@NonNull SessionInfo sessionInfo) {
		Log.i("[MIRROR_FS][LIFECYCLE] Full-Screen Mirror Session Created | displayType=" + sessionInfo.getDisplayType());
		return new Session() {
			@NonNull
			@Override
			public Screen onCreateScreen(@NonNull Intent intent) {
				return new MirrorScreen(getCarContext());
			}
		};
	}

	@NonNull
	@Override
	public HostValidator createHostValidator() {
		return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
	}

	private final class MirrorScreen extends Screen implements SurfaceCallback {
		// Keep tap / drag-end coordinate armed for 2.5s for floating window handles
		private static final long RECENT_ANCHOR_TTL_MS = 2500L;
		// Keep Double-Tap / QuickScale coordinate armed for 3.0s without clicking or zooming
		private static final long SCALE_ANCHOR_TTL_MS = 3000L;
		private static final long SCALE_SETTLE_MS = 240L;
		private static final float MIN_PINCH_DELTA = 0.12f;
		private static final float MIN_TWO_FINGER_FOCUS_DRIFT_PX = 0.5f;

		private static final long SCROLL_LIFT_CENTER_MS = 340L;
		private static final long SCROLL_LIFT_ANCHORED_MS = 480L;

		private final HandlerExecutor handler = PermataApplication.get().getHandler();
		private float scrollStartX;
		private float scrollStartY;
		private float scrollX;
		private float scrollY;
		private long scrollDownTime;
		private long scrollMoveTime;
		private Cancellable scrollUp;
		private Cancellable pendingResumeRebind;

		// Persistent post-tap / Double-Tap / QuickScale / chained-drag anchor state
		private float anchorX = -1f;
		private float anchorY = -1f;
		private long anchorExpiryTime = 0L;
		private String anchorKind = "CENTER";

		// Double-Tap / QuickScale vs Two-Finger Pinch discriminator state
		private Cancellable pendingScale;
		private float scaleBurstStartX;
		private float scaleBurstStartY;
		private float scaleBurstFirstFactor = 1.0f;
		private float scaleBurstCumulative = 1.0f;
		private float scaleBurstMaxDrift = 0f;
		private int scaleBurstCount = 0;

		private boolean isAnchoredDrag = false;

		MirrorScreen(@NonNull CarContext ctx) {
			super(ctx);
			ctx.getCarService(AppManager.class).setSurfaceCallback(this);
			getLifecycle().addObserver(new DefaultLifecycleObserver() {
				@Override
				public void onResume(@NonNull LifecycleOwner owner) {
					isScreenResumed = true;
					Log.i("[MIRROR_FS][LIFECYCLE] MirrorScreen.onResume | scPresent=" + (MirrorServiceFS.sc != null));
					scheduleResumeRebind();
				}

				@Override
				public void onPause(@NonNull LifecycleOwner owner) {
					isScreenResumed = false;
					cancelPendingResumeRebind();
					Log.i("[MIRROR_FS][LIFECYCLE] MirrorScreen.onPause");
				}

				@Override
				public void onDestroy(@NonNull LifecycleOwner owner) {
					isScreenResumed = false;
					cancelPendingResumeRebind();
				}
			});
		}

		private void scheduleResumeRebind() {
			cancelPendingResumeRebind();
			pendingResumeRebind = handler.schedule(() -> {
				pendingResumeRebind = null;
				SurfaceContainer curSc = MirrorServiceFS.sc;
				if (!isScreenResumed || curSc == null) return;
				if (MainCarActivity.activeInstance != null || MirrorActivity.isForeground) return;
				Log.i("[MIRROR_FS][LIFECYCLE] MirrorScreen.onResume (" + RESUME_REBIND_DELAY_MS
						+ "ms rebind) | action=REATTACH_AND_ANCHOR");
				md();
				PermataMediaService.requestFocusAndAnchor(MirrorServiceFS.this);
			}, RESUME_REBIND_DELAY_MS);
		}

		private void cancelPendingResumeRebind() {
			if (pendingResumeRebind != null) {
				pendingResumeRebind.cancel();
				pendingResumeRebind = null;
			}
		}

		@Override
		public void onSurfaceAvailable(@NonNull SurfaceContainer localSc) {
			if (localSc.getSurface() == null) return;
			Log.i("[MIRROR_FS][SURFACE] onSurfaceAvailable | size=" + localSc.getWidth() + "x" + localSc.getHeight()
					+ ", dpi=" + localSc.getDpi());
			MirrorServiceFS.sc = localSc;

			if (md == null || md.isDestroyed()) {
				if (md != null) md.release();
				md = MirrorDisplay.get();
			}

			if (md != null) md.setSurface(localSc);

			scrollStartX = localSc.getWidth() / 2f;
			scrollStartY = localSc.getHeight() / 2f;
			clearAnchor();
			invalidate();
		}

		@Override
		public void onSurfaceDestroyed(@NonNull SurfaceContainer localSc) {
			Log.i("[MIRROR_FS][SURFACE] onSurfaceDestroyed | size=" + localSc.getWidth() + "x" + localSc.getHeight());
			cancelPendingResumeRebind();
			cancelPendingScale();
			clearAnchor();
			if (scrollUp != null) {
				scrollUp.cancel();
				scrollUp = null;
			}
			isAnchoredDrag = false;
			AccessibilityEventDispatcherService.abortActiveGesture();
			if (md != null) {
				md.releaseSurface(localSc);
			}
			MirrorServiceFS.sc = null;
		}

		@Override
		public void onClick(float x, float y) {
			cancelPendingScale();
			if (scrollUp != null) {
				scrollUp.cancel();
				scrollUp = null;
				isAnchoredDrag = false;
				AccessibilityEventDispatcherService.abortActiveGesture();
			}

			armAnchor(x, y, RECENT_ANCHOR_TTL_MS, "RECENT_TAP");
			Log.i("[MIRROR_FS][INPUT] Full-Screen Tap | x=" + (int) x + ", y=" + (int) y
					+ " (anchor armed " + RECENT_ANCHOR_TTL_MS + "ms)");
			var mDisp = md();
			if (mDisp != null) mDisp.tapFS(x, y);
		}

		@Override
		public void onScroll(float distanceX, float distanceY) {
			var mDisp = md();
			SurfaceContainer curSc = MirrorServiceFS.sc;
			if (mDisp == null || curSc == null) return;

			long time = uptimeMillis();
			scrollMoveTime = time;

			if (scrollUp == null) {
				String anchorSource;
				if (pendingScale != null) {
					cancelPendingScale();
					scrollStartX = scaleBurstStartX;
					scrollStartY = scaleBurstStartY;
					isAnchoredDrag = true;
					anchorSource = "SCALE_IMMEDIATE";
				} else if (time <= anchorExpiryTime && anchorX >= 0f && anchorY >= 0f) {
					scrollStartX = anchorX;
					scrollStartY = anchorY;
					isAnchoredDrag = true;
					anchorSource = anchorKind;
				} else {
					scrollStartX = curSc.getWidth() / 2f;
					scrollStartY = curSc.getHeight() / 2f;
					isAnchoredDrag = false;
					anchorSource = "CENTER";
				}

				clearAnchor();
				scrollX = clamp(scrollStartX, 0f, curSc.getWidth());
				scrollY = clamp(scrollStartY, 0f, curSc.getHeight());
				Log.i("[MIRROR_FS][INPUT] Full-Screen Drag Started | anchored=" + isAnchoredDrag
						+ ", source=" + anchorSource
						+ ", startX=" + (int) scrollX + ", startY=" + (int) scrollY);

				if (!mDisp.motionEventFS(time, time, ACTION_DOWN, scrollX, scrollY, isAnchoredDrag)) {
					isAnchoredDrag = false;
					return;
				}
				scrollDownTime = time;
				scheduleScrollUp(isAnchoredDrag ? SCROLL_LIFT_ANCHORED_MS : SCROLL_LIFT_CENTER_MS);
			}

			scrollX = clamp(scrollX - distanceX, 0f, curSc.getWidth());
			scrollY = clamp(scrollY - distanceY, 0f, curSc.getHeight());
			mDisp.motionEventFS(scrollDownTime, time, ACTION_MOVE, scrollX, scrollY, isAnchoredDrag);

			if (scrollUp != null) {
				scrollUp.cancel();
				scheduleScrollUp(isAnchoredDrag ? SCROLL_LIFT_ANCHORED_MS : SCROLL_LIFT_CENTER_MS);
			}
		}

		private void scheduleScrollUp(long delay) {
			scrollUp = handler.schedule(() -> {
				if (scrollUp == null) return;
				var mDisp = md();
				if (mDisp == null) {
					scrollUp = null;
					isAnchoredDrag = false;
					return;
				}

				long time = uptimeMillis();
				long end = scrollMoveTime + delay;
				if (end > time) {
					scheduleScrollUp(end - time);
				} else {
					scrollUp = null;
					boolean wasAnchored = isAnchoredDrag;
					isAnchoredDrag = false;
					mDisp.motionEventFS(scrollDownTime, time, ACTION_UP, scrollX, scrollY, wasAnchored);
					if (wasAnchored) {
						armAnchor(scrollX, scrollY, RECENT_ANCHOR_TTL_MS, "CHAINED_DRAG");
					}
					Log.i("[MIRROR_FS][INPUT] Full-Screen Drag Ended | anchored=" + wasAnchored
							+ ", endX=" + (int) scrollX + ", endY=" + (int) scrollY);
				}
			}, delay);
		}

		@Override
		public void onScale(float focusX, float focusY, float scaleFactor) {
			if (scrollUp != null) return;

			if (pendingScale == null) {
				scaleBurstStartX = focusX;
				scaleBurstStartY = focusY;
				scaleBurstFirstFactor = scaleFactor;
				scaleBurstCumulative = scaleFactor;
				scaleBurstMaxDrift = 0f;
				scaleBurstCount = 1;
			} else {
				pendingScale.cancel();
				scaleBurstCumulative *= scaleFactor;
				float drift = (float) Math.hypot(focusX - scaleBurstStartX, focusY - scaleBurstStartY);
				if (drift > scaleBurstMaxDrift) scaleBurstMaxDrift = drift;
				scaleBurstCount++;
			}

			// Pre-arm silent anchor immediately so an instant swipe grabs the bubble without opening it
			armAnchor(scaleBurstStartX, scaleBurstStartY, SCALE_ANCHOR_TTL_MS, "SCALE_ANCHOR");

			pendingScale = handler.schedule(() -> {
				if (pendingScale == null) return;
				pendingScale = null;
				float delta = Math.abs(scaleBurstCumulative - 1.0f);
				boolean isHostDoubleTap = (scaleBurstCount == 1) || (Math.abs(scaleBurstFirstFactor - 2.0f) < 0.05f);
				boolean isTwoFingerPinch = !isHostDoubleTap
						&& scaleBurstCount >= 3
						&& scaleBurstMaxDrift >= MIN_TWO_FINGER_FOCUS_DRIFT_PX
						&& delta >= MIN_PINCH_DELTA;

				if (isTwoFingerPinch) {
					clearAnchor();
					boolean zoomIn = scaleBurstCumulative > 1.0f;
					Log.i(String.format(Locale.US,
							"[MIRROR_FS][INPUT] Full-Screen Pinch Scale | focusX=%d, focusY=%d, factor=%.2f, zoomIn=%b",
							(int) scaleBurstStartX, (int) scaleBurstStartY, scaleBurstCumulative, zoomIn));
					var mDisp = md();
					if (mDisp != null) mDisp.scaleFS(scaleBurstStartX, scaleBurstStartY, zoomIn);
				} else {
					armAnchor(scaleBurstStartX, scaleBurstStartY, SCALE_ANCHOR_TTL_MS, "SCALE_ANCHOR");
					Log.i(String.format(Locale.US,
							"[MIRROR_FS][INPUT] Silent Drag-Anchor Armed | x=%d, y=%d, count=%d, firstFactor=%.2f, drift=%.2f",
							(int) scaleBurstStartX, (int) scaleBurstStartY, scaleBurstCount, scaleBurstFirstFactor, scaleBurstMaxDrift));
				}
			}, SCALE_SETTLE_MS);
		}

		private void armAnchor(float x, float y, long ttlMs, @NonNull String kind) {
			anchorX = x;
			anchorY = y;
			anchorExpiryTime = uptimeMillis() + ttlMs;
			anchorKind = kind;
		}

		private void clearAnchor() {
			anchorX = -1f;
			anchorY = -1f;
			anchorExpiryTime = 0L;
			anchorKind = "CENTER";
		}

		private float clamp(float val, float min, float max) {
			return Math.max(min, Math.min(max, val));
		}

		private void cancelPendingScale() {
			if (pendingScale != null) {
				pendingScale.cancel();
				pendingScale = null;
			}
		}

		@NonNull
		@Override
		public Template onGetTemplate() {
			var homeButton = new Action.Builder().setIcon(
							new CarIcon.Builder(createWithResource(getCarContext(), R.drawable.home)).build())
					.setOnClickListener(MirrorActivity::onHomeButtonClick).build();

			var recentButton = new Action.Builder().setIcon(
							new CarIcon.Builder(createWithResource(getCarContext(), R.drawable.recent)).setTint(CarColor.DEFAULT).build())
					.setOnClickListener(MirrorActivity::onRecentButtonClick).build();

			var backButton = new Action.Builder().setIcon(
							new CarIcon.Builder(createWithResource(getCarContext(), R.drawable.back)).build())
					.setOnClickListener(MirrorActivity::onBackButtonClick).build();

			var exitButton = new Action.Builder().setIcon(
							new CarIcon.Builder(createWithResource(getCarContext(), R.drawable.shutdown)).setTint(CarColor.DEFAULT).build())
					.setOnClickListener(MirrorActivity::onExitButtonClick).build();

			int fitFillIconRes = MirrorDisplay.isFillMode(true) ? R.drawable.fullscreen_exit : R.drawable.fullscreen;
			var fitFillButton = new Action.Builder().setIcon(
							new CarIcon.Builder(createWithResource(getCarContext(), fitFillIconRes))
									.setTint(CarColor.DEFAULT).build())
					.setOnClickListener(() -> {
						boolean nowFill = MirrorDisplay.toggleFillMode(true);
						Log.i("[MIRROR_FS][INPUT] Toolbar Button Pressed | button=FIT_FILL, fillMode=" + nowFill);
						invalidate();
					}).build();

			return new NavigationTemplate.Builder()
					.setPanModeListener(isPanningMode ->
							Log.i("[MIRROR_FS][INPUT] Pan Mode Toggled | isPanningMode=" + isPanningMode))
					.setMapActionStrip(new ActionStrip.Builder()
							.addAction(fitFillButton)
							.addAction(Action.PAN)
							.build())
					.setActionStrip(new ActionStrip.Builder()
							.addAction(homeButton)
							.addAction(recentButton)
							.addAction(backButton)
							.addAction(exitButton)
							.build())
					.build();
		}

		private MirrorDisplay md() {
			if (md == null || md.isDestroyed()) md = MirrorDisplay.get();
			if (md != null && sc != null) md.setSurface(sc);
			return md;
		}
	}
}