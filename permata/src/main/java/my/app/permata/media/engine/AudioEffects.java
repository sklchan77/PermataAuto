package my.app.permata.media.engine;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.os.Build;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import my.app.utils.log.Log;
import my.app.permata.ui.activity.MainActivityPrefs;

/**
 * @author sklchan77
 */
public class AudioEffects {
	private static final byte EQUALIZER = 1;
	private static final byte VIRTUALIZER = 2;
	private static final byte BASS_BOOST = 4;
	private static final byte LOUDNESS_ENHANCER = 8;
	private static final byte supported;
	private final Equalizer equalizer;
	private final Virtualizer virtualizer;
	private final BassBoost bassBoost;
	private final LoudnessEnhancer loudnessEnhancer;
	
	// Dynamic runtime stream tracker
	private static String sCurrentActiveChannelId = "default_stream";

	static {
		byte s = 0;
		for (AudioEffect.Descriptor d : AudioEffect.queryEffects()) {
			if (AudioEffect.EFFECT_TYPE_EQUALIZER.equals(d.type)) s |= EQUALIZER;
			else if (AudioEffect.EFFECT_TYPE_VIRTUALIZER.equals(d.type)) s |= VIRTUALIZER;
			else if (AudioEffect.EFFECT_TYPE_BASS_BOOST.equals(d.type)) s |= BASS_BOOST;
			else if (AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER.equals(d.type)) s |= LOUDNESS_ENHANCER;
		}
		supported = s;
	}

	public AudioEffects(int priority, int audioSessionId) {
		equalizer = supported(EQUALIZER) ? new Equalizer(priority, audioSessionId) : null;
		virtualizer = SDK_INT < VANILLA_ICE_CREAM && supported(VIRTUALIZER) ?
				new Virtualizer(priority, audioSessionId) : null;
		bassBoost = supported(BASS_BOOST) ? new BassBoost(priority, audioSessionId) : null;
		loudnessEnhancer = supported(LOUDNESS_ENHANCER) ? new LoudnessEnhancer(audioSessionId) : null;

		// Automatically restore channel mapping configuration parameters out of permata.xml
		restoreChannelSettings();
	}

	private static boolean supported(byte type) {
		return (supported & type) != 0;
	}

	/**
	 * Binds active channel URL tracking before a player instance is constructed.
	 */
	public static void setCurrentChannel(String channelIdOrUrl) {
		if (channelIdOrUrl != null) {
			sCurrentActiveChannelId = channelIdOrUrl;
		}
	}

	/**
	 * UI COMMUNICATOR LINK: Call this from the sliders panel to serialize values to permata.xml
	 */
	public static void saveChannelSettings(boolean enabled, short[] bandLevels) {
		if (bandLevels == null) return;
		
		StringBuilder sb = new StringBuilder();
		sb.append(enabled ? "1" : "0");
		for (short level : bandLevels) {
			sb.append(",").append(level);
		}

		try {
			MainActivityPrefs.get().saveChannelAudioEffects(sCurrentActiveChannelId, sb.toString());
		} catch (Exception e) {
			Log.e(e, "Failed to commit equalizer updates into local permata.xml data");
		}
	}

	/**
	 * FIXED LOGIC STRUCT: Safely decodes text tokens array and maps properties down onto native hardware
	 */
	private void restoreChannelSettings() {
		if (equalizer == null) return;

		try {
			String serializedData = MainActivityPrefs.get().getChannelAudioEffects(sCurrentActiveChannelId);
			if (serializedData == null || serializedData.isEmpty()) {
				equalizer.setEnabled(false);
				return;
			}

			String[] tokens = serializedData.split(",");
			if (tokens.length > 0 && "1".equals(tokens[0])) { // FIXED: Compares array element string properly
				short numberOfBands = equalizer.getNumberOfBands();
				int bound = Math.min(numberOfBands, tokens.length - 1);
				
				for (short i = 0; i < bound; i++) {
					int level = Integer.parseInt(tokens[i + 1]);
					equalizer.setBandLevel(i, (short) level);
				}
				equalizer.setEnabled(true);
			} else {
				equalizer.setEnabled(false);
			}
		} catch (Exception e) {
			Log.e(e, "Error parsing XML channel equalization configurations mapping entries");
			try { equalizer.setEnabled(false); } catch (Exception ignored) {}
		}
	}

	/**
	 * VLC EXTENSION BRIDGE: Generates flag commands for VLC engine initialization steps
	 */
	public static ArrayList<String> getVlcEqualizerOptions() {
		ArrayList<String> options = new ArrayList<>();
		
		try {
			String serializedData = MainActivityPrefs.get().getChannelAudioEffects(sCurrentActiveChannelId);
			if (serializedData == null || serializedData.isEmpty() || serializedData.startsWith("0")) {
				options.add("--no-audio-filter");
				return options;
			}

			options.add("--audio-filter=equalizer");
			String[] tokens = serializedData.split(",");
			StringBuilder bandsBuilder = new StringBuilder();
			
			for (int i = 0; i < 10; i++) {
				int levelMilliBels = 0;
				if (i + 1 < tokens.length) {
					levelMilliBels = Integer.parseInt(tokens[i + 1]);
				}
				double decibelValue = levelMilliBels / 100.0;
				bandsBuilder.append(decibelValue).append(" ");
			}
			
			options.add("--equalizer-bands=" + bandsBuilder.toString().trim());
			options.add("--equalizer-preamp=12.0");
		} catch (Exception e) {
			options.add("--no-audio-filter");
		}
		return options;
	}

	@Nullable
	public static AudioEffects create(int priority, int audioSessionId) {
		if (supported == 0) return null;

		try {
			return new AudioEffects(priority, audioSessionId);
		} catch (Exception ex) {
			Log.w("Failed to create AudioEffects - retrying...");

			try {
				Thread.sleep(300);
				return new AudioEffects(priority, audioSessionId);
			} catch (Exception ex1) {
				Log.e(ex1, "Failed to create AudioEffects");
				return null;
			}
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

	public void release() {
		if (equalizer != null) equalizer.release();
		if (virtualizer != null) virtualizer.release();
		if (bassBoost != null) bassBoost.release();
		if (loudnessEnhancer != null) loudnessEnhancer.release();
	}
}
