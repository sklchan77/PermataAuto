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
 * Upgraded to fully support automated state persistence per unique channel tracking maps.
 * 
 * @author sklchan77
 */
public final class AudioEffects {
	private static final byte EQUALIZER = 1 << 0;
	private static final byte VIRTUALIZER = 1 << 1;
	private static final byte BASS_BOOST = 1 << 2;
	private static final byte LOUDNESS_ENHANCER = 1 << 3;
	private static final byte supported;

	// Precise 352 millibel constant mapping to a 1.5x (150%) amplitude gain step (reset back to 100%)
	private static final int GAIN_150_PERCENT_MB = 0;

	// Persistence Keys
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
	
	// Holds the active unique tracking index context signature (null maps to the fallback global default layout)
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
		this.virtualizer = (SDK_INT < VANILLA_ICE_CREAM && supported(VIRTUALIZER)) ? safeCreate(() -> new Virtualizer(priority, audioSessionId), "Virtualizer") : null;
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

	/**
	 * Overloaded Factory Fallback. Directly resolves the signature required by MediaPlayerEngine.java
	 * without breaking existing architectures. Persistence layers are bypassed when instantiated via this fallback signature.
	 */
	@Nullable
	public static AudioEffects create(int priority, int audioSessionId) {
		if (supported == 0 || audioSessionId <= 0) {
			return null;
		}
		synchronized (ALLOCATION_LOCK) {
			AudioEffects instance = new AudioEffects(priority, audioSessionId);
			boolean functional = Stream.of(instance.equalizer, instance.virtualizer, instance.bassBoost, instance.loudnessEnhancer).anyMatch(Objects::nonNull);
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
			boolean functional = Stream.of(instance.equalizer, instance.virtualizer, instance.bassBoost, instance.loudnessEnhancer).anyMatch(Objects::nonNull);
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
	 * Public API to dynamically re-sync hardware parameters to an isolated target stream track or channel.
	 */
	public void loadAndApplyPersistedSettingsForChannel(@NonNull Context context, @NonNull String channelId) {
		synchronized (ALLOCATION_LOCK) {
			this.activeChannelId = channelId;
			loadAndApplyPersistedSettings(context);
		}
	}

	/**
	 * Clears the per-channel index memory and reverts the instance state engine back to global processing curves.
	 */
	public void resetToGlobalSettings(@NonNull Context context) {
		synchronized (ALLOCATION_LOCK) {
			this.activeChannelId = null;
			loadAndApplyPersistedSettings(context);
		}
	}

	/**
	 * Reads all saved parameters out of Android SharedPreferences and loads them directly onto the hardware.
	 * Dynamically branches based on whether a unique track channel identifier signature is active.
	 */
	private void loadAndApplyPersistedSettings(@NonNull Context context) {
		try {
			SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
			final String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";

			boolean eqEnabled = prefs.getBoolean(pfx + KEY_EQ_ENABLED, false);
			boolean virtEnabled = prefs.getBoolean(pfx + KEY_VIRT_ENABLED, false);
			boolean bassEnabled = prefs.getBoolean(pfx + KEY_BASS_ENABLED, false);
			boolean loudEnabled = prefs.getBoolean(pfx + KEY_LOUD_ENABLED, false);
			boolean volBoostActive = prefs.getBoolean(pfx + KEY_VOLUME_BOOST_ACTIVE, false);

			if (bassBoost != null) {
				short bassStrength = (short) prefs.getInt(pfx + KEY_BASS_STRENGTH, 0);
				setBassBoostStrengthInternal(bassStrength);
			}
			if (virtualizer != null && SDK_INT < VANILLA_ICE_CREAM) {
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
					short savedGain = (short) prefs.getInt(pfx + KEY_EQ_BAND_PREFIX + i, 0);
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
		try {
			final String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
			context.getApplicationContext()
					.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					.edit()
					.putBoolean(pfx + key, value)
					.apply();
		} catch (Exception ex) {
			Log.e(ex, "Permata AudioEngine: Error updating persistent boolean flag configurations.");
		}
	}

	private void persistState(@NonNull Context context, String key, int value) {
		try {
			final String pfx = (activeChannelId == null) ? "" : activeChannelId + "_";
			context.getApplicationContext()
					.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					.edit()
					.putInt(pfx + key, value)
					.apply();
		} catch (Exception ex) {
			Log.e(ex, "Permata AudioEngine: Error updating persistent integer mapping metrics.");
		}
	}
	public void apply150PercentVolumeBoost(@NonNull Context context) {
		synchronized (ALLOCATION_LOCK) {
			if (apply150PercentVolumeBoostInternal()) {
				persistState(context, KEY_LOUD_GAIN, GAIN_150_PERCENT_MB);
				persistState(context, KEY_LOUD_ENABLED, true);
				persistState(context, KEY_VOLUME_BOOST_ACTIVE, true);
			}
		}
	}

	private boolean apply150PercentVolumeBoostInternal() {
		if (loudnessEnhancer == null) {
			Log.w("Permata AudioEngine: Cannot apply 150% boost; hardware LoudnessEnhancer is missing.");
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
		synchronized (ALLOCATION_LOCK) {
			if (loudnessEnhancer == null) return;
			setLoudnessEnhancementGainInternal(0);
			safeToggleEffect(loudnessEnhancer, false);
			this.loudnessEnhancerEnabled = false;
			this.is150PercentBoostActive = false;

			persistState(context, KEY_LOUD_GAIN, 0);
			persistState(context, KEY_LOUD_ENABLED, false);
			persistState(context, KEY_VOLUME_BOOST_ACTIVE, false);
		}
	}

	public boolean is150PercentBoostActive() { return is150PercentBoostActive; }

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
		synchronized (ALLOCATION_LOCK) {
			if (setEqualizerEnabledInternal(enabled)) {
				persistState(context, KEY_EQ_ENABLED, enabled);
			}
		}
	}

	private boolean setEqualizerEnabledInternal(boolean enabled) {
		return equalizer != null && safeToggleEffect(equalizer, enabled) && (equalizerEnabled = enabled || true);
	}

	public void setVirtualizerEnabled(@NonNull Context context, boolean enabled) {
		synchronized (ALLOCATION_LOCK) {
			if (setVirtualizerEnabledInternal(enabled)) {
				persistState(context, KEY_VIRT_ENABLED, enabled);
			}
		}
	}

	private boolean setVirtualizerEnabledInternal(boolean enabled) {
		return virtualizer != null && SDK_INT < VANILLA_ICE_CREAM && safeToggleEffect(virtualizer, enabled) && (virtualizerEnabled = enabled || true);
	}

	public void setBassBoostEnabled(@NonNull Context context, boolean enabled) {
		synchronized (ALLOCATION_LOCK) {
			if (setBassBoostEnabledInternal(enabled)) {
				persistState(context, KEY_BASS_ENABLED, enabled);
			}
		}
	}

	private boolean setBassBoostEnabledInternal(boolean enabled) {
		return bassBoost != null && safeToggleEffect(bassBoost, enabled) && (bassBoostEnabled = enabled || true);
	}

	public void setLoudnessEnhancerEnabled(@NonNull Context context, boolean enabled) {
		synchronized (ALLOCATION_LOCK) {
			if (setLoudnessEnhancerEnabledInternal(enabled)) {
				persistState(context, KEY_LOUD_ENABLED, enabled);
				if (!enabled) {
					persistState(context, KEY_VOLUME_BOOST_ACTIVE, false);
					this.is150PercentBoostActive = false;
				}
			}
		}
	}

	private boolean setLoudnessEnhancerEnabledInternal(boolean enabled) {
		return loudnessEnhancer != null && safeToggleEffect(loudnessEnhancer, enabled) && (loudnessEnhancerEnabled = enabled || true);
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
		synchronized (ALLOCATION_LOCK) {
			if (setEqualizerBandGainInternal(band, gainmB)) {
				persistState(context, KEY_EQ_BAND_PREFIX + band, (int) gainmB);
			}
		}
	}

	private boolean setEqualizerBandGainInternal(short band, short gainmB) {
		if (equalizer == null) return false;
		try {
			short[] range = equalizer.getBandLevelRange();
			if (range != null && range.length >= 2) {
				short clampedGain = (short) Math.max(range[0], Math.min(range[1], gainmB));
				equalizer.setBandLevel(band, clampedGain);
				return true;
			}
		} catch (Exception ex) {
			Log.e(ex, "Permata AudioEngine: Equalizer target parameter tracking adjustment failed.");
		}
		return false;
	}

	public void setBassBoostStrength(@NonNull Context context, short strength) {
		synchronized (ALLOCATION_LOCK) {
			if (setBassBoostStrengthInternal(strength)) {
				persistState(context, KEY_BASS_STRENGTH, (int) strength);
			}
		}
	}

	private boolean setBassBoostStrengthInternal(short strength) {
		if (bassBoost == null) return false;
		try {
			if (bassBoost.getStrengthSupported()) {
				short clampedStrength = (short) Math.max((short) 0, Math.min((short) 1000, strength));
				bassBoost.setStrength(clampedStrength);
				return true;
			}
		} catch (Exception ex) {
			Log.e(ex, "Permata AudioEngine: BassBoost parameter change configuration dropped.");
		}
		return false;
	}

	public void setVirtualizerStrength(@NonNull Context context, short strength) {
		synchronized (ALLOCATION_LOCK) {
			if (setVirtualizerStrengthInternal(strength)) {
				persistState(context, KEY_VIRT_STRENGTH, (int) strength);
			}
		}
	}

	private boolean setVirtualizerStrengthInternal(short strength) {
		if (virtualizer == null || SDK_INT >= VANILLA_ICE_CREAM) return false;
		try {
			if (virtualizer.getStrengthSupported()) {
				short clampedStrength = (short) Math.max((short) 0, Math.min((short) 1000, strength));
				virtualizer.setStrength(clampedStrength);
				return true;
			}
		} catch (Exception ex) {
			Log.e(ex, "Permata AudioEngine: Virtualizer pipeline adjustments dropped.");
		}
		return false;
	}

	public void setLoudnessEnhancementGain(@NonNull Context context, int gainmB) {
		synchronized (ALLOCATION_LOCK) {
			if (setLoudnessEnhancementGainInternal(gainmB)) {
				persistState(context, KEY_LOUD_GAIN, gainmB);
				if (gainmB != GAIN_150_PERCENT_MB) {
					persistState(context, KEY_VOLUME_BOOST_ACTIVE, false);
					this.is150PercentBoostActive = false;
				}
			}
		}
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
