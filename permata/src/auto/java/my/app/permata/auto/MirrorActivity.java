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
		if (sc != null && md != null) md.setSurface(sc);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (MirrorServiceFS.sc != null && md != null) md.setSurface(MirrorServiceFS.sc);
	}

	@Override
	public void surfaceCreated(@NonNull SurfaceHolder h) {
		var r = h.getSurfaceFrame();
		sc = new SurfaceContainer(h.getSurface(), r.width(), r.height(),
				getResources().getDisplayMetrics().densityDpi);
		if (md != null) {
			md.setSurface(sc);
		}
	}

	@Override
	public void surfaceChanged(@NonNull SurfaceHolder h, int format, int width, int height) {
		sc = new SurfaceContainer(h.getSurface(), width, height,
				getResources().getDisplayMetrics().densityDpi);
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

    // SURGICAL FIX: Non-static actions so they can call finish() on the CarActivity
	void onHomeButtonClick() {
		var ctx = PermataApplication.get();
		if (ctx != null) {
			Intent intent = new Intent(ctx, LauncherActivity.class);
			intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP);
			ctx.startActivity(intent);
		}
		disableAccelRotation();
        finish(); // Closes the split-screen window
	}

	void onBackButtonClick() {
		var d = EventDispatcher.get();
		if (d != null) {
			d.directBack();
		}
		disableAccelRotation();
	}

	void onExitButtonClick() {
		Log.i("User triggered manual shutdown from Split-Screen toolbar.");
		MirrorDisplay.close();
        finish(); // Destroys the zombie window and returns to native Car Dashboard
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
		private View downButton;
		private boolean moving;

		public ToolBar(Context ctx) {
			super(ctx);
			pad = (int) toPx(ctx, 8);
			moveTolerance = toPx(ctx, 10);
			animation = AnimationUtils.loadAnimation(ctx, my.app.utils.R.anim.button_press);
			
			// Rigid 48dp sizing guarantees uniform button size regardless of projection DPI
			iconSize = (int) toPx(ctx, 48); 
			
			setOrientation(HORIZONTAL);
			setLayoutParams(new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

			var iconPad = iconSize / 4;
			var icons = new int[]{R.drawable.home, R.drawable.back, R.drawable.shutdown};
			for (int i = 0; i < icons.length; i++) {
				var v = new AppCompatImageView(ctx);
				v.setId(icons[i]);
				v.setImageResource(icons[i]);
				
				if (i == 2) v.setColorFilter(Color.WHITE); 
				
				v.setBackgroundResource(R.drawable.oval_bg);
				v.setPadding(iconPad, iconPad, iconPad, iconPad);
				var lp = new LinearLayout.LayoutParams(iconSize, iconSize);
				if (i > 0) lp.setMarginStart(pad);
				v.setLayoutParams(lp);
				v.setAdjustViewBounds(true);
				v.setScaleType(ImageView.ScaleType.FIT_CENTER);
				addView(v);
			}
			
			// Auto-position to the right edge during layout inflation
			addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
				if (!moving) { 
					int pw = ((View)getParent()).getWidth();
					if (pw > 0) setX(pw - getWidth() - pad);
					setY(pad);
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
					moving = false; // Reset drag lock
					
                    // SURGICAL FIX: Uses direct local pixel boundaries instead of skewed raw math
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
					float deltaX = Math.abs(e.getRawX() - downX);
					float deltaY = Math.abs(e.getRawY() - downY);
					
					if (!moving && (deltaX >= moveTolerance || deltaY >= moveTolerance)) {
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
					    
					    var newX = Math.max(pad, Math.min(pw - pad - w, e.getRawX() - w / 2f));
					    var newY = Math.max(pad, Math.min(ph - pad - h, e.getRawY() - h / 2f));
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

        // Guarantees 100% click accuracy by checking physical element boundaries
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
			else if (viewId == R.drawable.back) onBackButtonClick();
			else if (viewId == R.drawable.shutdown) onExitButtonClick();
		}
	}
}