package my.app.permata.media.engine;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_ERROR;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED;
import static java.lang.System.currentTimeMillis;
import static my.app.permata.media.lib.MediaLib.StreamItem.STREAM_START_TIME;
import static my.app.permata.media.lib.MediaLib.StreamItem.STREAM_END_TIME;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.AudioFocusRequestCompat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import my.app.permata.media.lib.MediaLib.ArchiveItem;
import my.app.permata.media.lib.MediaLib.PlayableItem;
import my.app.permata.media.lib.MediaLib.StreamItem;
import my.app.permata.media.lib.PlayableItemWrapper;
import my.app.permata.ui.view.VideoInfoView;
import my.app.permata.ui.view.VideoView;
import my.app.utils.app.App;
import my.app.utils.async.FutureSupplier;
import my.app.utils.async.Completed;
import my.app.utils.function.Cancellable;
import my.app.utils.text.SharedTextBuilder;
import my.app.utils.text.TextUtils;
import my.app.utils.ui.menu.OverlayMenu;

/**
 * Enterprise-Grade Thread-Safe StreamEngine for Permata Auto.
 * Re-engineered with explicit atomic concurrency guards, modern pattern-matching 
 * instanceof extensions, and robust streaming lag calculations.
 * 
 * @author sklchan77
 */
public class StreamEngine implements MediaEngine, MediaEngine.Listener {
	
	private static final FutureSupplier<Float> SPEED = Completed.completed(1f);
	
	private final MediaEngine eng;
	private final MediaEngine.Listener listener;
	private final Object lock = new Object();
	
	// Monitored State Fields protected by global engine lock loop
	private int state = STATE_STOPPED;
	private PlayableItem source;
	private VideoView videoView;
	private long startTime;
	private long endTime;
	private long position;
	private long lag;
	private long startStamp;
	private long bufferingStamp;
	private Cancellable timer;
	private boolean positionChanged;
	
	@NonNull 
	private FutureSupplier<Long> duration = Completed.completed(0L);

	public StreamEngine(@NonNull MediaEngineProvider p, @NonNull MediaEngine.Listener listener) {
		this.eng = p.createEngine(this);
		this.listener = listener;
	}

	@Override
	public void prepare(@NonNull PlayableItem src) {
		synchronized (lock) {
			if (src instanceof ArchiveItem a) {
				setSource(src, a.getStartTime(), a.getEndTime());
				Optional.ofNullable(listener).ifPresent(l -> l.onEnginePrepared(this));
			} else {
				setSource(src, true);
			}
		}
	}

	private FutureSupplier<MediaDescriptionCompat> setSource(@NonNull PlayableItem src, boolean notify) {
		final PlayableItem old;
		synchronized (lock) {
			old = source;
		}
		
		return src.getMediaDescription().main().onCompletion((md, err) -> {
			synchronized (lock) {
				if (source != old) return;
				
				if (err != null) {
					setSource(src, 0, 0);
					onEngineError(eng, err);
				} else {
					Bundle b = md != null ? md.getExtras() : null;
					if (b != null) {
						setSource(src, b.getLong(STREAM_START_TIME, 0), b.getLong(STREAM_END_TIME, 0));
					} else {
						setSource(src, 0, 0);
					}
					if (notify) {
						Optional.ofNullable(listener).ifPresent(l -> l.onEnginePrepared(this));
					}
				}
			}
		});
	}

	private void setSource(@NonNull PlayableItem src, long start, long end) {
		synchronized (lock) {
			reset();
			source = src;
			startStamp = currentTimeMillis();
			if (start > 0 && start < end) {
				startTime = start;
				endTime = end;
				duration = Completed.completed(end - start);
			}
			
			// --- NEW: Dynamic Audio Effects Per-Stream Channel Profile Sync ---
			Optional.ofNullable(eng.getAudioEffects()).ifPresent(fx -> {
				String channelIdentifier = "stream_" + src.getLocation().hashCode();
			fx.loadAndApplyPersistedSettingsForChannel(App.get(), channelIdentifier); 
			});
			// ------------------------------------------------------------------
		}
	}

	private void updateSource() {
		synchronized (lock) {
			if (state != STATE_PLAYING) return;
			stopTimer();
			final PlayableItem src = source;

			if (src instanceof ArchiveItem) {
				eng.stop();
				state = STATE_STOPPED;
				Optional.ofNullable(listener).ifPresent(l -> l.onEngineEnded(this));
			} else if (src instanceof StreamItem streamItem) {
				if (!positionChanged) {
					long lg = lag;
					setSource(src, false).main().onSuccess(md -> {
						synchronized (lock) {
							if (source != src) return;
							startTimer();
							lag = lg;
							position = 0;
							state = STATE_PLAYING;
							
							VideoInfoView vi = videoView != null ? videoView.getVideoInfoView() : null;
							if (vi != null) {
								vi.onPlayableChanged(src, src);
							}
						}
					});
				} else {
					streamItem.getEpg(startTime + position() + 1000).onSuccess(e -> {
						synchronized (lock) {
							if (e == null || source != src || state != STATE_PLAYING) return;
							startTimer();
							startTime = e.getStartTime();
							endTime = e.getEndTime();
							position = 0;
							startStamp = currentTimeMillis();
							
							VideoInfoView vi = videoView != null ? videoView.getVideoInfoView() : null;
							if (vi != null) {
								src.getMediaDescription().main().and(e.getMediaDescription().main(), (sd, ed) -> {
									synchronized (lock) {
										if (source != src || state != STATE_PLAYING) return;
									}
									MediaDescriptionCompat.Builder dsc = new MediaDescriptionCompat.Builder();
									CharSequence sub = ed.getTitle();
									Uri icon = ed.getIconUri();
									
									dsc.setTitle(sd.getTitle());
									dsc.setDescription(ed.getSubtitle());
									dsc.setIconUri(icon != null ? icon : sd.getIconUri());

									if (sub != null) {
										try (SharedTextBuilder b = SharedTextBuilder.get()) {
											b.append(sub).append(".\n");
											TextUtils.dateToTimeString(b, startTime, false);
											b.append(" - ");
											TextUtils.dateToTimeString(b, endTime, false);
											dsc.setSubtitle(b.toString());
										}
									}
									vi.setDescription(src, dsc.build());
								});
							}
						}
					});
				}
			}
		}
	}

	private void reset() {
		synchronized (lock) {
			stopTimer();
			
			// --- NEW: Clear Active Channel Mapping State ---
			Optional.ofNullable(eng.getAudioEffects()).ifPresent(fx -> 
fx.resetToGlobalSettings(App.get()) 
			);
			// -----------------------------------------------
			
			source = null;
			state = STATE_STOPPED;
			position = -1;
			duration = Completed.completed(0L);
			positionChanged = false;
			startTime = endTime = lag = startStamp = bufferingStamp = 0L;
		}
	}


	private void stopTimer() {
		synchronized (lock) {
			if (timer != null) {
				timer.cancel();
				timer = null;
			}
		}
	}

	private void startTimer() {
		synchronized (lock) {
			stopTimer();
			long dur = endTime - startTime;
			if (dur <= 0) return;
			
			long delay = dur - position();
			final PlayableItem src = source;
			
			timer = App.get().getHandler().schedule(() -> {
				synchronized (lock) {
					if (source != src || state != STATE_PLAYING) return;
					if (dur > position()) {
						startTimer();
					} else {
						updateSource();
					}
				}
			}, delay);
		}
	}
	@Override
	public FutureSupplier<Long> getPosition() {
		synchronized (lock) {
			long pos = position();
			if (state == STATE_PLAYING && (endTime - startTime > 0) && (pos >= duration.peek(0L))) {
				updateSource();
				pos = position();
			}
			return Completed.completed(pos);
		}
	}

	private long position() {
		synchronized (lock) {
			if (startStamp == 0) return position;
			long pos = position + currentTimeMillis() - startStamp - lag;
			return Math.max(pos, 0L);
		}
	}

	@Override
	public void setPosition(long position) {
		synchronized (lock) {
			if (!canSeek() || position > duration.peek(0L)) return;
			stopTimer();
			boolean playing = (state == STATE_PLAYING);
			
			if (source instanceof StreamItem) {
				long max = currentTimeMillis() - startTime;
				if (position >= max) {
					if (playing) {
						positionChanged = false;
						if (eng.getSource() != null && eng.getSource().getLocation().equals(source.getLocation())) {
							return;
						}
					}
					this.position = max;
				} else {
					this.position = position;
				}
			} else {
				this.position = Math.min(position, endTime - startTime);
			}
			
			lag = 0;
			startStamp = 0;
			positionChanged = true;
			if (!playing) return;
			
			eng.stop();
			PlayableItem i = createItem();
			if (i != null) eng.prepare(i);
		}
	}

	@Nullable
	private PlayableItem createItem() {
		synchronized (lock) {
			PlayableItem src = source;
			Uri u = null;
			
			if (src instanceof StreamItem s) {
				if (position == -1) {
					u = s.getLocation();
					position = currentTimeMillis() - startTime;
				} else {
					u = s.getLocation(startTime + position, Long.MAX_VALUE);
				}
			} else if (src instanceof ArchiveItem a) {
				if (position == -1) position = 0;
				long start = a.getStartTime() + position;
				if (a.getParent() != null) {
					u = a.getParent().getLocation(start, a.getEndTime() - start);
				}
			}
			
			if (u == null) {
				eng.stop();
				onEngineError(this, new IllegalArgumentException("Failed to play: " + (source != null ? source.getName() : "unknown")));
				return null;
			} else {
				return new Stream(src, u);
			}
		}
	}

	@Override public void start() {
		synchronized (lock) {
			PlayableItem i = createItem();
			if (i == null) return;
			state = STATE_PLAYING;
			eng.prepare(i);
		}
	}

	@Override public void stop() { synchronized (lock) { eng.stop(); reset(); } }

	@Override public void pause() {
		synchronized (lock) {
			if (!canPause()) return;
			stopTimer();
			position = position();
			lag = 0;
			startStamp = 0;
			state = STATE_PAUSED;
			positionChanged = true;
			eng.stop();
		}
	}

	@Override public void close() { synchronized (lock) { eng.close(); reset(); } }
	@Override public boolean canSeek() { synchronized (lock) { return (startTime < endTime) && eng.canSeek(); } }
	@Override public PlayableItem getSource() { synchronized (lock) { return source; } }
	@NonNull @Override public FutureSupplier<Long> getDuration() { synchronized (lock) { return duration; } }
	@Override public FutureSupplier<Float> getSpeed() { return SPEED; }
	@Override public void setSpeed(float speed) {}
	@Override public int getId() { return eng.getId(); }
	@Override public float getVideoWidth() { return eng.getVideoWidth(); }
	@Override public float getVideoHeight() { return eng.getVideoHeight(); }
	@Nullable @Override public AudioEffects getAudioEffects() { return eng.getAudioEffects(); }
	@Override public boolean isSubtitlesSupported() { return eng.isSubtitlesSupported(); }
	@Override public List<AudioStreamInfo> getAudioStreamInfo() { return eng.getAudioStreamInfo(); }
	@Override public FutureSupplier<List<SubtitleStreamInfo>> getSubtitleStreamInfo() { return eng.getSubtitleStreamInfo(); }
	@Nullable @Override public AudioStreamInfo getCurrentAudioStreamInfo() { return eng.getCurrentAudioStreamInfo(); }
	@Override
	public void setCurrentAudioStream(@Nullable AudioStreamInfo i) {
		// Route track target selections safely to downstream hardware layers
		eng.setCurrentAudioStream(i);
		
		// --- NEW: Audio Stream Track Switch Presets Migration Loop ---
		if (i != null) {
			synchronized (lock) {
				if (source != null) {
					Optional.ofNullable(eng.getAudioEffects()).ifPresent(fx -> {
						String streamTrackIdentifier = "stream_" + source.getLocation().hashCode() + "_track_" + i.getId();
					fx.loadAndApplyPersistedSettingsForChannel(App.get(), streamTrackIdentifier); 
					});
				}
			}
		}
		// -------------------------------------------------------------
	}

	@Nullable @Override public SubtitleStreamInfo getCurrentSubtitleStreamInfo() { return eng.getCurrentSubtitleStreamInfo(); }
	@Override public void setCurrentSubtitleStream(@Nullable SubtitleStreamInfo i) { eng.setCurrentSubtitleStream(i); }
	@Override public boolean isAudioDelaySupported() { return eng.isAudioDelaySupported(); }
	@Override public int getAudioDelay() { return eng.getAudioDelay(); }
	@Override public void setAudioDelay(int ms) { eng.setAudioDelay(ms); }
	@Override public int getSubtitleDelay() { return eng.getSubtitleDelay(); }
	@Override public void setSubtitleDelay(int ms) { eng.setSubtitleDelay(ms); }
	@Override public boolean setSurfaceSize(VideoView view) { return eng.setSurfaceSize(view); }
	@Override public void mute(Context ctx) { eng.mute(ctx); }
	@Override public void unmute(Context ctx) { eng.unmute(ctx); }
	@Override public boolean hasVideoMenu() { return eng.hasVideoMenu(); }
	@Override public void contributeToMenu(OverlayMenu.Builder b) { eng.contributeToMenu(b); }

	@Override 
	public void setVideoView(VideoView view) { 
		synchronized (lock) { 
			this.videoView = view; 
			eng.setVideoView(view); 
		} 
	}

	@Override 
	public boolean requestAudioFocus(@Nullable AudioManager am, @Nullable AudioFocusRequestCompat req) { 
		return eng.requestAudioFocus(am, req); 
	}
	
	@Override 
	public void releaseAudioFocus(@Nullable AudioManager am, @Nullable AudioFocusRequestCompat req) { 
		eng.releaseAudioFocus(am, req); 
	}

	// =========================================================================
	// MEDIAENGINE.LISTENER INTERFACE REDIRECTION CALLBACKS
	// =========================================================================

	@Override public void onEnginePrepared(MediaEngine engine) { eng.start(); }

	@Override 
	public void onEngineStarted(MediaEngine engine) {
		synchronized (lock) {
			startStamp = currentTimeMillis();
			startTimer();
		}
		Optional.ofNullable(listener).ifPresent(l -> l.onEngineStarted(this));
	}

	@Override 
	public void onEngineEnded(MediaEngine engine) {
		synchronized (lock) {
			if (state != STATE_PLAYING) return;
			state = STATE_STOPPED;
		}
		Optional.ofNullable(listener).ifPresent(l -> l.onEngineEnded(this));
	}

	@Override 
	public void onEngineBuffering(MediaEngine engine, int percent) {
		synchronized (lock) {
			if (bufferingStamp == 0L) bufferingStamp = currentTimeMillis();
		}
		Optional.ofNullable(listener).ifPresent(l -> l.onEngineBuffering(this, percent));
	}

	@Override 
	public void onEngineBufferingCompleted(MediaEngine engine) {
		synchronized (lock) {
			if (bufferingStamp > 0) {
				lag += (currentTimeMillis() - bufferingStamp);
				bufferingStamp = 0L;
			}
		}
		Optional.ofNullable(listener).ifPresent(l -> l.onEngineBufferingCompleted(this));
	}

	@Override 
	public void onVideoSizeChanged(MediaEngine engine, int width, int height) {
		Optional.ofNullable(listener).ifPresent(l -> l.onVideoSizeChanged(this, width, height));
	}

	@Override 
	public void onEngineError(MediaEngine engine, Throwable ex) {
		synchronized (lock) {
			state = STATE_ERROR;
		}
		Optional.ofNullable(listener).ifPresent(l -> l.onEngineError(this, ex));
	}

	@NonNull @Override public String toString() { return super.toString(); }

	// =========================================================================
	// INNER STATIC UTILITY PLAYABLE WRAPPER
	// =========================================================================

	private static final class Stream extends PlayableItemWrapper {
		private final Uri location;

		public Stream(PlayableItem item, Uri location) {
			super(item);
			this.location = location;
		}

		@NonNull 
		@Override 
		public Uri getLocation() { 
			return location; 
		}
	}
}
