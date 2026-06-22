package my.app.permata.media.engine;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

import my.app.utils.log.Log;

/**
 * Enhanced AudioEffects manager tailored for media engine pipelines.
 * Patches unstable HAL (Hardware Abstraction Layer) crashes common in Android Auto environments.
 * 
 * @author sklchan77
 */
public final class AudioEffects {
	private static final byte EQUALIZER = 1 << 0;       // Modern bitwise shift definition
	private static final byte VIRTUALIZER = 1 << 1;
	private static final byte BASS_BOOST = 1 << 2;
	private static final byte LOUDNESS_ENHANCER = 1 << 3;
	private static final byte supported;

	private final Equalizer equalizer;
	private final Virtualizer virtualizer;
	private final BassBoost bassBoost;
	private final LoudnessEnhancer loudnessEnhancer;

	static {
		byte s = 0;
		try {
			// Defensively wrap system query to prevent media server crashes on custom car ROMs
			for (AudioEffect.Descriptor d : AudioEffect.queryEffects()) {
				if (AudioEffect.EFFECT_TYPE_EQUALIZER.equals(d.type)) s |= EQUALIZER;
				else if (AudioEffect.EFFECT_TYPE_VIRTUALIZER.equals(d.type)) s |= VIRTUALIZER;
				else if (AudioEffect.EFFECT_TYPE_BASS_BOOST.equals(d.type)) s |= BASS_BOOST;
				else if (AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER.equals(d.type)) s |= LOUDNESS_ENHANCER;
			}
		} catch (Exception ex) {
			Log.e(ex, "Failed to query hardware audio effects");
		}
		supported = s;
	}

	private AudioEffects(int priority, int audioSessionId) {
		// Isolate every instantiation step to guarantee partial success.
		// If one effect is rejected by a broken car head unit, the others still load.
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

	/**
	 * Isolated component factory. Prevents a single faulty hardware driver from taking down 
	 * the entire media playback pipeline.
	 */
	@Nullable
	private static <T extends AudioEffect> T safeCreate(@NonNull AudioEffectFactory<T> factory, @NonNull String effectName) {
		try {
			return factory.create();
		} catch (Exception ex) {
			Log.w("AudioEffect " + effectName + " initialization failed. Retrying after hardware cooldown...", ex);
			try {
				// Yield thread execution context safely to allow Android's AudioFlinger to cycle states
				Thread.sleep(250);
				return factory.create();
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt(); // Restore standard interruption state
				Log.e(ie, "Audio effect creation thread interrupted for " + effectName);
				return null;
			} catch (Exception retryEx) {
				Log.e(retryEx, "Hardware continuously rejected creation of " + effectName);
				return null;
			}
		}
	}

	@Nullable
	public static AudioEffects create(int priority, int audioSessionId) {
		if (supported == 0) return null;

		AudioEffects instance = new AudioEffects(priority, audioSessionId);

		// Use Java 8 Stream to check if the instance is functional.
		// Returns null if the hardware layer completely blocked every single engine effect.
		boolean hasFunctionalEffects = Stream.of(instance.equalizer, instance.virtualizer, 
				                                 instance.bassBoost, instance.loudnessEnhancer)
				                             .anyMatch(Objects::nonNull);

		if (!hasFunctionalEffects) {
			Log.e("AudioEffects wrapper cancelled: Zero functional backend engine components initialized.");
			return null;
		}

		return instance;
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
	 * Safely unbinds native Android OS binders and releases memory hooks without crashing.
	 */
	public void release() {
		safeRelease(equalizer);
		safeRelease(virtualizer);
		safeRelease(bassBoost);
		safeRelease(loudnessEnhancer);
	}

	private static void safeRelease(@Nullable AudioEffect effect) {
		if (effect != null) {
			try {
				effect.setEnabled(false); // Disable processing immediately to clear native queues
				effect.release();         // Safe release of low-level media driver reference
			} catch (Exception ex) {
				Log.e(ex, "Exception trapped during safe hardware-level object release sequence");
			}
		}
	}

	@FunctionalInterface
	private interface AudioEffectFactory<T extends AudioEffect> {
		T create() throws Exception;
	}
}
