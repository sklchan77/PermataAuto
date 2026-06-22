package my.app.permata.media.engine;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import my.app.utils.log.Log;

/**
 * Modernized, completely standalone, and memory-safe wrapper for Android system audio effects.
 * Handles its own JSON state serialization, deserialization, and persistent storage
 * via SharedPreferences to remain configuration-stable after full app restarts.
 * 
 * @author sklchan77
 */
public final class AudioEffects {
    private static final byte FLAG_EQUALIZER = 1 << 0;
    private static final byte FLAG_VIRTUALIZER = 1 << 1;
    private static final byte FLAG_BASS_BOOST = 1 << 2;
    private static final byte FLAG_LOUDNESS_ENHANCER = 1 << 3;

    private static final byte SUPPORTED_MASK;

    // Storage Constants
    private static final String PREF_FILE_NAME = "audio_effects_engine_prefs";
    private static final String PREF_KEY_SNAPSHOT = "saved_effects_snapshot";

    // JSON Keys for State Persistence
    private static final String KEY_EQ_ENABLED = "eq_enabled";
    private static final String KEY_EQ_PRESET = "eq_preset";
    private static final String KEY_EQ_BANDS = "eq_bands";
    private static final String KEY_VIRT_ENABLED = "virt_enabled";
    private static final String KEY_VIRT_STRENGTH = "virt_strength";
    private static final String KEY_BASS_ENABLED = "bass_enabled";
    private static final String KEY_BASS_STRENGTH = "bass_strength";
    private static final String KEY_LOUD_ENABLED = "loud_enabled";
    private static final String KEY_LOUD_GAIN = "loud_gain";

    @Nullable private Equalizer equalizer;
    @Nullable private Virtualizer virtualizer;
    @Nullable private BassBoost bassBoost;
    @Nullable private LoudnessEnhancer loudnessEnhancer;

    static {
        byte flags = 0;
        try {
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
            release();
            throw new IllegalStateException("Failed to bind target hardware subsystem engines.", ex);
        }
    }

    private static boolean isSupported(final byte featureFlag) {
        return (SUPPORTED_MASK & featureFlag) != 0;
    }

    /**
     * Standard factory method to construct audio effects from default settings.
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

    /**
     * Enhanced factory method that instantiates the engine and automatically restores 
     * its historical configurations from local disk storage.
     * 
     * @param context An active application context to read local SharedPreferences.
     */
    @Nullable
    public static AudioEffects createAndRestore(@NonNull Context context, final int priority, final int audioSessionId) {
        AudioEffects instance = create(priority, audioSessionId);
        if (instance != null) {
            try {
                SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);
                String savedJson = prefs.getString(PREF_KEY_SNAPSHOT, null);
                if (savedJson != null) {
                    instance.restoreFromSnapshot(savedJson);
                }
            } catch (Exception ex) {
                Log.e(ex, "Failed to restore persistent audio profile during initialization sequence.");
            }
        }
        return instance;
    }

    /**
     * Saves the current physical runtime hardware parameters cleanly back to SharedPreferences.
     * Call this whenever your UI knobs move or your playback process halts.
     * 
     * @param context An active application context to write to local storage.
     */
    public void saveToStorage(@NonNull Context context) {
        try {
            String snapshotJson = captureToSnapshot();
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(PREF_KEY_SNAPSHOT, snapshotJson).apply();
        } catch (Exception ex) {
            Log.e(ex, "Failed to commit ongoing audio settings snapshot directly to storage.");
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
     * Converts the current hardware audio configurations into a serialized JSON string.
     */
    @NonNull
    public String captureToSnapshot() {
        try {
            JSONObject snapshot = new JSONObject();

            if (equalizer != null) {
                snapshot.put(KEY_EQ_ENABLED, equalizer.getEnabled());
                snapshot.put(KEY_EQ_PRESET, (int) equalizer.getCurrentPreset());
                
                JSONArray bands = new JSONArray();
                short numBands = equalizer.getNumberOfBands();
                for (short i = 0; i < numBands; i++) {
                    bands.put((int) equalizer.getBandLevel(i));
                }
                snapshot.put(KEY_EQ_BANDS, bands);
            }

            if (virtualizer != null) {
                snapshot.put(KEY_VIRT_ENABLED, virtualizer.getEnabled());
                snapshot.put(KEY_VIRT_STRENGTH, (int) virtualizer.getRoundedStrength());
            }

            if (bassBoost != null) {
                snapshot.put(KEY_BASS_ENABLED, bassBoost.getEnabled());
                snapshot.put(KEY_BASS_STRENGTH, (int) bassBoost.getRoundedStrength());
            }

            if (loudnessEnhancer != null) {
                snapshot.put(KEY_LOUD_ENABLED, loudnessEnhancer.getEnabled());
                snapshot.put(KEY_LOUD_GAIN, loudnessEnhancer.getTargetGain());
            }

            return snapshot.toString();
        } catch (Exception ex) {
            Log.e(ex, "Failed to compile audio framework settings state blueprint.");
            return "{}";
        }
    }

    /**
     * Parses a serialized JSON snapshot string and pushes values directly back onto the active native hardware engines.
     */
    public void restoreFromSnapshot(@Nullable String jsonState) {
        if (jsonState == null || jsonState.isEmpty() || "{}".equals(jsonState)) return;

        try {
            JSONObject snapshot = new JSONObject(jsonState);

            if (equalizer != null && snapshot.has(KEY_EQ_ENABLED)) {
                equalizer.setEnabled(snapshot.getBoolean(KEY_EQ_ENABLED));
                int preset = snapshot.getInt(KEY_EQ_PRESET);
                
                if (preset >= 0 && preset < equalizer.getNumberOfPresets()) {
                    equalizer.usePreset((short) preset);
                } 
                if (snapshot.has(KEY_EQ_BANDS)) {
                    JSONArray bands = snapshot.getJSONArray(KEY_EQ_BANDS);
                    int hardwareBandsCount = equalizer.getNumberOfBands();
                    for (short i = 0; i < bands.length(); i++) {
                        if (i < hardwareBandsCount) {
                            equalizer.setBandLevel(i, (short) bands.getInt(i));
                        }
                    }
                }
            }

if (virtualizer != null && snapshot.has(KEY_VIRT_ENABLED)) {virtualizer.setEnabled(snapshot.getBoolean(KEY_VIRT_ENABLED));virtualizer.setStrength((short) snapshot.getInt(KEY_VIRT_STRENGTH));}if (bassBoost != null && snapshot.has(KEY_BASS_ENABLED)) {bassBoost.setEnabled(snapshot.getBoolean(KEY_BASS_ENABLED));bassBoost.setStrength((short) snapshot.getInt(KEY_BASS_STRENGTH));}if (loudnessEnhancer != null && snapshot.has(KEY_LOUD_ENABLED)) {loudnessEnhancer.setEnabled(snapshot.getBoolean(KEY_LOUD_ENABLED));loudnessEnhancer.setTargetGain(snapshot.getInt(KEY_LOUD_GAIN));}} catch (Exception ex) {Log.e(ex, "Aborted loading payload: Input string schema data is corrupted or mismatched.");}}public synchronized void release() {if (equalizer != null) {try { equalizer.release(); } catch (Exception ignored) {}equalizer = null;}if (virtualizer != null) {try { virtualizer.release(); } catch (Exception ignored) {}virtualizer = null;}if (bassBoost != null) {try { bassBoost.release(); } catch (Exception ignored) {}bassBoost = null;}if (loudnessEnhancer != null) {try { loudnessEnhancer.release(); } catch (Exception ignored) {}loudnessEnhancer = null;}}}