package my.app.permata.addon.web;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;

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
    private AudioFocusRequest audioFocusRequest;
    
    // Anti-Ping-Pong State Tracking for Feed Auto-Play
    private boolean isFocusHeld = false;
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
        // 2. Safely request focus only if we don't already have it secured
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
     * The direct AudioManager focus steal has been replaced by Service delegation.
     */
    private void stealAudioFocus() {
        if (isFocusHeld) {
            return; // We already securely hold the state.
        }

        Context context = contextRef.get();
        if (context == null) return;

        Log.i("[JavaBridge]", TAG + ": Delegating Play State to PermataMediaService.");

        // =========================================================================
        // [NEW CODE] - Delegate to PermataMediaService as Single Source of Truth
        // =========================================================================
        try {
            Intent intent = new Intent("my.app.permata.action.WEB_MEDIA_PLAYING");
            intent.setPackage(context.getPackageName());
            // Optionally, explicit component targeting:
            // intent.setClassName(context, "my.app.permata.media.service.PermataMediaService");
            context.startService(intent);
            isFocusHeld = true;
        } catch (Exception e) {
            Log.e(e, "[JavaBridge]", TAG + ": Failed to delegate PLAY state.");
        }

        // =========================================================================
        // [OLD CODE PRESERVED FOR REVERT] - Direct Hardware Focus Steal
        // =========================================================================
        /*
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        AudioManager.OnAudioFocusChangeListener focusListener = focusChange -> {
            Log.i("[JavaBridge]", TAG + ": WebMedia AudioFocus state changed to: " + focusChange);
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                isFocusHeld = false;
            }
        };

        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build();

            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build();

            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            @SuppressWarnings("deprecation")
            int reqResult = audioManager.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            );
            result = reqResult;
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            isFocusHeld = true;
        }
        */
        // =========================================================================
    }

    /**
     * Notifies the PermataMediaService that web media has paused (after 5000ms delay).
     * The direct AudioManager abandon command has been replaced by Service delegation.
     */
    private void doReleaseAudioFocus() {
        Context context = contextRef.get();
        if (context == null) return;

        Log.i("[JavaBridge]", TAG + ": Delegating Pause State to PermataMediaService.");

        // =========================================================================
        // [NEW CODE] - Delegate to PermataMediaService as Single Source of Truth
        // =========================================================================
        try {
            Intent intent = new Intent("my.app.permata.action.WEB_MEDIA_PAUSED");
            intent.setPackage(context.getPackageName());
            context.startService(intent);
        } catch (Exception e) {
            Log.e(e, "[JavaBridge]", TAG + ": Failed to delegate PAUSE state.");
        }
        
        isFocusHeld = false;

        // =========================================================================
        // [OLD CODE PRESERVED FOR REVERT] - Direct Hardware Focus Abandon
        // =========================================================================
        /*
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
        } else {
            @SuppressWarnings("deprecation")
            int result = audioManager.abandonAudioFocus(focusChange -> {});
        }
        */
        // =========================================================================
    }
}