package my.app.permata.media.engine;

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
 * Persistence utility helper to map AudioEffects states to/from JSON strings.
 */
public final class AudioEffectsStateBridge {

    private static final String KEY_EQ_ENABLED = "eq_enabled";
    private static final String KEY_EQ_PRESET = "eq_preset";
    private static final String KEY_EQ_BANDS = "eq_bands";
    private static final String KEY_VIRT_ENABLED = "virt_enabled";
    private static final String KEY_VIRT_STRENGTH = "virt_strength";
    private static final String KEY_BASS_ENABLED = "bass_enabled";
    private static final String KEY_BASS_STRENGTH = "bass_strength";
    private static final String KEY_LOUD_ENABLED = "loud_enabled";
    private static final String KEY_LOUD_GAIN = "loud_gain";

    /**
     * Extracts the active configuration from an AudioEffects engine and packs it into a JSON string.
     */
    @NonNull
    public static String captureToSnapshot(@NonNull AudioEffects effects) {
        try {
            JSONObject snapshot = new JSONObject();

            // 1. Equalizer State Collection
            Equalizer eq = effects.getEqualizer();
            if (eq != null) {
                snapshot.put(KEY_EQ_ENABLED, eq.getEnabled());
                snapshot.put(KEY_EQ_PRESET, (int) eq.getCurrentPreset());
                
                JSONArray bands = new JSONArray();
                short numBands = eq.getNumberOfBands();
                for (short i = 0; i < numBands; i++) {
                    bands.put((int) eq.getBandLevel(i));
                }
                snapshot.put(KEY_EQ_BANDS, bands);
            }

            // 2. Virtualizer State Collection
            Virtualizer virt = effects.getVirtualizer();
            if (virt != null) {
                snapshot.put(KEY_VIRT_ENABLED, virt.getEnabled());
                snapshot.put(KEY_VIRT_STRENGTH, (int) virt.getRoundedStrength());
            }

            // 3. BassBoost State Collection
            BassBoost bass = effects.getBassBoost();
            if (bass != null) {
                snapshot.put(KEY_BASS_ENABLED, bass.getEnabled());
                snapshot.put(KEY_BASS_STRENGTH, (int) bass.getRoundedStrength());
            }

            // 4. LoudnessEnhancer State Collection
            LoudnessEnhancer loud = effects.getLoudnessEnhancer();
            if (loud != null) {
                snapshot.put(KEY_LOUD_ENABLED, loud.getEnabled());
                snapshot.put(KEY_LOUD_GAIN, loud.getTargetGain());
            }

            return snapshot.toString();
        } catch (Exception ex) {
            Log.e(ex, "Failed to compile audio framework settings state blueprint.");
            return "{}";
        }
    }

    /**
     * Parses a JSON snapshot and securely pushes values back onto the native hardware loops.
     */
    public static void restoreFromSnapshot(@NonNull AudioEffects targetEffects, @Nullable String jsonState) {
        if (jsonState == null || jsonState.isEmpty() || "{}".equals(jsonState)) return;

        try {
            JSONObject snapshot = new JSONObject(jsonState);

            // 1. Equalizer Restructuring Execution
            Equalizer eq = targetEffects.getEqualizer();
            if (eq != null && snapshot.has(KEY_EQ_ENABLED)) {
                eq.setEnabled(snapshot.getBoolean(KEY_EQ_ENABLED));
                int preset = snapshot.getInt(KEY_EQ_PRESET);
                if (preset >= 0 && preset < eq.getNumberOfPresets()) {
                    eq.usePreset((short) preset);
                } else if (snapshot.has(KEY_EQ_BANDS)) {
                    JSONArray bands = snapshot.getJSONArray(KEY_EQ_BANDS);
                    for (short i = 0; i < bands.length(); i++) {
                        if (i < eq.getNumberOfBands()) {
                            eq.setBandLevel(i, (short) bands.getInt(i));
                        }
                    }
                }
            }

            // 2. Virtualizer Restructuring Execution
            Virtualizer virt = targetEffects.getVirtualizer();
            if (virt != null && snapshot.has(KEY_VIRT_ENABLED)) {
                virt.setEnabled(snapshot.getBoolean(KEY_VIRT_ENABLED));
                virt.setStrength((short) snapshot.getInt(KEY_VIRT_STRENGTH));
            }

            // 3. BassBoost Restructuring Execution
            BassBoost bass = targetEffects.getBassBoost();
            if (bass != null && snapshot.has(KEY_BASS_ENABLED)) {
                bass.setEnabled(snapshot.getBoolean(KEY_BASS_ENABLED));
                bass.setStrength((short) snapshot.getInt(KEY_BASS_STRENGTH));
            }

            // 4. LoudnessEnhancer Restructuring Execution
            LoudnessEnhancer loud = targetEffects.getLoudnessEnhancer();
            if (loud != null && snapshot.has(KEY_LOUD_ENABLED)) {
                loud.setEnabled(snapshot.getBoolean(KEY_LOUD_ENABLED));
                loud.setTargetGain(snapshot.getInt(KEY_LOUD_GAIN));
            }

        } catch (Exception ex) {
            Log.e(ex, "Aborted data loading: Input payload schema state is corrupted or mismatched.");
        }
    }
}
