package my.app.permata.auto;

import static android.accessibilityservice.GestureDescription.getMaxGestureDuration;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOWS_CHANGED;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_ADDED;
import static android.view.accessibility.AccessibilityEvent.WINDOWS_CHANGE_REMOVED;
import static android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION;
import static android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Display;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import my.app.permata.PermataApplication;
import my.app.utils.log.Log;

public class AccessibilityEventDispatcherService extends AccessibilityService {
    private static String clickOnButton;
    public static boolean isConsentGranted = false;
    private static AccessibilityEventDispatcherService instance;

    private static volatile String lastActivePackage = null;
    public static volatile boolean isInjectingBack = false;

    private static long lastActiveBtTime = 0;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Integer> windowIds =
            (VERSION.SDK_INT < VERSION_CODES.TIRAMISU) ? Collections.emptySet() : new HashSet<>();
    private final Path path = new Path();
    private GestureDescription.Builder gb;
    private Pointer[] pointers = new Pointer[]{new Pointer()};

    private static final Set<String> SCROLLER_APPS = new HashSet<>(Arrays.asList(
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.lite",
            "com.instagram.android",
            "com.instagram.barcelona",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.google.android.youtube",
            "com.xingin.xhs",
            "com.smile.gifmaker",
            "com.kuaishou.nebula",
            "com.kwai.video",
            "tv.danmaku.bili",
            "com.reddit.frontpage",
            "com.snapchat.android",
            "com.twitter.android"
    ));

    // Full-Screen Mirroring (MirrorServiceFS) serialized continuous drag engine
    private static final long DRAG_IDLE_TIMEOUT_MS = 1500L;
    private static final long DRAG_CENTER_HOLD_MS = 35L;
    private static final long DRAG_ANCHORED_HOLD_MS = 110L;
    private static final long DRAG_MIN_SEGMENT_MS = 32L;
    private static final long DRAG_MAX_SEGMENT_MS = 120L;
    private static final long DRAG_CENTER_LIFT_MS = 25L;
    private static final long DRAG_ANCHORED_SETTLE_LIFT_MS = 75L;

    private static volatile boolean nextDragAnchored = false;

    private long dragSessionId = 0L;
    private boolean currentDragAnchored = false;
    private boolean isDragDispatching = false;
    private StrokeDescription activeDragStroke = null;
    private float lastDragX;
    private float lastDragY;
    private boolean hasPendingMove = false;
    private float pendingMoveX;
    private float pendingMoveY;
    private boolean pendingUp = false;

    public static void setNextDragAnchored(boolean anchored) {
        nextDragAnchored = anchored;
    }

    private final Runnable dragIdleWatchdog = () -> {
        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            forceLiftActiveDrag("IDLE_TIMEOUT_1500MS");
        }
    };

    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("my.app.permata.ACTION_GLOBAL_BACK".equals(intent.getAction())) {
                performGlobalAction(GLOBAL_ACTION_BACK);
            } else if ("my.app.permata.ACTION_GLOBAL_HOME".equals(intent.getAction())) {
                performGlobalAction(GLOBAL_ACTION_HOME);
            } else if ("my.app.permata.ACTION_GLOBAL_RECENT".equals(intent.getAction())) {
                performGlobalAction(GLOBAL_ACTION_RECENTS);
            }
        }
    };

    public static boolean isAvailable() {
        return instance != null;
    }

    public static String getLastActivePackage() {
        var ds = instance;
        if (ds != null) {
            String livePkg = ds.resolveTopApplicationPackage();
            if (livePkg != null) {
                lastActivePackage = livePkg;
                return livePkg;
            }
        }
        return lastActivePackage;
    }

    @Nullable
    private String resolveTopApplicationPackage() {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows == null || windows.isEmpty()) return null;

            List<AccessibilityWindowInfo> appWindows = new ArrayList<>();
            for (AccessibilityWindowInfo w : windows) {
                if (w == null) continue;
                if ((VERSION.SDK_INT >= VERSION_CODES.R) && (w.getDisplayId() != DEFAULT_DISPLAY)) continue;
                if (w.getType() == TYPE_APPLICATION) {
                    appWindows.add(w);
                }
            }
            if (appWindows.isEmpty()) return null;

            appWindows.sort((a, b) -> Integer.compare(b.getLayer(), a.getLayer()));

            String topPkg = null;
            int checked = 0;
            for (AccessibilityWindowInfo w : appWindows) {
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null) continue;
                String pkg = null;
                try {
                    CharSequence p = root.getPackageName();
                    if (p != null) pkg = p.toString();
                } finally {
                    root.recycle();
                }
                if (pkg == null || isIgnoredOverlay(pkg) || isCurrentImePackage(pkg)) continue;

                if (checked == 0 && pkg.equals(getPackageName())) {
                    return pkg;
                }
                if (topPkg == null) topPkg = pkg;
                if (SCROLLER_APPS.contains(pkg)) {
                    return pkg;
                }
                if (++checked >= 2) break;
            }
            return topPkg;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void abortActiveGesture() {
        var ds = instance;
        if (ds == null || VERSION.SDK_INT < VERSION_CODES.O) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            ds.forceLiftActiveDrag("EXTERNAL_ABORT");
        } else {
            ds.mainHandler.post(() -> ds.forceLiftActiveDrag("EXTERNAL_ABORT"));
        }
    }

    static int getWindowCount() {
        var ds = instance;
        if (ds == null) return -1;
        return (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) ? ds.windowIds.size() :
                ds.listWindows().size();
    }

    static void autoClickOnButton(@Nullable String text) {
        clickOnButton = text;
    }

    public static boolean dispatchBack() {
        var ds = instance;
        if (ds == null) return false;
        return ds.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public static boolean dispatchUniversalBack() {
        var ds = instance;
        if (ds == null) return false;

        if (PermataApplication.get().isMirroringMode()) {
            my.app.permata.auto.MirrorDisplay md = my.app.permata.auto.MirrorDisplay.getActiveInstance();
            if (md != null) {
                long now = android.os.SystemClock.uptimeMillis();

                try {
                    md.motionEvent(now, now, android.view.MotionEvent.ACTION_DOWN, 1f, 1f);
                    md.motionEvent(now, now + 10, android.view.MotionEvent.ACTION_UP, 1f, 1f);
                } catch (Exception ignored) {}

                android.view.KeyEvent down = new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK, 0);
                android.view.KeyEvent up = new android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK, 0);

                boolean injected = false;
                String[] methods = {"keyEvent", "injectKeyEvent", "dispatchKeyEvent", "sendKeyEvent", "injectInputEvent"};
                for (String mName : methods) {
                    try {
                        java.lang.reflect.Method m = md.getClass().getMethod(mName, android.view.KeyEvent.class);
                        m.invoke(md, down);
                        m.invoke(md, up);
                        injected = true;
                        break;
                    } catch (Exception ignored) {}
                    try {
                        java.lang.reflect.Method m = md.getClass().getMethod(mName, android.view.InputEvent.class);
                        m.invoke(md, down);
                        m.invoke(md, up);
                        injected = true;
                        break;
                    } catch (Exception ignored) {}
                }
                if (injected) return true;
            }
        }

        dispatchTap(1f, 1f);

        isInjectingBack = true;
        boolean success = ds.performGlobalAction(GLOBAL_ACTION_BACK);
        ds.mainHandler.postDelayed(() -> isInjectingBack = false, 300);
        return success;
    }

    public static boolean dispatchGodModeSwipe(boolean up) {
        var ds = instance;
        if (ds == null || VERSION.SDK_INT < VERSION_CODES.N) return false;

        try { MirrorDisplay.notifyInjectedTouch(); } catch (Exception ignored) {}

        android.util.DisplayMetrics metrics = ds.getResources().getDisplayMetrics();
        float actionX = metrics.widthPixels * 0.50f;
        float centerY = metrics.heightPixels * 0.50f;
        float span = metrics.heightPixels * 0.20f;

        float yStart = up ? (centerY - span / 2f) : (centerY + span / 2f);
        float yEnd = up ? (centerY + span / 2f) : (centerY - span / 2f);
        long duration = up ? 90L : 70L;

        Path swipePath = new Path();
        swipePath.moveTo(actionX, yStart);
        swipePath.lineTo(actionX, yEnd);

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new StrokeDescription(swipePath, 0L, duration));

        return ds.dispatchGesture(gestureBuilder.build(), null, null);
    }

    static boolean dispatchHome() {
        var ds = instance;
        if (ds == null) return false;
        return ds.performGlobalAction(GLOBAL_ACTION_HOME);
    }

    static boolean dispatchRecent() {
        var ds = instance;
        if (ds == null) return false;
        return ds.performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    static boolean dispatchTap(float x, float y) {
        var ds = instance;
        if ((VERSION.SDK_INT < VERSION_CODES.N) || (ds == null)) return false;
        if ((x < 0f) || (y < 0f)) return true;
        ds.path.reset();
        ds.path.moveTo(x, y);
        var gb = new GestureDescription.Builder().addStroke(new StrokeDescription(ds.path, 0L, 1L));
        return ds.dispatchGesture(gb.build(), null, null);
    }

    static boolean dispatchScale(float x, float y, float diff) {
        var ds = instance;
        if ((VERSION.SDK_INT < VERSION_CODES.O) || (ds == null)) return false;
        if ((x < 0f) || (y < 0f)) return true;
        var path = ds.path;
        var dur = 100L;
        if (diff > 0) {
            path.reset();
            path.moveTo(x, y);
            path.lineTo(x - diff, y);
            ds.addStroke(new StrokeDescription(path, 0L, dur, false));
            path.reset();
            path.moveTo(x + 10, y);
            path.lineTo(x + diff + 10, y);
            ds.addStroke(new StrokeDescription(path, 0L, dur, false));
        } else {
            path.reset();
            path.moveTo(x + diff, y);
            path.lineTo(x, y);
            ds.addStroke(new StrokeDescription(path, 0L, dur, false));
            path.reset();
            path.moveTo(x - diff + 10, y);
            path.lineTo(x + 10, y);
            ds.addStroke(new StrokeDescription(path, 0L, dur, false));
        }
        return ds.dispatch();
    }

    static boolean dispatchMotionEvent(MotionEvent e) {
        var ds = instance;
        if ((VERSION.SDK_INT < VERSION_CODES.O) || (ds == null) || (e == null)) return false;

        // Full-Screen Mirroring (MirrorServiceFS) tagged stream -> serialized continuous drag engine
        int edgeFlags = e.getEdgeFlags();
        if ((edgeFlags & MirrorDisplay.EDGE_FLAG_FS_STREAM) != 0) {
            final int fsAction = e.getActionMasked();
            final float fsX = e.getX(0);
            final float fsY = e.getY(0);
            final boolean fsAnchored = (edgeFlags & MirrorDisplay.EDGE_FLAG_FS_ANCHORED) != 0;
            if (Looper.myLooper() == Looper.getMainLooper()) {
                return ds.handleSerializedDragEvent(fsAction, fsX, fsY, fsAnchored);
            } else {
                ds.mainHandler.post(() -> ds.handleSerializedDragEvent(fsAction, fsX, fsY, fsAnchored));
                return true;
            }
        }

        // Split-Screen Mirroring (MirrorActivity) -> 100% original multi-pointer gesture builder
        var pointers = ds.pointers;
        var cnt = e.getPointerCount();
        var action = e.getActionMasked();
        boolean completePrev;

        if ((cnt > 1) && (action == ACTION_MOVE)) {
            long timeDiff = e.getEventTime() - ds.pointers[0].time;
            if (timeDiff < 30L) {
                if (timeDiff < 0) ds.pointers[0].time = e.getEventTime();
                completePrev = false;
            } else {
                completePrev = true;
            }
        } else {
            completePrev = false;
        }

        if (cnt > pointers.length) {
            ds.pointers = pointers = Arrays.copyOf(pointers, cnt);
            for (int i = 0; i < cnt; i++) {
                if (pointers[i] == null) pointers[i] = new Pointer();
            }
        }

        var downTime = e.getDownTime();
        var eventTime = e.getEventTime();
        int idx;

        if ((action == ACTION_POINTER_DOWN) || (action == ACTION_POINTER_UP)) {
            action = (action == ACTION_POINTER_DOWN) ? ACTION_DOWN : ACTION_UP;
            idx = e.getActionIndex();
            cnt = idx + 1;
        } else {
            idx = 0;
        }

        for (; idx < cnt; idx++) {
            var x = e.getX(idx);
            var y = e.getY(idx);
            if ((x < 0f) || (y < 0f)) continue;
            var p = pointers[idx];
            if (!ds.buildGesture(p, downTime, eventTime, action, x, y, completePrev)) return false;
        }

        return (ds.gb == null) || ds.dispatch();
    }

    static boolean dispatchMotionEvent(long downTime, long eventTime, int action, float x, float y) {
        var ds = instance;
        if ((VERSION.SDK_INT < VERSION_CODES.O) || (ds == null)) return false;
        if ((x < 0f) || (y < 0f)) return true;

        final boolean anchored = nextDragAnchored;
        if (action == ACTION_DOWN) {
            nextDragAnchored = false;
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            return ds.handleSerializedDragEvent(action, x, y, anchored);
        } else {
            ds.mainHandler.post(() -> ds.handleSerializedDragEvent(action, x, y, anchored));
            return true;
        }
    }

    private float[] clampToDisplay(float x, float y) {
        float maxW = 8192f;
        float maxH = 8192f;
        try {
            DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            Display d = (dm != null) ? dm.getDisplay(DEFAULT_DISPLAY) : null;
            if (d != null) {
                Point realSize = new Point();
                d.getRealSize(realSize);
                if (realSize.x > 2 && realSize.y > 2) {
                    maxW = realSize.x;
                    maxH = realSize.y;
                }
            }
            PermataApplication app = PermataApplication.get();
            if (app != null && app.isMirroringMode()) {
                boolean land = app.isMirroringLandscape();
                if (land && maxW < maxH) {
                    float tmp = maxW;
                    maxW = maxH;
                    maxH = tmp;
                } else if (!land && maxW > maxH) {
                    float tmp = maxW;
                    maxW = maxH;
                    maxH = tmp;
                }
            }
        } catch (Exception ignored) {}
        return new float[]{
                Math.max(1f, Math.min(maxW - 2f, x)),
                Math.max(1f, Math.min(maxH - 2f, y))
        };
    }

    @RequiresApi(api = VERSION_CODES.O)
    private boolean handleSerializedDragEvent(int action, float rawX, float rawY, boolean anchored) {
        float[] pt = clampToDisplay(rawX, rawY);
        float x = pt[0];
        float y = pt[1];

        switch (action) {
            case ACTION_DOWN -> {
                if (activeDragStroke != null) {
                    forceLiftActiveDrag("PREEMPTED_BY_NEW_DOWN");
                }
                final long sessionId = ++dragSessionId;
                currentDragAnchored = anchored;

                lastDragX = x;
                lastDragY = y;
                hasPendingMove = false;
                pendingUp = false;
                isDragDispatching = true;

                long holdMs = currentDragAnchored ? DRAG_ANCHORED_HOLD_MS : DRAG_CENTER_HOLD_MS;
                Path p = new Path();
                p.moveTo(x, y);
                p.lineTo(x, y);
                StrokeDescription initial = new StrokeDescription(p, 0L, holdMs, true);
                activeDragStroke = initial;
                resetDragWatchdog();

                boolean ok = dispatchGesture(
                        new GestureDescription.Builder().addStroke(initial).build(),
                        new GestureResultCallback() {
                            @Override
                            public void onCompleted(GestureDescription gestureDescription) {
                                if (sessionId != dragSessionId) return;
                                isDragDispatching = false;
                                pumpNextDragSegment(sessionId);
                            }

                            @Override
                            public void onCancelled(GestureDescription gestureDescription) {
                                if (sessionId != dragSessionId) return;
                                Log.w("[MIRROR_FS][DRAG] Initial stroke cancelled | anchored=" + currentDragAnchored);
                                resetDragState();
                            }
                        },
                        mainHandler
                );
                if (!ok) {
                    resetDragState();
                }
                return ok;
            }
            case ACTION_MOVE -> {
                if (activeDragStroke == null && !isDragDispatching) return false;
                pendingMoveX = x;
                pendingMoveY = y;
                hasPendingMove = true;
                resetDragWatchdog();
                if (!isDragDispatching) {
                    pumpNextDragSegment(dragSessionId);
                }
                return true;
            }
            case ACTION_UP, ACTION_CANCEL -> {
                if (activeDragStroke == null && !isDragDispatching) return true;
                pendingMoveX = x;
                pendingMoveY = y;
                pendingUp = true;
                if (!isDragDispatching) {
                    pumpNextDragSegment(dragSessionId);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @RequiresApi(api = VERSION_CODES.O)
    private void pumpNextDragSegment(final long sessionId) {
        if (sessionId != dragSessionId || activeDragStroke == null || isDragDispatching) return;

        float dist = (float) Math.hypot(pendingMoveX - lastDragX, pendingMoveY - lastDragY);

        // Complete pending travel first so overlay windows and chat bubbles park cleanly at destination
        if (hasPendingMove && dist > 1.5f) {
            hasPendingMove = false;
            Path p = new Path();
            p.moveTo(lastDragX, lastDragY);
            p.lineTo(pendingMoveX, pendingMoveY);

            long segMs = currentDragAnchored
                    ? Math.max(50L, Math.min(DRAG_MAX_SEGMENT_MS, Math.round(dist * 0.32f)))
                    : DRAG_MIN_SEGMENT_MS;

            StrokeDescription next = activeDragStroke.continueStroke(p, 0L, segMs, true);
            lastDragX = pendingMoveX;
            lastDragY = pendingMoveY;
            activeDragStroke = next;
            isDragDispatching = true;
            resetDragWatchdog();

            boolean ok = dispatchGesture(
                    new GestureDescription.Builder().addStroke(next).build(),
                    new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            if (sessionId != dragSessionId) return;
                            isDragDispatching = false;
                            pumpNextDragSegment(sessionId);
                        }

                        @Override
                        public void onCancelled(GestureDescription gestureDescription) {
                            if (sessionId != dragSessionId) return;
                            Log.w("[MIRROR_FS][DRAG] Move stroke cancelled | anchored=" + currentDragAnchored);
                            resetDragState();
                        }
                    },
                    mainHandler
            );
            if (!ok) {
                resetDragState();
            }
            return;
        }

        if (pendingUp) {
            hasPendingMove = false;
            mainHandler.removeCallbacks(dragIdleWatchdog);
            Path p = new Path();
            p.moveTo(lastDragX, lastDragY);
            p.lineTo(pendingMoveX, pendingMoveY);

            long liftMs = currentDragAnchored ? DRAG_ANCHORED_SETTLE_LIFT_MS : DRAG_CENTER_LIFT_MS;
            StrokeDescription lift = activeDragStroke.continueStroke(p, 0L, liftMs, false);
            resetDragState();
            try {
                dispatchGesture(new GestureDescription.Builder().addStroke(lift).build(), null, null);
            } catch (Exception ignored) {}
        }
    }

    @RequiresApi(api = VERSION_CODES.O)
    private void forceLiftActiveDrag(String reason) {
        mainHandler.removeCallbacks(dragIdleWatchdog);
        StrokeDescription stroke = activeDragStroke;
        float x = lastDragX;
        float y = lastDragY;
        dragSessionId++;
        resetDragState();

        if (stroke != null) {
            Log.w("[MIRROR_FS][SAFE_FUSE] Ending active drag stroke | reason=" + reason);
            try {
                Path p = new Path();
                p.moveTo(x, y);
                p.lineTo(x, y);
                StrokeDescription lift = stroke.continueStroke(p, 0L, DRAG_CENTER_LIFT_MS, false);
                dispatchGesture(new GestureDescription.Builder().addStroke(lift).build(), null, null);
            } catch (Exception ignored) {}
        }
    }

    private void resetDragWatchdog() {
        mainHandler.removeCallbacks(dragIdleWatchdog);
        mainHandler.postDelayed(dragIdleWatchdog, DRAG_IDLE_TIMEOUT_MS);
    }

    private void resetDragState() {
        mainHandler.removeCallbacks(dragIdleWatchdog);
        activeDragStroke = null;
        currentDragAnchored = false;
        isDragDispatching = false;
        hasPendingMove = false;
        pendingUp = false;
    }

    @RequiresApi(api = VERSION_CODES.O)
    private boolean buildGesture(Pointer p, long downTime, long eventTime, int action, float x, float y, boolean completePrev) {
        switch (action) {
            case ACTION_DOWN -> {
                p.x = x;
                p.y = y;
                p.time = eventTime;
                path.reset();
                path.moveTo(x, y);
                var dur = Math.max(1L, Math.min(eventTime - downTime, getMaxGestureDuration()));
                p.sd = new StrokeDescription(path, 0L, dur, true);
                addStroke(p.sd);
            }
            case ACTION_UP -> {
                if (p.sd == null) return true;
                path.reset();
                path.moveTo(x, y);
                var dur = Math.max(1L, Math.min(eventTime - p.time, getMaxGestureDuration()));
                var sd = p.sd.continueStroke(path, 0L, dur, false);
                p.sd = null;
                addStroke(sd);
            }
            case ACTION_MOVE -> {
                path.reset();
                path.moveTo(p.x, p.y);
                path.lineTo(x, y);
                var dur = Math.max(1L, Math.min(eventTime - p.time, getMaxGestureDuration()));
                p.x = x;
                p.y = y;
                p.time = eventTime;
                StrokeDescription sd;
                if (p.sd == null) {
                    p.sd = sd = new StrokeDescription(path, 0L, dur, true);
                } else if (completePrev) {
                    sd = p.sd.continueStroke(path, 0L, dur, false);
                    p.sd = null;
                } else {
                    p.sd = sd = p.sd.continueStroke(path, 0L, dur, true);
                }
                addStroke(sd);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @RequiresApi(api = VERSION_CODES.N)
    private void addStroke(StrokeDescription sd) {
        if (gb == null) gb = new GestureDescription.Builder();
        gb.addStroke(sd);
    }

    @RequiresApi(api = VERSION_CODES.N)
    private boolean dispatch() {
        if (gb == null) return false;
        var g = gb.build();
        gb = null;
        return dispatchGesture(g, null, null);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            windowIds.clear();
            windowIds.addAll(listWindows());
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction("my.app.permata.ACTION_GLOBAL_BACK");
        filter.addAction("my.app.permata.ACTION_GLOBAL_HOME");
        filter.addAction("my.app.permata.ACTION_GLOBAL_RECENT");
        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(commandReceiver, filter);
        }
    }

    @Override
    public void onDestroy() {
        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            forceLiftActiveDrag("SERVICE_DESTROY");
        }
        super.onDestroy();
        instance = null;
        isConsentGranted = false;
        try {
            unregisterReceiver(commandReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        var type = event.getEventType();
        if ((VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) && (type == TYPE_WINDOWS_CHANGED)) {
            if (event.getDisplayId() != DEFAULT_DISPLAY) return;
            var change = event.getWindowChanges();
            if (change == WINDOWS_CHANGE_ADDED) windowIds.add(event.getWindowId());
            else if (change == WINDOWS_CHANGE_REMOVED) windowIds.remove(event.getWindowId());
            return;
        }

        if (type != TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence pkg = event.getPackageName();
        String pkgName = (pkg != null) ? pkg.toString() : null;
        CharSequence cls = event.getClassName();
        String className = (cls != null) ? cls.toString() : null;

        if (!isTransientWindow(className) && !isIgnoredOverlay(pkgName)
                && !isCurrentImePackage(pkgName) && !isMirroringHostWindow(pkgName, className)) {
            lastActivePackage = pkgName;

            if (isStubbornNativeApp(pkgName) && isCarSessionActive()) {
                Log.w("[WATCHDOG_TELEMETRY] Stubborn App Detected: " + pkgName + ". Firing Aggressive Reclaim!");
                Intent reclaimIntent = new Intent(this, my.app.permata.media.service.PermataMediaService.class);
                reclaimIntent.setAction("my.app.permata.action.AGGRESSIVE_RECLAIM");
                try {
                    startService(reclaimIntent);
                } catch (Exception ignored) {}
            }
        }

        if (isConsentGranted && clickOnButton == null) return;
        if (pkgName == null) return;

        var root = getRootInActiveWindow();
        if (root == null) return;

        try {
            if (clickOnButton != null) {
                var btn = root.findAccessibilityNodeInfosByText(clickOnButton);
                if (btn.isEmpty()) {
                    btn = root.findAccessibilityNodeInfosByViewId("android:id/button1");
                }
                if (!btn.isEmpty()) {
                    performClickNode(btn.get(0));
                    clickOnButton = null;
                    return;
                }
            }
            checkAndClickMediaProjectionConsent(root, pkgName);
        } finally {
            root.recycle();
        }
    }

    private static final String[] IGNORE_PKG_KEYWORDS = {
            "gearhead", "permissioncontroller", "packageinstaller",
            "googlequicksearchbox", "bixby", "keymapper",
            "systemui", "inputmethod", "launcher", "gboard", "swiftkey", "honeyboard",
            "biometrics", "keyguard", "lockscreen", "faceid", "fingerprint",
            "alwaysondisplay", "aod", "overlay", "screenfilter", "bluelight",
            "twilight", "lux", "smartface", "hidenotch",
            "rotationcontrol", "snowlife01",
            "edgepanel", "edge", "sidegesture", "gestural", "threebutton",
            "whatsapp", "telegram", "messenger", "discord", "wechat", "line",
            "viber", "sms", "mms", "messaging", "dialer", "incallui", "truecaller",
            "com.google.android.gm", "gmail", "email", "outlook", "mail", "calendar", "reminder",
            "tasks", "todo", "keep", "alarm", "clock", "notes",
            "miui", "xiaomi", "coloros", "oplus", "nexuslauncher", "trebuchet"
    };

    private boolean isMirroringHostWindow(@Nullable String pkgName, @Nullable String className) {
        if (pkgName == null || !pkgName.equals(getPackageName())) return false;
        if (!PermataApplication.get().isMirroringMode()) return false;
        if (className == null) return true;
        return !className.contains("MainCarActivity")
                && !className.contains("LauncherActivity")
                && !className.contains("MainActivity");
    }

    private static boolean isTransientWindow(@Nullable String className) {
        if (className == null) return false;
        return className.contains("Toast")
                || className.contains("PopupWindow")
                || className.contains("VolumeDialog");
    }

    private boolean isCurrentImePackage(@Nullable String pkgName) {
        if (pkgName == null) return false;
        try {
            String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null) {
                int slash = ime.indexOf('/');
                String imePkg = (slash > 0) ? ime.substring(0, slash) : ime;
                return pkgName.equals(imePkg);
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isIgnoredOverlay(String pkgName) {
        if (pkgName == null || pkgName.equals("android")) return true;
        String lower = pkgName.toLowerCase();
        for (String keyword : IGNORE_PKG_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    private static final Set<String> STUBBORN_APPS = new HashSet<>(Arrays.asList(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.instagram.android"
    ));

    private boolean isStubbornNativeApp(String pkg) {
        return STUBBORN_APPS.contains(pkg);
    }

    private boolean isCarSessionActive() {
        if (PermataApplication.get().isConnectedToAuto() || PermataApplication.get().isMirroringMode()) {
            lastActiveBtTime = System.currentTimeMillis();
            return true;
        }

        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null && (am.isBluetoothA2dpOn() || am.isBluetoothScoOn())) {
                lastActiveBtTime = System.currentTimeMillis();
                return true;
            }
        } catch (Exception ignored) {}

        if (System.currentTimeMillis() - lastActiveBtTime < 30000) {
            return true;
        }

        return false;
    }

    private void checkAndClickMediaProjectionConsent(AccessibilityNodeInfo root, String pkgName) {
        if (isConsentGranted) return;

        if ("com.android.systemui".equals(pkgName) || "android".equals(pkgName) || pkgName.contains("systemui")) {
            String[] targetTexts = new String[]{
                    "Start now", "Start recording", "Allow", "Start",
                    "立即开始", "开始录制", "开始", "允许",
                    "立即開始", "開始錄製", "開始", "允許",
                    "Mulai sekarang", "Izinkan",
                    "Mula sekarang", "Benarkan",
                    "เริ่มเลย", "อนุญาต", "เริ่ม",
                    "Bắt đầu ngay", "Cho phép", "Bắt đầu",
                    "Magsimula na", "Simulan ngayon", "Payagan",
                    "अभी शुरू करें", "अनुमति दें", "शुरू करें",
                    "இப்போதே தொடங்கு", "அனுமதி",
                    "البدء الآن", "ابدأ الآن", "السماح", "بدء",
                    "今すぐ開始", "許可", "開始",
                    "지금 시작", "허용", "시작",
                    "Iniciar ahora", "Iniciar grabación", "Permitir",
                    "Commencer", "Démarrer", "Autoriser",
                    "Jetzt starten", "Starten", "Zulassen",
                    "Avvia ora", "Consenti", "Iniciar agora", "Permitir",
                    "Начать", "Разрешить", "Начать запись",
                    "Şimdi başlat", "İzin ver",
                    "Nu starten", "Toestaan", "Rozpocznij teraz", "Zezwól"
            };

            for (String text : targetTexts) {
                var nodes = root.findAccessibilityNodeInfosByText(text);
                if (nodes != null && !nodes.isEmpty()) {
                    for (var node : nodes) {
                        if (node != null && performClickNode(node)) {
                            isConsentGranted = true;
                            return;
                        }
                    }
                }
            }

            var btn1 = root.findAccessibilityNodeInfosByViewId("android:id/button1");
            if (btn1 != null && !btn1.isEmpty()) {
                for (var node : btn1) {
                    if (node != null && performClickNode(node)) {
                        isConsentGranted = true;
                        return;
                    }
                }
            }
        }
    }

    private boolean performClickNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isClickable()) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        AccessibilityNodeInfo parent = node.getParent();
        while (parent != null) {
            if (parent.isClickable()) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
            parent = parent.getParent();
        }
        return false;
    }

    @Override
    public void onInterrupt() {
        if (VERSION.SDK_INT >= VERSION_CODES.O) {
            forceLiftActiveDrag("SERVICE_INTERRUPT");
        }
        if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            windowIds.clear();
        }
    }

    private List<Integer> listWindows() {
        var windows = getWindows();
        var ids = new ArrayList<Integer>(windows.size());
        for (var w : getWindows()) {
            if ((VERSION.SDK_INT >= VERSION_CODES.R) && (w.getDisplayId() != DEFAULT_DISPLAY)) continue;
            var type = w.getType();
            if ((type == TYPE_APPLICATION) || (type == TYPE_INPUT_METHOD)) ids.add(w.getId());
        }
        return ids;
    }

    private static final class Pointer {
        float x;
        float y;
        long time;
        StrokeDescription sd;
    }
}