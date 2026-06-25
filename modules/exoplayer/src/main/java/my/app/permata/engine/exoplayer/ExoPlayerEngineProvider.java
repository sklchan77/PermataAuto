package my.app.permata.engine.exoplayer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import my.app.permata.media.engine.MediaEngine;
import my.app.permata.media.engine.MediaEngine.Listener;
import my.app.permata.media.engine.MediaEngineProvider;

/**
 * Enterprise-Grade ExoPlayerEngineProvider for Permata Auto.
 * Re-engineered to mitigate context-retention memory leaks via safe global application bounds mapping.
 *
 * @author sklchan77 (Optimized Modern Version)
 */
@SuppressWarnings("unused")
public class ExoPlayerEngineProvider implements MediaEngineProvider {
    
    // Mitigation: Cleans up activity context references to ensure zero leaks
    private Context appContext;
    @Override
    public void init(@NonNull Context ctx) {
        // Mitigation: Extract and hold only the top-level application context safe wrapper bounds monitor
        this.appContext = ctx.getApplicationContext();
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public MediaEngine createEngine(@NonNull Listener listener) {
        if (appContext == null) {
            throw new IllegalStateException("ExoPlayerEngineProvider must be initialized with context before calling createEngine.");
        }
        return new ExoPlayerEngine(appContext, listener);
    }
}
