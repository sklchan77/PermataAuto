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

import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.utils.concurrent.HandlerExecutor;
import my.app.utils.function.Cancellable;
import my.app.utils.log.Log;

public class MirrorServiceFS extends CarAppService {
	private MirrorDisplay md;
	static SurfaceContainer sc;

	@Override
	public void onCreate() {
		super.onCreate();
		Log.i("[MIRROR_FS][LIFECYCLE] MirrorServiceFS.onCreate | type=SPLIT_SCREEN_CAR_APP_SERVICE");
		md = MirrorDisplay.get();
	}

	@Override
	public void onDestroy() {
		Log.i("[MIRROR_FS][LIFECYCLE] MirrorServiceFS.onDestroy");
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
		Log.i("[MIRROR_FS][LIFECYCLE] Split-Screen Mirror Session Created | displayType=" + sessionInfo.getDisplayType());
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
		private static final long TAP_THEN_DRAG_WINDOW_MS = 300L; // Standard human double-tap speed
		private static final long SCALE_DRAG_WINDOW_MS = 250L;    // Anchor window when AA fires onScale before onScroll
		private static final long SCROLL_LIFT_DELAY_MS = 400L;

		private final HandlerExecutor handler = PermataApplication.get().getHandler();
		private float scrollStartX;
		private float scrollStartY;
		private float scrollX;
		private float scrollY;
		private long scrollDownTime;
		private long scrollMoveTime;
		private Cancellable scrollUp;

		// [TAP-THEN-DRAG] 300ms hold window state (onClick -> onScroll)
		private Cancellable pendingTap;
		private float pendingTapX;
		private float pendingTapY;

		// [SCALE-TO-DRAG] 250ms hold window state (onScale -> onScroll when AA intercepts double-tap-drag)
		private Cancellable pendingScale;
		private float pendingScaleX;
		private float pendingScaleY;
		private boolean pendingScaleZoomIn;

		private boolean isAnchoredDrag = false;

		MirrorScreen(@NonNull CarContext ctx) {
			super(ctx);
			ctx.getCarService(AppManager.class).setSurfaceCallback(this);
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
			invalidate();
		}

		@Override
		public void onSurfaceDestroyed(@NonNull SurfaceContainer localSc) {
			Log.i("[MIRROR_FS][SURFACE] onSurfaceDestroyed | size=" + localSc.getWidth() + "x" + localSc.getHeight());
			cancelPendingTap();
			cancelPendingScale();
			if (scrollUp != null) {
				scrollUp.cancel();
				scrollUp = null;
			}
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
				AccessibilityEventDispatcherService.abortActiveGesture();
			}

			// Normal human double-tap (down-up-down-up within 300ms): flush both taps transparently to OS
			if (pendingTap != null) {
				float firstX = pendingTapX;
				float firstY = pendingTapY;
				cancelPendingTap();
				Log.i("[MIRROR_FS][INPUT] Fast Double-Tap Flush | first=(" + (int) firstX + "," + (int) firstY
						+ "), second=(" + (int) x + "," + (int) y + ")");
				var mDisp = md();
				if (mDisp != null) {
					mDisp.tap(firstX, firstY);
					mDisp.tap(x, y);
				}
				return;
			}

			pendingTapX = x;
			pendingTapY = y;
			pendingTap = handler.schedule(() -> {
				if (pendingTap == null) return;
				pendingTap = null;
				Log.i("[MIRROR_FS][INPUT] Split-Screen Tap | x=" + (int) pendingTapX + ", y=" + (int) pendingTapY);
				var mDisp = md();
				if (mDisp != null) mDisp.tap(pendingTapX, pendingTapY);
			}, TAP_THEN_DRAG_WINDOW_MS);
		}

		@Override
		public void onScroll(float distanceX, float distanceY) {
			var mDisp = md();
			if (mDisp == null || MirrorServiceFS.sc == null) return;

			var time = uptimeMillis();
			scrollMoveTime = time;

			if (scrollUp == null) {
				String anchorSource = "CENTER";
				if (pendingTap != null) {
					cancelPendingTap();
					scrollStartX = pendingTapX;
					scrollStartY = pendingTapY;
					isAnchoredDrag = true;
					anchorSource = "TAP";
				} else if (pendingScale != null) {
					cancelPendingScale();
					scrollStartX = pendingScaleX;
					scrollStartY = pendingScaleY;
					isAnchoredDrag = true;
					anchorSource = "SCALE_FOCUS";
				} else {
					scrollStartX = MirrorServiceFS.sc.getWidth() / 2f;
					scrollStartY = MirrorServiceFS.sc.getHeight() / 2f;
					isAnchoredDrag = false;
				}

				scrollX = scrollStartX;
				scrollY = scrollStartY;
				Log.i("[MIRROR_FS][INPUT] Split-Screen Drag Started | anchored=" + isAnchoredDrag
						+ ", source=" + anchorSource
						+ ", startX=" + (int) scrollX + ", startY=" + (int) scrollY);

				if (!mDisp.motionEvent(time, time, ACTION_DOWN, scrollX, scrollY)) {
					return;
				}
				scrollDownTime = time;
				scheduleScrollUp(SCROLL_LIFT_DELAY_MS);
			}

			scrollX -= distanceX;
			scrollY -= distanceY;
			mDisp.motionEvent(scrollDownTime, time, ACTION_MOVE, scrollX, scrollY);

			if (scrollUp != null) {
				scrollUp.cancel();
				scheduleScrollUp(SCROLL_LIFT_DELAY_MS);
			}
		}

		private void scheduleScrollUp(long delay) {
			scrollUp = handler.schedule(() -> {
				if (scrollUp == null) return;
				var mDisp = md();
				if (mDisp == null) return;

				var time = uptimeMillis();
				var end = scrollMoveTime + delay;
				if (end > time) {
					scheduleScrollUp(end - time);
				} else {
					scrollUp = null;
					mDisp.motionEvent(scrollDownTime, time, ACTION_UP, scrollX, scrollY);
					Log.i("[MIRROR_FS][INPUT] Split-Screen Drag Ended | anchored=" + isAnchoredDrag
							+ ", endX=" + (int) scrollX + ", endY=" + (int) scrollY);
					isAnchoredDrag = false;
				}
			}, delay);
		}

		@Override
		public void onScale(float focusX, float focusY, float scaleFactor) {
			cancelPendingTap();
			cancelPendingScale();
			if (scrollUp != null) return;

			pendingScaleX = focusX;
			pendingScaleY = focusY;
			pendingScaleZoomIn = scaleFactor > 1f;
			pendingScale = handler.schedule(() -> {
				if (pendingScale == null) return;
				pendingScale = null;
				Log.i("[MIRROR_FS][INPUT] Split-Screen Scale | focusX=" + (int) pendingScaleX
						+ ", focusY=" + (int) pendingScaleY + ", zoomIn=" + pendingScaleZoomIn);
				scrollStartX = pendingScaleX;
				scrollStartY = pendingScaleY;
				var mDisp = md();
				if (mDisp != null) mDisp.scale(pendingScaleX, pendingScaleY, pendingScaleZoomIn);
			}, SCALE_DRAG_WINDOW_MS);
		}

		private void cancelPendingTap() {
			if (pendingTap != null) {
				pendingTap.cancel();
				pendingTap = null;
			}
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

			// [FIT/FILL] Standalone FS state: shows fullscreen (Fill) in Fit mode, fullscreen_exit (Fit) in Fill mode
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
					// 1. Interactive Floating Tools (Fit/Fill toggle + Pan)
					.setMapActionStrip(new ActionStrip.Builder()
							.addAction(fitFillButton)
							.addAction(Action.PAN)
							.build())
					// 2. Primary Navigation Bar (Original 4 buttons remain untouched)
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