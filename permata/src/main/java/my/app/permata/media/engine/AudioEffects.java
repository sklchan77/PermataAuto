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

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

import my.app.utils.log.Log;

/**
 * Enterprise-grade, autonomous AudioEffects engine tailored for Permata Auto.
 * Part 1: Core Fields, Device Queries, and Component Instantiators.
 * 
 * @author sklchan77
 */
public final class AudioEffects {
	private static final byte EQUALIZER = 1 << 0;
	private static final byte VIRTUALIZER = 1 << 1;
	private static final byte BASS_BOOST = 1 << 2;
	private static final byte LOUDNESS_ENHANCER = 1 << 3;
	private static final byte supported;

	// Precise millibel constant mapping to a 1.5x (150%) amplitude gain step
	private static final int GAIN_150_PERCENT_MB = 352; 

	// Persistence Keys
	private static final String PREFS_NAME = "fermata_audio_effects_prefs";
	private static final String KEY_EQ_ENABLED = "eq_enabled";
	private static final String KEY_VIRT_ENABLED = "virt_enabled";
	private static final String KEY_BASS_ENABLED = "bass_enabled";
	private static final String KEY_LOUD_ENABLED = "loud_enabled";
	private static final String KEY_BASS_STRENGTH = "bass_strength";
	private static final String KEY_VIRT_STRENGTH = "virt_strength";
	private static final String KEY_LOUD_GAIN = "loud_gain";
	private static final String KEY_VOLUME_BOOST_ACTIVE = "volume_boost_active";
	private static final String KEY_EQ_BAND_PREFIX = "eq_band_";

	// Global synchronization mutex to prevent race conditions during session allocation cycles
	private static final Object ALLOCATION_LOCK = new Object();

	// Volatile markers ensure immediate thread-visibility across background processing pipelines
	@Nullable private volatile Equalizer equalizer;
	@Nullable private volatile Virtualizer virtualizer;
	@Nullable private volatile BassBoost bassBoost;
	@Nullable private volatile LoudnessEnhancer loudnessEnhancer;

	private volatile boolean equalizerEnabled;
	private volatile boolean virtualizerEnabled;
	private volatile boolean bassBoostEnabled;
	private volatile boolean loudnessEnhancerEnabled;
	private volatile boolean is150PercentBoostActive;

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
		this.equalizer = supported(EQUALIZER) ? 
				safeCreate(() -> new Equalizer(priority, audioSessionId), "Equalizer") : null;
		
		this.virtualizer = (SDK_INT < VANILLA_ICE_CREAM && supported(VIRTUALIZER)) ? 
				safeCreate(() -> new Virtualizer(priority, audioSessionId), "Virtualizer") : null;
		
		this.bassBoost = supported(BASS_BOOST) ? 
				safeCreate(() -> new BassBoost(priority, audioSessionId), "BassBoost") : null;
		
		this.loudnessEnhancer = supported(LOUDNESS_ENHANCER) ? 
				safeCreate(() -> new LoudnessEnhancer(audioSessionId), "LoudnessEnhancer") : null;
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
	/**
	 * Overloaded Factory Fallback.
	 * Directly resolves the signature required by MediaPlayerEngine.java without breaking existing architectures.
	 * Persistence layers are bypassed when instantiated via this fallback signature.
	 */
	@Nullable
	public static AudioEffects create(int priority, int audioSessionId) {
		if (supported == 0 || audioSessionId <= 0) {
			return null;
		}

		synchronized (ALLOCATION_LOCK) {
			AudioEffects instance = new AudioEffects(priority, audioSessionId);

			boolean functional = Stream.of(instance.equalizer, instance.virtualizer, 
					                        instance.bassBoost, instance.loudnessEnhancer)
					                    .anyMatch(Objects::nonNull);

			if (!functional) {
				Log.e("Permata AudioEngine: Fallback block instantiation aborted. Hardware layers unresponsive.");
				return null;
			}

			return instance;
		}
	}

	/**
	 * Thread-safe engine factory. Builds instances and loads previously saved settings seamlessly.
	 */
	@Nullable
	public static AudioEffects create(@NonNull Context context, int priority, int audioSessionId) {
		if (supported == 0 || audioSessionId <= 0) {
			return null;
		}

		synchronized (ALLOCATION_LOCK) {
			AudioEffects instance = new AudioEffects(priority, audioSessionId);

			boolean functional = Stream.of(instance.equalizer, instance.virtualizer, 
					                        instance.bassBoost, instance.loudnessEnhancer)
					                    .anyMatch(Objects::nonNull);

			if (!functional) {
				Log.e("Permata AudioEngine: Block instantiation aborted. Hardware layers are fully unresponsive.");
				return null;
			}

			instance.loadAndApplyPersistedSettings(context.getApplicationContext());
			return instance;
		}
	}

	@Nullable public Equalizer getEqualizer() { return equalizer; }
	@Nullable public Virtualizer getVirtualizer() { return virtualizer; }
	@Nullable public BassBoost getBassBoost() { return bassBoost; }
	@Nullable public LoudnessEnhancer getLoudnessEnhancer() { return loudnessEnhancer; }

	/**
	 * Reads all saved parameters out of Android SharedPreferences and loads them directly onto the hardware.
	 */
	private void loadAndApplyPersistedSettings(@NonNull Context context) {
		try {
			SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
			
			boolean eqEnabled = prefs.getBoolean(KEY_EQ_ENABLED, false);
			boolean virtEnabled = prefs.getBoolean(KEY_VIRT_ENABLED, false);
			boolean bassEnabled = prefs.getBoolean(KEY_BASS_ENABLED, false);
			boolean loudEnabled = prefs.getBoolean(KEY_LOUD_ENABLED, false);
			boolean volBoostActive = prefs.getBoolean(KEY_VOLUME_BOOST_ACTIVE, false);

			if (bassBoost != null) {
				short bassStrength = (short) prefs.getInt(KEY_BASS_STRENGTH, 0);
				setBassBoostStrength(bassStrength);
			}

			if (virtualizer != null && SDK_INT < VANILLA_ICE_CREAM) {
				short virtStrength = (short) prefs.getInt(KEY_VIRT_STRENGTH, 0);
				setVirtualizerStrength(virtStrength);
			}

			if (loudnessEnhancer != null) {
				int targetGain = prefs.getInt(KEY_LOUD_GAIN, 0);
				setLoudnessEnhancementGain(targetGain);
			}

			if (equalizer != null) {
				short bands = equalizer.getNumberOfBands();
				for (short i = 0; i < bands; i++) {
					short savedGain = (short) prefs.getInt(KEY_EQ_BAND_PREFIX + i, 0);
					setEqualizerBandGain(i, savedGain);
				}
			}

			setEqualizerEnabled(eqEnabled);
			setVirtualizerEnabled(virtEnabled);
			setBassBoostEnabled(bassEnabled);
			
			if (volBoostActive) {
				apply150PercentVolumeBoost(context);
			} else {
				setLoudnessEnhancerEnabled(loudEnabled);
				this.is150PercentBoostActive = false;
			}
			
			Log.i("Permata AudioEngine: State storage tracking profiles synced up successfully.");
		} catch (Exception ex) {
			Log.e(ex, "Permata AudioEngine: Error loading structural data out of storage mapping components.");
		}
	}

	/**
	 * Configures a clean 150% volume gain target (+3.52dB / 352mB) safely managed by look-ahead limiters.
	 * Persists state seamlessly to the application storage block.
	 */
	public void apply150PercentVolumeBoost(@NonNull Context context) {
		synchronized (ALLOCATION_LOCK) {
			if (loudnessEnhancer == null) {
				Log.w("Permata AudioEngine: Cannot apply 150% boost; hardware LoudnessEnhancer is missing.");
				return;
			}
			
			setLoudnessEnhancementGain(GAIN_150_PERCENT_MB);
			setLoudnessEnhancerEnabled(true);
			this.is150PercentBoostActive = true;

			try {
				context.getApplicationContext()
				       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
				       .edit()
				       .putInt(KEY_LOUD_GAIN, GAIN_150_PERCENT_MB)
				       .putBoolean(KEY_LOUD_ENABLED, true)
				       .putBoolean(KEY_VOLUME_BOOST_ACTIVE, true)
				       .apply();
			} catch (Exception ex) {
				Log.e(ex, "Permata AudioEngine: Persistence pipeline commit failed for volume boost toggle.");
			}
			Log.i("Permata AudioEngine: Anti-clipping 150% volume boost sequence engaged and cached.");
		}
	}

	/**
	 * Disables the explicit 150% volume boost multiplier and updates persistence storage.
	 */
	public void disableVolumeBoost(@NonNull Context context) {
		synchronized (ALLOCATION_LOCK) {
			if (loudnessEnhancer == null) return;

			setLoudnessEnhancementGain(0);
			setLoudnessEnhancerEnabled(false);
			this.is150PercentBoostActive = false;
			try {
				context.getApplicationContext()
				       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
				       .edit()
				       .putInt(KEY_LOUD_GAIN, 0)
				       .putBoolean(KEY_LOUD_ENABLED, false)
				       .putBoolean(KEY_VOLUME_BOOST_ACTIVE, false)
				       .apply();
			} catch (Exception ex) {
				Log.e(ex, "Permata AudioEngine: Persistence pipeline clear failed for volume boost toggle.");
			}
		}
	}

	public boolean is150PercentBoostActive() {
		return is150PercentBoostActive;
	}
	public void setEffectsEnabled(@NonNull Context context, boolean enabled) {
		synchronized (ALLOCATION_LOCK) {
			setEqualizerEnabled(context, enabled);
			setVirtualizerEnabled(context, enabled);
			setBassBoostEnabled(context, enabled);
			if (!enabled && is150PercentBoostActive) {
				disableVolumeBoost(context);
			} else {
				setLoudnessEnhancerEnabled(context, enabled);
			}
		}
	}

	public void setEqualizerEnabled(@NonNull Context context, boolean enabled) {
		if (equalizer != null && safeToggleEffect(equalizer, enabled)) {
			equalizerEnabled = enabled;
			persistState(context, KEY_EQ_ENABLED, enabled);
		}
	}

	public void setVirtualizerEnabled(@NonNull Context context, boolean enabled) {
		if (virtualizer != null && SDK_INT < VANILLA_ICE_CREAM && safeToggleEffect(virtualizer, enabled)) {
			virtualizerEnabled = enabled;
			persistState(context, KEY_VIRT_ENABLED, enabled);
		}
	}

	public void setBassBoostEnabled(@NonNull Context context, boolean enabled) {
		if (bassBoost != null && safeToggleEffect(bassBoost, enabled)) {
			bassBoostEnabled = enabled;
			persistState(context, KEY_BASS_ENABLED, enabled);
		}
	}

	public void setLoudnessEnhancerEnabled(@NonNull Context context, boolean enabled) {
		if (loudnessEnhancer != null && safeToggleEffect(loudnessEnhancer, enabled)) {
			loudnessEnhancerEnabled = enabled;
			persistState(context, KEY_LOUD_ENABLED, enabled);
			if (!enabled) {
				persistState(context, KEY_VOLUME_BOOST_ACTIVE, false);
				this.is150PercentBoostActive = false;
			}
		}
	}

	private void setEqualizerEnabled(boolean enabled) {
		if (equalizer != null && safeToggleEffect(equalizer, enabled)) equalizerEnabled = enabled;
	}

	private void setVirtualizerEnabled(boolean enabled) {
		if (virtualizer != null && SDK_INT < VANILLA_ICE_CREAM && safeToggleEffect(virtualizer, enabled)) virtualizerEnabled = enabled;
	}

	private void setBassBoostEnabled(boolean enabled) {
		if (bassBoost != null && safeToggleEffect(bassBoost, enabled)) bassBoostEnabled = enabled;
	}

	private void setLoudnessEnhancerEnabled(boolean enabled) {
		if (loudnessEnhancer != null && safeToggleEffect(loudnessEnhancer, enabled)) loudnessEnhancerEnabled = enabled;
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
		if (equalizer == null) return;
		try {
			short[] range = equalizer.getBandLevelRange();
			if (range != null && range.length >= 2) {
				short clampedGain = (short) Math.max(range[0], Math.min(range[1], gainmB));
				equalizer.setBandLevel(band, clampedGain);
				persistState(context, KEY_EQ_BAND_PREFIX + band, clampedGain);
			}
		} catch (Exception ex) {
			Log.e(ex, "Permata AudioEngine: Equalizer target parameter tracking adjustment failed.");
		}
	}

	private void setEqualizerBandGain(short band, short gainmB) {
		if (equalizer == null) return;
		try {
			short[] range = equalizer.getBandLevelRange();
			if (range != null && range.length >= 2) {
				equalizer.setBandLevel(band, (short) Math.max(range[0], Math.min(range[1], gainmB)));
			}
		} catch (Exception ex) {
			Log.e(ex, "Permata AudioEngine: EQ initialization config injection failed.");
		}
	}

	public void setBassBoostStrength(@NonNull Context context, short strength) {
		if (bassBoost == null) return;
		try {
			if (bassBoost.getStrengthSupported()) {
				short clampedStrength = (short) Math.max((short)0, Math.min((short)1000, strength));
				bassBoost.setStrength(clampedStrength);
				persistState(context, KEY_BASS_STRENGTH, clampedStrength);
			}
		} catch (Exception ex) {
			Log.e(ex, "Permata AudioEngine: BassBoost parameter change configuration dropped.");
		}
	}

	private void setBassBoostStrength(short strength) {
		if (bassBoost != null && bassBoost.getStrengthSupported()) {
			try { bassBoost.setStrength((short) Math.max((short)0, Math.min((short)1000, strength))); } catch (Exception ignored) {}
		}
	}

	public void setVirtualizerStrength(@NonNull Context context, short strength) {
		if (virtualizer == null || SDK_INT >= VANILLA_ICE_CREAM) return;
		try {
			if (virtualizer.getStrengthSupported()) {
				short clampedStrength = (short) Math.max((short)0, Math.min((short)1000, strength));
				virtualizer.setStrength(clampedStrength);
				persistState(context, KEY_VIRT_STRENGTH, clampedStrength);
			}
		} catch (Exception ex) {
			Log.e(ex, "Permata AudioEngine: Virtualizer pipeline adjustments dropped.");
		}
	}

	private void setVirtualizerStrength(short strength) {
		if (virtualizer != null && SDK_INT < VANILLA_ICE_CREAM && virtualizer.getStrengthSupported()) {
			try { virtualizer.setStrength((short) Math.max((short)0, Math.min((short)1000, strength))); } catch (Exception ignored) {}
		}
	}

	public void setLoudnessEnhancementGain(@NonNull Context context, int gainmB) {
		if (loudnessEnhancer == null) return;
		try {
			int clampedGain = Math.max(0, Math.min(3000, gainmB));
			loudnessEnhancer.setTargetGain(clampedGain);
			persistState(context, KEY_LOUD_GAIN, clampedGain);
			if (clampedGain != GAIN_150_PERCENT_MB) {
				persistState(context, KEY_VOLUME_BOOST_ACTIVE, false);
				this.is150PercentBoostActive = false;
			}
		} catch (Exception ex) {
			Log.e(ex, "Permata AudioEngine: Volume normalization parameters adjustment failed.");
		}
	}

	private void setLoudnessEnhancementGain(int gainmB) {
		if (loudnessEnhancer != null) {
			try { loudnessEnhancer.setTargetGain(Math.max(0, Math.min(3000, gainmB))); } catch (Exception ignored) {}
		}
	}

	public void applySerializedPreset(@NonNull Context context, @NonNull EqualizerPreset preset) {
		synchronized (ALLOCATION_LOCK) {
			setEffectsEnabled(context, preset.masterSwitchEnabled);
			setBassBoostStrength(context, preset.bassBoostStrength);
			setVirtualizerStrength(context, preset.virtualizerStrength);
			setLoudnessEnhancementGain(context, preset.loudnessGainmB);
			
			if (equalizer != null && preset.bandGains != null) {
				short totalBands = (short) Math.min(equalizer.getNumberOfBands(), preset.bandGains.length);
				for (short i = 0; i < totalBands; i++) {
					setEqualizerBandGain(context, i, preset.bandGains[i]);
				}
			}
		}
	}

	private static void persistState(@NonNull Context context, String key, boolean value) {
		try {
			context.getApplicationContext()
			       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			       .edit().putBoolean(key, value).apply();
		} catch (Exception ex) {
			Log.e(ex, "Permata AudioEngine: Error updating persistent boolean flag mapping configurations.");
		}
	}

	private static void persistState(@NonNull Context context, String key, int value) {
		try {
			context.getApplicationContext()
			       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			       .edit().putInt(key, value).apply();
		} catch (Exception ex) {
			Log.e(ex, "Permata AudioEngine: Error updating persistent integer mapping metrics.");
		}
	}

	public void release() {
		synchronized (ALLOCATION_LOCK) {
			safeRelease(equalizer);
			equalizer = null;
			equalizerEnabled = false;

			safeRelease(virtualizer);
			virtualizer = null;
			virtualizerEnabled = false;

			safeRelease(bassBoost);
			bassBoost = null;
			bassBoostEnabled = false;

			safeRelease(loudnessEnhancer);
			loudnessEnhancer = null;
			loudnessEnhancerEnabled = false;
			is150PercentBoostActive = false;
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

		public EqualizerPreset(boolean masterSwitchEnabled, short bassBoostStrength, 
		                       short virtualizerStrength, int loudnessGainmB, @NonNull short[] bandGains) {
			this.masterSwitchEnabled = masterSwitchEnabled;
			this.bassBoostStrength = bassBoostStrength;
			this.virtualizerStrength = virtualizerStrength;
			this.loudnessGainmB = loudnessGainmB;
			this.bandGains = Arrays.copyOf(bandGains, bandGains.length);
		}
	}
}
