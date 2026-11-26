package my.app.permata.engine.vlc;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static java.util.Collections.emptyList;
import static my.app.permata.media.pref.MediaPrefs.HW_ACCEL_DECODING;
import static my.app.permata.media.pref.MediaPrefs.HW_ACCEL_DISABLED;
import static my.app.permata.media.pref.MediaPrefs.HW_ACCEL_FULL;
import static my.app.permata.media.pref.MediaPrefs.SCALE_16_9;
import static my.app.permata.media.pref.MediaPrefs.SCALE_4_3;
import static my.app.permata.media.pref.MediaPrefs.SCALE_BEST;
import static my.app.permata.media.pref.MediaPrefs.SCALE_FILL;
import static my.app.permata.media.pref.MediaPrefs.SCALE_ORIGINAL;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.completedEmptyList;

import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.SurfaceView;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import my.app.permata.BuildConfig;
import my.app.permata.media.engine.AudioEffects;
import my.app.permata.media.engine.AudioStreamInfo;
import my.app.permata.media.engine.MediaEngine;
import my.app.permata.media.engine.MediaEngineBase;
import my.app.permata.media.engine.MediaEngineException;
import my.app.permata.media.engine.SubtitleStreamInfo;
import my.app.permata.media.lib.MediaLib.PlayableItem;
import my.app.permata.media.pref.MediaPrefs;
import my.app.permata.media.pref.PlayableItemPrefs;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.view.VideoView;
import my.app.utils.async.FutureSupplier;
import my.app.utils.collection.CollectionUtils;
import my.app.utils.io.IoUtils;
import my.app.utils.log.Log;

/**
 * Enterprise-Grade Robust LibVLC Execution Engine for Permata Auto.
 * Section 1: Core Definitions & Context-Aware Initialisation.
 * Fully optimized with thread-safe dynamic channel switching profile synchronization
 * and non-blocking off-main-thread native LibVLC teardown.
 *
 * @author sklchan77
 */
public class VlcEngine extends MediaEngineBase
		implements MediaPlayer.EventListener, IVLCVout.OnNewVideoLayoutListener {

	private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
	private static final ExecutorService VLC_RELEASE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Permata-VlcRelease");
		t.setDaemon(true);
		return t;
	});

	private final VlcEngineProvider provider;
	private final LibVLC vlc;
	@Nullable private MediaPlayer player;
	@Nullable private final AudioEffects effects;

	private final Object engineLock = new Object();
	@NonNull private Source source = Source.NULL;
	private long pendingPosition = -1;
	@Nullable private VideoView videoView;
	private boolean closed = false;

	public VlcEngine(@NonNull VlcEngineProvider provider, @NonNull Listener listener) {
		super(listener);
		this.provider = provider;
		this.vlc = provider.getVlc();
		this.player = new MediaPlayer(vlc);
		this.player.setEventListener(this);

		int sessionId = provider.getAudioSessionId();
		Context appCtx = vlc.getAppContext().getApplicationContext();

		this.effects = (sessionId != AudioManager.ERROR) ? AudioEffects.create(appCtx, 0, sessionId) : null;
	}

	@Nullable
	private MediaPlayer ensurePlayerLocked() {
		if (closed) return null;
		if (player == null) {
			MediaPlayer mp = new MediaPlayer(vlc);
			mp.setEventListener(this);
			player = mp;
			if (videoView != null && Looper.myLooper() == Looper.getMainLooper()) {
				IVLCVout out = mp.getVLCVout();
				out.setVideoView(videoView.getVideoSurface());
				out.setSubtitlesView(videoView.getSubtitleSurface());
				out.attachViews(this);
				setSurfaceSize(videoView);
			}
		}
		return player;
	}

	private static void retireAsync(@Nullable MediaPlayer retiredPlayer, @Nullable Source retiredSource) {
		if (retiredPlayer == null && (retiredSource == null || retiredSource == Source.NULL)) {
			return;
		}
		if (retiredPlayer != null) {
			try {
				retiredPlayer.setEventListener(null);
			} catch (Throwable ignored) {}

			if (Looper.myLooper() == Looper.getMainLooper()) {
				try {
					retiredPlayer.getVLCVout().detachViews();
				} catch (Throwable ignored) {}
				VLC_RELEASE_EXECUTOR.execute(() -> releaseNativeResources(retiredPlayer, retiredSource));
			} else {
				MAIN_HANDLER.post(() -> {
					try {
						retiredPlayer.getVLCVout().detachViews();
					} catch (Throwable ignored) {}
					VLC_RELEASE_EXECUTOR.execute(() -> releaseNativeResources(retiredPlayer, retiredSource));
				});
			}
		} else {
			VLC_RELEASE_EXECUTOR.execute(() -> releaseNativeResources(null, retiredSource));
		}
	}

	private static void releaseNativeResources(@Nullable MediaPlayer retiredPlayer, @Nullable Source retiredSource) {
		if (retiredPlayer != null) {
			try {
				retiredPlayer.stop();
			} catch (Throwable ex) {
				Log.w(ex, "VlcEngine: Exception during async MediaPlayer.stop()");
			}
			try {
				retiredPlayer.release();
			} catch (Throwable ex) {
				Log.w(ex, "VlcEngine: Exception during async MediaPlayer.release()");
			}
		}
		if (retiredSource != null && retiredSource != Source.NULL) {
			try {
				retiredSource.close();
			} catch (Throwable ignored) {}
		}
	}

	@Override
	public int getId() {
		return MediaPrefs.MEDIA_ENG_VLC;
	}

	@Override
	public void prepare(@NonNull PlayableItem sourceItem) {
		final Source prevSource;
		synchronized (engineLock) {
			if (closed) return;
			stopped(false);
			prevSource = this.source;
			this.source = Source.NULL;
			ensurePlayerLocked();
		}
		retireAsync(null, prevSource);

		Media media = null;
		ParcelFileDescriptor fd = null;
		Uri uri = sourceItem.getLocation();
		String scheme = uri.getScheme();

		try {
			if ("content".equals(scheme)) {
				ContentResolver cr = vlc.getAppContext().getContentResolver();
				fd = cr.openFileDescriptor(uri, "r");
				media = (fd != null) ? new Media(vlc, fd.getFileDescriptor()) : new Media(vlc, uri);
			} else {
				media = new Media(vlc, uri);
			}

			if (scheme != null && scheme.startsWith("http")) {
				String agent = sourceItem.getUserAgent();
				if (agent != null) {
					media.addOption(":http-user-agent=" + agent);
				}
			}

			media.addOption(":input-fast-seek");

			switch (sourceItem.getPrefs().getHwAccelPref()) {
				case HW_ACCEL_DECODING -> {
					media.setHWDecoderEnabled(true, true);
					media.addOption(":no-mediacodec-dr");
					media.addOption(":no-omxil-dr");
				}
				case HW_ACCEL_FULL -> media.setHWDecoderEnabled(true, true);
				case HW_ACCEL_DISABLED -> media.setHWDecoderEnabled(false, false);
			}

			final PendingSource pending = new PendingSource(sourceItem, media, fd);
			synchronized (engineLock) {
				if (closed) {
					retireAsync(null, pending);
					return;
				}
				this.source = pending;
			}

			if (media.isParsed()) {
				prepared(pending);
			} else {
				final Media finalMedia = media;
				finalMedia.setEventListener(e -> {
					if (finalMedia.isParsed()) {
						finalMedia.setEventListener(null);
						prepared(pending);
					}
				});
				finalMedia.parseAsync();
			}
		} catch (Throwable ex) {
			IoUtils.close(fd);
			if (media != null) {
				media.setEventListener(null);
				media.release();
			}
			final Source stale;
			synchronized (engineLock) {
				if (this.source == Source.NULL) {
					this.source = new Source(sourceItem, null);
					stale = null;
				} else {
					stale = this.source;
					this.source = new Source(sourceItem, null);
				}
			}
			retireAsync(null, stale);
			Optional.ofNullable(listener).ifPresent(l -> l.onEngineError(this, ex));
		}
	}

	private void prepared(@NonNull PendingSource pendingSource) {
		final MediaPlayer mp;
		synchronized (engineLock) {
			if (closed || pendingSource != this.source) {
				retireAsync(null, pendingSource);
				return;
			}
			mp = ensurePlayerLocked();
			if (mp == null) {
				this.source = Source.NULL;
				retireAsync(null, pendingSource);
				return;
			}
			this.source = pendingSource.prepare();
			this.pendingPosition = -1;
		}

		IMedia media = pendingSource.getMedia();
		long off = pendingSource.getItem().getOffset();
		mp.setMedia(media);
		pendingSource.release();

		// --- Dynamic Per-File Channel Profile Sync Sequence ---
		if (effects != null) {
			String channelIdentifier = "vlc_file_" + pendingSource.getItem().getLocation().hashCode();
			effects.loadAndApplyPersistedSettingsForChannel(vlc.getAppContext(), channelIdentifier);
		}
		// ------------------------------------------------------

		if (off > 0) {
			mp.setTime(off);
		}
		Optional.ofNullable(listener).ifPresent(l -> l.onEnginePrepared(this));
	}

	@Override
	public void start() {
		synchronized (engineLock) {
			if (player != null) {
				player.play();
			}
		}
	}

	@Override
	public void stop() {
		final MediaPlayer retiredPlayer;
		final Source retiredSource;
		synchronized (engineLock) {
			stopped(false);
			pendingPosition = -1;
			retiredPlayer = this.player;
			this.player = null;
			retiredSource = this.source;
			this.source = Source.NULL;
		}
		retireAsync(retiredPlayer, retiredSource);
	}

	@Override
	public void pause() {
		synchronized (engineLock) {
			stopped(true);
			if (player != null) {
				player.pause();
			}
		}
	}

	@Override
	public PlayableItem getSource() {
		synchronized (engineLock) {
			return source.getItem();
		}
	}

	@NonNull
	@Override
	public FutureSupplier<Long> getDuration() {
		synchronized (engineLock) {
			if (!source.isSeekable()) {
				return completed(0L);
			}
			long dur = source.getDuration();
			if (dur <= 0 && player != null) {
				dur = player.getLength();
				if (dur > 0) {
					source.setDuration(dur);
					return completed(dur);
				} else {
					return completed(0L);
				}
			}
			return completed(Math.max(dur, 0L));
		}
	}

	@NonNull
	@Override
	public FutureSupplier<Long> getPosition() {
		final long pos;
		final float rate;
		synchronized (engineLock) {
			pos = pos();
			rate = (player != null) ? player.getRate() : 1.0f;
		}
		syncSub(pos, rate, false);
		return completed(pos);
	}

	@NonNull
	@Override
	protected FutureSupplier<Long> getSubtitlePosition() {
		return completed(pos());
	}

	private long pos() {
		synchronized (engineLock) {
			Source src = source;
			if (src == Source.NULL || !src.isSeekable()) {
				return 0L;
			}
			long current = (pendingPosition == -1)
					? ((player != null) ? player.getTime() : 0L)
					: pendingPosition;
			return Math.max(current - src.getItem().getOffset(), 0L);
		}
	}

	@Override
	public void setPosition(long position) {
		synchronized (engineLock) {
			Source src = source;
			if (src == Source.NULL) return;

			long pos = src.getItem().getOffset() + position;
			if ((isPlaying() || isPaused()) && player != null) {
				player.setTime(pos);
				syncSub(position, player.getRate(), true);
			} else {
				pendingPosition = pos;
			}
		}
	}

	@NonNull
	@Override
	public FutureSupplier<Float> getSpeed() {
		synchronized (engineLock) {
			return completed((player != null) ? player.getRate() : 1.0f);
		}
	}

	@Override
	public void setSpeed(float speed) {
		synchronized (engineLock) {
			if (player != null) {
				player.setRate(speed);
			}
			syncSub(pos(), speed, true);
		}
	}

	@Override
	public void mute(Context ctx) {
		synchronized (engineLock) {
			if (player != null) {
				player.setVolume(0);
			}
		}
	}

	@Override
	public void unmute(Context ctx) {
		synchronized (engineLock) {
			if (player != null) {
				player.setVolume(100);
			}
		}
	}

	@Override
	public void setVideoView(@Nullable VideoView view) {
		synchronized (engineLock) {
			super.setVideoView(view);
			this.videoView = view;
			if (player == null) return;
			IVLCVout out = player.getVLCVout();
			out.detachViews();

			if (view != null) {
				out.setVideoView(view.getVideoSurface());
				out.setSubtitlesView(view.getSubtitleSurface());
				out.attachViews(this);
				setSurfaceSize(view);
			}
		}
	}

	@Override
	public float getVideoWidth() {
		synchronized (engineLock) {
			float w = source.getVideoWidth();
			if ((int) w == 0 && player != null) {
				MediaPlayer.TrackDescription[] tracks = player.getVideoTracks();
				if (tracks != null && tracks.length > 0) {
					return tracks[0].id;
				}
			}
			return w;
		}
	}

	@Override
	public float getVideoHeight() {
		synchronized (engineLock) {
			float h = source.getVideoHeight();
			if ((int) h == 0 && player != null) {
				MediaPlayer.TrackDescription[] tracks = player.getVideoTracks();
				if (tracks != null && tracks.length > 0) {
					return tracks[0].id;
				}
			}
			return h;
		}
	}

	@Nullable
	@Override
	public AudioEffects getAudioEffects() {
		return effects;
	}

	@NonNull
	@Override
	public List<AudioStreamInfo> getAudioStreamInfo() {
		synchronized (engineLock) {
			if (source == Source.NULL || player == null) return emptyList();
			MediaPlayer.TrackDescription[] tracks = player.getAudioTracks();
			if (tracks == null || tracks.length == 0) return emptyList();

			IMedia m = player.getMedia();
			if (m == null) return emptyList();

			try {
				List<AudioStreamInfo> streams = new ArrayList<>(tracks.length);
				for (MediaPlayer.TrackDescription td : tracks) {
					if (td.id == -1) continue;
					IMedia.Track track = m.getTrack(td.id);
					if (track instanceof IMedia.AudioTrack audioTrack) {
						streams.add(new AudioStreamInfo(td.id, audioTrack.language, td.name));
					}
				}
				return streams;
			} finally {
				m.release();
			}
		}
	}

	@Nullable
	@Override
	public AudioStreamInfo getCurrentAudioStreamInfo() {
		synchronized (engineLock) {
			if (player == null) return null;
			int id = player.getAudioTrack();
			return CollectionUtils.find(getAudioStreamInfo(), s -> s.getId() == id);
		}
	}

	@Override
	public void setCurrentAudioStream(@Nullable AudioStreamInfo info) {
		synchronized (engineLock) {
			if (player == null) return;
			player.setAudioTrack(info != null ? (int) info.getId() : -1);

			// --- Audio Stream Track Switch Presets Migration Loop ---
			if (info != null && effects != null && source != Source.NULL) {
				String streamTrackIdentifier = "vlc_file_" + source.getItem().getLocation().hashCode() + "_track_" + info.getId();
				effects.loadAndApplyPersistedSettingsForChannel(vlc.getAppContext(), streamTrackIdentifier);
			}
			// --------------------------------------------------------
		}
	}

	@Override
	public boolean isAudioDelaySupported() {
		return true;
	}

	@Override
	public int getAudioDelay() {
		synchronized (engineLock) {
			return (player != null) ? (int) (player.getAudioDelay() / 1000) : 0;
		}
	}

	@Override
	public void setAudioDelay(int milliseconds) {
		synchronized (engineLock) {
			if (player != null) {
				player.setAudioDelay(milliseconds * 1000L);
			}
		}
	}

	@Override
	public boolean isSubtitlesSupported() {
		synchronized (engineLock) {
			if (super.isSubtitlesSupported()) return true;
			if (player == null) return false;
			MediaPlayer.TrackDescription[] tracks = player.getSpuTracks();
			return tracks != null && tracks.length > 0;
		}
	}

	@NonNull
	@Override
	public FutureSupplier<List<SubtitleStreamInfo>> getSubtitleStreamInfo() {
		synchronized (engineLock) {
			if (source == Source.NULL || player == null) return completedEmptyList();
		}

		return super.getSubtitleStreamInfo().map(subFiles -> {
			final MediaPlayer mp;
			synchronized (engineLock) {
				if (source == Source.NULL || player == null) return subFiles;
				mp = player;
			}
			MediaPlayer.TrackDescription[] tracks = mp.getSpuTracks();
			if (tracks == null || tracks.length == 0) return subFiles;

			IMedia m = mp.getMedia();
			if (m == null) return subFiles;

			try {
				List<SubtitleStreamInfo> streams = new ArrayList<>(subFiles.size() + tracks.length);
				streams.addAll(subFiles);
				for (MediaPlayer.TrackDescription td : tracks) {
					if (td.id == -1) continue;
					IMedia.Track track = m.getTrack(td.id);
					if (track instanceof IMedia.SubtitleTrack subTrack) {
						streams.add(new SubtitleStreamInfo(td.id, subTrack.language, td.name));
					}
				}
				return streams;
			} finally {
				m.release();
			}
		});
	}

	@Nullable
	@Override
	public SubtitleStreamInfo getCurrentSubtitleStreamInfo() {
		SubtitleStreamInfo currentSuper = super.getCurrentSubtitleStreamInfo();
		if (currentSuper != null) return currentSuper;

		final MediaPlayer mp;
		synchronized (engineLock) {
			mp = player;
		}
		if (mp == null) return null;

		IMedia m = mp.getMedia();
		if (m == null) return null;

		try {
			int id = mp.getSpuTrack();
			if (id == -1) return null;
			MediaPlayer.TrackDescription[] tracks = mp.getSpuTracks();
			if (tracks == null || tracks.length == 0) return null;

			for (MediaPlayer.TrackDescription td : tracks) {
				if (td.id != id) continue;
				IMedia.Track track = m.getTrack(id);
				if (track instanceof IMedia.SubtitleTrack subTrack) {
					return new SubtitleStreamInfo(id, subTrack.language, td.name);
				}
			}
			return null;
		} finally {
			m.release();
		}
	}

	@Override
	public void setCurrentSubtitleStream(@Nullable SubtitleStreamInfo info) {
		synchronized (engineLock) {
			if (info == null) {
				if (player != null) player.setSpuTrack(-1);
				super.setCurrentSubtitleStream(null);
			} else if (info.getFiles().isEmpty()) {
				if (player != null) player.setSpuTrack((int) info.getId());
				super.setCurrentSubtitleStream(null);
			} else {
				if (player != null) player.setSpuTrack(-1);
				super.setCurrentSubtitleStream(info);
			}
		}
	}

	@Override
	public void setSubtitleDelay(int milliseconds) {
		super.setSubtitleDelay(milliseconds);
		synchronized (engineLock) {
			if (player != null) {
				player.setSpuDelay(milliseconds * 1000L);
			}
		}
	}

	@Override
	public void close() {
		final MediaPlayer retiredPlayer;
		final Source retiredSource;
		synchronized (engineLock) {
			closed = true;
			stopped(false);
			pendingPosition = -1;
			this.videoView = null;
			retiredPlayer = this.player;
			this.player = null;
			retiredSource = this.source;
			this.source = Source.NULL;
			super.close();
		}
		retireAsync(retiredPlayer, retiredSource);
		Optional.ofNullable(effects).ifPresent(AudioEffects::release);
	}

	@Override
	public void onEvent(@NonNull MediaPlayer.Event event) {
		final MediaPlayer mp;
		synchronized (engineLock) {
			mp = this.player;
			if (closed || mp == null || this.source == Source.NULL) {
				return;
			}
		}

		switch (event.type) {
			case MediaPlayer.Event.Buffering -> {
				float percent = event.getBuffering();
				if (percent == 100F) {
					Optional.ofNullable(listener).ifPresent(l -> l.onEngineBufferingCompleted(this));
				} else {
					Optional.ofNullable(listener).ifPresent(l -> l.onEngineBuffering(this, (int) percent));
				}
			}
			case MediaPlayer.Event.Playing -> {
				synchronized (engineLock) {
					if (closed || this.player == null) return;
					if (this.source instanceof VideoSource vs) {
						PlayableItemPrefs prefs = vs.getItem().getPrefs();
						MediaEngine.selectMediaStream(
								prefs::getAudioIdPref,
								prefs::getAudioLangPref,
								prefs::getAudioKeyPref,
								() -> completed(getAudioStreamInfo()),
								this::setCurrentAudioStream
						);

						if (BuildConfig.AUTO && (videoView != null)) {
							MainActivityDelegate.getActivityDelegate(videoView.getContext()).onSuccess(a -> {
								synchronized (engineLock) {
									if (this.player != null) {
										int delay = prefs.getAudioDelayPref(a.isCarActivity());
										if (delay != 0) this.player.setAudioDelay(delay * 1000L);
									}
								}
							});
						} else {
							int delay = prefs.getAudioDelayPref(false);
							if (delay != 0) this.player.setAudioDelay(delay * 1000L);
						}
					} else {
						this.player.setAudioDelay(0);
					}

					if (pendingPosition != -1) {
						this.player.setTime(pendingPosition);
						pendingPosition = -1;
					}
					if (!isPaused()) {
						this.player.setSpuTrack(-1);
					}
					started();
				}
				Optional.ofNullable(listener).ifPresent(l -> l.onEngineStarted(this));
			}
			case MediaPlayer.Event.EndReached -> {
				boolean isStreamUrl = false;
				synchronized (engineLock) {
					if (closed || this.player == null) return;
					stopped(false);

					// --- Reset Active Channel Profile Context on Media EndReached ---
					if (effects != null) {
						effects.resetToGlobalSettings(vlc.getAppContext());
					}
					// ----------------------------------------------------------------

					PlayableItem s = getSource();
					if (s != null) {
						if (s.isStream()) {
							isStreamUrl = true;
						} else {
							String scheme = s.getLocation().getScheme();
							if (scheme != null && scheme.startsWith("http")) isStreamUrl = true;
						}
					}
				}

				if (isStreamUrl) {
					float pos = mp.getTime();
					float dur = mp.getLength() * 0.9F;
					if (dur > 0 && pos < dur) {
						Log.d("Position=", pos, "< duration=", dur);
						Optional.ofNullable(listener).ifPresent(l -> l.onEngineError(this, new MediaEngineException("Failed to read stream")));
						break;
					}
				}
				Optional.ofNullable(listener).ifPresent(l -> l.onEngineEnded(this));
			}
			case MediaPlayer.Event.EncounteredError ->
				Optional.ofNullable(listener).ifPresent(l -> l.onEngineError(this, new MediaEngineException("VLC Engine Intercepted Critical Playback Exception")));
		}
	}

	@Override
	public void onNewVideoLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
		synchronized (engineLock) {
			if (player == null || videoView == null || !(source instanceof VideoSource src)) return;
			src.videoWidth = width;
			src.videoHeight = height;
			src.visibleVideoWidth = visibleWidth;
			src.visibleVideoHeight = visibleHeight;
			src.videoSarNum = sarNum;
			src.videoSarDen = sarDen;
			setSurfaceSize(videoView, src);
		}
	}

	@Override
	public boolean setSurfaceSize(VideoView view) {
		synchronized (engineLock) {
			if (player != null && source instanceof VideoSource src) {
				setSurfaceSize(view, src);
				return true;
			}
			return false;
		}
	}

	private void setSurfaceSize(VideoView view, VideoSource src) {
		if (player == null) return;
		int sw = view.getWidth();
		int sh = view.getHeight();
		if (sw == 0 || sh == 0) return;

		int scaleType = src.getItem().getPrefs().getVideoScalePref();
		player.getVLCVout().setWindowSize(sw, sh);

		if (src.videoWidth == 0 || src.videoHeight == 0) {
			setPlayerLayout(sw, sh, scaleType);
			setSurfaceLayout(view, MATCH_PARENT, MATCH_PARENT);
			return;
		}

		ViewGroup.LayoutParams lp = view.getVideoSurface().getLayoutParams();
		if (lp.width == MATCH_PARENT && lp.height == MATCH_PARENT) {
			player.setScale(0);
			player.setAspectRatio(null);
		}

		double dw = sw;
		double dh = sh;
		double ar;
		double vw;

		if (src.videoSarDen == src.videoSarNum) {
			vw = src.visibleVideoWidth;
			ar = (double) src.visibleVideoWidth / (double) src.visibleVideoHeight;
		} else {
			vw = src.visibleVideoWidth * ((double) src.videoSarNum / (double) src.videoSarDen);
			ar = vw / src.visibleVideoHeight;
		}

		double dar = dw / dh;
		switch (scaleType) {
			case SCALE_BEST:
			default:
				if (dar < ar) dh = dw / ar;
				else dw = dh * ar;
				break;
			case SCALE_FILL:
				if (dar >= ar) dh = dw / ar;
				else dw = dh * ar;
				break;
			case SCALE_ORIGINAL:
				dh = src.visibleVideoHeight;
				dw = vw;
				break;
			case SCALE_4_3:
				ar = 4.0 / 3.0;
				if (dar < ar) dh = dw / ar;
				else dw = dh * ar;
				break;
			case SCALE_16_9:
				ar = 16.0 / 9.0;
				if (dar < ar) dh = dw / ar;
				else dw = dh * ar;
				break;
		}

		int targetSw = (int) Math.ceil(dw * src.videoWidth / src.visibleVideoWidth);
		int targetSh = (int) Math.ceil(dh * src.videoHeight / src.visibleVideoHeight);
		setSurfaceLayout(view, targetSw, targetSh);
	}

	private void setPlayerLayout(int surfaceW, int surfaceH, int scaleType) {
		if (player == null) return;
		switch (scaleType) {
			case SCALE_BEST:
			default:
				player.setScale(0);
				player.setAspectRatio(null);
				break;
			case SCALE_FILL:
				player.setScale(0);
				player.setAspectRatio(null);
				break;
			case SCALE_ORIGINAL:
				player.setScale(1);
				player.setAspectRatio(null);
				break;
		}
	}

	private void setSurfaceLayout(VideoView view, int width, int height) {
		SurfaceView surface = view.getVideoSurface();
		ViewGroup.LayoutParams lp = surface.getLayoutParams();
		if (lp.width != width || lp.height != height) {
			lp.width = width;
			lp.height = height;
			surface.setLayoutParams(lp);
		}
		SurfaceView subtitles = view.getSubtitleSurface();
		if (subtitles != null) {
			ViewGroup.LayoutParams slp = subtitles.getLayoutParams();
			if (slp.width != width || slp.height != height) {
				slp.width = width;
				slp.height = height;
				subtitles.setLayoutParams(slp);
			}
		}
	}

	private static class Source implements Closeable {
		static final Source NULL = new Source(null, null);
		private final PlayableItem item;
		final ParcelFileDescriptor fd;
		private final AtomicBoolean closed = new AtomicBoolean(false);

		Source(PlayableItem item, ParcelFileDescriptor fd) {
			this.item = item;
			this.fd = fd;
		}

		PlayableItem getItem() { return item; }
		long getDuration() { return 0; }
		boolean isSeekable() { return false; }
		void setDuration(long duration) {}
		int getVideoWidth() { return 0; }
		int getVideoHeight() { return 0; }

		@Override
		@CallSuper
		public void close() {
			if (this == NULL) return;
			if (closed.compareAndSet(false, true) && fd != null) {
				IoUtils.close(fd);
			}
		}
	}

	private static class PendingSource extends Source {
		private final Media media;
		private final AtomicBoolean mediaReleased = new AtomicBoolean(false);

		PendingSource(PlayableItem item, Media media, ParcelFileDescriptor fd) {
			super(item, fd);
			this.media = media;
		}

		Media getMedia() { return media; }

		PreparedSource prepare() {
			PlayableItem pi = getItem();
			boolean seekable = pi.isSeekable();
			long dur = media.getDuration();
			if (dur == -1) {
				Long itemDur = getItem().getDuration().peek();
				if (itemDur != null) dur = itemDur;
			}
			return (pi.isVideo()) ? new VideoSource(pi, fd, dur, seekable) : new PreparedSource(pi, fd, dur, seekable);
		}

		@Override
		public void close() {
			super.close();
			release();
		}

		void release() {
			if (media != null && mediaReleased.compareAndSet(false, true)) {
				media.setEventListener(null);
				media.release();
			}
		}
	}

	private static class PreparedSource extends Source {
		private long duration;
		private final boolean seekable;

		PreparedSource(PlayableItem item, ParcelFileDescriptor fd, long duration, boolean seekable) {
			super(item, fd);
			this.duration = duration;
			this.seekable = seekable;
		}

		@Override long getDuration() { return duration; }
		@Override public boolean isSeekable() { return seekable; }
		@Override void setDuration(long duration) { this.duration = duration; }
	}

	private static final class VideoSource extends PreparedSource {
		int videoWidth;
		int videoHeight;
		int visibleVideoWidth;
		int visibleVideoHeight;
		int videoSarNum;
		int videoSarDen;

		VideoSource(PlayableItem item, ParcelFileDescriptor fd, long duration, boolean seekable) {
			super(item, fd, duration, seekable);
		}

		@Override int getVideoWidth() { return videoWidth; }
		@Override int getVideoHeight() { return videoHeight; }
	}
}