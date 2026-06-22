package my.app.permata.media.engine;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;

import androidx.annotation.Nullable;

import my.app.utils.log.Log;

/**
 * Modernized, memory-safe wrapper for Android system audio effects.
 * Optimized to prevent leaks and gracefully handle platform architectural deprecations.
 * 
 * @author sklchan77
 */
public final class AudioEffects {
    private static final byte FLAG_EQUALIZER = 1 << 0;
    private static final byte FLAG_VIRTUALIZER = 1 << 1;
    private static final byte FLAG_BASS_BOOST = 1 << 2;
    private static final byte FLAG_LOUDNESS_ENHANCER = 1 << 3;

    private static final byte SUPPORTED_MASK;

    @Nullable private Equalizer equalizer;
    @Nullable private Virtualizer virtualizer;
    @Nullable private BassBoost bassBoost;
    @Nullable private LoudnessEnhancer loudnessEnhancer;

    static {
        byte flags = 0;
        try {
            // Retrieve system engine capacity safely away from direct initialization pipelines
            final AudioEffect.Descriptor[] descriptors = AudioEffect.queryEffects();
            if (descriptors != null) {
                for (final AudioEffect.Descriptor d : descriptors) {
                    if (d == null || d.type == null) continue;
                    
                    if (AudioEffect.EFFECT_TYPE_EQUALIZER.equals(d.type)) {
                        flags |= FLAG_EQUALIZER;
                    } else if (AudioEffect.EFFECT_TYPE_VIRTUALIZER.equals(d.type)) {
                        flags |= FLAG_VIRTUALIZER;
                    } else if (AudioEffect.EFFECT_TYPE_BASS_BOOST.equals(d.type)) {
                        flags |= FLAG_BASS_BOOST;
                    } else if (AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER.equals(d.type)) {
                        flags |= FLAG_LOUDNESS_ENHANCER;
                    }
                }
            }
        } catch (Exception ex) {
            Log.e(ex, "Critical failure querying hardware audio capability components.");
        }
        SUPPORTED_MASK = flags;
    }

    private AudioEffects(final int priority, final int audioSessionId) throws IllegalStateException {
        try {
            if (isSupported(FLAG_EQUALIZER)) {
                equalizer = new Equalizer(priority, audioSessionId);
            }
            // Explicitly filter legacy Virtualizer implementations out of modern platform HAL layers
            if (SDK_INT < VANILLA_ICE_CREAM && isSupported(FLAG_VIRTUALIZER)) {
                virtualizer = new Virtualizer(priority, audioSessionId);
            }
            if (isSupported(FLAG_BASS_BOOST)) {
                bassBoost = new BassBoost(priority, audioSessionId);
            }
            if (isSupported(FLAG_LOUDNESS_ENHANCER)) {
                loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
            }
        } catch (Exception ex) {
            // Failure anywhere cascades into an absolute clean-up cycle to block native memory leaks
            release();
            throw new IllegalStateException("Failed to bind target hardware subsystem engines.", ex);
        }
    }

    private static boolean isSupported(final byte featureFlag) {
        return (SUPPORTED_MASK & featureFlag) != 0;
    }

    /**
     * Lazily constructs an instance of AudioEffects targeting a specific hardware audio output path.
     */
    @Nullable
    public static AudioEffects create(final int priority, final int audioSessionId) {
        if (SUPPORTED_MASK == 0) {
            Log.w("Aborting instantiation: No underlying hardware system extensions supported.");
            return null;
        }

        try {
            return new AudioEffects(priority, audioSessionId);
        } catch (Exception ex) {
            Log.e(ex, "Terminal error configuring device audio processing matrix pipeline.");
            return null;
        }
    }

    @Nullable
    public Equalizer getEqualizer() {
        return equalizer;
    }

    @Nullable
    public Virtualizer getVirtualizer() {
        return virtualizer;
    }

    @Nullable
    public BassBoost getBassBoost() {
        return bassBoost;
    }

    @Nullable
    public LoudnessEnhancer getLoudnessEnhancer() {
        return loudnessEnhancer;
    }

    /**
     * Flushes allocations, breaking links cleanly to give the GC zero overhead on references.
     */
    public synchronized void release() {
        if (equalizer != null) {
            try { equalizer.release(); } catch (Exception ignored) {}
            equalizer = null;
        }
        if (virtualizer != null) {
            try { virtualizer.release(); } catch (Exception ignored) {}
            virtualizer = null;
        }
        if (bassBoost != null) {
            try { bassBoost.release(); } catch (Exception ignored) {}
            bassBoost = null;
        }
        if (loudnessEnhancer != null) {
            try { loudnessEnhancer.release(); } catch (Exception ignored) {}
            loudnessEnhancer = null;
        }
    }
}
