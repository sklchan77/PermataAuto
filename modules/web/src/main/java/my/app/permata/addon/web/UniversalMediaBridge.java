package my.app.permata.addon.web;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;

import my.app.permata.PermataApplication;
import my.app.permata.media.service.PermataMediaService;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.log.Log;
import my.app.utils.ui.fragment.ActivityFragment;

public class UniversalMediaBridge {
    private static final String TAG = "UniversalMediaBridge";

    private static final String PAUSE_HIDDEN_MEDIA_JS =
            "(function() { " +
            "  try { " +
            "    document.querySelectorAll('video, audio').forEach(function(m) { " +
            "      if (!m.paused) m.pause(); " +
            "    }); " +
            "  } catch(e) {} " +
            "})();";

    private final WeakReference<Context> contextRef;
    private final WeakReference<WebView> webViewRef;
    private final Handler mainHandler;

    private final AtomicLong lastBridgePlayTime = new AtomicLong(0L);

    private final Runnable handlePlayRunnable = this::handleMediaPlayOnMain;
    private final Runnable handlePauseRunnable = this::handleMediaPauseOnMain;
    private final Runnable focusReleaseRunnable = this::doReleaseAudioFocus;

    public UniversalMediaBridge(@NonNull Context context, @NonNull WebView webView) {
        this.contextRef = new WeakReference<>(context);
        this.webViewRef = new WeakReference<>(webView);
        this.mainHandler = new Handler(Looper.getMainLooper());

        // Inject the 10ms micro-seek payload to fix audio latency/drift on car head units
        injectAudioSyncScript(webView);
    }

    private void injectAudioSyncScript(WebView webView) {
        if (webView == null || webView.getClass() != PermataWebView.class) return;

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

    private boolean isActiveWebBrowserView(@Nullable WebView wv) {
        if (wv == null || wv.getClass() != PermataWebView.class) return false;
        if (!wv.isAttachedToWindow() || !wv.isShown()) return false;

        try {
            MainActivityDelegate delegate = MainActivityDelegate.getActivityDelegate(wv.getContext()).peek();
            if (delegate != null) {
                ActivityFragment active = delegate.getActiveFragment();
                if (active == null || active.getClass() != WebBrowserFragment.class) {
                    return false;
                }
            }
        } catch (Exception ignored) {
            // Fall back to view visibility boundary if delegate lookup is unavailable
        }

        return true;
    }

    @JavascriptInterface
    public void onMediaPlay() {
        mainHandler.removeCallbacks(handlePlayRunnable);
        mainHandler.post(handlePlayRunnable);
    }

    @JavascriptInterface
    public void onMediaPause() {
        mainHandler.removeCallbacks(handlePauseRunnable);
        mainHandler.post(handlePauseRunnable);
    }

    private void handleMediaPlayOnMain() {
        WebView wv = webViewRef.get();
        if (!isActiveWebBrowserView(wv)) {
            mainHandler.removeCallbacks(focusReleaseRunnable);
            if (wv != null && wv.isAttachedToWindow() && wv.getClass() == PermataWebView.class) {
                try {
                    wv.evaluateJavascript(PAUSE_HIDDEN_MEDIA_JS, null);
                } catch (Exception ignored) {}
            }
            return;
        }

        long now = SystemClock.uptimeMillis();
        if ((now - lastBridgePlayTime.get() < 2000L)
                && (PermataApplication.getMediaCrown() == PermataApplication.CROWN_WEB)) {
            return;
        }
        lastBridgePlayTime.set(now);

        Log.i("[JavaBridge]", TAG + ": Universal HTML5 Media PLAY detected.");
        mainHandler.removeCallbacks(focusReleaseRunnable);

        // [FIFO SURGERY] Web Browser takes the Crown
        PermataApplication.setMediaCrown(PermataApplication.CROWN_WEB);

        stealAudioFocus();
    }

    private void handleMediaPauseOnMain() {
        WebView wv = webViewRef.get();
        if (!isActiveWebBrowserView(wv)
                || (PermataApplication.getMediaCrown() != PermataApplication.CROWN_WEB)) {
            mainHandler.removeCallbacks(focusReleaseRunnable);
            return;
        }

        Log.i("[JavaBridge]", TAG + ": Universal HTML5 Media PAUSE detected.");
        mainHandler.removeCallbacks(focusReleaseRunnable);
        mainHandler.postDelayed(focusReleaseRunnable, 5000L);
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
            // the steering wheel buttons route to Permata while WebBrowser media is playing.
            Intent reclaimIntent = new Intent(context, PermataMediaService.class);
            reclaimIntent.setAction(PermataMediaService.ACTION_RECLAIM_SESSION);
            context.startService(reclaimIntent);
        } catch (Exception e) {
            Log.e(e, "[JavaBridge]", TAG + ": Failed to delegate PLAY state.");
        }
    }

    private void doReleaseAudioFocus() {
        WebView wv = webViewRef.get();
        if (!isActiveWebBrowserView(wv)
                || (PermataApplication.getMediaCrown() != PermataApplication.CROWN_WEB)) {
            return;
        }

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