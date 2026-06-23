package my.app.permata.media.engine;

import static android.content.ContentResolver.SCHEME_CONTENT;
import static android.media.MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO;
import static java.util.Collections.emptyList;
import static my.app.utils.async.Completed.completed;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import my.app.permata.media.lib.MediaLib.PlayableItem;
import my.app.permata.media.pref.MediaPrefs;
import my.app.permata.ui.view.VideoView;
import my.app.utils.async.FutureSupplier;
import my.app.utils.log.Log;

/**
 * Enterprise-Grade Unified MediaPlayerEngine for Permata Auto.
 * Fully modernized with thread-safe state monitors and functional streams.
 * 
 * @author sklchan77
 */
public class MediaPlayerEngine extends MediaEngineBase
		implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
		MediaPlayer.OnVideoSizeChangedListener, MediaPlayer.OnErrorListener {

	private enum State { IDLE, INITIALIZED, PREPARING, PREPARED, STARTED, PAUSED, STOPPED, COMPLETED, ERROR, RELEASED }

	// Shared State Fields (Single-instance memory footprint)
	private final Context ctx;
	private final MediaPlayer player;
	@Nullable private final AudioEffects audioEffects;
	
	private final Object stateLock = new Object();
	private State currentState = State.IDLE;
	private PlayableItem source;

	// =========================================================================
	// SECTION 1: CORE LIFECYCLE & STATE MANAGEMENT
	// =========================================================================

	public MediaPlayerEngine(@NonNull Context ctx, @NonNull Listener listener) {
		super(listener);
		this.ctx = ctx.getApplicationContext();
		this.player = new MediaPlayer();
		
		int sessionId = player.getAudioSessionId();
		this.audioEffects = AudioEffects.create(this.ctx, 0, sessionId);
		
		AudioAttributes attrs = new AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_MEDIA)
				.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
				.build();
				
		player.setAudioAttributes(attrs);
		player.setOnPreparedListener(this);
		player.setOnCompletionListener(this);
		player.setOnErrorListener(this);
		player.setOnVideoSizeChangedListener(this);
	}

	@Override
	public int getId() {
		return MediaPrefs.MEDIA_ENG_MP;
	}

	@Override
	public void close() {
		synchronized (stateLock) {
			if (currentState == State.RELEASED) return;
			currentState = State.RELEASED;
		}
		
		super.close();

		// Offload native player hardware disposal to a safe background thread
		new Thread(() -> {
			try {
				if (player.isPlaying()) {
					player.stop();
				}
			} catch (Exception ignore) {}

			Optional.ofNullable(audioEffects).ifPresent(AudioEffects::release);
			
			try {
				player.release();
			} catch (Exception ex) {
				Log.e(ex, "Error thrown during hardware media framework native teardown sequence execution hook.");
			}
		}).start();
		
		synchronized (stateLock) {
			source = null;
		}
	}

	@NonNull
	@Override
	public AudioEffects getAudioEffects() {
		return audioEffects;
	}

	@Override
	public PlayableItem getSource() {
		return source;
	}
	// =========================================================================
	// SECTION 2: PLAYBACK CONTROL & STREAM API EXTENSIONS
	// =========================================================================

	@Override
	public void prepare(@NonNull PlayableItem source) {
		synchronized (stateLock) {
			if (currentState == State.RELEASED) return;
			stopped(false);
			this.source = source;
			Uri u = source.getLocation();

			try {
				player.reset();
				currentState = State.IDLE;
				
				String scheme = u.getScheme();
				if (SCHEME_CONTENT.equals(scheme)) {
					player.setDataSource(ctx, u);
				} else if (scheme != null && scheme.startsWith("http")) {
					String agent = source.getUserAgent();
					if (agent != null) {
						player.setDataSource(ctx, u, Map.of("User-Agent", agent));
					} else {
						player.setDataSource(u.toString());
					}
				} else {
					player.setDataSource(ctx, u);
				}
				
				currentState = State.INITIALIZED;
				player.prepareAsync();
				currentState = State.PREPARING;
			} catch (Exception ex) {
				currentState = State.ERROR;
				Optional.ofNullable(listener).ifPresent(l -> l.onEngineError(this, ex));
				this.source = null;
			}
		}
	}

	@Override
	public void start() {
		synchronized (stateLock) {
			if (currentState == State.PREPARED || currentState == State.STARTED || currentState == State.PAUSED || currentState == State.COMPLETED) {
				try {
					player.start();
					currentState = State.STARTED;
					started();
					Optional.ofNullable(listener).ifPresent(l -> l.onEngineStarted(this));
				} catch (IllegalStateException ex) {
					Log.e(ex, "Failed executing transition into play state pipeline loop.");
				}
			}
		}
	}

	@Override
	public void stop() {
		synchronized (stateLock) {
			if (currentState == State.RELEASED || currentState == State.IDLE) return;
			stopped(false);
			try {
				if (currentState == State.STARTED || currentState == State.PAUSED || currentState == State.PREPARED || currentState == State.COMPLETED) {
					player.stop();
				}
				player.reset();
				currentState = State.IDLE;
			} catch (IllegalStateException ex) {
				Log.d(ex, "Hardware device wrapper pipeline reset step rejected.");
			}
			source = null;
		}
	}

	@Override
	public void pause() {
		synchronized (stateLock) {
			if (currentState == State.STARTED) {
				try {
					player.pause();
					currentState = State.PAUSED;
					stopped(true);
				} catch (IllegalStateException ex) {
					Log.d(ex, "Suspension execution directive tracking was ignored.");
				}
			}
		}
	}

	@Override
	public FutureSupplier<Long> getDuration() {
		synchronized (stateLock) {
			if (source == null || !source.isSeekable() || currentState == State.IDLE || currentState == State.INITIALIZED || currentState == State.PREPARING || currentState == State.ERROR || currentState == State.RELEASED) {
				return completed(0L);
			}
			try {
				return completed((long) player.getDuration());
			} catch (IllegalStateException ex) {
				return completed(0L);
			}
		}
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		long pos = pos();
		syncSub(pos, speed(), false);
		return completed(pos);
	}

	@Override
	protected FutureSupplier<Long> getSubtitlePosition() {
		return completed(pos());
	}

	private long pos() {
		synchronized (stateLock) {
			if (source == null || currentState == State.IDLE || currentState == State.INITIALIZED || currentState == State.PREPARING || currentState == State.ERROR || currentState == State.RELEASED) {
				return 0L;
			}
			try {
				return ((long) player.getCurrentPosition() - source.getOffset());
			} catch (IllegalStateException ex) {
				return 0L;
			}
		}
	}

	@Override
	public void setPosition(long position) {
		synchronized (stateLock) {
			if (source == null || currentState == State.IDLE || currentState == State.INITIALIZED || currentState == State.PREPARING || currentState == State.ERROR || currentState == State.RELEASED) return;
			long pos = source.getOffset() + position;
			try {
				player.seekTo((int) pos);
				syncSub(pos, speed(), true);
			} catch (IllegalStateException ex) {
				Log.e(ex, "State configuration blocked seeking target sequence execution index step.");
			}
		}
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		return completed(speed());
	}

	private float speed() {
		synchronized (stateLock) {
			if (currentState == State.RELEASED || currentState == State.ERROR || currentState == State.IDLE) return 1f;
			try {
				PlaybackParams params = player.getPlaybackParams();
				if (params == null) return 1f;
				float speed = params.getSpeed();
				return (speed <= 0f) ? 1f : speed;
			} catch (Exception ex) {
				Log.d(ex);
				return 1f;
			}
		}
	}

	@Override
	public void setSpeed(float speed) {
		synchronized (stateLock) {
			if (currentState == State.RELEASED || currentState == State.ERROR || currentState == State.IDLE) return;
			try {
				PlaybackParams p = player.getPlaybackParams();
				if (p == null) p = new PlaybackParams();
				p.setSpeed(speed);
				player.setPlaybackParams(p);
				syncSub(pos(), speed, true);
			} catch (Exception ex) {
				Log.e(ex, "Failed setting framework variable speed targets metrics value parameters: ", speed);
			}
		}
	}

	@Override
	public void setVideoView(VideoView view) {
		synchronized (stateLock) {
			if (currentState == State.RELEASED) return;
			try {
				super.setVideoView(view);
				player.setDisplay(view == null ? null : view.getVideoSurface().getHolder());
			} catch (Exception ex) {
				Log.e(ex, "Failed mapping low-tier view surface handler layers references.");
			}
		}
	}

	@Override
	public float getVideoWidth() {
		synchronized (stateLock) {
			if (currentState == State.IDLE || currentState == State.INITIALIZED || currentState == State.ERROR || currentState == State.RELEASED) return 0f;
			try {
				return player.getVideoWidth();
			} catch (IllegalStateException e) {
				return 0f;
			}
		}
	}

	@Override
	public float getVideoHeight() {
		synchronized (stateLock) {
			if (currentState == State.IDLE || currentState == State.INITIALIZED || currentState == State.ERROR || currentState == State.RELEASED) return 0f;
			try {
				return player.getVideoHeight();
			} catch (IllegalStateException e) {
				return 0f;
			}
		}
	}

	@Override
	public List<AudioStreamInfo> getAudioStreamInfo() {
		synchronized (stateLock) {
			if (currentState == State.RELEASED || currentState == State.IDLE || currentState == State.INITIALIZED || currentState == State.PREPARING) return emptyList();
			try {
				MediaPlayer.TrackInfo[] tracks = player.getTrackInfo();
				if (tracks == null || tracks.length == 0) return emptyList();
				
				return java.util.stream.IntStream.range(0, tracks.length)
						.filter(i -> tracks[i] != null && tracks[i].getTrackType() == MEDIA_TRACK_TYPE_AUDIO)
						.mapToObj(i -> new AudioStreamInfo(i, tracks[i].getLanguage(), null))
						.collect(java.util.stream.Collectors.toList());
			} catch (Exception ex) {
				Log.e(ex, "Failure thrown when requesting audio tracks allocation mappings definitions layouts.");
				return emptyList();
			}
		}
	}

	@Nullable
	@Override
	public AudioStreamInfo getCurrentAudioStreamInfo() {
		synchronized (stateLock) {
			if (currentState == State.RELEASED || currentState == State.IDLE || currentState == State.INITIALIZED || currentState == State.PREPARING) return null;
			try {
				int id = player.getSelectedTrack(MEDIA_TRACK_TYPE_AUDIO);
				MediaPlayer.TrackInfo[] tracks = player.getTrackInfo();
				
				if (id < 0 || tracks == null || id >= tracks.length) return null;
				
				MediaPlayer.TrackInfo t = tracks[id];
				if (t != null && t.getTrackType() == MEDIA_TRACK_TYPE_AUDIO) {
					return new AudioStreamInfo(id, t.getLanguage(), null);
				}
				return null;
			} catch (Exception ex) {
				Log.e(ex, "Failure querying parameters specifications data out of current track profile block details.");
				return null;
			}
		}
	}

	@Override
	public void setCurrentAudioStream(@Nullable AudioStreamInfo i) {
		synchronized (stateLock) {
			if (currentState == State.RELEASED || currentState == State.IDLE || currentState == State.INITIALIZED) return;
			try {
				if (i != null) {
					player.selectTrack((int) i.getId());
				}
			} catch (Exception ex) {
				Log.e(ex, "Failed configuring physical audio layout routing parameters context definitions mapping switch: ", i);
			}
		}
	}

	@Override
	public void mute(Context ctx) {
		synchronized (stateLock) {
			if (currentState == State.RELEASED) return;
			try {
				player.setVolume(0f, 0f);
			} catch (IllegalStateException ignore) {}
		}
	}

	@Override
	public void unmute(Context ctx) {
		synchronized (stateLock) {
			if (currentState == State.RELEASED) return;
			try {
				player.setVolume(1f, 1f);
			} catch (IllegalStateException ignore) {}
		}
	}
	// =========================================================================
	// SECTION 3: ASYNCHRONOUS FRAMEWORK EVENTS
	// =========================================================================

	@Override
	public void onPrepared(MediaPlayer mp) {
		synchronized (stateLock) {
			if (currentState == State.RELEASED) return;
			currentState = State.PREPARED;
			
			long off = source != null ? source.getOffset() : 0L;
			if (off > 0) {
				try {
					player.seekTo((int) off);
				} catch (IllegalStateException ex) {
					Log.d(ex, "Initial timeline seek offset application parameter context variant dropped.");
				}
			}
			Optional.ofNullable(listener).ifPresent(l -> l.onEnginePrepared(this));
		}
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		synchronized (stateLock) {
			if (currentState == State.RELEASED) return;
			currentState = State.COMPLETED;
			stopped(false);
			try {
				player.reset();
				currentState = State.IDLE;
			} catch (Exception e) {
				Log.e(e, "State context tracking reset routine failed during completing pipeline sequences.");
			}
			Optional.ofNullable(listener).ifPresent(l -> l.onEngineEnded(this));
		}
	}

	@Override
	public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
		if (currentState == State.RELEASED) return;
		Optional.ofNullable(listener).ifPresent(l -> l.onVideoSizeChanged(this, width, height));
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		synchronized (stateLock) {
			if (currentState == State.RELEASED) return true;
			currentState = State.ERROR;
		}

		MediaEngineException err = switch (extra) {
			case MediaPlayer.MEDIA_ERROR_IO -> new MediaEngineException("MEDIA_ERROR_IO");
			case MediaPlayer.MEDIA_ERROR_MALFORMED -> new MediaEngineException("MEDIA_ERROR_MALFORMED");
			case MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> new MediaEngineException("MEDIA_ERROR_UNSUPPORTED");
			case MediaPlayer.MEDIA_ERROR_TIMED_OUT -> new MediaEngineException("MEDIA_ERROR_TIMED_OUT");
			default -> {
				if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
					yield new MediaEngineException("MEDIA_ERROR_SERVER_DIED");
				} else {
					yield new MediaEngineException("MEDIA_ERROR_UNKNOWN");
				}
			}
		};

		Optional.ofNullable(listener).ifPresent(l -> l.onEngineError(this, err));
		return true;
	}
}
