package my.app.permata.media.engine;

import static android.media.AudioManager.ADJUST_MUTE;
import static android.media.AudioManager.ADJUST_UNMUTE;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
import static android.media.AudioManager.FLAG_SHOW_UI;
import static android.media.AudioManager.STREAM_MUSIC;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.completedEmptyList;
import static my.app.utils.async.Completed.completedVoid;
import static my.app.utils.text.TextUtils.isBlank;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import my.app.permata.media.lib.MediaLib.PlayableItem;
import my.app.permata.media.sub.SubGrid;
import my.app.permata.media.sub.Subtitles;
import my.app.permata.ui.view.VideoView;
import my.app.utils.async.FutureSupplier;
import my.app.utils.function.BiConsumer;
import my.app.utils.function.Consumer;
import my.app.utils.function.Supplier;
import my.app.utils.text.TextUtils;
import my.app.utils.ui.menu.OverlayMenu;

/**
 * Enterprise-Grade Unified MediaEngine Contract for Permata Auto.
 * Fully modernized with fluid Stream selections, type-safe null mappings,
 * and robust focus management.
 * 
 * @author sklchan77
 */
public interface MediaEngine extends Closeable {

	FutureSupplier<SubGrid> NO_SUBTITLES = completed(SubGrid.EMPTY);

	int getId();

	void prepare(@NonNull PlayableItem source);

	void start();

	void stop();

	void pause();

	default boolean canPause() {
		return canSeek();
	}

	default boolean canSeek() {
		return Optional.ofNullable(getSource())
				.map(PlayableItem::isSeekable)
				.orElse(false);
	}

	default boolean muteOnTransientFocusLoss() {
		return !canPause();
	}

	@Nullable
	PlayableItem getSource();

	@NonNull
	FutureSupplier<Long> getDuration();

	@NonNull
	FutureSupplier<Long> getPosition();

	void setPosition(long position);

	@NonNull
	FutureSupplier<Float> getSpeed();

	void setSpeed(float speed);

	void setVideoView(@Nullable VideoView view);

	float getVideoWidth();

	float getVideoHeight();

	@Override
	void close();

	@Nullable
	default AudioEffects getAudioEffects() {
		return null;
	}

	@NonNull
	default List<AudioStreamInfo> getAudioStreamInfo() {
		return Collections.emptyList();
	}

	@Nullable
	default AudioStreamInfo getCurrentAudioStreamInfo() {
		return null;
	}

	default void setCurrentAudioStream(@Nullable AudioStreamInfo i) {}

	default boolean isAudioDelaySupported() {
		return false;
	}

	default int getAudioDelay() {
		return 0;
	}

	default void setAudioDelay(int milliseconds) {}

	default boolean isSubtitlesSupported() {
		return false;
	}
	@NonNull
	default FutureSupplier<List<SubtitleStreamInfo>> getSubtitleStreamInfo() {
		return completedEmptyList();
	}

	@Nullable
	default SubtitleStreamInfo getCurrentSubtitleStreamInfo() {
		return null;
	}

	default void setCurrentSubtitleStream(@Nullable SubtitleStreamInfo i) {}

	@NonNull
	default FutureSupplier<Void> selectSubtitleStream() {
		var src = getSource();
		if (src == null) return completedVoid();
		
		var prefs = src.getPrefs();
		if (prefs != null && prefs.getSubEnabledPref()) {
			int delay = prefs.getSubDelayPref();
			if (delay != 0) {
				setSubtitleDelay(delay);
			}
			return selectMediaStream(
					prefs::getSubIdPref,
					prefs::getSubLangPref,
					prefs::getSubKeyPref,
					this::getSubtitleStreamInfo,
					si -> {
						if (getSource() == src) {
							setCurrentSubtitleStream(si);
						}
					}
			);
		}
		return completedVoid();
	}

	@NonNull
	default FutureSupplier<SubGrid> getCurrentSubtitles() {
		return NO_SUBTITLES;
	}

	default void addSubtitleConsumer(@NonNull BiConsumer<SubGrid.Position, Subtitles.Text> consumer) {}

	default void removeSubtitleConsumer(@NonNull BiConsumer<SubGrid.Position, Subtitles.Text> consumer) {}

	default int getSubtitleDelay() {
		return 0;
	}

	default void setSubtitleDelay(int milliseconds) {}

	/**
	 * Modernized Stream filtering selection pipeline replacing legacy StringTokenizer loop mechanics.
	 */
	@NonNull
	static <I extends MediaStreamInfo> FutureSupplier<Void> selectMediaStream(
			@NonNull Supplier<Long> idSupplier,
			@NonNull Supplier<String> langSupplier,
			@NonNull Supplier<String> keySupplier,
			@NonNull Supplier<FutureSupplier<List<I>>> streamSupplier,
			@NonNull Consumer<I> streamConsumer) {

		Long id = idSupplier.get();
		String rawLang = langSupplier.get();
		String rawKey = keySupplier.get();

		if ((id == null || id == -1) && isBlank(rawLang) && isBlank(rawKey)) {
			return completedVoid();
		}

		return streamSupplier.get().main().map(streams -> {
			if (streams == null || streams.isEmpty()) return null;

			// Priority 1: Match by explicit Track ID Configuration
			if (id != null && id != -1) {
				for (I i : streams) {
					if (i != null && id.equals(i.getId())) {
						streamConsumer.accept(i);
						return null;
					}
				}
			}

			// Priority 2: Match by Language Tags mapping metrics
			String langPattern = Optional.ofNullable(rawLang).map(String::trim).orElse("");
			final boolean[] hasMatching = { false };
			List<I> filteredStreams = streams;

			if (!langPattern.isEmpty()) {
				List<String> langTokens = Arrays.stream(langPattern.split(","))
						.map(String::trim)
						.filter(s -> !s.isEmpty())
						.toList();

				if (!langTokens.isEmpty()) {
					filteredStreams = streams.stream().filter(i -> {
						if (i == null) return false;
						
						List<String> availableLangs = Arrays.asList(i.getIsoLanguage(), i.getLanguage());
						for (String token : langTokens) {
							for (String lang : availableLangs) {
								if (lang == null) continue;
								if (lang.equalsIgnoreCase(token)) {
									hasMatching[0] = true;
									return true;
								}
								if (token.startsWith("+") && lang.endsWith(token.substring(1).trim())) {
									hasMatching[0] = true;
									return true;
								}
								if (token.endsWith("+") && lang.startsWith(token.substring(0, token.length() - 1).trim())) {
									hasMatching[0] = true;
									return true;
								}
							}
						}
						return false;
					}).collect(Collectors.toList());
				}
			}

			// Priority 3: Match by Metadata text keywords
			String keyPattern = Optional.ofNullable(rawKey).map(String::trim).orElse("");
			if (!keyPattern.isEmpty()) {
				List<String> keyTokens = Arrays.stream(keyPattern.split(","))
						.map(String::trim)
						.filter(s -> !s.isEmpty())
						.map(String::toLowerCase)
						.toList();

				if (!keyTokens.isEmpty()) {
					for (String k : keyTokens) {
						for (I i : filteredStreams) {
							if (i == null) continue;
							String dsc = i.getDescription();
							if (dsc != null && TextUtils.containsWord(dsc.toLowerCase(), k)) {
								streamConsumer.accept(i);
								return null;
							}
						}
					}
				}
			}

			// Fallback: Bind first match element from filtering sequence configuration
			if (hasMatching[0] && !filteredStreams.isEmpty()) {
				streamConsumer.accept(filteredStreams.get(0));
			}
			return null;
		});
	}
	default boolean isVideoModeRequired() {
		return Optional.ofNullable(getSource())
				.map(PlayableItem::isVideo)
				.orElse(false);
	}

	default boolean isSplitModeSupported() {
		return true;
	}

	default boolean setSurfaceSize(@NonNull VideoView view) {
		return false;
	}

	default boolean requestAudioFocus(@Nullable AudioManager audioManager, @Nullable AudioFocusRequestCompat audioFocusReq) {
		if (audioManager == null || audioFocusReq == null) return true;
		return AudioManagerCompat.requestAudioFocus(audioManager, audioFocusReq) == AUDIOFOCUS_REQUEST_GRANTED;
	}

	default void releaseAudioFocus(@Nullable AudioManager audioManager, @Nullable AudioFocusRequestCompat audioFocusReq) {
		if (audioManager != null && audioFocusReq != null) {
			AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusReq);
		}
	}

	default boolean hasVideoMenu() {
		return false;
	}

	default void contributeToMenu(@NonNull OverlayMenu.Builder b) {}

	default boolean adjustVolume(int direction) {
		return false;
	}

	default void mute(@NonNull Context ctx) {
		Optional.ofNullable((AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE))
				.ifPresent(am -> am.adjustStreamVolume(STREAM_MUSIC, ADJUST_MUTE, FLAG_SHOW_UI));
	}

	default void unmute(@NonNull Context ctx) {
		Optional.ofNullable((AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE))
				.ifPresent(am -> am.adjustStreamVolume(STREAM_MUSIC, ADJUST_UNMUTE, FLAG_SHOW_UI));
	}

	// =========================================================================
	// NESTED CALLBACK INTERFACE MATRIX
	// =========================================================================

	interface Listener {
		Listener DUMMY = new Listener() {};

		default void onEnginePrepared(@NonNull MediaEngine engine) {}

		default void onEngineStarted(@NonNull MediaEngine engine) {}

		default void onEngineEnded(@NonNull MediaEngine engine) {}

		default void onEngineBuffering(@NonNull MediaEngine engine, int percent) {}

		default void onEngineBufferingCompleted(@NonNull MediaEngine engine) {}

		default void onEngineError(@NonNull MediaEngine engine, @NonNull Throwable ex) {}

		default void onVideoSizeChanged(@NonNull MediaEngine engine, int width, int height) {}

		default void onSubtitleStreamChanged(@NonNull MediaEngine engine, @Nullable SubtitleStreamInfo info) {}
	}
}
