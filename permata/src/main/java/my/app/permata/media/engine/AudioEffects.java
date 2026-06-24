package my.app.permata.media.engine;

import static android.os.Build.VERSION.SDK_INT;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import my.app.utils.log.Log;

/**
 * Enterprise-grade, autonomous, completely ANR-safe AudioEffects engine for Permata Auto.
 * Offloads heavy disk I/O and synchronous hardware HAL driver sequences to a dedicated background pipeline.
 *
 * @author sklchan77
 */
public final class AudioEffects {
    private static final byte EQUALIZER = 1 << 0;
    private static final byte VIRTUALIZER = 1 << 1;
    private static final byte BASS_BOOST = 1 << 2;
    private static final byte LOUDNESS_ENHANCER = 1 << 3;
    private static final byte supported;

    // Fixed at 0 mB to cleanly maintain amplitude at the original 100% neutral volume curve
    private static final int GAIN_150_PERCENT_MB = 0;
    private static final String PREFS_NAME = "permata_audio_effects_prefs";
    private static final String KEY_EQ_ENABLED = "eq_enabled";
    private static final String KEY_VIRT_ENABLED = "virt_enabled";
    private static final String KEY_BASS_ENABLED = "bass_enabled";
    private static final String KEY_LOUD_ENABLED = "loud_enabled";
    private static final String KEY_BASS_STRENGTH = "bass_strength";
    private static final String KEY_VIRT_STRENGTH = "virt_strength";
    private static final String KEY_LOUD_GAIN = "loud_gain";
    private static final String KEY_VOLUME_BOOST_ACTIVE = "volume_boost_active";
    private static final String KEY_EQ_BAND_PREFIX = "eq_band_";

    private static final Object ALLOCATION_LOCK = new Object();

    // Dedicated single-threaded execution context to permanently insulate the main UI from blocking delays
    private static final ExecutorService ASYNC_WORKER = Executors.newSingleThreadExecutor();

    @Nullable private volatile Equalizer equalizer;
    @Nullable private volatile Virtualizer virtualizer;
    @Nullable private volatile BassBoost bassBoost;
    @Nullable private volatile LoudnessEnhancer loudnessEnhancer;

    private volatile boolean equalizerEnabled;
    private volatile boolean virtualizerEnabled;
    private volatile boolean bassBoostEnabled;
    private volatile boolean loudnessEnhancerEnabled;
    private volatile boolean is150PercentBoostActive;

    @Nullable private String activeChannelId = null;
    static {
        byte s = 0;
        try {
            for (AudioEffect.Descriptor d : AudioEffect.queryEffects()) {
                if (AudioEffect.EFFECT_TYPE_EQUALIZER.equals(d.type)) s |= EQUALIZER;
                else if (AudioEffect.EFFECT_TYPE_VIRTUALIZER.equals(d.type)) s |= VIRTUALIZER;
                else if (AudioEffect.EFFECT_TYPE_BASS_BOOST.equals(d.type)) s |= BASS_BOOST;
                else if (AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER.equals(d.type)) s |= LOUDNESS_ENHANCER;
            }
        } catch (Exception ex) {
            Log.e(ex, "Permata AudioEngine: Critical system capability query fault dropped out.");
        }
        supported = s;
    }

    private AudioEffects(int priority, int audioSessionId) {
        this.equalizer = supported(EQUALIZER) ? safeCreate(() -> new Equalizer(priority, audioSessionId), "Equalizer") : null;
        this.virtualizer = (SDK_INT < 35 && supported(VIRTUALIZER)) ? safeCreate(() -> new Virtualizer(priority, audioSessionId), "Virtualizer") : null;
        this.bassBoost = supported(BASS_BOOST) ? safeCreate(() -> new BassBoost(priority, audioSessionId), "BassBoost") : null;
        this.loudnessEnhancer = supported(LOUDNESS_ENHANCER) ? safeCreate(() -> new LoudnessEnhancer(audioSessionId), "LoudnessEnhancer") : null;
    }

    private static boolean supported(byte type) {
        return (supported & type) != 0;
    }
    @Nullable
    private static <T extends AudioEffect> T safeCreate(@NonNull AudioEffectFactory<T> factory, @NonNull String effectName) {
        try {
            return factory.create();
        } catch (Exception ex) {
            Log.w("Permata AudioEngine: " + effectName + " initialization failed. Running HAL recovery routine...", ex);
            try {
                Thread.sleep(250);
                return factory.create();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                Log.e(ie, "Permata AudioEngine: Recovery routine interrupted for " + effectName);
                return null;
            } catch (Exception retryEx) {
                Log.e(retryEx, "Permata AudioEngine: Low-level driver rejected " + effectName + " permanently.");
                return null;
            }
        }
    }
    @Nullable
    public static AudioEffects create(int priority, int audioSessionId) {
        if (supported == 0 || audioSessionId <= 0) {
            return null;
        }
        synchronized (ALLOCATION_LOCK) {
            AudioEffects instance = new AudioEffects(priority, audioSessionId);
            boolean functional = Stream.of(instance.equalizer, instance.virtualizer, instance.bassBoost, instance.loudnessEnhancer)
                    .anyMatch(Objects::nonNull);
            if (!functional) {
                Log.e("Permata AudioEngine: Fallback block instantiation aborted. Hardware layers unresponsive.");
                return null;
            }
            return instance;
        }
    }

    @Nullable
    public static AudioEffects create(@NonNull Context context, int priority, int audioSessionId) {
        if (supported == 0 || audioSessionId <= 0) {
            return null;
        }
        synchronized (ALLOCATION_LOCK) {
            AudioEffects instance = new AudioEffects(priority, audioSessionId);
            boolean functional = Stream.of(instance.equalizer, instance.virtualizer, instance.bassBoost, instance.loudnessEnhancer)
                    .anyMatch(Objects::nonNull);
            if (!functional) {
                Log.e("Permata AudioEngine: Block instantiation aborted. Hardware layers are fully unresponsive.");
                return null;
            }
            final Context appContext = context.getApplicationContext();
            ASYNC_WORKER.execute(() -> {
                synchronized (ALLOCATION_LOCK) {
                    instance.loadAndApplyPersistedSettings(appContext);
                }
            });
            return instance;
        }
    }
    @Nullable public Equalizer getEqualizer() { return equalizer; }
    @Nullable public Virtualizer getVirtualizer() { return virtualizer; }
    @Nullable public BassBoost getBassBoost() { return bassBoost; }
    @Nullable public LoudnessEnhancer getLoudnessEnhancer() { return loudnessEnhancer; }

    public void loadAndApplyPersistedSettingsForChannel(@NonNull Context context, @NonNull String channelId) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            synchronized (ALLOCATION_LOCK) {
                this.activeChannelId = channelId;
                loadAndApplyPersistedSettings(appContext);
            }
        });
    }

    public void resetToGlobalSettings(@NonNull Context context) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            synchronized (ALLOCATION_LOCK) {
                this.activeChannelId = null;
                loadAndApplyPersistedSettings(appContext);
            }
        });
    }
    private void loadAndApplyPersistedSettings(@NonNull Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            final String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
            
            // Reusable optimization StringBuilder to permanently eliminate high-frequency GC allocations inside loops
            StringBuilder keyBuilder = new StringBuilder(64);

            boolean eqEnabled = prefs.getBoolean(pfx + KEY_EQ_ENABLED, false);
            boolean virtEnabled = prefs.getBoolean(pfx + KEY_VIRT_ENABLED, false);
            boolean bassEnabled = prefs.getBoolean(pfx + KEY_BASS_ENABLED, false);
            boolean loudEnabled = prefs.getBoolean(pfx + KEY_LOUD_ENABLED, false);
            boolean volBoostActive = prefs.getBoolean(pfx + KEY_VOLUME_BOOST_ACTIVE, false);

            if (bassBoost != null) {
                short bassStrength = (short) prefs.getInt(pfx + KEY_BASS_STRENGTH, 0);
                setBassBoostStrengthInternal(bassStrength);
            }
            if (virtualizer != null && SDK_INT < 35) {
                short virtStrength = (short) prefs.getInt(pfx + KEY_VIRT_STRENGTH, 0);
                setVirtualizerStrengthInternal(virtStrength);
            }
            if (loudnessEnhancer != null) {
                int targetGain = prefs.getInt(pfx + KEY_LOUD_GAIN, 0);
                setLoudnessEnhancementGainInternal(targetGain);
            }
            if (equalizer != null) {
                short bands = equalizer.getNumberOfBands();
                for (short i = 0; i < bands; i++) {
                    keyBuilder.setLength(0);
                    String bandKey = keyBuilder.append(pfx).append(KEY_EQ_BAND_PREFIX).append(i).toString();
                    short savedGain = (short) prefs.getInt(bandKey, 0);
                    setEqualizerBandGainInternal(i, savedGain);
                }
            }

            setEqualizerEnabledInternal(eqEnabled);
            setVirtualizerEnabledInternal(virtEnabled);
            setBassBoostEnabledInternal(bassEnabled);

            if (volBoostActive) {
                apply150PercentVolumeBoostInternal();
            } else {
                setLoudnessEnhancerEnabledInternal(loudEnabled);
                this.is150PercentBoostActive = false;
            }
            Log.i("Permata AudioEngine: State storage tracking profiles synced up successfully. Channel Pfx: " + pfx);
        } catch (Exception ex) {
            Log.e(ex, "Permata AudioEngine: Error loading structural data out of storage mapping components.");
        }
    }
    private void persistState(@NonNull Context context, String key, boolean value) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            try {
                String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                       .edit()
                       .putBoolean(pfx + key, value)
                       .apply();
            } catch (Exception ex) {
                Log.e(ex, "Permata AudioEngine: Error updating persistent boolean flag configurations.");
            }
        });
    }

    private void persistState(@NonNull Context context, String key, int value) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            try {
                String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                       .edit()
                       .putInt(pfx + key, value)
                       .apply();
            } catch (Exception ex) {
                Log.e(ex, "Permata AudioEngine: Error updating persistent integer mapping metrics.");
            }
        });
    }
    public void apply150PercentVolumeBoost(@NonNull Context context) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            synchronized (ALLOCATION_LOCK) {
                if (apply150PercentVolumeBoostInternal()) {
                    try {
                        String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
                        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                               .edit()
                               .putInt(pfx + KEY_LOUD_GAIN, GAIN_150_PERCENT_MB)
                               .putBoolean(pfx + KEY_LOUD_ENABLED, true)
                               .putBoolean(pfx + KEY_VOLUME_BOOST_ACTIVE, true)
                               .apply();
                    } catch (Exception ex) {
                        Log.e(ex, "Permata AudioEngine: Failed to serialize volume boost transaction payload.");
                    }
                }
            }
        });
    }

    private boolean apply150PercentVolumeBoostInternal() {
        if (loudnessEnhancer == null) {
            Log.w("Permata AudioEngine: Cannot apply 100% boost; hardware LoudnessEnhancer is missing.");
            return false;
        }
        setLoudnessEnhancementGainInternal(GAIN_150_PERCENT_MB);
        boolean active = safeToggleEffect(loudnessEnhancer, true);
        if (active) {
            this.loudnessEnhancerEnabled = true;
            this.is150PercentBoostActive = true;
        }
        return active;
    }
    public void disableVolumeBoost(@NonNull Context context) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            synchronized (ALLOCATION_LOCK) {
                if (loudnessEnhancer == null) return;
                setLoudnessEnhancementGainInternal(0);
                safeToggleEffect(loudnessEnhancer, false);
                this.loudnessEnhancerEnabled = false;
                this.is150PercentBoostActive = false;

                try {
                    String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
                    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                           .edit()
                           .putInt(pfx + KEY_LOUD_GAIN, 0)
                           .putBoolean(pfx + KEY_LOUD_ENABLED, false)
                           .putBoolean(pfx + KEY_VOLUME_BOOST_ACTIVE, false)
                           .apply();
                } catch (Exception ex) {
                    Log.e(ex, "Permata AudioEngine: Failed to serialize volume boost disable payload.");
                }
            }
        });
    }

    public boolean is150PercentBoostActive() { 
        return is150PercentBoostActive; 
    }
    public void setEffectsEnabled(@NonNull Context context, boolean enabled) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            synchronized (ALLOCATION_LOCK) {
                setEqualizerEnabledBackground(appContext, enabled);
                setVirtualizerEnabledBackground(appContext, enabled);
                setBassBoostEnabledBackground(appContext, enabled);
                if (!enabled && is150PercentBoostActive) {
                    // Force clean background execution bypassing additional worker hops
                    if (loudnessEnhancer != null) {
                        setLoudnessEnhancementGainInternal(0);
                        safeToggleEffect(loudnessEnhancer, false);
                        this.loudnessEnhancerEnabled = false;
                        this.is150PercentBoostActive = false;

                        String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
                        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                               .edit()
                               .putInt(pfx + KEY_LOUD_GAIN, 0)
                               .putBoolean(pfx + KEY_LOUD_ENABLED, false)
                               .putBoolean(pfx + KEY_VOLUME_BOOST_ACTIVE, false)
                               .apply();
                    }
                } else {
                    setLoudnessEnhancerEnabledBackground(appContext, enabled);
                }
            }
        });
    }
    public void setEqualizerEnabled(@NonNull Context context, boolean enabled) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            synchronized (ALLOCATION_LOCK) {
                setEqualizerEnabledBackground(appContext, enabled);
            }
        });
    }

    private void setEqualizerEnabledBackground(@NonNull Context context, boolean enabled) {
        if (setEqualizerEnabledInternal(enabled)) {
            try {
                String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                       .edit()
                       .putBoolean(pfx + KEY_EQ_ENABLED, enabled)
                       .apply();
            } catch (Exception ex) {
                Log.e(ex, "Permata AudioEngine: Error serializing equalizer toggle update.");
            }
        }
    }

    private boolean setEqualizerEnabledInternal(boolean enabled) {
        if (equalizer == null || equalizerEnabled == enabled) return false;
        boolean success = safeToggleEffect(equalizer, enabled);
        if (success) this.equalizerEnabled = enabled;
        return success;
    }
    public void setVirtualizerEnabled(@NonNull Context context, boolean enabled) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            synchronized (ALLOCATION_LOCK) {
                setVirtualizerEnabledBackground(appContext, enabled);
            }
        });
    }

    private void setVirtualizerEnabledBackground(@NonNull Context context, boolean enabled) {
        if (setVirtualizerEnabledInternal(enabled)) {
            try {
                String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                       .edit()
                       .putBoolean(pfx + KEY_VIRT_ENABLED, enabled)
                       .apply();
            } catch (Exception ex) {
                Log.e(ex, "Permata AudioEngine: Error serializing virtualizer toggle update.");
            }
        }
    }

    private boolean setVirtualizerEnabledInternal(boolean enabled) {
        if (virtualizer == null || SDK_INT >= 35 || virtualizerEnabled == enabled) return false;
        boolean success = safeToggleEffect(virtualizer, enabled);
        if (success) this.virtualizerEnabled = enabled;
        return success;
    }
    public void setBassBoostEnabled(@NonNull Context context, boolean enabled) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            synchronized (ALLOCATION_LOCK) {
                setBassBoostEnabledBackground(appContext, enabled);
            }
        });
    }

    private void setBassBoostEnabledBackground(@NonNull Context context, boolean enabled) {
        if (setBassBoostEnabledInternal(enabled)) {
            try {
                String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                       .edit()
                       .putBoolean(pfx + KEY_BASS_ENABLED, enabled)
                       .apply();
            } catch (Exception ex) {
                Log.e(ex, "Permata AudioEngine: Error serializing bass boost toggle update.");
            }
        }
    }

    private boolean setBassBoostEnabledInternal(boolean enabled) {
        if (bassBoost == null || bassBoostEnabled == enabled) return false;
        boolean success = safeToggleEffect(bassBoost, enabled);
        if (success) this.bassBoostEnabled = enabled;
        return success;
    }
    public void setLoudnessEnhancerEnabled(@NonNull Context context, boolean enabled) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            synchronized (ALLOCATION_LOCK) {
                setLoudnessEnhancerEnabledBackground(appContext, enabled);
            }
        });
    }

    private void setLoudnessEnhancerEnabledBackground(@NonNull Context context, boolean enabled) {
        if (setLoudnessEnhancerEnabledInternal(enabled)) {
            try {
                String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
                SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                editor.putBoolean(pfx + KEY_LOUD_ENABLED, enabled);
                if (!enabled) {
                    editor.putBoolean(pfx + KEY_VOLUME_BOOST_ACTIVE, false);
                    this.is150PercentBoostActive = false;
                }
                editor.apply();
            } catch (Exception ex) {
                Log.e(ex, "Permata AudioEngine: Error serializing loudness enhancer toggle update.");
            }
        }
    }

    private boolean setLoudnessEnhancerEnabledInternal(boolean enabled) {
        if (loudnessEnhancer == null || loudnessEnhancerEnabled == enabled) return false;
        boolean success = safeToggleEffect(loudnessEnhancer, enabled);
        if (success) this.loudnessEnhancerEnabled = enabled;
        return success;
    }
    private static boolean safeToggleEffect(@NonNull AudioEffect effect, boolean enabled) {
        try {
            if (effect.getEnabled() != enabled) {
                effect.setEnabled(enabled);
            }
            return true;
        } catch (Exception ex) {
            Log.e(ex, "Permata AudioEngine: Error changing toggle state on audio driver reference layer.");
            return false;
        }
    }
    public void setEqualizerBandGain(@NonNull Context context, short band, short gainmB) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            synchronized (ALLOCATION_LOCK) {
                if (setEqualizerBandGainInternal(band, gainmB)) {
                    try {
                        String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
                        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                               .edit()
                               .putInt(pfx + KEY_EQ_BAND_PREFIX + band, (int) gainmB)
                               .apply();
                    } catch (Exception ex) {
                        Log.e(ex, "Permata AudioEngine: Error serializing equalizer band gain tweak.");
                    }
                }
            }
        });
    }

    private boolean setEqualizerBandGainInternal(short band, short gainmB) {
        if (equalizer == null) return false;
        try {
            short[] range = equalizer.getBandLevelRange();
            if (range != null && range.length >= 2) {
                short clampedGain = (short) Math.max(range[0], Math.min(range[1], gainmB));
                if (equalizer.getBandLevel(band) == clampedGain) return false;
                equalizer.setBandLevel(band, clampedGain);
                return true;
            }
        } catch (Exception ex) {
            Log.e(ex, "Permata AudioEngine: Equalizer target parameter tracking adjustment failed.");
        }
        return false;
    }
    public void setBassBoostStrength(@NonNull Context context, short strength) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            synchronized (ALLOCATION_LOCK) {
                if (setBassBoostStrengthInternal(strength)) {
                    try {
                        String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
                        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                               .edit()
                               .putInt(pfx + KEY_BASS_STRENGTH, (int) strength)
                               .apply();
                    } catch (Exception ex) {
                        Log.e(ex, "Permata AudioEngine: Error serializing bass boost strength tweak.");
                    }
                }
            }
        });
    }

    private boolean setBassBoostStrengthInternal(short strength) {
        if (bassBoost == null) return false;
        try {
            if (bassBoost.getStrengthSupported()) {
                short clampedStrength = (short) Math.max(0, Math.min(1000, strength));
                if (bassBoost.getRoundedStrength() == clampedStrength) return false;
                bassBoost.setStrength(clampedStrength);
                return true;
            }
        } catch (Exception ex) {
            Log.e(ex, "Permata AudioEngine: BassBoost parameter change configuration dropped.");
        }
        return false;
    }
    public void setVirtualizerStrength(@NonNull Context context, short strength) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            synchronized (ALLOCATION_LOCK) {
                if (setVirtualizerStrengthInternal(strength)) {
                    try {
                        String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
                        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                               .edit()
                               .putInt(pfx + KEY_VIRT_STRENGTH, (int) strength)
                               .apply();
                    } catch (Exception ex) {
                        Log.e(ex, "Permata AudioEngine: Error serializing virtualizer strength tweak.");
                    }
                }
            }
        });
    }

    private boolean setVirtualizerStrengthInternal(short strength) {
        if (virtualizer == null || SDK_INT >= 35) return false;
        try {
            if (virtualizer.getStrengthSupported()) {
                short clampedStrength = (short) Math.max(0, Math.min(1000, strength));
                if (virtualizer.getRoundedStrength() == clampedStrength) return false;
                virtualizer.setStrength(clampedStrength);
                return true;
            }
        } catch (Exception ex) {
            Log.e(ex, "Permata AudioEngine: Virtualizer pipeline adjustments dropped.");
        }
        return false;
    }
    public void setLoudnessEnhancementGain(@NonNull Context context, int gainmB) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            synchronized (ALLOCATION_LOCK) {
                if (setLoudnessEnhancementGainInternal(gainmB)) {
                    try {
                        String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
                        SharedPreferences.Editor editor = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                        editor.putInt(pfx + KEY_LOUD_GAIN, gainmB);
                        if (gainmB != GAIN_150_PERCENT_MB) {
                            editor.putBoolean(pfx + KEY_VOLUME_BOOST_ACTIVE, false);
                            this.is150PercentBoostActive = false;
                        }
                        editor.apply();
                    } catch (Exception ex) {
                        Log.e(ex, "Permata AudioEngine: Error serializing loudness gain configuration.");
                    }
                }
            }
        });
    }

    private boolean setLoudnessEnhancementGainInternal(int gainmB) {
        if (loudnessEnhancer == null) return false;
        try {
            int clampedGain = Math.max(0, Math.min(3000, gainmB));
            loudnessEnhancer.setTargetGain(clampedGain);
            return true;
        } catch (Exception ex) {
            Log.e(ex, "Permata AudioEngine: Volume normalization parameters adjustment failed.");
            return false;
        }
    }
    public void applySerializedPreset(@NonNull Context context, @NonNull EqualizerPreset preset) {
        final Context appContext = context.getApplicationContext();
        ASYNC_WORKER.execute(() -> {
            synchronized (ALLOCATION_LOCK) {
                setEqualizerEnabledInternal(preset.masterSwitchEnabled);
                setVirtualizerEnabledInternal(preset.masterSwitchEnabled);
                setBassBoostEnabledInternal(preset.masterSwitchEnabled);
                setLoudnessEnhancerEnabledInternal(preset.masterSwitchEnabled);

                setBassBoostStrengthInternal(preset.bassBoostStrength);
                setVirtualizerStrengthInternal(preset.virtualizerStrength);
                setLoudnessEnhancementGainInternal(preset.loudnessGainmB);

                try {
                    String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
                    SharedPreferences.Editor editor = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                    
                    editor.putBoolean(pfx + KEY_EQ_ENABLED, preset.masterSwitchEnabled);
                    editor.putBoolean(pfx + KEY_VIRT_ENABLED, preset.masterSwitchEnabled);
                    editor.putBoolean(pfx + KEY_BASS_ENABLED, preset.masterSwitchEnabled);
                    editor.putBoolean(pfx + KEY_LOUD_ENABLED, preset.masterSwitchEnabled);
                    editor.putInt(pfx + KEY_BASS_STRENGTH, (int) preset.bassBoostStrength);
                    editor.putInt(pfx + KEY_VIRT_STRENGTH, (int) preset.virtualizerStrength);
                    editor.putInt(pfx + KEY_LOUD_GAIN, preset.loudnessGainmB);

                    if (preset.loudnessGainmB != GAIN_150_PERCENT_MB) {
                        editor.putBoolean(pfx + KEY_VOLUME_BOOST_ACTIVE, false);
                        this.is150PercentBoostActive = false;
                    }

                    if (equalizer != null && preset.bandGains != null) {
                        short totalBands = (short) Math.min(equalizer.getNumberOfBands(), preset.bandGains.length);
                        for (short i = 0; i < totalBands; i++) {
                            setEqualizerBandGainInternal(i, preset.bandGains[i]);
                            editor.putInt(pfx + KEY_EQ_BAND_PREFIX + i, (int) preset.bandGains[i]);
                        }
                    }
                    editor.apply();
                } catch (Exception ex) {
                    Log.e(ex, "Permata AudioEngine: Error bulk-serializing hardware presets database.");
                }
            }
        });
    }
    public void release() {
        synchronized (ALLOCATION_LOCK) {
            safeRelease(equalizer); equalizer = null; equalizerEnabled = false;
            safeRelease(virtualizer); virtualizer = null; virtualizerEnabled = false;
            safeRelease(bassBoost); bassBoost = null; bassBoostEnabled = false;
            safeRelease(loudnessEnhancer); loudnessEnhancer = null; loudnessEnhancerEnabled = false;
            is150PercentBoostActive = false;
            activeChannelId = null;
        }
    }

    private static void safeRelease(@Nullable AudioEffect effect) {
        if (effect != null) {
            try {
                effect.setEnabled(false);
                effect.release();
            } catch (Exception ex) {
                Log.e(ex, "Permata AudioEngine: Exception intercepted during a lower-tier component dump routine.");
            }
        }
    }
    @FunctionalInterface
    private interface AudioEffectFactory<T extends AudioEffect> {
        T create() throws Exception;
    }

    public static final class EqualizerPreset {
        public final boolean masterSwitchEnabled;
        public final short bassBoostStrength;
        public final short virtualizerStrength;
        public final int loudnessGainmB;
        @NonNull public final short[] bandGains;

        public EqualizerPreset(boolean masterSwitchEnabled, short bassBoostStrength, short virtualizerStrength, int loudnessGainmB, @NonNull short[] bandGains) {
            this.masterSwitchEnabled = masterSwitchEnabled;
            this.bassBoostStrength = bassBoostStrength;
            this.virtualizerStrength = virtualizerStrength;
            this.loudnessGainmB = loudnessGainmB;
            this.bandGains = Arrays.copyOf(bandGains, bandGains.length);
        }
    }
}
