package my.app.permata.action;

import static android.os.SystemClock.uptimeMillis;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_MULTIPLE;
import static android.view.KeyEvent.ACTION_UP;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import my.app.permata.PermataApplication;
import my.app.permata.media.service.MediaSessionCallback;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.function.IntObjectFunction;
import my.app.utils.log.Log;
import my.app.utils.ui.fragment.ActivityFragment;

public class KeyEventHandler {

    private static final int DBL_CLICK_INTERVAL = 500;
    private static final int LONG_CLICK_INTERVAL = 1000;

    private static volatile Worker worker;
    private static volatile long lastGlobalActionTime = 0L;
    
    private static final Map<Class<?>, Method> webViewMethodCache = new ConcurrentHashMap<>();

    // Universal JS payload to scroll any active WebView
    private static final String JS_UNIVERSAL_PAYLOAD = "(function(){" +
            "  try {" +
            "    var amount = window.innerHeight * 0.85;" +
            "    window.scrollBy({ top: amount, behavior: 'smooth' });" +
            "  } catch (err) {}" +
            "})();";
            
    private static final String JS_UNIVERSAL_PAYLOAD_UP = "(function(){" +
            "  try {" +
            "    var amount = -window.innerHeight * 0.85;" +
            "    window.scrollBy({ top: amount, behavior: 'smooth' });" +
            "  } catch (err) {}" +
            "})();";

    public static boolean handleKeyEvent(MediaSessionCallback cb, KeyEvent event,
                                             IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
        return handleKeyEvent(cb, null, event, defaultHandler);
    }

    public static boolean handleKeyEvent(MainActivityDelegate activity, KeyEvent event,
                                             IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
        return handleKeyEvent(activity.getMediaSessionCallback(), activity, event, defaultHandler);
    }

    private static boolean handleKeyEvent(MediaSessionCallback cb,
                                                @Nullable MainActivityDelegate activity, KeyEvent event,
                                                IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
        
        if (event.isCanceled()) {
            worker = null;
            return defaultHandler.apply(event.getKeyCode(), event);
        }

        if (worker != null) {
            if (worker.handle(event)) return true;
            worker = null;
            return false;
        }

        var code = event.getKeyCode();

        MainActivityDelegate targetActivity = activity;
        if (targetActivity == null && cb != null) {
            if (cb.getAssistant() instanceof MainActivityDelegate) {
                targetActivity = (MainActivityDelegate) cb.getAssistant();
            }
        }

        // SURVIVAL FETCH: Ensure we always have a live UI reference even after a RECENT kill
        boolean isUiDead = false;
        if (targetActivity == null || targetActivity.getAppActivity() == null) {
            isUiDead = true;
        } else if (targetActivity.getAppActivity() instanceof Activity) {
            isUiDead = ((Activity) targetActivity.getAppActivity()).isDestroyed();
        }

        if (isUiDead) {
            if (my.app.permata.auto.MainCarActivity.activeInstance != null) {
                MainActivityDelegate carDelegate = my.app.permata.auto.MainCarActivity.activeInstance.getActivityDelegate().peek();
                if (carDelegate != null) targetActivity = carDelegate;
            }
            if (targetActivity == null && my.app.permata.ui.activity.MainActivity.getActiveInstance() != null) {
                MainActivityDelegate phoneDelegate = my.app.permata.ui.activity.MainActivity.getActiveInstance().getActivityDelegate().peek();
                if (phoneDelegate != null) targetActivity = phoneDelegate;
            }
        }

        final MainActivityDelegate finalTargetActivity = targetActivity;

        if (event.getAction() == ACTION_DOWN) {
            if (code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                
                long currentUptime = android.os.SystemClock.uptimeMillis();
                if (currentUptime - lastGlobalActionTime < 250) {
                    return true; // Prevent rapid-fire double inputs
                }
                lastGlobalActionTime = currentUptime;
                boolean isNext = (code == KeyEvent.KEYCODE_MEDIA_NEXT);

                Log.i("[ROUTING_TELEMETRY]", "Executing Bulletproof Catch-All Steering Input: Mirroring + Web + Media");

                // 1. BLIND BROADCAST TO MIRRORING DISPLAY (Executes seamlessly if active)
                try {
                    if (PermataApplication.get().isMirroringMode()) {
                        my.app.permata.auto.MirrorDisplay md = my.app.permata.auto.MirrorDisplay.get();
                        if (md != null) {
                            int width = 1000, height = 1000;
                            if (finalTargetActivity != null && finalTargetActivity.getResources() != null) {
                                android.util.DisplayMetrics metrics = finalTargetActivity.getResources().getDisplayMetrics();
                                width = metrics.widthPixels;
                                height = metrics.heightPixels;
                            }
                            float actionX = width * 0.50f;
                            float centerY = height * 0.50f;
                            float span = height * 0.30f;
                            float yStart = (!isNext) ? (centerY - span / 2f) : (centerY + span / 2f);
                            float yEnd = (!isNext) ? (centerY + span / 2f) : (centerY - span / 2f);
                            
                            long now = android.os.SystemClock.uptimeMillis();
                            md.motionEvent(now, now, MotionEvent.ACTION_DOWN, actionX, yStart);
                            md.motionEvent(now, now + 50, MotionEvent.ACTION_MOVE, actionX, centerY);
                            md.motionEvent(now, now + 100, MotionEvent.ACTION_UP, actionX, yEnd);
                        }
                    }
                } catch (Exception ignored) {}

                // 2. BLIND BROADCAST TO WEB BROWSER (Executes seamlessly if any WebView is alive)
                if (finalTargetActivity != null) {
                    finalTargetActivity.post(() -> {
                        try {
                            WebView resolvedWebView = null;
                            ActivityFragment frag = finalTargetActivity.getActiveFragment();
                            if (frag != null) {
                                resolvedWebView = scanFragmentsForWebView(frag);
                            }
                            if (resolvedWebView == null) {
                                resolvedWebView = findTopVisibleWebView(finalTargetActivity);
                            }
                            if (resolvedWebView != null && resolvedWebView.isShown() && resolvedWebView.getSettings().getJavaScriptEnabled()) {
                                resolvedWebView.evaluateJavascript(isNext ? JS_UNIVERSAL_PAYLOAD : JS_UNIVERSAL_PAYLOAD_UP, value -> {});
                            }
                        } catch (Exception ignored) {}
                    });
                }

                // 3. BLIND BROADCAST TO MEDIA PLAYER (Always yields to ExoPlayer / VLC)
                return defaultHandler.apply(code, event);
            }
        }

        var k = Key.get(code);
        if (k == null) return defaultHandler.apply(code, event);

        if (!k.isMedia() && (finalTargetActivity != null) && (finalTargetActivity.getCurrentFocus() instanceof EditText)) {
            return defaultHandler.apply(code, event);
        }

        var dblClickAction = k.getDblClickAction();
        if (dblClickAction == null) return defaultHandler.apply(code, event);

        var action = event.getAction();
        if (action == ACTION_MULTIPLE) {
            performAction(dblClickAction, cb, finalTargetActivity, uptimeMillis());
            return true;
        }
        
        if (action != ACTION_DOWN) return defaultHandler.apply(code, event);

        var clickAction = k.getClickAction();
        if (clickAction == null) return defaultHandler.apply(code, event);
        
        var longClickAction = k.getLongClickAction();
        if (longClickAction == null) return defaultHandler.apply(code, event);

        if (((clickAction == dblClickAction) && (clickAction == longClickAction)) ||
                ((dblClickAction == Action.NONE) && (longClickAction == Action.NONE))) {
            performAction(clickAction, cb, finalTargetActivity, uptimeMillis());
            return true;
        }

        worker = new Worker(cb, finalTargetActivity, k, clickAction, dblClickAction, longClickAction);
        return true;
    }

    private static WebView scanFragmentsForWebView(ActivityFragment activeFragment) {
        try {
            Class<?> fragClass = activeFragment.getClass();
            Method getWebViewMethod = webViewMethodCache.get(fragClass);
            
            if (getWebViewMethod == null) {
                getWebViewMethod = fragClass.getMethod("getWebView");
                webViewMethodCache.put(fragClass, getWebViewMethod);
            }
            
            Object result = getWebViewMethod.invoke(activeFragment);
            if (result instanceof WebView) return (WebView) result;
        } catch (Exception e) {}
        return null;
    }

    private static WebView findTopVisibleWebView(MainActivityDelegate activity) {
        if (activity == null || activity.getWindow() == null) return null;
        View decorView = activity.getWindow().getDecorView();
        return findWebViewRecursively(decorView);
    }

    private static WebView findWebViewRecursively(View view) {
        if (view instanceof WebView && view.isShown() && view.getVisibility() == View.VISIBLE && view.getWidth() > 0) {
            if (view.getAlpha() > 0.1f) {
                return (WebView) view;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = vg.getChildCount() - 1; i >= 0; i--) { 
                WebView res = findWebViewRecursively(vg.getChildAt(i));
                if (res != null) return res;
            }
        }
        return null;
    }

    private static void performAction(Action action, MediaSessionCallback cb,
                                        @Nullable MainActivityDelegate activity, long timestamp) {
        worker = null;
        action.getHandler().handle(cb, activity, timestamp);
    }

    private static final class Worker implements Runnable {
        private final MediaSessionCallback cb;
        private final java.lang.ref.WeakReference<MainActivityDelegate> activityRef;
        private final Key key;
        private final Action clickAction;
        private final Action dblClickAction;
        private final Action longClickAction;
        private final long time;
        private long longClickTime;
        private boolean up;

        Worker(MediaSessionCallback cb, @Nullable MainActivityDelegate activity, Key key,
                     Action clickAction, Action dblClickAction, Action longClickAction) {
            this.cb = cb;
            this.activityRef = new java.lang.ref.WeakReference<>(activity);
            this.key = key;
            this.clickAction = clickAction;
            this.dblClickAction = dblClickAction;
            this.longClickAction = longClickAction;
            time = longClickTime = uptimeMillis();
            sched(DBL_CLICK_INTERVAL);
        }

        @Override
        public void run() {
            if (worker != this) return;
            if (up) {
                handle(clickAction);
                return;
            }

            long now = uptimeMillis();
            long diff = now - longClickTime;

            if (diff < LONG_CLICK_INTERVAL) {
                sched(LONG_CLICK_INTERVAL - diff);
            } else if (diff > 15000) { 
                worker = null;
            } else {
                longClickTime = time;
                handle(longClickAction);
                worker = this;
                sched(LONG_CLICK_INTERVAL);
            }
        }

        boolean handle(KeyEvent e) {
            if (e.getKeyCode() != key.getCode()) return false;

            switch (e.getAction()) {
                case ACTION_DOWN -> {
                    if (!up) {
                        if ((longClickAction == clickAction) || (longClickAction == Action.NONE)) {
                            handle(clickAction);
                        }
                    }
                    return true;
                }
                case ACTION_UP -> {
                    long holdTime = uptimeMillis() - time;

                    if (holdTime <= DBL_CLICK_INTERVAL) {
                        if (up) {
                            handle(dblClickAction);
                        } else if (dblClickAction == clickAction) {
                            handle(clickAction);
                        } else {
                            up = true;
                        }
                    } else if (holdTime >= LONG_CLICK_INTERVAL) {
                        worker = null;
                    } else {
                        worker = null;
                        if (longClickTime == time) {
                            handle(clickAction);
                        }
                    }
                    return true;
                }
                case ACTION_MULTIPLE -> {
                    handle(dblClickAction);
                    return true;
                }
            }
            return false;
        }

        private void handle(Action action) {
            MainActivityDelegate activity = activityRef.get();
            performAction(action, cb, activity, time);
        }

        private void sched(long delay) {
            MainActivityDelegate activity = activityRef.get();
            var handler = (activity == null) ? cb.getHandler() : activity.getHandler();
            handler.postDelayed(this, delay);
        }
    }
}