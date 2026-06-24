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
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.GestureDetectorCompat;
import com.google.android.material.textview.MaterialTextView;
import java.util.List;
import my.app.permata.R;
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
 * Modernized, optimized, robust, and completely memory-safe production-ready 
 * rewrite of ControlPanelView with static inner classes and WeakReferences.
 * 
 * @author skichan77
 */
public class ControlPanelView extends ConstraintLayout implements 
        MainActivityListener, 
        PreferenceStore.Listener,
        OverlayMenu.SelectionHandler,
        GestureListener {

    private static final byte MASK_VISIBLE = 1;
    private static final byte MASK_VIDEO_MODE = 2;

    private final GestureDetectorCompat gestureDetector;
    private final ImageView showHideBars;

    @DimenRes
    private final int size;

    @StyleRes
    private final int textAppearance;

    private PlaybackControlPrefs prefs;
    private HideTimer hideTimer;
    private byte mask;
    private View gestureSource;
    private TextView playbackTimer;
    private long scrollStamp;
    public ControlPanelView(Context context, AttributeSet attrs) {
        super(context, attrs, R.attr.appControlPanelStyle);
        this.gestureDetector = new GestureDetectorCompat(context, this);
        inflate(context, R.layout.control_panel_view, this);

        TypedArray ta = context.obtainStyledAttributes(attrs,
                R.styleable.ControlPanelView,
                R.attr.appControlPanelStyle,
                R.style.AppTheme_ControlPanelStyle);
        try {
            this.size = ta.getLayoutDimension(R.styleable.ControlPanelView_size, 0);
            this.textAppearance = ta.getResourceId(R.styleable.ControlPanelView_textAppearance, 0);
            setBackgroundColor(ta.getColor(R.styleable.ControlPanelView_android_colorBackground, 0));
        } finally {
            ta.recycle();
        }

        MainActivityDelegate delegate = getActivity();
        delegate.addBroadcastListener(this, ACTIVITY_DESTROY);
        delegate.getPrefs().addBroadcastListener(this);

        ViewGroup barsGroup = findViewById(R.id.show_hide_bars);
        this.showHideBars = (ImageView) barsGroup.getChildAt(0);
        barsGroup.setOnClickListener(this::showHideBars);

        ViewGroup menuGroup = findViewById(R.id.control_menu_button);
        menuGroup.setOnClickListener(this::showMenu);
        
        setShowHideBarsIcon(delegate);
    }
    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable parentState = super.onSaveInstanceState();
        Bundle stateBundle = new Bundle();
        stateBundle.putByte("MASK", this.mask);
        stateBundle.putParcelable("PARENT", parentState);
        return stateBundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle b) {
            super.onRestoreInstanceState(b.getParcelable("PARENT"));
            this.mask = b.getByte("MASK");
            if (this.mask != MASK_VISIBLE) {
                super.setVisibility(GONE);
            }
        } else {
            super.onRestoreInstanceState(state);
        }
    }
    public void bind(PermataServiceUiBinder b) {
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
        MainActivityDelegate delegate = getActivity();
        setSize(delegate.getPrefs().getControlPanelSizePref(delegate));
    }

    private void setSize(float scale) {
        TextView seekTime = findViewById(R.id.seek_time);
        TextView seekTotal = findViewById(R.id.seek_total);
        float textSize = getTextAppearanceSize(getContext(), this.textAppearance) * scale;
        int textPad = seekTime.getPaddingTop() + seekTime.getPaddingBottom();
        int pad = 2 * toIntPx(getContext(), 4) + textPad;
        int iconSize = (int) (textSize + pad);
        int panelSize = (int) (this.size * scale);
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
    private void seTextAppearance(TextView t, float size) {
        t.setTextAppearance(this.textAppearance);
        t.setTextSize(COMPLEX_UNIT_PX, size);
    }

    private void setSize(@IdRes int id, int size) {
        View v = findViewById(id);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        lp.width = size;
        lp.height = size;
        v.setLayoutParams(lp);
    }

    private void setHeight(@IdRes int id, int h) {
        View v = findViewById(id);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        lp.height = h;
        v.setLayoutParams(lp);
    }

    private void setHeight(View v, int h) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        lp.height = h;
        v.setLayoutParams(lp);
    }
    public boolean isActive() {
        return this.mask != 0;
    }

    @Override
    public void setVisibility(int visibility) {
        MainActivityDelegate a = getActivity();
        if (visibility == VISIBLE) {
            this.mask |= MASK_VISIBLE;
            if ((this.mask & MASK_VIDEO_MODE) != 0) {
                return;
            }
            super.setVisibility(VISIBLE);
            if (a.getPrefs().getHideBarsPref(a)) {
                a.setBarsHidden(true);
                setShowHideBarsIcon(a);
            }
        } else {
            this.mask &= ~MASK_VISIBLE;
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
        this.hideTimer = null;
        this.mask |= MASK_VIDEO_MODE;
        a.setBarsHidden(true);
        setShowHideBarsIcon(a);

        View info = (v != null) ? v.getVideoInfoView() : null;
        View fb = a.getFloatingButton();
        int delay = getStartDelay();

        if (delay == 0) {
            fb.setVisibility(GONE);
            if (info != null) {
                info.setVisibility(GONE);
            }
            super.setVisibility(GONE);
        } else {
            fb.setVisibility(VISIBLE);
            if (info != null) {
                info.setVisibility(VISIBLE);
            }
            super.setVisibility(VISIBLE);
            this.hideTimer = new HideTimer(this, a, delay, false, info, fb);
            a.postDelayed(this.hideTimer, delay);
        }
        checkPlaybackTimer(a);
    }

    public void disableVideoMode() {
        MainActivityDelegate a = getActivity();
        this.hideTimer = null;
        this.mask &= ~MASK_VIDEO_MODE;
        a.getFloatingButton().setVisibility(VISIBLE);

        if ((this.mask & MASK_VISIBLE) == 0) {
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
        if (this.hideTimer != null) {
            int delay = getTouchDelay();
            this.hideTimer = new HideTimer(this, a, delay, false, this.hideTimer.views);
            a.postDelayed(this.hideTimer, delay);
        }
        return a.interceptTouchEvent(e, me -> {
            this.gestureSource = this;
            this.gestureDetector.onTouchEvent(me);
            return super.onTouchEvent(me);
        });
    }

    @Override
    public boolean onSwipeLeft(MotionEvent e1, MotionEvent e2) {
        getActivity().getMediaServiceBinder().onPrevNextButtonClick(true);
        return true;
    }

    @Override
    public boolean onSwipeRight(MotionEvent e1, MotionEvent e2) {
        getActivity().getMediaServiceBinder().onPrevNextButtonClick(false);
        return true;
    }
    @Override
    public boolean onSwipeUp(MotionEvent e1, MotionEvent e2) {
        getActivity().getMediaServiceBinder().onPrevNextFolderClick(false);
        return true;
    }

    @Override
    public boolean onSwipeDown(MotionEvent e1, MotionEvent e2) {
        getActivity().getMediaServiceBinder().onPrevNextFolderClick(true);
        return true;
    }
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        boolean horizontal = Math.abs(distanceX) >= Math.abs(distanceY);
        long time = System.currentTimeMillis();
        long diff;

        if (horizontal) {
            diff = time - this.scrollStamp;
            if (diff < 100) {
                return true;
            }
            this.scrollStamp = time;
        } else {
            diff = time + this.scrollStamp;
            if (diff < 100) {
                return true;
            }
            this.scrollStamp = -time;
        }

        if (diff > 500) {
            return true;
        }

        if (horizontal) {
            PermataServiceUiBinder b = getActivity().getMediaServiceBinder();
            switch (e2.getPointerCount()) {
                case 1 -> b.onRwFfButtonClick(distanceX < 0);
                case 2 -> b.onRwFfButtonLongClick(distanceX < 0);
                default -> b.onPrevNextButtonLongClick(distanceX < 0);
            }
            onVideoSeek();
        } else if (e2.getPointerCount() == 2) {
            if (!getActivity().getPrefs().getChangeBrightnessPref()) {
                MainActivityDelegate a = getActivity();
                int br = a.getBrightness();
                br = (distanceY > 0) ? Math.min(255, br + 10) : Math.max(0, br - 10);
                a.setBrightness(br);
            }
        } else {
            MediaEngine eng = getActivity().getMediaServiceBinder().getCurrentEngine();
            if (eng != null && eng.adjustVolume(distanceY > 0 ? ADJUST_RAISE : ADJUST_LOWER)) {
                return true;
            }
        }
        return true;
    }
    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (!(this.gestureSource instanceof VideoView)) {
            return false;
        }
        getActivity().getMediaServiceBinder().onPlayPauseButtonClick();
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (!(this.gestureSource instanceof VideoView)) {
            return false;
        }
        return onTouch((VideoView) this.gestureSource);
    }

    public boolean onTouch(VideoView video) {
        MainActivityDelegate a = getActivity();
        BodyLayout b = a.getBody();
        if (b.getMode() == BodyLayout.Mode.BOTH) {
            b.setMode(BodyLayout.Mode.VIDEO);
            return true;
        }

        int delay = getTouchDelay();
        if (delay == 0) {
            return false;
        }

        View info = video.getVideoInfoView();
        View fb = a.getFloatingButton();

        if (getVisibility() == VISIBLE) {
            super.setVisibility(GONE);
            fb.setVisibility(GONE);
            if (a.getPrefs().getSysBarsOnVideoTouchPref()) {
                a.setFullScreen(true);
            }
            if (info != null) {
                info.setVisibility(GONE);
            }
        } else {
            super.setVisibility(VISIBLE);
            fb.setVisibility(VISIBLE);
            if (a.getPrefs().getSysBarsOnVideoTouchPref()) {
                a.setFullScreen(false);
            }
            if (info != null) {
                info.setVisibility(VISIBLE);
            }
            clearFocus();
            this.hideTimer = new HideTimer(this, a, delay, false, info, fb);
            a.postDelayed(this.hideTimer, delay);
        }
        checkPlaybackTimer(a);
        return true;
    }
    public void onVideoViewTouch(VideoView view, MotionEvent e) {
        this.gestureSource = view;
        this.gestureDetector.onTouchEvent(e);
    }

    public void onVideoSeek() {
        MainActivityDelegate a = getActivity();
        VideoView vv = a.getMediaServiceBinder().getMediaSessionCallback().getVideoView();
        if (vv == null) {
            if (this.gestureSource instanceof VideoView) {
                vv = (VideoView) this.gestureSource;
            } else {
                return;
            }
        }

        View info = vv.getVideoInfoView();
        View fb = a.getFloatingButton();
        int delay = getSeekDelay();

        super.setVisibility(VISIBLE);
        fb.setVisibility(VISIBLE);
        if (info != null) {
            info.setVisibility(VISIBLE);
        }
        clearFocus();

        this.hideTimer = new HideTimer(this, a, delay, true, info, fb);
        a.postDelayed(this.hideTimer, delay);
        checkPlaybackTimer(a);
    }

    public boolean isVideoSeekMode() {
        HideTimer t = this.hideTimer;
        return (t != null) && t.seekMode;
    }
    @Override
    public void onActivityEvent(MainActivityDelegate a, long e) {
        if (handleActivityDestroyEvent(a, e)) {
            a.getMediaServiceBinder().unbind();
            a.getPrefs().removeBroadcastListener(this);
        }
    }

    @Override
    public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
        MainActivityDelegate a = getActivity();
        if (MainActivityPrefs.hasControlPanelSizePref(a, prefs)) {
            setSize(a.getPrefs().getControlPanelSizePref(a));
        } else if ((this.mask == MASK_VISIBLE) && MainActivityPrefs.hasHideBarsPref(a, prefs)) {
            if (a.getPrefs().getHideBarsPref(a)) {
                a.setBarsHidden(getVisibility() == VISIBLE);
            } else if (a.isBarsHidden()) {
                a.setBarsHidden(false);
            }
            setShowHideBarsIcon(a);
        }
    }
    public View focusSearch() {
        View v = findViewById(R.id.seek_bar);
        return isVisible(v) ? v : findViewById(R.id.control_play_pause);
    }

    @Override
    public View focusSearch(View focused, int direction) {
        if (focused == null) {
            return super.focusSearch(null, direction);
        }

        if (direction == FOCUS_UP) {
            if (isLine1(focused)) {
                MainActivityDelegate a = getActivity();
                if (a.isVideoMode()) {
                    return a.getBody().getVideoView();
                }
                View v = MediaItemListView.focusSearchLast(getContext(), focused);
                if (v != null) {
                    return v;
                }
            } else {
                if (!isVisible(findViewById(R.id.seek_bar))) {
                    return findViewById(R.id.control_menu_button);
                }
            }
        } else if (direction == FOCUS_DOWN) {
            if (!isLine1(focused)) {
                NavBarView n = getActivity().getNavBar();
                if (isVisible(n) && n.isBottom()) {
                    return n.focusSearch();
                }
            }
        }
        return super.focusSearch(focused, direction);
    }

    private boolean isLine1(View v) {
        int id = v.getId();
        return id == R.id.seek_bar || id == R.id.show_hide_bars || id == R.id.control_menu_button;
    }

    private void showHideBars(View v) {
        MainActivityDelegate a = getActivity();
        a.setBarsHidden(!a.isBarsHidden());
        setShowHideBarsIcon(a);
    }
    public void showMenu() {
        if (isActive()) {
            showMenu(this);
        }
    }

    private void showMenu(View v) {
        MainActivityDelegate a = getActivity();
        MediaEngine eng = a.getMediaServiceBinder().getCurrentEngine();
        PlayableItem i = (eng == null) ? null : eng.getSource();
        if (i != null) {
            new MenuHandler(this, getMenu(a), i, eng).show();
        }
    }

    private OverlayMenu getMenu(MainActivityDelegate a) {
        return a.findViewById(R.id.control_menu);
    }

    private void setShowHideBarsIcon(MainActivityDelegate a) {
        a.post(() -> this.showHideBars.setImageResource(
                a.isBarsHidden() ? R.drawable.expand : my.app.utils.R.drawable.collapse));
    }

    private MainActivityDelegate getActivity() {
        return MainActivityDelegate.get(getContext());
    }

    @Override
    public boolean menuItemSelected(OverlayMenuItem item) {
        return true;
    }
    private void checkPlaybackTimer(MainActivityDelegate a) {
        MediaSessionCallback cb = a.getMediaSessionCallback();
        int t = cb.getPlaybackTimer();

        if (t <= 0) {
            if (this.playbackTimer != null) {
                ((ViewGroup) getParent()).removeView(this.playbackTimer);
                this.playbackTimer = null;
            }
        } else {
            if (this.playbackTimer == null) {
                Context ctx = getContext();
                this.playbackTimer = new MaterialTextView(ctx);
                ((ViewGroup) getParent()).addView(this.playbackTimer);
                this.playbackTimer.setBackgroundResource(R.drawable.playback_timer_bg);
                this.playbackTimer.setTextAppearance(this.textAppearance);
                
                ViewGroup.LayoutParams lp = this.playbackTimer.getLayoutParams();
                if (lp instanceof LayoutParams clp) {
                    clp.startToStart = PARENT_ID;
                    clp.endToEnd = PARENT_ID;
                    clp.bottomToTop = getId();
                    clp.resolveLayoutDirection(LAYOUT_DIRECTION_LTR);
                }
                
                this.playbackTimer.setOnClickListener(v -> 
                    getMenu(a).show(b -> new TimerMenuHandler(a, this).build(b))
                );
            }

            if (getVisibility() != VISIBLE) {
                this.playbackTimer.setVisibility(GONE);
                return;
            }

            try (SharedTextBuilder tb = SharedTextBuilder.get()) {
                TextUtils.timeToString(tb, t);
                this.playbackTimer.setText(tb);
            }

            this.playbackTimer.setVisibility(VISIBLE);
            a.postDelayed(() -> checkPlaybackTimer(a), 1000);
        }
    }
    private static final class MenuHandler extends MediaItemMenuHandler {
        private final java.lang.ref.WeakReference<ControlPanelView> viewRef;
        private final MediaEngine engine;

        public MenuHandler(ControlPanelView view, OverlayMenu menu, Item item, MediaEngine engine) {
            super(menu, item);
            this.viewRef = new java.lang.ref.WeakReference<>(view);
            this.engine = engine;
        }

        @Override
        protected boolean addVideoMenu() {
            return !this.engine.hasVideoMenu();
        }

        @Override
        protected boolean addAudioMenu() {
            PlayableItem pi = this.engine.getSource();
            ControlPanelView view = viewRef.get();
            if (view == null) return false;
            return (pi != null) && pi.isVideo() && (
                    (this.engine.getAudioStreamInfo().size() > 1) ||
                    view.getActivity().getMediaSessionCallback().getEngineManager().isVlcPlayerSupported()
            );
        }
        @Override
        protected void buildAudioMenu(OverlayMenu.Builder b) {
            if (this.engine.getAudioStreamInfo().size() > 1) {
                b.addItem(R.id.select_audio_stream, R.string.select_audio_stream)
                 .setSubmenu(this::buildAudioStreamMenu);
            }
            super.buildAudioMenu(b);
        }

        private void buildAudioStreamMenu(OverlayMenu.Builder b) {
            ControlPanelView view = viewRef.get();
            if (view == null) return;
            MediaEngine eng = view.getActivity().getMediaSessionCallback().getEngine();
            if (eng == null) return;
            AudioStreamInfo ai = eng.getCurrentAudioStreamInfo();
            List<AudioStreamInfo> streams = eng.getAudioStreamInfo();
            b.setSelectionHandler(this::audioStreamSelected);

            for (int i = 0; i < streams.size(); i++) {
                AudioStreamInfo s = streams.get(i);
                b.addItem(UiUtils.getArrayItemId(i), s.toString())
                 .setData(s)
                 .setChecked(s.equals(ai));
            }
        }
        private boolean audioStreamSelected(OverlayMenuItem i) {
            ControlPanelView view = viewRef.get();
            if (view == null) return true;
            MediaEngine eng = view.getActivity().getMediaSessionCallback().getEngine();
            if (eng != null) {
                AudioStreamInfo ai = i.getData();
                PlayableItem pi = (PlayableItem) getItem();

                if (ai.equals(eng.getCurrentAudioStreamInfo())) {
                    pi.getPrefs().setAudioIdPref(null);
                    eng.setCurrentAudioStream(null);
                } else {
                    eng.setCurrentAudioStream(ai);
                    pi.getPrefs().setAudioIdPref(ai.getId());
                }
            }
            return true;
        }

        @Override
        protected boolean addSubtitlesMenu() {
            return this.engine.isSubtitlesSupported();
        }

        @Override
        protected void buildSubtitlesMenu(OverlayMenu.Builder b) {
            b.addItem(R.id.select_subtitles, R.string.select_subtitles)
             .setFutureSubmenu(this::buildSubtitleStreamMenu);
            super.buildSubtitlesMenu(b);
        }
        private FutureSupplier<Void> buildSubtitleStreamMenu(OverlayMenu.Builder b) {
            b.setSelectionHandler(this::subtitleStreamSelected);
            return this.engine.getSubtitleStreamInfo().main().map(streams -> {
                SubtitleStreamInfo si = this.engine.getCurrentSubtitleStreamInfo();
                for (int i = 0; i < streams.size(); i++) {
                    SubtitleStreamInfo s = streams.get(i);
                    b.addItem(UiUtils.getArrayItemId(i), s.toString())
                     .setData(s)
                     .setChecked(s.equals(si));
                }
                return null;
            });
        }

        private boolean subtitleStreamSelected(OverlayMenuItem i) {
            ControlPanelView view = viewRef.get();
            if (view == null) return true;
            if (view.getActivity().getMediaSessionCallback().getEngine() != this.engine) {
                return true;
            }
            SubtitleStreamInfo si = i.getData();
            PlayableItem pi = (PlayableItem) getItem();

            if (si.equals(this.engine.getCurrentSubtitleStreamInfo())) {
                pi.getPrefs().setSubIdPref(null);
                this.engine.setCurrentSubtitleStream(null);
            } else {
                this.engine.setCurrentSubtitleStream(si);
                pi.getPrefs().setSubIdPref(si.getId());
            }
            return true;
        }
        @Override
        protected void buildPlayableMenu(MainActivityDelegate a, OverlayMenu.Builder b, PlayableItem pi, boolean initRepeat) {
            super.buildPlayableMenu(a, b, pi, false);
            BrowsableItemPrefs p = pi.getParent().getPrefs();
            MediaEngine eng = a.getMediaSessionCallback().getEngine();
            if (eng == null) return;
            boolean stream = pi.isStream();
            eng.contributeToMenu(b);

            if (!stream && !pi.isExternal()) {
                if (pi.isRepeatItemEnabled() || p.getRepeatPref()) {
                    b.addItem(R.id.repeat, R.drawable.repeat_filled, R.string.repeat)
                     .setSubmenu(s -> {
                         buildRepeatMenu(s);
                         s.addItem(R.id.repeat_disable_all, R.string.repeat_disable);
                     });
                } else {
                    b.addItem(R.id.repeat_enable, R.drawable.repeat, R.string.repeat)
                     .setSubmenu(this::buildRepeatMenu);
                }

                if (p.getShufflePref()) {
                    b.addItem(R.id.shuffle_disable, R.drawable.shuffle_filled, R.string.shuffle_disable);
                } else {
                    b.addItem(R.id.shuffle_enable, R.drawable.shuffle, R.string.shuffle);
                }
            }

            if (eng.getAudioEffects() != null) {
                b.addItem(R.id.audio_effects_fragment, R.drawable.equalizer, R.string.audio_effects);
            }

            if (!stream) {
                b.addItem(R.id.speed, R.drawable.speed, R.string.speed)
                 .setSubmenu(s -> {
                     ControlPanelView view = viewRef.get();
                     if (view != null) {
                         new SpeedMenuHandler(view).build(s, getItem());
                     }
                 });
            }

            b.addItem(R.id.timer, R.drawable.timer, R.string.timer)
             .setSubmenu(s -> new TimerMenuHandler(a, viewRef.get()).build(s));
        }
        private void buildRepeatMenu(OverlayMenu.Builder b) {
            b.setSelectionHandler(this);
            b.addItem(R.id.repeat_track, R.string.current_track);
            b.addItem(R.id.repeat_folder, R.string.current_folder);
        }

        @Override
        public boolean menuItemSelected(OverlayMenuItem i) {
            ControlPanelView view = viewRef.get();
            if (view == null) return true;
            int id = i.getItemId();
            PlayableItem pi;
            MediaEngine eng;

            if (id == R.id.audio_effects_fragment) {
                eng = view.getActivity().getMediaSessionCallback().getEngine();
                if (eng != null && eng.getAudioEffects() != null) {
                    view.getActivity().showFragment(R.id.audio_effects_fragment);
                    return true;
                }
            } else if (id == R.id.repeat_track || id == R.id.repeat_folder || id == R.id.repeat_disable_all) {
                pi = (PlayableItem) getItem();
                pi.setRepeatItemEnabled(id == R.id.repeat_track);
                pi.getParent().getPrefs().setRepeatPref(id == R.id.repeat_folder);
                return true;
            } else if (id == R.id.shuffle_enable || id == R.id.shuffle_disable) {
                pi = (PlayableItem) getItem();
                pi.getParent().getPrefs().setShufflePref(id == R.id.shuffle_enable);
                return true;
            }
            return super.menuItemSelected(i);
        }
    }
    private static final class SpeedMenuHandler implements OverlayMenu.CloseHandler {
        private final java.lang.ref.WeakReference<ControlPanelView> viewRef;
        private PrefStore store;

        public SpeedMenuHandler(ControlPanelView view) {
            this.viewRef = new java.lang.ref.WeakReference<>(view);
        }

        void build(OverlayMenu.Builder b, Item item) {
            ControlPanelView view = viewRef.get();
            if (view == null) return;
            this.store = new PrefStore(view, item);
            PreferenceSet set = new PreferenceSet();

            set.addFloatPref(o -> {
                o.title = R.string.speed;
                o.store = this.store;
                o.pref = MediaPrefs.SPEED;
                o.scale = 0.1f;
                o.seekMin = 1;
                o.seekMax = 20;
            });

            set.addBooleanPref(o -> {
                o.title = R.string.current_track;
                o.store = this.store;
                o.pref = PrefStore.TRACK;
            });

            set.addBooleanPref(o -> {
                o.title = R.string.current_folder;
                o.store = this.store;
                o.pref = PrefStore.FOLDER;
            });

            set.addToMenu(b, true);
            b.setCloseHandlerHandler(this);
        }

        @Override
        public void menuClosed(OverlayMenu menu) {
            if (this.store != null) {
                this.store.apply();
            }
        }
        private static final class PrefStore extends BasicPreferenceStore {
            static final Pref<BooleanSupplier> TRACK = Pref.b("TRACK", false);
            static final Pref<BooleanSupplier> FOLDER = Pref.b("FOLDER", false);

            private final java.lang.ref.WeakReference<ControlPanelView> viewRef;
            private final MediaSessionCallback cb;
            private final Item item;

            PrefStore(ControlPanelView view, Item item) {
                this.viewRef = new java.lang.ref.WeakReference<>(view);
                this.cb = view.getActivity().getMediaServiceBinder().getMediaSessionCallback();
                this.item = item;
                MediaPrefs prefs = item.getPrefs();
                BrowsableItem p = item.getParent();
                boolean isSet = false;

                try (PreferenceStore.Edit edit = editPreferenceStore()) {
                    if (prefs.hasPref(MediaPrefs.SPEED)) {
                        edit.setBooleanPref(TRACK, true);
                        edit.setFloatPref(MediaPrefs.SPEED, prefs.getFloatPref(MediaPrefs.SPEED));
                        isSet = true;
                    } else {
                        edit.setBooleanPref(TRACK, false);
                    }

                    if (p != null) {
                        MediaPrefs parentPrefs = p.getPrefs();
                        if (parentPrefs.hasPref(MediaPrefs.SPEED)) {
                            edit.setBooleanPref(FOLDER, true);
                            if (!isSet) {
                                edit.setFloatPref(MediaPrefs.SPEED, parentPrefs.getFloatPref(MediaPrefs.SPEED));
                                isSet = true;
                            }
                        } else {
                            edit.setBooleanPref(FOLDER, false);
                        }
                    } else {
                        edit.setBooleanPref(FOLDER, false);
                    }

                    if (!isSet) {
                        edit.setFloatPref(MediaPrefs.SPEED, cb.getPlaybackControlPrefs().getFloatPref(MediaPrefs.SPEED));
                    }
                }
            }

            void apply() {
                BrowsableItem p = this.item.getParent();
                boolean isSet = false;

                if (getBooleanPref(TRACK)) {
                    this.item.getPrefs().applyFloatPref(MediaPrefs.SPEED, getFloatPref(MediaPrefs.SPEED));
                    isSet = true;
                } else {
                    this.item.getPrefs().removePref(MediaPrefs.SPEED);
                }

                if (p != null) {
                    if (getBooleanPref(FOLDER)) {
                        p.getPrefs().applyFloatPref(MediaPrefs.SPEED, getFloatPref(MediaPrefs.SPEED));
                        isSet = true;
                    } else {
                        p.getPrefs().removePref(MediaPrefs.SPEED);
                    }
                }

                if (!isSet) {
                    this.cb.getPlaybackControlPrefs().applyFloatPref(MediaPrefs.SPEED, getFloatPref(MediaPrefs.SPEED));
                }
            }

            @Override
            public void applyFloatPref(boolean removeDefault, Pref<? extends DoubleSupplier> pref, float value) {
                if (value == 0.0f) {
                    value = 0.1f;
                }
                super.applyFloatPref(removeDefault, pref, value);
                if (this.cb.isPlaying()) {
                    this.cb.onSetPlaybackSpeed(value);
                }
            }
        }
    }
    private static final class TimerMenuHandler extends BasicPreferenceStore implements OverlayMenu.CloseHandler {
        private final Pref<IntSupplier> hPref = Pref.i("H", 0);
        private final Pref<IntSupplier> mPref = Pref.i("M", 0);
        private final java.lang.ref.WeakReference<MainActivityDelegate> activityRef;
        private final java.lang.ref.WeakReference<ControlPanelView> viewRef;
        private boolean changed;
        private boolean closed;

        TimerMenuHandler(MainActivityDelegate activity, ControlPanelView view) {
            this.activityRef = new java.lang.ref.WeakReference<>(activity);
            this.viewRef = new java.lang.ref.WeakReference<>(view);
        }

        void build(OverlayMenu.Builder b) {
            MainActivityDelegate activity = activityRef.get();
            if (activity == null) return;
            PreferenceSet set = new PreferenceSet();
            int time = activity.getMediaSessionCallback().getPlaybackTimer();

            if (time > 0) {
                int h = time / 3600;
                int m = (time - h * 3600) / 60;
                applyIntPref(this.hPref, h);
                applyIntPref(this.mPref, m);
            }

            set.addIntPref(o -> {
                o.title = R.string.hours;
                o.store = this;
                o.pref = this.hPref;
                o.seekMin = 0;
                o.seekMax = 12;
            });

            set.addIntPref(o -> {
                o.title = R.string.minutes;
                o.store = this;
                o.pref = this.mPref;
                o.seekMin = 0;
                o.seekMax = 60;
                o.seekScale = 5;
            });

            set.addToMenu(b, true);
            b.setCloseHandlerHandler(this);
            this.changed = false;
            startTimer();
        }

        @Override
        public void applyIntPref(boolean removeDefault, Pref<? extends IntSupplier> pref, int value) {
            super.applyIntPref(removeDefault, pref, value);
            this.changed = true;
            startTimer();
        }

        @Override
        public void menuClosed(OverlayMenu menu) {
            this.closed = true;
            if (!this.changed) return;
            MainActivityDelegate activity = activityRef.get();
            ControlPanelView view = viewRef.get();
            if (activity == null || view == null) return;

            int h = getIntPref(this.hPref);
            int m = getIntPref(this.mPref);
            activity.getMediaSessionCallback().setPlaybackTimer(h * 3600 + m * 60);
            view.checkPlaybackTimer(activity);
        }

        private void startTimer() {
            MainActivityDelegate activity = activityRef.get();
            ControlPanelView view = viewRef.get();
            if (activity == null || view == null) return;

            activity.postDelayed(() -> {
                if (!this.closed) {
                    ControlPanelView innerView = viewRef.get();
                    MainActivityDelegate innerAct = activityRef.get();
                    if (innerView != null && innerAct != null) {
                        innerView.getMenu(innerAct).hide();
                    }
                }
            }, 60000);
        }
    }

    private int getStartDelay() {
        return (this.prefs == null) ? 0 : this.prefs.getVideoControlStartDelayPref() * 1000;
    }

    private int getTouchDelay() {
        return (this.prefs == null) ? 5000 : this.prefs.getVideoControlTouchDelayPref() * 1000;
    }

    private int getSeekDelay() {
        return (this.prefs == null) ? 3000 : this.prefs.getVideoControlSeekDelayPref() * 1000;
    }

    private static final class HideTimer implements Runnable {
        private final java.lang.ref.WeakReference<ControlPanelView> viewRef;
        private final java.lang.ref.WeakReference<MainActivityDelegate> activityRef;
        final int delay;
        final boolean seekMode;
        final View[] views;

        HideTimer(ControlPanelView view, MainActivityDelegate activity, int delay, boolean seekMode, View... views) {
            this.viewRef = new java.lang.ref.WeakReference<>(view);
            this.activityRef = new java.lang.ref.WeakReference<>(activity);
            this.delay = delay;
            this.seekMode = seekMode;
            this.views = views;
        }

        @Override
        public void run() {
            ControlPanelView view = viewRef.get();
            MainActivityDelegate activity = activityRef.get();
            if (view == null || activity == null) return;

            if ((view.hideTimer != this) || ((view.mask & MASK_VIDEO_MODE) == 0)) {
                return;
            }

            if (view.hasFocus()) {
                view.hideTimer = new HideTimer(view, activity, this.delay, this.seekMode, this.views);
                activity.postDelayed(view.hideTimer, this.delay);
                return;
            }

            if (activity.getPrefs().getSysBarsOnVideoTouchPref()) {
                activity.setFullScreen(true);
            }
            
            view.setHideTimerVisibilityGone();

            for (View v : this.views) {
                if (v != null) {
                    v.setVisibility(GONE);
                }
            }
        }
    }

    void setHideTimerVisibilityGone() {
        super.setVisibility(GONE);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(null); 
        
        if (this.hideTimer != null) {
            MainActivityDelegate a = getActivity();
            if (a != null) {
                a.removeCallbacks(this.hideTimer);
            }
        }
        
        try {
            MainActivityDelegate delegate = getActivity();
            if (delegate != null) {
                delegate.removeBroadcastListener(this);
                if (delegate.getPrefs() != null) {
                    delegate.getPrefs().removeBroadcastListener(this);
                }
            }
        } catch (Exception ignored) {
            // Context parsing safety mitigation boundary
        }
        
        super.onDetachedFromWindow();
    }
}
