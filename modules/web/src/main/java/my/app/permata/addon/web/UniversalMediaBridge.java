package my.app.permata.addon.web;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;

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
    private final Handler mainHandler;
    private AudioFocusRequest audioFocusRequest;

    // Restored to match WebBrowserFragment's instantiation: new UniversalMediaBridge(ctx)
    public UniversalMediaBridge(@NonNull Context context) {
        this.contextRef = new WeakReference<>(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @JavascriptInterface
    public void onMediaPlay() {
        Log.i("[JavaBridge]", TAG + ": Universal HTML5 Media PLAY detected.");

        mainHandler.post(() -> {
            // IF YOU HAD ORIGINAL CODE HERE (like EventBus.getDefault().post(...) or sendBroadcast), 
            // PASTE IT BACK HERE. Otherwise, the audio focus steal below handles the background pausing natively.
            
            stealAudioFocus();
        });
    }

    @JavascriptInterface
    public void onMediaPause() {
        Log.i("[JavaBridge]", TAG + ": Universal HTML5 Media PAUSE detected.");

        mainHandler.post(() -> {
            // IF YOU HAD ORIGINAL CODE HERE, PASTE IT BACK HERE.
            
            releaseAudioFocus();
        });
    }

    /**
     * Aggressively requests Audio Focus for the WebView. 
     * This forces the Android OS to natively send an AUDIOFOCUS_LOSS (-1) event to the background ExoPlayer/IPTV.
     */
    private void stealAudioFocus() {
        Context context = contextRef.get();
        if (context == null) return;

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        Log.i("[JavaBridge]", TAG + ": Requesting Audio Focus for WebMedia to pause background players.");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build();

            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(focusChange -> {
                        Log.i("[JavaBridge]", TAG + ": WebMedia AudioFocus state changed to: " + focusChange);
                    })
                    .build();

            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            @SuppressWarnings("deprecation")
            int result = audioManager.requestAudioFocus(
                    focusChange -> Log.i("[JavaBridge]", TAG + ": WebMedia AudioFocus state changed to: " + focusChange),
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            );
        }
    }

    /**
     * Abandons the Audio Focus request so background services can resume if requested.
     */
    private void releaseAudioFocus() {
        Context context = contextRef.get();
        if (context == null) return;

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        Log.i("[JavaBridge]", TAG + ": Releasing Audio Focus.");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
        } else {
            @SuppressWarnings("deprecation")
            int result = audioManager.abandonAudioFocus(focusChange -> {});
        }
    }
}