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
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.SessionInfo;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
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
		private final HandlerExecutor handler = PermataApplication.get().getHandler();
		private float scrollStartX;
		private float scrollStartY;
		private float scrollX;
		private float scrollY;
		private long scrollDownTime;
		private long scrollMoveTime;
		private Cancellable scrollUp;

		// [NEW] The Grab Toggle
		private boolean isGrabMode = false;

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
		}

		@Override
		public void onSurfaceDestroyed(@NonNull SurfaceContainer localSc) {
			Log.i("[MIRROR_FS][SURFACE] onSurfaceDestroyed | size=" + localSc.getWidth() + "x" + localSc.getHeight());
			if (scrollUp != null) {
				scrollUp.cancel();
				scrollUp = null;
			}
			if (md != null) {
				md.releaseSurface(localSc);
			}
			MirrorServiceFS.sc = null;
		}

		@Override
		public void onClick(float x, float y) {
			// [NEW] If Grab Mode is active, swallow the click to prevent opening the bubble, 
			// and save the coordinates for the upcoming drag.
			if (isGrabMode) {
				scrollStartX = x;
				scrollStartY = y;
				Log.i("[MIRROR_FS][INPUT] Grab Target Locked | x=" + (int) x + ", y=" + (int) y);
				CarToast.makeText(getCarContext(), "Target Locked. Drag anywhere.", CarToast.LENGTH_SHORT).show();
				return;
			}

			Log.i("[MIRROR_FS][INPUT] Split-Screen Tap | x=" + (int) x + ", y=" + (int) y);
			if (scrollUp != null) {
				scrollUp.cancel();
				scrollUp = null;
			}
			if (md() != null) md().tap(x, y);
		}

		@Override
		public void onScroll(float distanceX, float distanceY) {
			var mDisp = md();
			if (mDisp == null || MirrorServiceFS.sc == null) return;
			
			var time = uptimeMillis();
			scrollMoveTime = time;
			
			if (scrollUp == null) {
				// [NEW] If not in grab mode, default to the center of the screen
				if (!isGrabMode) {
					scrollStartX = MirrorServiceFS.sc.getWidth() / 2f;
					scrollStartY = MirrorServiceFS.sc.getHeight() / 2f;
				}

				scrollX = scrollStartX;
				scrollY = scrollStartY;
				Log.i("[MIRROR_FS][INPUT] Split-Screen Drag Started | grabMode=" + isGrabMode
						+ ", startX=" + (int) scrollX + ", startY=" + (int) scrollY);
				
				if (!mDisp.motionEvent(time, time, ACTION_DOWN, scrollX, scrollY)) {
					return;
				}
				scrollDownTime = time;
				scheduleScrollUp(500);
			}
			
			scrollX -= distanceX;
			scrollY -= distanceY;
			mDisp.motionEvent(scrollDownTime, time, ACTION_MOVE, scrollX, scrollY);
			
			if (scrollUp != null) {
				scrollUp.cancel();
				scheduleScrollUp(500);
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
					Log.i("[MIRROR_FS][INPUT] Split-Screen Drag Ended | grabMode=" + isGrabMode
							+ ", endX=" + (int) scrollX + ", endY=" + (int) scrollY);
					// [NEW] Reset Grab Mode after the drag is completely finished
					isGrabMode = false;
				}
			}, delay);
		}

		@Override
		public void onScale(float focusX, float focusY, float scaleFactor) {
			Log.i("[MIRROR_FS][INPUT] Split-Screen Scale | focusX=" + (int) focusX + ", focusY=" + (int) focusY
					+ ", zoomIn=" + (scaleFactor > 1f));
			scrollStartX = focusX;
			scrollStartY = focusY;
			if (md() != null) md().scale(focusX, focusY, scaleFactor > 1f);
		}

		@NonNull
		@Override
		public Template onGetTemplate() {
			var homeButton = new Action.Builder().setIcon(
							new CarIcon.Builder(createWithResource(getCarContext(), R.drawable.home)).build())
					.setOnClickListener(MirrorActivity::onHomeButtonClick).build();
					
			var recentButton = new Action.Builder().setIcon(
							new CarIcon.Builder(createWithResource(getCarContext(), R.drawable.recent)).setTint(androidx.car.app.model.CarColor.DEFAULT).build())
					.setOnClickListener(MirrorActivity::onRecentButtonClick).build();
					
			var backButton = new Action.Builder().setIcon(
							new CarIcon.Builder(createWithResource(getCarContext(), R.drawable.back)).build())
					.setOnClickListener(MirrorActivity::onBackButtonClick).build();
					
			var exitButton = new Action.Builder().setIcon(
							new CarIcon.Builder(createWithResource(getCarContext(), R.drawable.shutdown)).setTint(androidx.car.app.model.CarColor.DEFAULT).build())
					.setOnClickListener(MirrorActivity::onExitButtonClick).build();

			// [NEW] The Grab Tool Button
			var grabButton = new Action.Builder().setIcon(
							new CarIcon.Builder(createWithResource(getCarContext(), R.drawable.cursor))
							.setTint(androidx.car.app.model.CarColor.DEFAULT).build())
					.setOnClickListener(() -> {
						isGrabMode = true;
						Log.i("[MIRROR_FS][INPUT] Toolbar Button Pressed | button=GRAB, grabMode=ON");
						CarToast.makeText(getCarContext(), "Grab Mode ON: Tap bubble to lock.", CarToast.LENGTH_SHORT).show();
					}).build();
			
			return new NavigationTemplate.Builder()
					.setPanModeListener(isPanningMode ->
							Log.i("[MIRROR_FS][INPUT] Pan Mode Toggled | isPanningMode=" + isPanningMode))
					// 1. Interactive Floating Tools (Renders on the right edge)
					.setMapActionStrip(new ActionStrip.Builder()
							.addAction(grabButton)
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