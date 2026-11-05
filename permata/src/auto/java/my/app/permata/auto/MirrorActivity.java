package my.app.permata.auto;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.os.SystemClock.uptimeMillis;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static my.app.permata.auto.MirrorDisplay.disableAccelRotation;
import static my.app.utils.ui.UiUtils.toPx;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.car.app.SurfaceContainer;

import com.google.android.apps.auto.sdk.CarActivity;

import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.concurrent.ReschedulableTask;
import my.app.utils.log.Log;

public class MirrorActivity extends CarActivity implements SurfaceHolder.Callback {
	private MirrorDisplay md;
	private SurfaceContainer sc;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		md = MirrorDisplay.get();
		MainActivityDelegate.setTheme(this, true);
		var s = new SurfaceView(this);
		var tb = new ToolBar(this);
		var v = new FrameLayout(this) {
			@SuppressLint("ClickableViewAccessibility")
			@Override
			public boolean onTouchEvent(MotionEvent e) {
				if (md == null || md.isDestroyed()) md = MirrorDisplay.get();
				if (sc != null && md != null) md.setSurface(sc);
				tb.show();
				
				boolean handled = md != null && md.motionEvent(e);
				if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
					return true;
				}
				return handled;
			}
		};
		s.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		s.getHolder().addCallback(this);
		v.addView(s);
		v.addView(tb);
		v.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		setContentView(v);
	}

	@Override
	public void onDestroy() {
		if (md != null) {
			md.release();
			md = null;
		}
		super.onDestroy();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (md == null || md.isDestroyed()) md = MirrorDisplay.get();
		if (sc != null && md != null) md.setSurface(sc);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (MirrorServiceFS.sc != null && md != null && !md.isDestroyed()) md.setSurface(MirrorServiceFS.sc);
	}

	@Override
	public void surfaceCreated(@NonNull SurfaceHolder h) {
		var r = h.getSurfaceFrame();
		sc = new SurfaceContainer(h.getSurface(), r.width(), r.height(),
				getResources().getDisplayMetrics().densityDpi);
				
		if (md == null || md.isDestroyed()) md = MirrorDisplay.get();
		if (md != null) {
			md.setSurface(sc);
		}
	}

	@Override
	public void surfaceChanged(@NonNull SurfaceHolder h, int format, int width, int height) {
		sc = new SurfaceContainer(h.getSurface(), width, height,
				getResources().getDisplayMetrics().densityDpi);
				
		if (md == null || md.isDestroyed()) md = MirrorDisplay.get();
		if (md != null) md.setSurface(sc);
	}

	@Override
	public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
		if (sc != null) {
			if (md != null) {
				md.releaseSurface(sc);
			}
			sc = null;
		}

		new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
			if (!PermataApplication.get().isConnectedToAuto()) {
				Log.i("Hard disconnect detected. Forcing complete cleanup.");
				MirrorDisplay.close();
			}
		}, 5000);
	}

	static void onHomeButtonClick() {
		var ctx = PermataApplication.get();
		if (ctx != null) {
			Intent intent = new Intent(ctx, LauncherActivity.class);
			intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
			ctx.startActivity(intent);
		}
		disableAccelRotation();
	}
	
	static void onRecentButtonClick() {
		var d = EventDispatcher.get();
		if (d != null) {
			d.directRecent();
		}
		disableAccelRotation();
	}

	static void onBackButtonClick() {
		// SURGICAL FIX: Layer 1 is standard system back. Layer 2 executes physical Edge-Swipe Back Gesture. Layer 3 attempts intent broadcast fallback.
		if (AccessibilityEventDispatcherService.dispatchBack()) {
			Log.i("[BACK] Floating Toolbar triggered standard Accessibility Back.");
		} else {
			Log.w("[BACK] Accessibility Back failed. Attempting Edge-Swipe Gesture Fallback.");
			if (AccessibilityEventDispatcherService.dispatchBackGesture()) {
				Log.i("[BACK] Edge-Swipe Back Gesture injected successfully.");
			} else {
				var d = EventDispatcher.get();
				if (d != null) {
					d.directBack();
					Log.w("[BACK] Triggered EventDispatcher Intent Broadcast Back.");
				}
			}
		}
		disableAccelRotation();
	}

	static void onExitButtonClick() {
		Log.i("User triggered manual shutdown from Split-Screen toolbar.");
		MirrorDisplay.close();
		
		var ctx = PermataApplication.get();
		if (ctx != null) {
			Intent intent = new Intent(ctx, LauncherActivity.class);
			intent.setAction(MainActivityDelegate.INTENT_ACTION_FINISH);
			intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
			ctx.startActivity(intent);
		}
	}

	private final class ToolBar extends LinearLayout {
		private final ReschedulableTask delayedHide = new ReschedulableTask() {
			@Override
			protected void perform() {
				setVisibility(GONE);
			}
		};
		private final float moveTolerance;
		private final Animation animation;
		private int iconSize;
		private final int pad;
		
		private long downTime;
		private float downX, downY;
		private float initialX, initialY;
		private View downButton;
		private boolean moving;
		private boolean isPositioned = false; 

		public ToolBar(Context ctx) {
			super(ctx);
			pad = (int) toPx(ctx, 8);
			moveTolerance = toPx(ctx, 10);
			animation = AnimationUtils.loadAnimation(ctx, my.app.utils.R.anim.button_press);
			
			iconSize = (int) toPx(ctx, 48); 
			
			setOrientation(HORIZONTAL);
			setLayoutParams(new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

			var iconPad = iconSize / 4;
			
			var icons = new int[]{R.drawable.home, R.drawable.recent, R.drawable.back, R.drawable.shutdown};
			for (int i = 0; i < icons.length; i++) {
				var v = new AppCompatImageView(ctx);
				v.setId(icons[i]);
				v.setImageResource(icons[i]);
				
				if (i == 1 || i == 3) v.setColorFilter(Color.WHITE); 
				
				v.setBackgroundResource(R.drawable.oval_bg);
				v.setPadding(iconPad, iconPad, iconPad, iconPad);
				var lp = new LinearLayout.LayoutParams(iconSize, iconSize);
				if (i > 0) lp.setMarginStart(pad);
				v.setLayoutParams(lp);
				v.setAdjustViewBounds(true);
				v.setScaleType(ImageView.ScaleType.FIT_CENTER);
				addView(v);
			}
			
			addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
				if (!isPositioned) { 
					int pw = ((View)getParent()).getWidth();
					if (pw > 0) {
						setX(pw - getWidth() - pad);
						setY(pad);
						isPositioned = true;
					}
				}
			});

			delayedHide.schedule(5000);
		}

		void show() {
			setVisibility(VISIBLE);
			delayedHide.schedule(5000);
		}

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouchEvent(MotionEvent e) {
			show();
			switch (e.getActionMasked()) {
				case MotionEvent.ACTION_DOWN -> {
					downTime = uptimeMillis();
					downX = e.getRawX();
					downY = e.getRawY();
					initialX = getX(); 
					initialY = getY();
					moving = false; 
					
					int btnIndex = getButtonAt(e.getX(), e.getY());
					if (btnIndex >= 0 && btnIndex < getChildCount()) {
						downButton = getChildAt(btnIndex);
						downButton.startAnimation(animation);
					} else {
						downButton = null;
					}
					return true;
				}
				case MotionEvent.ACTION_MOVE -> {
					float deltaX = e.getRawX() - downX;
					float deltaY = e.getRawY() - downY;
					
					if (!moving && (Math.abs(deltaX) >= moveTolerance || Math.abs(deltaY) >= moveTolerance)) {
						moving = true;
						if (downButton != null) {
							downButton.clearAnimation();
							downButton = null;
						}
					}
					
					if (moving) {
						int w = getWidth();
						int h = getHeight();
						int pw = ((View)getParent()).getWidth();
						int ph = ((View)getParent()).getHeight();
						
						var newX = Math.max(pad, Math.min(pw - pad - w, initialX + deltaX));
						var newY = Math.max(pad, Math.min(ph - pad - h, initialY + deltaY));
						animate().x(newX).y(newY).setDuration(0).start();
					}
					return true;
				}
				case MotionEvent.ACTION_UP -> {
					if (moving) {
						moving = false;
						return true;
					}
					
					if (downButton != null) {
						downButton.clearAnimation();
						if ((uptimeMillis() - downTime) < 500) {
							handleClick(downButton.getId());
						}
					}
					return true;
				}
				case MotionEvent.ACTION_CANCEL -> {
					moving = false;
					if (downButton != null) {
						downButton.clearAnimation();
						downButton = null;
					}
					return true;
				}
				default -> {
					return super.onTouchEvent(e);
				}
			}
		}

		private int getButtonAt(float localX, float localY) {
			for (int i = 0; i < getChildCount(); i++) {
				View child = getChildAt(i);
				if (localX >= child.getLeft() && localX <= child.getRight() &&
					localY >= child.getTop() && localY <= child.getBottom()) {
					return i;
				}
			}
			return -1;
		}

		private void handleClick(int viewId) {
			if (viewId == R.drawable.home) onHomeButtonClick();
			else if (viewId == R.drawable.recent) onRecentButtonClick(); 
			else if (viewId == R.drawable.back) onBackButtonClick();
			else if (viewId == R.drawable.shutdown) onExitButtonClick();
		}
	}
}