package my.app.permata.engine.exoplayer;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.completedNull;
import static my.app.utils.async.Completed.completedVoid;
import static my.app.utils.misc.Assert.assertMainThread;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.HandlerWrapper;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cronet.CronetDataSource;
import androidx.media3.datasource.cronet.CronetUtil;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;

import org.chromium.net.CronetEngine;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import my.app.permata.BuildConfig;
import my.app.permata.PermataApplication;
import my.app.permata.addon.SubGenAddon;
import my.app.permata.addon.TranslateAddon;
import my.app.permata.addon.TranslateAddon.Translator;
import my.app.permata.media.engine.AudioEffects;
import my.app.permata.media.engine.AudioStreamInfo;
import my.app.permata.media.engine.MediaEngine;
import my.app.permata.media.engine.MediaEngineBase;
import my.app.permata.media.engine.SubtitleStreamInfo;
import my.app.permata.media.lib.MediaLib.PlayableItem;
import my.app.permata.media.pref.MediaPrefs;
import my.app.permata.media.service.MediaSessionCallback;
import my.app.permata.media.sub.SubGrid;
import my.app.permata.media.sub.Subtitles;
import my.app.permata.ui.view.VideoView;
import my.app.utils.app.App;
import my.app.utils.async.FutureSupplier;
import my.app.utils.log.Log;
import my.app.utils.text.SharedTextBuilder;
/**
 * Enterprise-Grade ExoPlayerEngine for Permata Auto Media Player.
 * Re-engineered with explicit synchronized bounds monitors, Media3 capability matrices,
 * async thread isolation, and proactive memory leak mitigations.
 * Optimized for continuous deep-buffered live streaming playback profiles with adaptive network recovery.
 *
 * @author sklchan77 (Optimized Modern Version)
 */
@UnstableApi
public class ExoPlayerEngine extends MediaEngineBase implements Player.Listener {

    private static final DataSource.Factory httpDsFactory;
    private static final ExecutorService asyncIoExecutor = Executors.newSingleThreadExecutor();

    static {
        CronetEngine cre = null;
        try {
            cre = CronetUtil.buildCronetEngine(
                    PermataApplication.get(),
                    "Permata/" + BuildConfig.VERSION_NAME,
                    true
            );
        } catch (Exception e) {
            Log.e(e, "Cronet engine initialization failed safely falling back.");
        }

        if (cre != null) {
            httpDsFactory = new CronetDataSource.Factory(cre, asyncIoExecutor);
        } else {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
            CookieHandler.setDefault(cookieManager);
            httpDsFactory = new DefaultHttpDataSource.Factory();
        }
    }
    private final Accessor accessor = new Accessor(this);
    private final Timeline.Period period = new Timeline.Period();
    private final PendingLoadAudioProcessor audioProc = new PendingLoadAudioProcessor(accessor);
    
    private ExoPlayer player;
    private AudioEffects audioEffects;
    private final Object engineLock = new Object();
    
    private volatile PlayableItem source;
    private volatile boolean preparing;
    private volatile boolean buffering;
    private volatile boolean isHls;
    private volatile Runnable drainBuffer;

    public ExoPlayerEngine(@NonNull Context ctx, @NonNull Listener listener) {
        super(listener);
        Context appCtx = ctx.getApplicationContext();

        // 1. Adaptive Bitrate (ABR) Optimization via explicit Bandwidth Meter
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter.Builder(appCtx)
                .setInitialBitrateEstimate(2_000_000)
                .build();

        DefaultDataSource.Factory dsFactory = new DefaultDataSource.Factory(appCtx, httpDsFactory);
        
        // 2. Network Recovery Policies injected to MediaSource (Automatic 5x cellular/tower reconnection)
        DefaultLoadErrorHandlingPolicy customErrorPolicy = new DefaultLoadErrorHandlingPolicy() {
            @Override
            public long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {
                if (loadErrorInfo.exception instanceof IOException) {
                    return Math.min(1000L * (1L << loadErrorInfo.errorCount), 8000L);
                }
                return C.TIME_UNSET;
            }

            @Override
            public int getMinimumLoadableRetryCount(int dataType) {
                return 5;
            }
        };

        MediaSource.Factory msFactory = new DefaultMediaSourceFactory(appCtx)
                .setDataSourceFactory(dsFactory)
                .setLoadErrorHandlingPolicy(customErrorPolicy);

        // 3. Mid-stream resolution optimization via customized buildVideoRenderers (Eliminates video flash frames)
        // Optimization: Force hardware asynchronous codec processing loops to safeguard against frames dropping
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(appCtx) {
            @Override
            protected void buildVideoRenderers(
                    @NonNull Context context,
                    int extensionRendererMode,
                    @NonNull androidx.media3.exoplayer.mediacodec.MediaCodecSelector mediaCodecSelector,
                    boolean enableDecoderFallback,
                    @NonNull android.os.Handler eventHandler,
                    @NonNull androidx.media3.exoplayer.video.VideoRendererEventListener eventListener,
                    long allowedVideoJoiningTimeMs,
                    @NonNull ArrayList<androidx.media3.exoplayer.Renderer> out) {
                super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, 
                        enableDecoderFallback, eventHandler, eventListener, 5000L, out);
            }

            @Override
            protected AudioSink buildAudioSink(
                    @NonNull Context context,
                    boolean enableFloatOutput,
                    boolean enableAudioTrackPlaybackParams) {
                return new DefaultAudioSink.Builder(context)
                        .setAudioTrackBufferSizeProvider(new DefaultAudioTrackBufferSizeProvider.Builder()
                                .setMaxPcmBufferDurationUs(5000_000)
                                .setPcmBufferMultiplicationFactor(16)
                                .setOffloadBufferDurationUs(120_000_000)
                                .build())
                        // Optimization: Enable dynamic track clock adjustments during bitrate adaptations
                        .setEnableAudioTrackPlaybackParams(true)
                        .setAudioProcessorChain(new DefaultAudioSink.DefaultAudioProcessorChain(audioProc))
                        .build();
            }
        };
        
        // Force asynchronous processing behavior onto the core renderers factory instance
        renderersFactory.forceEnableMediaCodecAsynchronousQueueing();

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(45_000, 75_000, 10_000, 10_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        DefaultLivePlaybackSpeedControl liveSpeedControl = new DefaultLivePlaybackSpeedControl.Builder()
                .setFallbackMinPlaybackSpeed(0.95f)
                .setFallbackMaxPlaybackSpeed(1.05f)
                .build();

        this.player = new ExoPlayer.Builder(appCtx, renderersFactory)
                .setMediaSourceFactory(msFactory)
                .setLoadControl(loadControl)
                .setLivePlaybackSpeedControl(liveSpeedControl)
                .setBandwidthMeter(bandwidthMeter)
                .build();

        this.player.addListener(this);
        this.audioEffects = AudioEffects.create(appCtx, 0, player.getAudioSessionId());

        asyncIoExecutor.execute(() -> {
            synchronized (engineLock) {
                if (player == null) return;
                try {
                    Field f = player.getClass().getDeclaredField("internalPlayer");
                    f.setAccessible(true);
                    Object internal = requireNonNull(f.get(player));
                    Field fh = internal.getClass().getDeclaredField("handler");
                    fh.setAccessible(true);
                    HandlerWrapper handler = (HandlerWrapper) requireNonNull(fh.get(internal));
                    
                    this.drainBuffer = () -> {
                        try {
                            handler.sendEmptyMessage(2 /* MSG_DO_SOME_WORK */);
                        } catch (Exception err) {
                            Log.w(err);
                        }
                    };
                } catch (Exception err) {
                    Log.w(err, "Failed establishing internal player hooks.");
                }
            }
        });
    }

    @Override
    public int getId() {
        return MediaPrefs.MEDIA_ENG_EXO;
    }
    @SuppressLint("SwitchIntDef")
    @Override
    public void prepare(@NonNull PlayableItem source) {
        synchronized (engineLock) {
            if (this.source == null) {
                stopped(false);
            } else {
                stop();
            }
            this.source = source;
            accessor.sourceChanged(source);
            this.preparing = true;
            this.buffering = false;

            Uri uri = source.getLocation();
            this.isHls = Util.inferContentType(uri) == C.CONTENT_TYPE_HLS;

            MediaItem m = new MediaItem.Builder()
                    .setUri(uri)
                    .setLiveConfiguration(new MediaItem.LiveConfiguration.Builder()
                            .setTargetOffsetMs(45_000)
                            .build())
                    .build();

            final int uriHash = uri.hashCode();
            asyncIoExecutor.execute(() -> {
                AudioEffects fx = getAudioEffects();
                if (fx != null) {
                    String channelIdentifier = "exo_file_" + uriHash;
                    fx.loadAndApplyPersistedSettingsForChannel(App.get(), channelIdentifier);
                }
            });

            if (player != null) {
                player.setMediaItem(m);
                player.prepare();
            }
        }
    }
    @Override
    public void start() {
        synchronized (engineLock) {
            if (player != null) {
                player.setPlayWhenReady(true);
            }
            Optional.ofNullable(listener).ifPresent(l -> l.onEngineStarted(this));
            started();
        }
    }

    @Override
    public void stop() {
        synchronized (engineLock) {
            stopped(false);
            if (player != null) {
                player.stop();
            }
            this.source = null;
            accessor.sourceChanged(null);
        }
    }

    @Override
    public void pause() {
        synchronized (engineLock) {
            stopped(true);
            if (player != null) {
                player.setPlayWhenReady(false);
            }
        }
    }
    @Override
    public PlayableItem getSource() {
        return source;
    }

    @Override
    public FutureSupplier<Long> getDuration() {
        synchronized (engineLock) {
            if (player == null) return completed(0L);
            long dur = (!isHls && source != null) ? player.getDuration() : 0L;
            return completed(dur < 0 ? 0L : dur);
        }
    }
    @Override
    public FutureSupplier<Long> getPosition() {
        syncSub(false);
        return completed(pos());
    }

    @Override
    protected FutureSupplier<Long> getSubtitlePosition() {
        return completed(pos());
    }

    private long pos() {
        synchronized (engineLock) {
            if (source == null || player == null) return 0L;
            long pos = player.getCurrentPosition();
            if (isHls) {
                Timeline tl = player.getCurrentTimeline();
                if (!tl.isEmpty()) {
                    pos -= tl.getPeriod(player.getCurrentPeriodIndex(), period).getPositionInWindowMs();
                }
            }
            long offsetPos = pos - source.getOffset();
            return Math.max(offsetPos, 0L);
        }
    }
    @Override
    protected long subSchedulerClock() {
        return pos();
    }

    void syncSub(boolean restart) {
        syncSub(subSchedulerClock(), speed(), restart);
    }

    @Override
    public void setPosition(long position) {
        synchronized (engineLock) {
            if (source == null || player == null) return;
            long pos = source.getOffset() + position;
            player.seekTo(pos);
            accessor.setSubGenTimeOffset(this);
            syncSub(true);
        }
    }
    @Override
    public FutureSupplier<Float> getSpeed() {
        return completed(speed());
    }

    private float speed() {
        synchronized (engineLock) {
            if (player == null) return 1f;
            try {
                return player.getPlaybackParameters().speed;
            } catch (Exception ex) {
                return 1f;
            }
        }
    }

    @Override
    public void setSpeed(float speed) {
        synchronized (engineLock) {
            if (player != null) {
                player.setPlaybackParameters(new PlaybackParameters(speed));
            }
            syncSub(true);
        }
    }

    @Override
    public void setVideoView(@Nullable VideoView view) {
        synchronized (engineLock) {
            super.setVideoView(view);
            if (player != null) {
                player.setVideoSurfaceHolder(view == null ? null : view.getVideoSurface().getHolder());
            }
        }
    }

    @Override
    public float getVideoWidth() {
        synchronized (engineLock) {
            if (player == null) return 0f;
            Format f = player.getVideoFormat();
            return f == null ? 0f : f.width;
        }
    }

    @Override
    public float getVideoHeight() {
        synchronized (engineLock) {
            if (player == null) return 0f;
            Format f = player.getVideoFormat();
            return f == null ? 0f : f.height;
        }
    }
    @Nullable
    @Override
    public AudioEffects getAudioEffects() {
        synchronized (engineLock) {
            return audioEffects;
        }
    }

    @Override
    public List<AudioStreamInfo> getAudioStreamInfo() {
        synchronized (engineLock) {
            if (player == null) return emptyList();
            try {
                var groups = player.getCurrentTracks().getGroups();
                var streams = new ArrayList<AudioStreamInfo>();
                for (int i = 0; i < groups.size(); i++) {
                    var group = groups.get(i);
                    if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
                    for (int j = 0; j < group.length; j++) {
                        var fmt = group.getTrackFormat(j);
                        streams.add(new AudioStreamInfo(i * 1000L + j, fmt.language, fmt.label));
                    }
                }
                return streams;
            } catch (Exception ex) {
                return emptyList();
            }
        }
    }
    @Nullable
    @Override
    public AudioStreamInfo getCurrentAudioStreamInfo() {
        synchronized (engineLock) {
            if (player == null) return null;
            try {
                var groups = player.getCurrentTracks().getGroups();
                for (int i = 0; i < groups.size(); i++) {
                    var group = groups.get(i);
                    if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
                    for (int j = 0; j < group.length; j++) {
                        if (group.isTrackSelected(j)) {
                            var fmt = group.getTrackFormat(j);
                            return new AudioStreamInfo(i * 1000L + j, fmt.language, fmt.label);
                        }
                    }
                }
                return null;
            } catch (Exception ex) {
                return null;
            }
        }
    }
    @Override
    public void setCurrentAudioStream(@Nullable AudioStreamInfo info) {
        if (info == null) return;
        synchronized (engineLock) {
            if (player == null) return;
            try {
                var groups = player.getCurrentTracks().getGroups();
                for (int i = 0; i < groups.size(); i++) {
                    var group = groups.get(i);
                    if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
                    for (int j = 0; j < group.length; j++) {
                        if (info.getId() != (i * 1000L + j)) continue;

                        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                                .setOverrideForType(new androidx.media3.common.TrackSelectionOverride(
                                        group.getMediaTrackGroup(), j))
                                .build());

                        final long streamTrackId = info.getId();
                        final PlayableItem currentSrc = source;
                        if (currentSrc != null) {
                            asyncIoExecutor.execute(() -> {
                                AudioEffects fx = getAudioEffects();
                                if (fx != null) {
                                    String streamTrackIdentifier = "exo_file_" + currentSrc.getLocation().hashCode() + "_track_" + streamTrackId;
                                    fx.loadAndApplyPersistedSettingsForChannel(App.get(), streamTrackIdentifier);
                                }
                            });
                        }
                        return;
                    }
                }
            } catch (Exception ex) {
                Log.e(ex, "Failed setting active exoplayer audio track target override.");
            }
        }
    }
    @NonNull
    @Override
    public FutureSupplier<Void> selectSubtitleStream() {
        var src = getSource();
        if (src == null) return completedVoid();
        var ps = src.getPrefs();
        if (!ps.getBooleanPref(SubGenAddon.ENABLED)) {
            return super.selectSubtitleStream();
        }
        setCurrentSubtitleStream(new SubtitleStreamInfo.Generated(ps.getStringPref(SubGenAddon.LANG)));
        if (BuildConfig.AUTO && !src.isVideo() && (listener instanceof MediaSessionCallback cb)) {
            addSubtitleConsumer(cb);
        }
        return completedVoid();
    }
    @Override
    public FutureSupplier<SubGrid> getCurrentSubtitles() {
        var cur = super.getCurrentSubtitles();
        if (cur != NO_SUBTITLES) return cur;
        var src = getSource();
        if (src == null) return cur;
        var ps = src.getPrefs();
        if (!ps.getBooleanPref(SubGenAddon.ENABLED)) return cur;
        setCurrentSubtitleStream(new SubtitleStreamInfo.Generated(ps.getStringPref(SubGenAddon.LANG)));
        return super.getCurrentSubtitles();
    }

    @Override
    public FutureSupplier<List<SubtitleStreamInfo>> getSubtitleStreamInfo() {
        return super.getSubtitleStreamInfo().main().map(subFiles -> {
            var src = getSource();
            if (src == null) return emptyList();
            var ps = src.getPrefs();
            if (ps.getBooleanPref(SubGenAddon.ENABLED)) {
                var streams = new ArrayList<SubtitleStreamInfo>(subFiles.size() + 1);
                streams.add(new SubtitleStreamInfo.Generated(ps.getStringPref(SubGenAddon.LANG)));
                streams.addAll(subFiles);
                return streams;
            }
            return subFiles;
        });
    }
    @Override
    public void close() {
        synchronized (engineLock) {
            stop();
            super.close();
            this.drainBuffer = null;
            accessor.releasePlayerEngineReference();
            if (player != null) {
                player.removeListener(this);
                player.release();
                player = null;
            }
            this.source = null;
            Optional.ofNullable(audioEffects).ifPresent(AudioEffects::release);
            audioEffects = null;
        }
    }

    @Override
    public void mute(@NonNull Context ctx) {
        synchronized (engineLock) {
            if (player != null) player.setVolume(0f);
        }
    }

    @Override
    public void unmute(@NonNull Context ctx) {
        synchronized (engineLock) {
            if (player != null) player.setVolume(1f);
        }
    }
    @Override
    public void onPlaybackStateChanged(int playbackState) {
        synchronized (engineLock) {
            if (player == null) return;
            if (playbackState == Player.STATE_BUFFERING) {
                this.buffering = true;
                Optional.ofNullable(listener).ifPresent(l -> l.onEngineBuffering(this, player.getBufferedPercentage()));
            } else if (playbackState == Player.STATE_READY) {
                if (buffering) {
                    this.buffering = false;
                    Optional.ofNullable(listener).ifPresent(l -> l.onEngineBufferingCompleted(this));
                }
                if (preparing) {
                    this.preparing = false;
                    long off = source != null ? source.getOffset() : 0L;
                    if (off > 0) player.seekTo(off);
                    accessor.setSubGenTimeOffset(this);
                    Optional.ofNullable(listener).ifPresent(l -> l.onEnginePrepared(this));

                    var prefs = source.getPrefs();
                    MediaEngine.selectMediaStream(
                            prefs::getAudioIdPref,
                            prefs::getAudioLangPref,
                            prefs::getAudioKeyPref,
                            () -> completed(getAudioStreamInfo()),
                            this::setCurrentAudioStream
                    );
                }
            } else if (playbackState == Player.STATE_ENDED) {
                stopped(false);
                asyncIoExecutor.execute(() -> {
                    AudioEffects fx = getAudioEffects();
                    if (fx != null) fx.resetToGlobalSettings(App.get());
                });
                Optional.ofNullable(listener).ifPresent(l -> l.onEngineEnded(this));
            }
        }
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        Optional.ofNullable(listener).ifPresent(l -> l.onVideoSizeChanged(this, videoSize.width, videoSize.height));
    }
    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        synchronized (engineLock) {
            this.preparing = false;
            this.buffering = false;
            Optional.ofNullable(listener).ifPresent(l -> l.onEngineError(this, error));
        }
    }

    @Override
    protected SubGrid createSubStreamGrid() {
        return accessor.createSubStreamGrid();
    }
    // =====================================================================================
    // NESTED TRANSLATION RESOLUTION ACCESSOR COMPONENT
    // =====================================================================================
    static class Accessor {
        private final WeakReference<ExoPlayerEngine> playerEngineRef;
        private volatile long subGenTimeOffset;
        private Subtitles.Stream subStream;
        private Subtitles.Stream subTransStream;
        private String transLang;
        private FutureSupplier<Translator> translator = completedNull();
        private boolean useBatchTranslate = true;

        private Accessor(ExoPlayerEngine player) {
            this.playerEngineRef = new WeakReference<>(player);
        }

        private void releasePlayerEngineReference() {
            this.playerEngineRef.clear();
        }
        void drainBuffer() {
            assertMainThread();
            ExoPlayerEngine p = playerEngineRef.get();
            if (p == null) return;
            if (p.drainBuffer != null) p.drainBuffer.run();
            p.syncSub(false);
        }

        @Nullable
        public PlayableItem getSource() {
            ExoPlayerEngine p = playerEngineRef.get();
            return p == null ? null : p.source;
        }

        public long getSubGenTimeOffset() {
            return subGenTimeOffset;
        }
        private void sourceChanged(@Nullable PlayableItem src) {
            if (subStream != null) subStream.clear();
            if (subTransStream != null) subTransStream.clear();
            if (src == null) {
                transLang = null;
                translator = completedNull();
                return;
            }
            var ps = src.getPrefs();
            var lang = ps.getBooleanPref(SubGenAddon.TRANSLATE) ? ps.getStringPref(SubGenAddon.TRANSLATE_LANG) : null;
            if (lang == null || !lang.equals(transLang)) {
                transLang = lang;
                translator = completedNull();
            }
        }
        void addSubtitles(String lang, @NonNull List<Subtitles.Text> subs) {
            if (subs.isEmpty()) return;
            App.get().run(() -> {
                if (subStream == null) subStream = new Subtitles.Stream();
                var added = subStream.add(subs);
                if (transLang == null) return;
                var src = getSource();
                if (src == null) return;
                
                final String targetLang = transLang;
                if (translator.isDone() && translator.peek() == null) {
                    translator = TranslateAddon.get().then(a -> {
                        if (a == null || !targetLang.equals(transLang)) return completedNull();
                        return a.getTranslator(src.getPrefs(), lang, transLang);
                    });
                }
                
                translator.main().onSuccess(tr -> {
                    if (tr == null || !targetLang.equals(transLang)) return;
                    if (useBatchTranslate && tr.supportsBatch()) {
                        batchTranslate(tr, targetLang, added);
                    } else {
                        perItemTranslate(tr, targetLang, added);
                    }
                });
            });
        }
        private void batchTranslate(Translator tr, String targetLang, List<Subtitles.Text> subs) {
            assertMainThread();
            boolean prependPrev = false;
            if (!subStream.isEmpty()) {
                var lastItem = subStream.get(subStream.size() - 1);
                if (lastItem != null) {
                    var last = lastItem.getText().trim();
                    char lastChar = last.isEmpty() ? '\0' : last.charAt(last.length() - 1);
                    prependPrev = lastChar != '.' && lastChar != ',' && lastChar != '!' && lastChar != '?';
                }
            }
            String concat;
            try (var tb = SharedTextBuilder.get()) {
                if (prependPrev) {
                    var prevItem = subStream.get(subStream.size() - 1);
                    if (prevItem != null) {
                        tb.append(prevItem.getText()).append("/");
                    }
                }
                for (var t : subs) {
                    if (t != null) tb.append(t.getText()).append("|");
                }
                if (tb.length() > 0) {
                    tb.setLength(tb.length() - 1);
                }
                concat = tb.toString();
            }
            boolean skipFirst = prependPrev;
            Log.d("Translating: ", concat);
            tr.translate(concat).onCompletion((r, err) -> {
                if (err != null) {
                    Log.e(err);
                    return;
                }
                Log.d("Translation: ", r);
                if (r == null) return;
                String[] parts = r.split("\\|", -1);
                int off = skipFirst ? 1 : 0;
                if (subs.size() != parts.length - off) {
                    Log.d("Fall back to per item translation");
                    useBatchTranslate = false;
                    perItemTranslate(tr, targetLang, subs);
                    return;
                }
                var translated = new ArrayList<Subtitles.Text>(subs.size());
                for (int i = 0; i < subs.size(); i++) {
                    var t = subs.get(i);
                    if (t != null && (off + i) < parts.length) {
                        t.setTranslation(parts[off + i].trim());
                        translated.add(new Subtitles.Text(t.getTranslation(), t.getTime(), t.getDuration()));
                    }
                }
                App.get().run(() -> {
                    if (!targetLang.equals(transLang)) return;
                    if (subTransStream == null) subTransStream = new Subtitles.Stream();
                    subTransStream.add(translated);
                });
            });
        }
        private void perItemTranslate(Translator tr, String targetLang, List<Subtitles.Text> subs) {
            for (var t : subs) {
                if (t == null) continue;
                tr.translate(t.getText()).onCompletion((r, err) -> {
                    if (err != null) {
                        Log.e(err);
                        return;
                    }
                    if (r == null) return;
                    t.setTranslation(r.trim());
                    var translated = new Subtitles.Text(t.getTranslation(), t.getTime(), t.getDuration());
                    App.get().run(() -> {
                        if (!targetLang.equals(transLang)) return;
                        if (subTransStream == null) subTransStream = new Subtitles.Stream();
                        subTransStream.add(List.of(translated));
                    });
                });
            }
        }
        private void setSubGenTimeOffset(ExoPlayerEngine eng) {
            this.subGenTimeOffset = eng.subSchedulerClock();
        }
        private SubGrid createSubStreamGrid() {
            assertMainThread();
            if (subStream == null) subStream = new Subtitles.Stream();
            if (transLang == null) return new SubGrid(subStream);
            if (subTransStream == null) subTransStream = new Subtitles.Stream();
            
            java.util.Map<SubGrid.Position, Subtitles> m = new EnumMap<>(SubGrid.Position.class);
            m.put(SubGrid.Position.BOTTOM_LEFT, subStream);
            m.put(SubGrid.Position.BOTTOM_RIGHT, subTransStream);
            return new SubGrid(m);
        }
    }
}
