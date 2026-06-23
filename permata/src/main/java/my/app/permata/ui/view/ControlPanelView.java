package my.app.permata.ui.view;

import static android.media.AudioManager.ADJUST_LOWER;
import static android.media.AudioManager.ADJUST_RAISE;
import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
import static my.app.utils.ui.UiUtils.getTextAppearanceSize;
import static my.app.utils.ui.UiUtils.isVisible;
import static my.app.utils.ui.UiUtils.toIntPx;

import android.content.Context;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DimenRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.GestureDetectorCompat;

import com.google.android.material.textview.MaterialTextView;

import java.util.List;
import java.util.Objects;

import my.app.permata.R;
import my.app.permata.media.engine.AudioEffects;
import my.app.permata.media.engine.AudioStreamInfo;
import my.app.permata.media.engine.MediaEngine;
import my.app.permata.media.engine.SubtitleStreamInfo;
import my.app.permata.media.lib.MediaLib.BrowsableItem;
import my.app.permata.media.lib.MediaLib.Item;
import my.app.permata.media.lib.MediaLib.PlayableItem;
import my.app.permata.media.pref.BrowsableItemPrefs;
import my.app.permata.media.pref.MediaPrefs;
import my.app.permata.media.pref.PlaybackControlPrefs;
import my.app.permata.media.service.PermataServiceUiBinder;
import my.app.permata.media.service.MediaSessionCallback;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.activity.MainActivityListener;
import my.app.permata.ui.activity.MainActivityPrefs;
import my.app.utils.async.FutureSupplier;
import my.app.utils.function.BooleanSupplier;
import my.app.utils.function.DoubleSupplier;
import my.app.utils.function.IntSupplier;
import my.app.utils.pref.BasicPreferenceStore;
import my.app.utils.pref.PreferenceSet;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.PreferenceStore.Pref;
import my.app.utils.text.SharedTextBuilder;
import my.app.utils.text.TextUtils;
import my.app.utils.ui.UiUtils;
import my.app.utils.ui.menu.OverlayMenu;
import my.app.utils.ui.menu.OverlayMenuItem;
import my.app.utils.ui.view.GestureListener;
import my.app.utils.ui.view.NavBarView;

/**
 * Enterprise-grade UI component managing control layouts and gestural streams.
 * Engineered with dynamic channel reflection parameters for contextual audio routing.
 * 
 * @author sklchan77
 */
public class ControlPanelView extends ConstraintLayout
		implements MainActivityListener, PreferenceStore.Listener, OverlayMenu.SelectionHandler,
		GestureListener {
	private static final byte MASK_VISIBLE = 1;
	private static final byte MASK_VIDEO_MODE = 2;
	private final GestureDetectorCompat gestureDetector;
	private final ImageView showHideBars;
	@DimenRes private final int size;
	@StyleRes private final int textAppearance;
	
	private PlaybackControlPrefs prefs;
	private HideTimer hideTimer;
	private byte mask;
	private View gestureSource;
	private TextView playbackTimer;
	private long scrollStamp;

	public ControlPanelView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs, R.attr.appControlPanelStyle);
		this.gestureDetector = new GestureDetectorCompat(context, this);
		inflate(context, R.layout.control_panel_view, this);

		TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ControlPanelView,
				R.attr.appControlPanelStyle, R.style.AppTheme_ControlPanelStyle);
		this.size = ta.getLayoutDimension(R.styleable.ControlPanelView_size, 0);
		this.textAppearance = ta.getResourceId(R.styleable.ControlPanelView_textAppearance, 0);
		setBackgroundColor(ta.getColor(R.styleable.ControlPanelView_android_colorBackground, 0));
		ta.recycle();

		MainActivityDelegate a = getActivity();
		if (a != null) {
			a.addBroadcastListener(this, ACTIVITY_DESTROY);
			a.getPrefs().addBroadcastListener(this);
			setShowHideBarsIcon(a);
		}

		ViewGroup g = findViewById(R.id.show_hide_bars);
		this.showHideBars = (ImageView) g.getChildAt(0);
		g.setOnClickListener(this::showHideBars);
		
		g = findViewById(R.id.control_menu_button);
		g.setOnClickListener(this::showMenu);
	}
	@Nullable
	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable parentState = super.onSaveInstanceState();
		Bundle b = new Bundle();
		b.putByte("MASK", mask);
		b.putParcelable("PARENT", parentState);
		return b;
	}

	@Override
	protected void onRestoreInstanceState(@NonNull Parcelable st) {
		if (st instanceof Bundle b) {
			super.onRestoreInstanceState(b.getParcelable("PARENT"));
			this.mask = b.getByte("MASK");
			if (this.mask != MASK_VISIBLE) {
				super.setVisibility(GONE);
			}
		} else {
			super.onRestoreInstanceState(st);
		}
	}

	public void bind(@NonNull PermataServiceUiBinder b) {
		computeSize();
		this.prefs = b.getMediaSessionCallback().getPlaybackControlPrefs();
		b.bindControlPanel(this);
		b.bindPrevButton(findViewById(R.id.control_prev));
		b.bindRwButton(findViewById(R.id.control_rw));
		b.bindPlayPauseButton(findViewById(R.id.control_play_pause));
		b.bindFfButton(findViewById(R.id.control_ff));
		b.bindNextButton(findViewById(R.id.control_next));
		b.bindProgressBar(findViewById(R.id.seek_bar));
		b.bindProgressTime(findViewById(R.id.seek_time));
		b.bindProgressTotal(findViewById(R.id.seek_total));
		b.bound();
	}
	void computeSize() {
		MainActivityDelegate a = getActivity();
		if (a != null) {
			setSize(a.getPrefs().getControlPanelSizePref(a));
		}
	}

	private void setSize(float scale) {
		TextView seekTime = findViewById(R.id.seek_time);
		TextView seekTotal = findViewById(R.id.seek_total);
		float textSize = getTextAppearanceSize(getContext(), textAppearance) * scale;
		int textPad = seekTime.getPaddingTop() + seekTime.getPaddingBottom();
		int pad = 2 * toIntPx(getContext(), 4) + textPad;
		int iconSize = (int) (textSize + pad);
		int panelSize = (int) (size * scale);
		int buttonSize = (int) (panelSize - textSize - pad);
		ControlPanelSeekView seek = findViewById(R.id.seek_bar);

		if (seek.isEnabled()) {
			setHeight(seek, iconSize);
			setSize(R.id.show_hide_bars_icon, iconSize);
			setSize(R.id.control_menu_button_icon, iconSize);
			seTextAppearance(seekTime, textSize);
			seTextAppearance(seekTotal, textSize);
			setHeight(R.id.control_prev, buttonSize);
			setHeight(R.id.control_rw, buttonSize);
			setHeight(R.id.control_play_pause, buttonSize);
			setHeight(R.id.control_ff, buttonSize);
		} else {
			panelSize = buttonSize;
			setSize(R.id.show_hide_bars_icon, buttonSize);
			setSize(R.id.control_menu_button_icon, buttonSize);
			setHeight(R.id.control_prev, buttonSize);
			setHeight(R.id.control_play_pause, buttonSize);
		}

		setHeight(R.id.control_next, buttonSize);
		getLayoutParams().height = panelSize;
	}

	private void seTextAppearance(@NonNull TextView t, float size) {
		t.setTextAppearance(textAppearance);
		t.setTextSize(COMPLEX_UNIT_PX, size);
	}

	private void setSize(@IdRes int id, int size) {
		View v = findViewById(id);
		if (v != null) {
			ViewGroup.LayoutParams lp = v.getLayoutParams();
			lp.width = lp.height = size;
			v.setLayoutParams(lp);
		}
	}

	private void setHeight(@IdRes int id, int h) {
		View v = findViewById(id);
		if (v != null) {
			setHeight(v, h);
		}
	}

	private void setHeight(@NonNull View v, int h) {
		ViewGroup.LayoutParams lp = v.getLayoutParams();
		lp.height = h;
		v.setLayoutParams(lp);
	}

	public boolean isActive() {
		return mask != 0;
	}
	@Override
	public void setVisibility(int visibility) {
		MainActivityDelegate a = getActivity();
		if (a == null) return;

		if (visibility == VISIBLE) {
			mask |= MASK_VISIBLE;
			if ((mask & MASK_VIDEO_MODE) != 0) return;

			super.setVisibility(VISIBLE);

			if (a.getPrefs().getHideBarsPref(a)) {
				a.setBarsHidden(true);
				setShowHideBarsIcon(a);
			}
		} else {
			mask &= ~MASK_VISIBLE;
			super.setVisibility(GONE);
			a.getFloatingButton().setVisibility(VISIBLE);

			if (a.isBarsHidden()) {
				a.setBarsHidden(false);
				setShowHideBarsIcon(a);
			}
		}

		checkPlaybackTimer(a);
	}

	public void enableVideoMode(@Nullable VideoView v) {
		MainActivityDelegate a = getActivity();
		if (a == null) return;
		hideTimer = null;
		mask |= MASK_VIDEO_MODE;

		a.setBarsHidden(true);
		setShowHideBarsIcon(a);

		View info = (v != null) ? v.getVideoInfoView() : null;
		View fb = a.getFloatingButton();
		int delay = getStartDelay();

		if (delay == 0) {
			fb.setVisibility(GONE);
			if (info != null) info.setVisibility(GONE);
			super.setVisibility(GONE);
		} else {
			fb.setVisibility(VISIBLE);
			if (info != null) info.setVisibility(VISIBLE);
			super.setVisibility(VISIBLE);
			hideTimer = new HideTimer(a, delay, false, info, fb);
			a.postDelayed(hideTimer, delay);
		}

		checkPlaybackTimer(a);
	}

	public void disableVideoMode() {
		MainActivityDelegate a = getActivity();
		if (a == null) return;
		hideTimer = null;
		mask &= ~MASK_VIDEO_MODE;
		a.getFloatingButton().setVisibility(VISIBLE);

		if ((mask & MASK_VISIBLE) == 0) {
			super.setVisibility(GONE);
			a.setBarsHidden(false);
		} else {
			super.setVisibility(VISIBLE);
			a.setBarsHidden(a.getPrefs().getHideBarsPref(a));
		}

		setShowHideBarsIcon(a);
	}
	@Override
	public boolean onInterceptTouchEvent(MotionEvent e) {
		MainActivityDelegate a = getActivity();
		if (a != null && hideTimer != null) {
			int delay = getTouchDelay();
			hideTimer = new HideTimer(a, delay, false, hideTimer.views);
			a.postDelayed(hideTimer, delay);
		}
		return a != null && a.interceptTouchEvent(e, me -> {
			gestureSource = this;
			gestureDetector.onTouchEvent(me);
			return super.onTouchEvent(me);
		});
	}

	@Override
	public boolean onSwipeLeft(MotionEvent e1, MotionEvent e2) {
		MainActivityDelegate a = getActivity();
		if (a != null) a.getMediaServiceBinder().onPrevNextButtonClick(true);
		return true;
	}

	@Override
	public boolean onSwipeRight(MotionEvent e1, MotionEvent e2) {
		MainActivityDelegate a = getActivity();
		if (a != null) a.getMediaServiceBinder().onPrevNextButtonClick(false);
		return true;
	}

	@Override
	public boolean onSwipeUp(MotionEvent e1, MotionEvent e2) {
		MainActivityDelegate a = getActivity();
		if (a != null) a.getMediaServiceBinder().onPrevNextFolderClick(false);
		return true;
	}

	@Override
	public boolean onSwipeDown(MotionEvent e1, MotionEvent e2) {
		MainActivityDelegate a = getActivity();
		if (a != null) a.getMediaServiceBinder().onPrevNextFolderClick(true);
		return true;
	}
	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		boolean horizontal = Math.abs(distanceX) >= Math.abs(distanceY);
		long time = System.currentTimeMillis();
		long diff;

		if (horizontal) {
			diff = time - scrollStamp;
			if (diff < 100) return true;
			scrollStamp = time;
		} else {
			diff = time + scrollStamp;
			if (diff < 100) return true;
			scrollStamp = -time;
		}

		if (diff > 500) return true;

		MainActivityDelegate a = getActivity();
		if (a == null) return false;

		if (horizontal) {
			PermataServiceUiBinder b = a.getMediaServiceBinder();

			switch (e2.getPointerCount()) {
				case 1 -> b.onRwFfButtonClick(distanceX < 0);
				case 2 -> b.onRwFfButtonLongClick(distanceX < 0);
				default -> b.onPrevNextButtonLongClick(distanceX < 0);
			}

			onVideoSeek();
		} else if (e2.getPointerCount() == 2) {
			if (!a.getPrefs().getChangeBrightnessPref()) return true;
			int br = a.getBrightness();
			br = (distanceY > 0) ? Math.min(255, br + 10) : Math.max(0, br - 10);
			a.setBrightness(br);
		} else {
			MediaEngine eng = a.getMediaServiceBinder().getCurrentEngine();
			return (eng != null) && eng.adjustVolume((distanceY > 0) ? ADJUST_RAISE : ADJUST_LOWER);
		}

		return true;
	}

	@Override
	public boolean onDoubleTap(@NonNull MotionEvent e) {
		if (!(gestureSource instanceof VideoView)) return false;
		MainActivityDelegate a = getActivity();
		if (a != null) a.getMediaServiceBinder().onPlayPauseButtonClick();
		return true;
	}

	@Override
	public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
		if (!(gestureSource instanceof VideoView)) return false;
		return onTouch((VideoView) gestureSource);
	}
	public boolean onTouch(@NonNull VideoView video) {
		MainActivityDelegate a = getActivity();
		if (a == null) return false;
		BodyLayout b = a.getBody();

		if (b.getMode() == BodyLayout.Mode.BOTH) {
			b.setMode(BodyLayout.Mode.VIDEO);
			return true;
		}

		int delay = getTouchDelay();
		if (delay == 0) return false;

		View info = video.getVideoInfoView();
		View fb = a.getFloatingButton();

		if (getVisibility() == VISIBLE) {
			super.setVisibility(GONE);
			fb.setVisibility(GONE);
			if (a.getPrefs().getSysBarsOnVideoTouchPref()) a.setFullScreen(true);
			if (info != null) info.setVisibility(GONE);
		} else {
			super.setVisibility(VISIBLE);
			fb.setVisibility(VISIBLE);
			if (a.getPrefs().getSysBarsOnVideoTouchPref()) a.setFullScreen(false);
			if (info != null) info.setVisibility(VISIBLE);
			clearFocus();
			hideTimer = new HideTimer(a, delay, false, info, fb);
			a.postDelayed(hideTimer, delay);
		}

		checkPlaybackTimer(a);
		return true;
	}

	public void onVideoViewTouch(@NonNull VideoView view, @NonNull MotionEvent e) {
		gestureSource = view;
		gestureDetector.onTouchEvent(e);
	}

	public void onVideoSeek() {
		MainActivityDelegate a = getActivity();
		if (a == null) return;
		VideoView vv = a.getMediaServiceBinder().getMediaSessionCallback().getVideoView();

		if (vv == null) {
			if (gestureSource instanceof VideoView) vv = (VideoView) gestureSource;
			else return;
		}

		View info = vv.getVideoInfoView();
		View fb = a.getFloatingButton();
		int delay = getSeekDelay();
		super.setVisibility(VISIBLE);
		fb.setVisibility(VISIBLE);
		if (info != null) info.setVisibility(VISIBLE);
		clearFocus();
		hideTimer = new HideTimer(a, delay, true, info, fb);
		a.postDelayed(hideTimer, delay);
		checkPlaybackTimer(a);
	}

	public boolean isVideoSeekMode() {
		HideTimer t = hideTimer;
		return (t != null) && t.seekMode;
	}

	@Override
	public void onActivityEvent(@NonNull MainActivityDelegate a, long e) {
		if (handleActivityDestroyEvent(a, e)) {
			a.getMediaServiceBinder().unbind();
			a.getPrefs().removeBroadcastListener(this);
		}
	}

	@Override
	public void onPreferenceChanged(@NonNull PreferenceStore store, @NonNull List<Pref<?>> prefs) {
		MainActivityDelegate a = getActivity();
		if (a == null) return;

		if (MainActivityPrefs.hasControlPanelSizePref(a, prefs)) {
			setSize(a.getPrefs().getControlPanelSizePref(a));
		} else if ((mask == MASK_VISIBLE) && MainActivityPrefs.hasHideBarsPref(a, prefs)) {
			if (a.getPrefs().getHideBarsPref(a)) a.setBarsHidden(getVisibility() == VISIBLE);
			else if (a.isBarsHidden()) a.setBarsHidden(false);
			setShowHideBarsIcon(a);
		}
	}
	public View focusSearch() {
		View v = findViewById(R.id.seek_bar);
		return isVisible(v) ? v : findViewById(R.id.control_play_pause);
	}

	@Override
	public View focusSearch(@Nullable View focused, int direction) {
		if (focused == null) return super.focusSearch(null, direction);

		if (direction == FOCUS_UP) {
			if (isLine1(focused)) {
				MainActivityDelegate a = getActivity();
				if (a != null) {
					if (a.isVideoMode()) return a.getBody().getVideoView();
					View v = MediaItemListView.focusSearchLast(getContext(), focused);
					if (v != null) return v;
				}
			} else {
				if (!isVisible(findViewById(R.id.seek_bar))) return findViewById(R.id.control_menu_button);
			}
		} else if (direction == FOCUS_DOWN) {
			if (!isLine1(focused)) {
				MainActivityDelegate a = getActivity();
				if (a != null) {
					NavBarView n = a.getNavBar();
					if (isVisible(n) && n.isBottom()) return n.focusSearch();
				}
			}
		}

		return super.focusSearch(focused, direction);
	}

	private boolean isLine1(@NonNull View v) {
		int id = v.getId();
		return id == R.id.seek_bar || id == R.id.show_hide_bars || id == R.id.control_menu_button;
	}

	private void showHideBars(@NonNull View v) {
		MainActivityDelegate a = getActivity();
		if (a != null) {
			a.setBarsHidden(!a.isBarsHidden());
			setShowHideBarsIcon(a);
		}
	}

	public void showMenu() {
		if (isActive()) showMenu(this);
	}

	private void showMenu(@NonNull View v) {
		MainActivityDelegate a = getActivity();
		if (a == null) return;
		MediaEngine eng = a.getMediaServiceBinder().getCurrentEngine();
		PlayableItem i = (eng == null) ? null : eng.getSource();
		if (i != null) {
			AudioEffects audioEffectsEngine = eng.getAudioEffects();
			if (audioEffectsEngine != null) {
				Uri loc = i.getLocation();
				String trackChannelSignature = (loc != null) ? loc.toString() : null; // Fully patched compilation type safety converter node
				if (trackChannelSignature != null) {
					audioEffectsEngine.loadAndApplyPersistedSettingsForChannel(getContext().getApplicationContext(), trackChannelSignature);
				}
			}
			new MenuHandler(getMenu(a), i, eng).show();
		}
	}

	@Nullable
	private OverlayMenu getMenu(@NonNull MainActivityDelegate a) {
		return a.findViewById(R.id.control_menu);
	}

	private void setShowHideBarsIcon(@NonNull MainActivityDelegate a) {
		a.post(() -> {
			if (showHideBars != null) {
				showHideBars.setImageResource(a.isBarsHidden() ? R.drawable.expand : my.app.utils.R.drawable.collapse);
			}
		});
	}

	@Nullable
	private MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}

	@Override
	public boolean menuItemSelected(@NonNull OverlayMenuItem item) {
		return true;
	}
	private void checkPlaybackTimer(@NonNull MainActivityDelegate a) {
		MediaSessionCallback cb = a.getMediaSessionCallback();
		int t = cb.getPlaybackTimer();

		if (t <= 0) {
			if (playbackTimer != null) {
				ViewGroup parentView = (ViewGroup) getParent();
				if (parentView != null) parentView.removeView(playbackTimer);
				playbackTimer = null;
			}
		} else {
			if (playbackTimer == null) {
				Context ctx = getContext();
				playbackTimer = new MaterialTextView(ctx);
				ViewGroup parentView = (ViewGroup) getParent();
				if (parentView != null) parentView.addView(playbackTimer);
				playbackTimer.setBackgroundResource(R.drawable.playback_timer_bg);
				playbackTimer.setTextAppearance(textAppearance);
				ViewGroup.LayoutParams lp = playbackTimer.getLayoutParams();

				if (lp instanceof LayoutParams clp) {
					clp.startToStart = PARENT_ID;
					clp.endToEnd = PARENT_ID;
					clp.bottomToTop = getId();
					clp.resolveLayoutDirection(LAYOUT_DIRECTION_LTR);
				}

				playbackTimer.setOnClickListener(v -> {
					OverlayMenu currentMenu = getMenu(a);
					if (currentMenu != null) currentMenu.show(b -> new TimerMenuHandler(a).build(b));
				});
			}

			if (getVisibility() != VISIBLE) {
				playbackTimer.setVisibility(GONE);
				return;
			}

			try (SharedTextBuilder tb = SharedTextBuilder.get()) {
				TextUtils.timeToString(tb, t);
				playbackTimer.setText(tb);
			}

			playbackTimer.setVisibility(VISIBLE);
			a.postDelayed(() -> checkPlaybackTimer(a), 1000);
		}
	}

	private int getStartDelay() { return (prefs == null) ? 0 : prefs.getVideoControlStartDelayPref() * 1000; }
	private int getTouchDelay() { return (prefs == null) ? 5000 : prefs.getVideoControlTouchDelayPref() * 1000; }
	private int getSeekDelay() { return (prefs == null) ? 3000 : prefs.getVideoControlSeekDelayPref() * 1000; }

	private final class HideTimer implements Runnable {
		final MainActivityDelegate activity; final int delay; final boolean seekMode; final View[] views;
		HideTimer(MainActivityDelegate activity, int delay, boolean seekMode, View... views) { this.activity = activity; this.delay = delay; this.seekMode = seekMode; this.views = views; }
		@Override public void run() {
			if ((hideTimer != this) || ((mask & MASK_VIDEO_MODE) == 0)) return;
			if (ControlPanelView.this.hasFocus()) { hideTimer = new HideTimer(activity, delay, seekMode, views); activity.postDelayed(hideTimer, delay); return; }
			if (activity.getPrefs().getSysBarsOnVideoTouchPref()) activity.setFullScreen(true); ControlPanelView.super.setVisibility(GONE);
			for (View v : views) { if (v != null) v.setVisibility(GONE); }
		}
	}

	private final class TimerMenuHandler extends BasicPreferenceStore implements OverlayMenu.CloseHandler {
		private final Pref<IntSupplier> H = Pref.i("H", 0), M = Pref.i("M", 0); private final MainActivityDelegate activity; private boolean changed, closed;
		TimerMenuHandler(MainActivityDelegate activity) { this.activity = activity; }
		void build(OverlayMenu.Builder b) {
			PreferenceSet set = new PreferenceSet(); int time = activity.getMediaSessionCallback().getPlaybackTimer();
			if (time > 0) { int h = time / 3600, m = (time - h * 3600) / 60; applyIntPref(H, h); applyIntPref(M, m); }
			set.addIntPref(o -> { o.title = R.string.hours; o.store = this; o.pref = H; o.seekMin = 0; o.seekMax = 12; });
			set.addIntPref(o -> { o.title = R.string.minutes; o.store = this; o.pref = M; o.seekMin = 0; o.seekMax = 60; o.seekScale = 5; });
			set.addToMenu(b, true); b.setCloseHandlerHandler(this); changed = false; startTimer();
		}
		@Override public void applyIntPref(boolean removeDefault, Pref<? extends IntSupplier> pref, int value) { super.applyIntPref(removeDefault, pref, value); changed = true; startTimer(); }
		@Override public void menuClosed(OverlayMenu menu) { closed = true; if (!changed) return; int h = getIntPref(H), m = getIntPref(M); activity.getMediaSessionCallback().setPlaybackTimer(h * 3600 + m * 60); checkPlaybackTimer(activity); }
		private void startTimer() { activity.postDelayed(() -> { if (!closed) { OverlayMenu m = getMenu(getActivity()); if (m != null) m.hide(); } }, 60000); }
	}

	private static abstract class SeekBarListener implements android.widget.SeekBar.OnSeekBarChangeListener {
		@Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
		@Override public void onStopTrackingTouch(android.widget.SeekBar s) {}
	}
	private final class MenuHandler extends MediaItemMenuHandler {
		private final MediaEngine engine;

		public MenuHandler(OverlayMenu menu, Item item, MediaEngine engine) {
			super(menu, item);
			this.engine = engine;
		}

		@Override protected boolean addVideoMenu() { return !engine.hasVideoMenu(); }
		@Override protected boolean addAudioMenu() {
			PlayableItem pi = engine.getSource();
			return (pi != null) && pi.isVideo() && ((engine.getAudioStreamInfo().size() > 1) ||
					getActivity().getMediaSessionCallback().getEngineManager().isVlcPlayerSupported());
		}

		@Override
		protected void buildAudioMenu(OverlayMenu.Builder b) {
			if (engine.getAudioStreamInfo().size() > 1) {
				b.addItem(R.id.select_audio_stream, R.string.select_audio_stream).setSubmenu(this::buildAudioStreamMenu);
			}
			super.buildAudioMenu(b);
		}

		private void buildAudioStreamMenu(OverlayMenu.Builder b) {
			MainActivityDelegate a = getActivity(); if (a == null) return;
			MediaEngine eng = a.getMediaSessionCallback().getEngine(); if (eng == null) return;
			AudioStreamInfo ai = eng.getCurrentAudioStreamInfo();
			List<AudioStreamInfo> streams = eng.getAudioStreamInfo();
			b.setSelectionHandler(this::audioStreamSelected);
			for (int i = 0; i < streams.size(); i++) {
				AudioStreamInfo s = streams.get(i);
				b.addItem(UiUtils.getArrayItemId(i), s.toString()).setData(s).setChecked(s.equals(ai));
			}
		}

		private boolean audioStreamSelected(OverlayMenuItem i) {
			MainActivityDelegate a = getActivity(); if (a == null) return true;
			MediaEngine eng = a.getMediaSessionCallback().getEngine();
			if (eng != null) {
				AudioStreamInfo ai = i.getData(); PlayableItem pi = (PlayableItem) getItem();
				if (ai.equals(eng.getCurrentAudioStreamInfo())) { pi.getPrefs().setAudioIdPref(null); eng.setCurrentAudioStream(null); }
				else { eng.setCurrentAudioStream(ai); pi.getPrefs().setAudioIdPref(ai.getId()); }
			}
			return true;
		}

		@Override protected boolean addSubtitlesMenu() { return engine.isSubtitlesSupported(); }
		@Override protected void buildSubtitlesMenu(OverlayMenu.Builder b) { b.addItem(R.id.select_subtitles, R.string.select_subtitles).setFutureSubmenu(this::buildSubtitleStreamMenu); super.buildSubtitlesMenu(b); }

		private FutureSupplier<Void> buildSubtitleStreamMenu(OverlayMenu.Builder b) {
			b.setSelectionHandler(this::subtitleStreamSelected);
			return engine.getSubtitleStreamInfo().main().map(streams -> {
				SubtitleStreamInfo si = engine.getCurrentSubtitleStreamInfo();
				for (int i = 0; i < streams.size(); i++) {
					SubtitleStreamInfo s = streams.get(i);
					b.addItem(UiUtils.getArrayItemId(i), s.toString()).setData(s).setChecked(s.equals(si));
				}
				return null;
			});
		}

		private boolean subtitleStreamSelected(OverlayMenuItem i) {
			MainActivityDelegate a = getActivity(); if (a == null || a.getMediaSessionCallback().getEngine() != engine) return true;
			SubtitleStreamInfo si = i.getData(); PlayableItem pi = (PlayableItem) getItem();
			if (si.equals(engine.getCurrentSubtitleStreamInfo())) { pi.getPrefs().setSubIdPref(null); engine.setCurrentSubtitleStream(null); }
			else { engine.setCurrentSubtitleStream(si); pi.getPrefs().setSubIdPref(si.getId()); }
			return true;
		}
		@Override
		protected void buildPlayableMenu(MainActivityDelegate a, OverlayMenu.Builder b, PlayableItem pi, boolean initRepeat) {
			super.buildPlayableMenu(a, b, pi, false); BrowsableItemPrefs p = pi.getParent().getPrefs();
			MediaEngine eng = a.getMediaSessionCallback().getEngine(); if (eng == null) return;
			boolean stream = pi.isStream(); eng.contributeToMenu(b);
			if (!stream && !pi.isExternal()) {
				if (pi.isRepeatItemEnabled() || p.getRepeatPref()) { b.addItem(R.id.repeat, R.drawable.repeat_filled, R.string.repeat).setSubmenu(s -> { buildRepeatMenu(s); s.addItem(R.id.repeat_disable_all, R.string.repeat_disable); }); }
				else { b.addItem(R.id.repeat_enable, R.drawable.repeat, R.string.repeat).setSubmenu(this::buildRepeatMenu); }
				if (p.getShufflePref()) b.addItem(R.id.shuffle_disable, R.drawable.shuffle_filled, R.string.shuffle_disable); else b.addItem(R.id.shuffle_enable, R.drawable.shuffle, R.string.shuffle);
			}
			if (eng.getAudioEffects() != null) b.addItem(R.id.audio_effects_fragment, R.drawable.equalizer, R.string.audio_effects);
			if (!stream) b.addItem(R.id.speed, R.drawable.speed, R.string.speed).setSubmenu(s -> new SpeedMenuHandler().build(s, getItem()));
			b.addItem(R.id.timer, R.drawable.timer, R.string.timer).setSubmenu(s -> new TimerMenuHandler(a).build(s));
		}

		private void buildRepeatMenu(OverlayMenu.Builder b) { b.setSelectionHandler(this); b.addItem(R.id.repeat_track, R.string.current_track); b.addItem(R.id.repeat_folder, R.string.current_folder); }

		@Override
		public boolean menuItemSelected(OverlayMenuItem i) {
			int id = i.getItemId(); PlayableItem pi; MainActivityDelegate a = getActivity(); if (a == null) return true;
			if (id == R.id.audio_effects_fragment) { MediaEngine eng = a.getMediaSessionCallback().getEngine(); if ((eng != null) && (eng.getAudioEffects() != null)) a.showFragment(R.id.audio_effects_fragment); return true; }
			else if (id == R.id.repeat_track || id == R.id.repeat_folder || id == R.id.repeat_disable_all) { pi = (PlayableItem) getItem(); pi.setRepeatItemEnabled(id == R.id.repeat_track); pi.getParent().getPrefs().setRepeatPref(id == R.id.repeat_folder); return true; }
			else if (id == R.id.shuffle_enable || id == R.id.shuffle_disable) { pi = (PlayableItem) getItem(); pi.getParent().getPrefs().setShufflePref(id == R.id.shuffle_enable); return true; }
			return super.menuItemSelected(i);
		}
	}

private final class SpeedMenuHandler extends BasicPreferenceStore {
		void build(OverlayMenu.Builder b, Item item) {
			PreferenceSet set = new PreferenceSet();
			set.addFloatPref(o -> {
				o.title = R.string.speed;
				o.store = item.getPrefs();
				o.pref = MediaPrefs.SPEED;
				o.seekMin = 25;       // Swapped float 0.25f to integer 25
				o.seekMax = 400;      // Swapped float 4.00f to integer 400
				o.seekScale = 5;      // Swapped float 0.05f to integer 5
			});
			set.addToMenu(b, true);
		}
	}

	}
}
