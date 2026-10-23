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
			md.setSessionStopListener(() -> {
				// Legacy CarActivity fallback
			});
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
				md.setSessionStopListener(null); 
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

	static void onBackButtonClick() {
		var d = EventDispatcher.get();
		if (d != null) {
			d.directBack();
		}
		disableAccelRotation();
	}

	static void onExitButtonClick() {
		Log.i("User triggered manual shutdown from Split-Screen toolbar.");
		MirrorDisplay.close();
	}

	private static final class ToolBar extends LinearLayout {
		private final ReschedulableTask delayedHide = new ReschedulableTask() {
			@Override
			protected void perform() {
				setVisibility(GONE);
			}
		};
		private final float moveTolerance;
		private final Animation animation;
		private final float minX;
		private final float maxX;
		private final float minY;
		private final float maxY;
		private final int iconSize;
		private final int pad;
		private long downTime;
		private float downX, downY;
		private View downButton;
		private boolean moving;

		public ToolBar(Context ctx) {
			super(ctx);
			pad = (int) toPx(ctx, 5);
			var dm = ctx.getResources().getDisplayMetrics();
			minX = pad;
			maxX = dm.widthPixels - pad;
			minY = pad;
			maxY = dm.heightPixels - pad;
			iconSize = (int) Math.max(Math.min(dm.widthPixels, dm.heightPixels) / 8f, toPx(ctx, 20));
			setLayoutParams(new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
			
			// Adjust starting X position to account for 3 buttons instead of 2
			setX(dm.widthPixels - (iconSize * 3) - (pad * 2));
			setY(pad);

			var iconPad = iconSize / 4;
			var icons = new int[]{R.drawable.home, R.drawable.back, R.drawable.shutdown};
			for (int i = 0; i < icons.length; i++) {
				var v = new AppCompatImageView(ctx);
				addView(v);
				v.setId(icons[i]);
				v.setImageResource(icons[i]);
				
				if (i == 2) v.setColorFilter(Color.WHITE); 
				
				v.setBackgroundResource(R.drawable.oval_bg);
				v.setPadding(iconPad, iconPad, iconPad, iconPad);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconSize, iconSize);
				if (i > 0) lp.setMarginStart(pad);
				v.setLayoutParams(lp);
				v.setAdjustViewBounds(true);
				v.setScaleType(ImageView.ScaleType.FIT_CENTER);
			}

			moveTolerance = toPx(ctx, 10);
			animation = AnimationUtils.loadAnimation(ctx, my.app.utils.R.anim.button_press);
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
					
					int btnIndex = getButtonAt(e.getX());
					if (btnIndex >= 0 && btnIndex < getChildCount()) {
						downButton = getChildAt(btnIndex);
						downButton.startAnimation(animation);
					}
					return true;
				}
				case MotionEvent.ACTION_MOVE -> {
					if ((downButton != null) && ((Math.abs(e.getRawX() - downX) >= moveTolerance) ||
							(Math.abs(e.getRawY() - downY) >= moveTolerance))) {
						downButton.clearAnimation();
					}
					int w = getWidth();
					int h = getHeight();
					var newX = Math.max(minX, Math.min(maxX - w, e.getRawX() - w / 2f));
					var newY = Math.max(minY, Math.min(maxY - h, e.getRawY() - h / 2f));
					animate().x(newX).y(newY).setDuration(0).start();
					moving = true;
					return true;
				}
				case MotionEvent.ACTION_UP -> {
					if (moving) {
						moving = false;
						if ((Math.abs(e.getRawX() - downX) >= moveTolerance) ||
								(Math.abs(e.getRawY() - downY) >= moveTolerance)) {
							return true;
						}
					}
					if ((uptimeMillis() - downTime) < 400) handleClick(e.getX());
					else handleLongClick(e.getX());
					return true;
				}
				default -> {
					return super.onTouchEvent(e);
				}
			}
		}

		// Fixed Dynamic Math: Supports 3 buttons calculating space + padding
		private int getButtonAt(float x) {
			int buttonWidthWithPad = iconSize + pad;
			int index = (int) (x / buttonWidthWithPad);
			return Math.max(0, Math.min(2, index));
		}

		private void handleClick(float x) {
			int btn = getButtonAt(x);
			if (btn == 0) onHomeButtonClick();
			else if (btn == 1) onBackButtonClick();
			else onExitButtonClick();
		}

		private void handleLongClick(float x) {
			int btn = getButtonAt(x);
			if (btn == 0) onHomeButtonClick(); 
			else if (btn == 1) onBackButtonClick();
			else onExitButtonClick();
		}
	}
}