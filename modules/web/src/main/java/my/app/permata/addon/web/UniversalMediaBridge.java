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
    
    private final AtomicLong lastBridgePlayTime = new AtomicLong(0);

    private final Runnable focusReleaseRunnable = this::doReleaseAudioFocus;

    public UniversalMediaBridge(@NonNull Context context, @NonNull WebView webView) {
        this.contextRef = new WeakReference<>(context);
        this.webViewRef = new WeakReference<>(webView);
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Inject the 10ms micro-seek payload to fix audio latency/drift on car head units
        injectAudioSyncScript(webView);
    }

    private void injectAudioSyncScript(WebView webView) {
        if (webView == null) return;
        
        String microSeekPayload = "(function() {" +
            "  if (window.__permataAudioSyncInjected) return;" +
            "  window.__permataAudioSyncInjected = true;" +
            "  document.addEventListener('playing', function(e) {" +
            "      const media = e.target;" +
            "      if (media && (media.tagName === 'VIDEO' || media.tagName === 'AUDIO')) {" +
            "          setTimeout(() => {" +
            "              if (!media.paused && media.readyState >= 2) {" +
            "                  media.currentTime = media.currentTime + 0.01;" +
            "                  console.log('[JavaBridge] Audio pipeline flushed and re-synced via 10ms micro-seek.');" +
            "              }" +
            "          }, 250);" +
            "      }" +
            "  }, true);" +
            "})();";
            
        mainHandler.post(() -> {
            try {
                webView.evaluateJavascript(microSeekPayload, null);
            } catch (Exception e) {
                Log.w(TAG, "Failed to inject audio sync script.");
            }
        });
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
        
        // [FIFO SURGERY] Web Browser takes the Crown
        my.app.permata.PermataApplication.setMediaCrown(my.app.permata.PermataApplication.CROWN_WEB);
        
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
            
            // [SURGICAL FIX] Force Permata to reclaim the MediaSession from Chromium's WebView.
            // This bypasses the "null package" block in the main service, guaranteeing 
            // the steering wheel buttons route to Permata while Douyin/YouTube is playing.
            Intent reclaimIntent = new Intent(context, PermataMediaService.class);
            reclaimIntent.setAction(PermataMediaService.ACTION_RECLAIM_SESSION);
            context.startService(reclaimIntent);
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
            
            // [SURGICAL FIX] Force the silent anchor to re-ignite to keep AVRCP alive.
            // This bypasses the "null package" block in the main service, guaranteeing 
            // the Bluetooth steering wheel link doesn't die when Web Media pauses.
            Intent hijackIntent = new Intent(context, PermataMediaService.class);
            hijackIntent.setAction(PermataMediaService.ACTION_HIJACK_FOCUS);
            context.startService(hijackIntent);
        } catch (Exception e) {
            Log.e(e, "[JavaBridge]", TAG + ": Failed to delegate PAUSE state.");
        }
    }
}