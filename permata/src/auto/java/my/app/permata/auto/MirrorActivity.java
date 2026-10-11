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
import android.graphics.Point;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
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
				Log.i("Hard disconnect detected (Engine Off/Unplug). Forcing complete cleanup.");
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
		if (d != null && !d.back()) {
			var vm = (WindowManager) PermataApplication.get().getSystemService(WINDOW_SERVICE);
			if (vm != null) {
				var size = new Point();
				vm.getDefaultDisplay().getRealSize(size);
				var y = size.y / 2f;
				var time = uptimeMillis();
				d.motionEvent(time, time, MotionEvent.ACTION_DOWN, size.x, y);
				d.motionEvent(time, time + 10, MotionEvent.ACTION_MOVE, size.x * 0.9f, y);
				d.motionEvent(time, time + 20, MotionEvent.ACTION_MOVE, size.x * 0.8f, y);
				d.motionEvent(time, time + 30, MotionEvent.ACTION_MOVE, size.x * 0.7f, y);
				d.motionEvent(time, time + 40, MotionEvent.ACTION_MOVE, size.x * 0.6f, y);
				d.motionEvent(time, time + 50, MotionEvent.ACTION_UP, size.x * 0.5f, y);
			}
		}
		disableAccelRotation();
	}

	private static final class ToolBar extends LinearLayout {
		public ToolBar(Context ctx) {
			super(ctx);
			// Cast the float values to int to satisfy Android LayoutParams parameters
			int pad = (int) toPx(ctx, 8);
			int iconSize = (int) toPx(ctx, 48); 

			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
			params.gravity = Gravity.TOP | Gravity.END;
			params.setMargins(pad, pad, pad, pad);
			setLayoutParams(params);
			setOrientation(LinearLayout.HORIZONTAL);

			setBackgroundColor(Color.parseColor("#90000000"));
			setPadding(pad, pad, pad, pad);

			int[] icons = new int[]{R.drawable.home, R.drawable.back};
			for (int i = 0; i < icons.length; i++) {
				var icon = icons[i];
				var v = new AppCompatImageView(ctx);
				v.setId(icon);
				v.setImageResource(icon);
				v.setPadding(pad, pad, pad, pad);
				LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconSize, iconSize);
				if (i > 0) lp.setMarginStart(pad);
				v.setLayoutParams(lp);
				v.setScaleType(ImageView.ScaleType.FIT_CENTER);
				
				if (i == 0) {
					v.setOnClickListener(view -> onHomeButtonClick());
				} else {
					v.setOnClickListener(view -> onBackButtonClick());
				}
				addView(v);
			}
		}
		
		void show() {
			setVisibility(VISIBLE);
		}
	}
}