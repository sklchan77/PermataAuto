package my.app.permata.addon.web;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;

import my.app.permata.media.service.PermataMediaService;
import my.app.utils.log.Log;

/**
 * Javascript Bridge for WebView Media interactions.
 * Enterprise Hardened: WeakReferences prevent Chromium engine memory leaks.
 */
public class UniversalMediaBridge {
    private static final String TAG = "UniversalMediaBridge";

    private final WeakReference<Context> contextRef;
    private final WeakReference<WebView> webViewRef;
    private final Handler mainHandler;
    
    // Anti-Ping-Pong State Tracking for Feed Auto-Play
    private final Runnable focusReleaseRunnable = this::doReleaseAudioFocus;

    public UniversalMediaBridge(@NonNull Context context, @NonNull WebView webView) {
        this.contextRef = new WeakReference<>(context);
        this.webViewRef = new WeakReference<>(webView);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @JavascriptInterface
    public void onMediaPlay() {
        Log.i("[JavaBridge]", TAG + ": Universal HTML5 Media PLAY detected.");
        // 1. Cancel any pending focus release immediately (user is scrolling feeds)
        mainHandler.removeCallbacks(focusReleaseRunnable);
        // 2. Delegate to service to dynamically lock steering controls
        mainHandler.post(this::stealAudioFocus);
    }

    @JavascriptInterface
    public void onMediaPause() {
        Log.i("[JavaBridge]", TAG + ": Universal HTML5 Media PAUSE detected.");
        // Delay the release by 5000ms. If another video plays during a swipe, 
        // the pending release is cancelled, keeping focus perfectly stable.
        mainHandler.removeCallbacks(focusReleaseRunnable);
        mainHandler.postDelayed(focusReleaseRunnable, 5000);
    }

    /**
     * Notifies the PermataMediaService that web media is playing.
     * HARDENED: Uses Explicit Component Intent to guarantee delivery to PermataMediaService,
     * bypassing AndroidManifest implicit routing limitations.
     */
    private void stealAudioFocus() {
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

    /**
     * Notifies the PermataMediaService that web media has paused (after 5000ms delay).
     * HARDENED: Uses Explicit Component Intent to guarantee delivery.
     */
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