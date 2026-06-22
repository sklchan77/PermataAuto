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
 * Enterprise-grade, autonomous AudioEffects engine tailored for Fermata Auto.
 * Features safe hardware state caching, parameter selectors, an anti-clipping
 * 150% volume boost routine, and self-contained SharedPreferences storage persistence.
 * 
 * @author sklchan77
 * @author Andrey Pavlenko (Original Author)
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

	// Global synchronization mutex to prevent race conditions during rapid session allocation cycles
	private static final Object ALLOCATION_LOCK = new Object();

	// Volatile markers ensure immediate thread-visibility across background ExoPlayer/VLC processing pipelines
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
			Log.e(ex, "Fermata AudioEngine: Critical system capability query fault dropped out.");
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
			Log.w("Fermata AudioEngine: " + effectName + " initialization failed. Running HAL recovery routine...", ex);
			try {
				Thread.sleep(250);
				return factory.create();
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt(); 
				Log.e(ie, "Fermata AudioEngine: Recovery routine interrupted for " + effectName);
				return null;
			} catch (Exception retryEx) {
				Log.e(retryEx, "Fermata AudioEngine: Low-level driver rejected " + effectName + " permanently.");
				return null;
			}
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
				Log.e("Fermata AudioEngine: Block instantiation aborted. Hardware layers are fully unresponsive.");
				return null;
			}

			// Automatically parse and apply persisted states down to the newly allocated instance hardware
			instance.loadAndApplyPersistedSettings(context.getApplicationContext());
			return instance;
		}
	}

	@Nullable public Equalizer getEqualizer() { return equalizer; }
	@Nullable public Virtualizer getVirtualizer() { return virtualizer; }
	@Nullable public BassBoost getBassBoost() { return bassBoost; }
	@Nullable public LoudnessEnhancer getLoudnessEnhancer() { return loudnessEnhancer; }

	/* ==================================================================================
	 * AUDIO STATE PERSISTENCE CORE IMPLEMENTATION
	 * ================================================================================== */

	/**
	 * Reads all saved parameters out of Android SharedPreferences and loads them directly onto the hardware.
	 */
	private void loadAndApplyPersistedSettings(@NonNull Context context) {
		try {
			SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
			
			// Load core toggle states
			boolean eqEnabled = prefs.getBoolean(KEY_EQ_ENABLED, false);
			boolean virtEnabled = prefs.getBoolean(KEY_VIRT_ENABLED, false);
			boolean bassEnabled = prefs.getBoolean(KEY_BASS_ENABLED, false);
			boolean loudEnabled = prefs.getBoolean(KEY_LOUD_ENABLED, false);
			boolean volBoostActive = prefs.getBoolean(KEY_VOLUME_BOOST_ACTIVE, false);

			// Apply underlying hardware parameters
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

			// Apply master functional toggles
			setEqualizerEnabled(eqEnabled);
			setVirtualizerEnabled(virtEnabled);
			setBassBoostEnabled(bassEnabled);
			
			// Handle 150% volume boost fallback checks cleanly
			if (volBoostActive) {
				apply150PercentVolumeBoost(context);
			} else {
				setLoudnessEnhancerEnabled(loudEnabled);
				this.is150PercentBoostActive = false;
			}
			
			Log.i("Fermata AudioEngine: State storage tracking profiles synced up successfully.");
		} catch (Exception ex) {
			Log.e(ex, "Fermata AudioEngine: Error loading structural data out of storage mapping components.");
		}
	}

	/* ==================================================================================
	 * 150% ANTI-CLIPPING VOLUME CONTROLLER
	 * ================================================================================== */

	/**
	 * Configures a clean 150% volume gain target (+3.52dB / 352mB) safely managed by look-ahead limiters.
	 * Persists state seamlessly to the application storage block.
	 */
	public void apply150PercentVolumeBoost(@NonNull Context context) {
		synchronized (ALLOCATION_LOCK) {
			if (loudnessEnhancer == null) {
				Log.w("Fermata AudioEngine: Cannot apply 150% boost; hardware LoudnessEnhancer is missing.");
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
				Log.e(ex, "Fermata AudioEngine: Persistence pipeline commit failed for volume boost toggle.");
			}
			Log.i("Fermata AudioEngine: Anti-clipping 150% volume boost sequence engaged and cached.");
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
equalizer = null;equalizerEnabled = false;safeRelease(virtualizer);virtualizer = null;virtualizerEnabled = false;safeRelease(bassBoost);bassBoost = null;bassBoostEnabled = false;safeRelease(loudnessEnhancer);loudnessEnhancer = null;loudnessEnhancerEnabled = false;is150PercentBoostActive = false;}}private static void safeRelease(@Nullable AudioEffect effect) {if (effect != null) {try {effect.setEnabled(false);effect.release();} catch (Exception ex) {Log.e(ex, "Fermata AudioEngine: Exception intercepted during a lower-tier component dump routine.");}}}@FunctionalInterfaceprivate interface AudioEffectFactory {T create() throws Exception;}public static final class EqualizerPreset {public final boolean masterSwitchEnabled;public final short bassBoostStrength;public final short virtualizerStrength;public final int loudnessGainmB;@NonNull public final short[] bandGains;public EqualizerPreset(boolean masterSwitchEnabled, short bassBoostStrength,short virtualizerStrength, int loudnessGainmB, @NonNull short[] bandGains) {this.masterSwitchEnabled = masterSwitchEnabled;this.bassBoostStrength = bassBoostStrength;this.virtualizerStrength = virtualizerStrength;this.loudnessGainmB = loudnessGainmB;this.bandGains = Arrays.copyOf(bandGains, bandGains.length);}}}