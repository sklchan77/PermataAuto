package my.app.permata.addon.web;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;

import my.app.permata.media.service.PermataMediaService;
import my.app.utils.log.Log;

public class UniversalMediaBridge {
    private static final String TAG = "UniversalMediaBridge";

    private final WeakReference<Context> contextRef;
    private final WeakReference<WebView> webViewRef;
    private final Handler mainHandler;
    
    // SURGICAL FIX: Atomic debounce throttle to eliminate endless audio focus ping-ponging
    private final AtomicLong lastBridgePlayTime = new AtomicLong(0);

    private final Runnable focusReleaseRunnable = this::doReleaseAudioFocus;

    public UniversalMediaBridge(@NonNull Context context, @NonNull WebView webView) {
        this.contextRef = new WeakReference<>(context);
        this.webViewRef = new WeakReference<>(webView);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @JavascriptInterface
    public void onMediaPlay() {
        long now = SystemClock.uptimeMillis();
        if (now - lastBridgePlayTime.get() < 2000) {
            return;
        }
        lastBridgePlayTime.set(now);

        Log.i("[JavaBridge]", TAG + ": Universal HTML5 Media PLAY detected.");
        mainHandler.removeCallbacks(focusReleaseRunnable);
        mainHandler.post(this::stealAudioFocus);
    }

    @JavascriptInterface
    public void onMediaPause() {
        Log.i("[JavaBridge]", TAG + ": Universal HTML5 Media PAUSE detected.");
        mainHandler.removeCallbacks(focusReleaseRunnable);
        mainHandler.postDelayed(focusReleaseRunnable, 5000);
    }

    private void stealAudioFocus() {
        mainHandler.removeCallbacks(focusReleaseRunnable);

        Context context = contextRef.get();
        if (context == null) return;

        Log.i("[JavaBridge]", TAG + ": Delegating Play State explicitly to PermataMediaService.");

        try {
            Intent intent = new Intent(context, PermataMediaService.class);
            intent.setAction(PermataMediaService.ACTION_WEB_MEDIA_PLAYING);
            context.startService(intent);
        } catch (Exception e) {
            Log.e(e, "[JavaBridge]", TAG + ": Failed to delegate PLAY state.");
        }
    }

    private void doReleaseAudioFocus() {
        Context context = contextRef.get();
        if (context == null) return;

        Log.i("[JavaBridge]", TAG + ": Delegating Pause State explicitly to PermataMediaService.");

        try {
            Intent intent = new Intent(context, PermataMediaService.class);
            intent.setAction(PermataMediaService.ACTION_WEB_MEDIA_PAUSED);
            context.startService(intent);
        } catch (Exception e) {
            Log.e(e, "[JavaBridge]", TAG + ": Failed to delegate PAUSE state.");
        }
    }
}