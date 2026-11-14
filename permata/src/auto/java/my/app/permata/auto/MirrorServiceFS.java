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
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.navigation.model.PanModeListener;
import androidx.car.app.validation.HostValidator;

import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.permata.action.KeyEventHandler;
import my.app.utils.concurrent.HandlerExecutor;
import my.app.utils.function.Cancellable;
import my.app.utils.log.Log;

public class MirrorServiceFS extends CarAppService {
	private static final float SCROLL_SENSITIVITY = 2.0f;
	
	private MirrorDisplay md;
	static SurfaceContainer sc;

	@Override
	public void onCreate() {
		super.onCreate();
		md = MirrorDisplay.get();
	}

	@Override
	public void onDestroy() {
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

		if (PermataApplication.get().isMirroringMode()) {
			MirrorDisplay.close();
		} else if (PermataApplication.get().isConnectedToAuto()) {
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
		
		private boolean isPanning = false;
		
		private float lastClickX = -1f;
		private float lastClickY = -1f;
		private long lastClickTime = 0L;

		MirrorScreen(@NonNull CarContext ctx) {
			super(ctx);
			ctx.getCarService(AppManager.class).setSurfaceCallback(this);
		}

		@Override
		public void onSurfaceAvailable(@NonNull SurfaceContainer localSc) {
			if (localSc.getSurface() == null) return;
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
			if (scrollUp != null) {
				scrollUp.cancel();
				scrollUp = null;
				isPanning = false;
			}
			
			if (md != null) {
				md.releaseSurface(localSc);
			}
			MirrorServiceFS.sc = null;
		}

		@Override
		public void onClick(float x, float y) {
			// FIX #4: Lock steering wheel routing to Mirroring aggressively on touch
			KeyEventHandler.manualTouchFocus = KeyEventHandler.TARGET_MIRROR;

			if (scrollUp != null) {
				scrollUp.cancel();
				scrollUp = null;
				isPanning = false;
			}
			
			lastClickX = x;
			lastClickY = y;
			lastClickTime = uptimeMillis();
			
			if (md() != null) md().tap(x, y);
		}

		@Override
		public void onScroll(float distanceX, float distanceY) {
			// FIX #4
			KeyEventHandler.manualTouchFocus = KeyEventHandler.TARGET_MIRROR;

			var mDisp = md();
			if (mDisp == null || MirrorServiceFS.sc == null) return;
			
			distanceX *= SCROLL_SENSITIVITY;
			distanceY *= SCROLL_SENSITIVITY;
			
			var time = uptimeMillis();
			scrollMoveTime = time;
			
			if (!isPanning || scrollUp == null) {
				isPanning = true;
				
				if (time - lastClickTime < 1500 && lastClickX >= 0 && lastClickY >= 0) {
					scrollStartX = lastClickX;
					scrollStartY = lastClickY;
				} else {
					scrollStartX = MirrorServiceFS.sc.getWidth() / 2f;
					scrollStartY = MirrorServiceFS.sc.getHeight() / 2f;
				}
				
				scrollX = scrollStartX;
				scrollY = scrollStartY;
				
				if (!mDisp.motionEvent(time, time, ACTION_DOWN, scrollX, scrollY)) {
					isPanning = false;
					return;
				}
				scrollDownTime = time;
				
				// FIX #2: Reduced timeout from 500 to 200 for faster drop tracking
				scheduleScrollUp(200);
			}
			
			scrollX -= distanceX;
			scrollY -= distanceY;
			mDisp.motionEvent(scrollDownTime, time, ACTION_MOVE, scrollX, scrollY);
			
			if (scrollUp != null) {
				scrollUp.cancel();
				scheduleScrollUp(200);
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
					isPanning = false;
					mDisp.motionEvent(scrollDownTime, time, ACTION_UP, scrollX, scrollY);
				}
			}, delay);
		}

		@Override
		public void onScale(float focusX, float focusY, float scaleFactor) {
			KeyEventHandler.manualTouchFocus = KeyEventHandler.TARGET_MIRROR;
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
			
			return new NavigationTemplate.Builder()
					.setPanModeListener(isPanningMode -> {})
					.setMapActionStrip(new ActionStrip.Builder().addAction(Action.PAN).build())
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