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
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
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
import java.util.concurrent.atomic.AtomicLong;

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
 * Fully integrated with all original dependencies, dual-pane translation accessors,
 * hardware effect profiles, and speech-to-text components.
 * Multi-threaded with background thread separation to protect against deadlocks and ANRs.
 */
@UnstableApi
public class ExoPlayerEngine extends MediaEngineBase implements Player.Listener {

    @Override
    public int getId() {
        return 1; // Original module registration index mapping reference
    }

    private static final DataSource.Factory httpDsFactory;
    private static final ExecutorService asyncIoExecutor = Executors.newFixedThreadPool(2);

    static {
        String standardUserAgent = "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36 Permata/" + BuildConfig.VERSION_NAME;
        CronetEngine cre = null;
        try {
            cre = CronetUtil.buildCronetEngine(
                    PermataApplication.get(),
                    standardUserAgent,
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
            httpDsFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent(standardUserAgent)
                    .setAllowCrossProtocolRedirects(true);
        }
    }

    private final Accessor accessor = new Accessor(this);
    private final Timeline.Period period = new Timeline.Period();
    private final PendingLoadAudioProcessor audioProc = new PendingLoadAudioProcessor(accessor);
    
    private ExoPlayer player;
    private AudioEffects audioEffects;
    private final Object engineLock = new Object();
    private final MediaSource.Factory mediaSourceFactory;
    private final Context appCtx;
    private final DefaultBandwidthMeter bandwidthMeter;
    private final DefaultDataSource.Factory dsFactory;
    
    private volatile PlayableItem source;
    private volatile boolean preparing;
    private volatile boolean buffering;
    private volatile boolean isHls;
    private volatile boolean hasSuccessfullyRendered;
    private volatile Runnable drainBuffer;

    // Generational structural tracking token cancels older retry threads instantly
    private final AtomicLong activeStreamId = new AtomicLong(0);
    public ExoPlayerEngine(@NonNull Context ctx, @NonNull Listener listener) {
        super(listener);
        this.appCtx = ctx.getApplicationContext();

        // 1. Adaptive Bitrate (ABR) Optimization via explicit Bandwidth Meter
        this.bandwidthMeter = new DefaultBandwidthMeter.Builder(appCtx)
                .setInitialBitrateEstimate(2_000_000)
                .build();

        // Intercept protocol schemes using a clean Media3 ResolvingDataSource wrapper instead
        androidx.media3.datasource.DataSource.Factory wrappingFactory = new androidx.media3.datasource.ResolvingDataSource.Factory(
                httpDsFactory,
                new androidx.media3.datasource.ResolvingDataSource.Resolver() {
                    @androidx.annotation.NonNull
                    @Override
                    public androidx.media3.datasource.DataSpec resolveDataSpec(@androidx.annotation.NonNull androidx.media3.datasource.DataSpec dataSpec) {
                        String scheme = dataSpec.uri.getScheme();
                        if ("p2p".equalsIgnoreCase(scheme) || "p3p".equalsIgnoreCase(scheme)) {
                            // Safely rewrite the custom request to route through your application's local P2P engine client loopback
                            android.net.Uri localProxyUri = android.net.Uri.parse("http://127.0.0.1:8080/stream?url=" + android.net.Uri.encode(dataSpec.uri.toString()));
                            return dataSpec.withUri(localProxyUri);
                        }
                        return dataSpec;
                    }
                }
        );

        this.dsFactory = new DefaultDataSource.Factory(appCtx, wrappingFactory);
              
        // 2. Combined Smart Context-Aware Recovery + Infinite Reconnection Policy
        DefaultLoadErrorHandlingPolicy customErrorPolicy = new DefaultLoadErrorHandlingPolicy() {
            @Override
            public long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {
                java.io.IOException exception = loadErrorInfo.exception;


// 1. Structural Fail-Fast (Authentication / Missing resources)
if (exception instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
    int responseCode = ((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) exception).responseCode;
    
    // FIX: Safely read dataType via the mediaLoadData element tracking property
    int currentDataType = loadErrorInfo.mediaLoadData != null ? loadErrorInfo.mediaLoadData.dataType : C.DATA_TYPE_UNKNOWN;

    if ((responseCode == 404 || responseCode == 410) && currentDataType == C.DATA_TYPE_MEDIA) {
        if (hasSuccessfullyRendered) {
            Log.w("ExoPlayerEngine", "Active media segment vanished (HTTP " + responseCode + "). Breaking internal chain for background re-probe.");
            return C.TIME_UNSET; // Drops out to invoke onPlayerError reloader
        } else {
            Log.e("ExoPlayerEngine", "Dead stream link caught on initialization step (HTTP " + responseCode + "). Halting.");
            return C.TIME_UNSET;
        }
    }
    if (responseCode == 401 || responseCode == 403 || responseCode == 404 || responseCode == 410) {
        return C.TIME_UNSET; // Halt loop immediately to trigger app token updates
    }
}

                    if (responseCode == 401 || responseCode == 403 || responseCode == 404 || responseCode == 410) {
                        return C.TIME_UNSET; // Halt loop immediately to trigger app token updates
                    }
                }

        // 2. Combined Smart Context-Aware Recovery + Infinite Reconnection Policy
        DefaultLoadErrorHandlingPolicy customErrorPolicy = new DefaultLoadErrorHandlingPolicy() {
            @Override
            public long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {
                java.io.IOException exception = loadErrorInfo.exception;

                // 1. Structural Fail-Fast (Authentication / Missing resources)
                if (exception instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                    int responseCode = ((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) exception).responseCode;
                    
                    // FIX: Safely read dataType via the mediaLoadData element tracking property
                    int currentDataType = loadErrorInfo.mediaLoadData != null ? loadErrorInfo.mediaLoadData.dataType : C.DATA_TYPE_UNKNOWN;

                    if ((responseCode == 404 || responseCode == 410) && currentDataType == C.DATA_TYPE_MEDIA) {
                        if (hasSuccessfullyRendered) {
                            Log.w("ExoPlayerEngine", "Active media segment vanished (HTTP " + responseCode + "). Breaking internal chain for background re-probe.");
                            return C.TIME_UNSET; // Drops out to invoke onPlayerError reloader
                        } else {
                            Log.e("ExoPlayerEngine", "Dead stream link caught on initialization step (HTTP " + responseCode + "). Halting.");
                            return C.TIME_UNSET;
                        }
                    }
                    if (responseCode == 401 || responseCode == 403 || responseCode == 404 || responseCode == 410) {
                        return C.TIME_UNSET; // Halt loop immediately to trigger app token updates
                    }
                }

                // 2. Continuous Live Optimization via Jittered Exponential Back-off
                if (exception instanceof java.io.IOException) {
                    // Gradually scale delay based on consecutive error counts (Cap at 10 seconds max)
                    long baseDelay = Math.min((loadErrorInfo.errorCount - 1) * 1500L + 1000L, 10000L); 
                    long jitter = (long) (Math.random() * 400) - 200; // Inject +/- 200ms variance
                    return baseDelay + jitter;
                }

                return super.getRetryDelayMsFor(loadErrorInfo);
            }

            @Override
            public int getMinimumLoadableRetryCount(int dataType) {
                // Let manifests fail fast internally so our background async worker loop can reboot them safely
                if (dataType == C.DATA_TYPE_MANIFEST) {
                    return 3; 
                }
                // Retain infinite retries for chunks/segments to sustain background connectivity indefinitely on functioning streams
                return dataType == C.DATA_TYPE_MEDIA ? Integer.MAX_VALUE : super.getMinimumLoadableRetryCount(dataType);
            }
        }; // Added missing closing brace and semicolon for the anonymous class assignment

        // Permissive extractor configurations matching VLC parser robustness (Corrected references)
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS | DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES);

        this.mediaSourceFactory = new DefaultMediaSourceFactory(appCtx, extractorsFactory)
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
        
        // Optimized adaptive buffer window avoiding starving on short-window live sliding streams
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        3_000,  // minBufferMs: Calibrated to 3s to support shallow sliding windows safely
                        50_000, // maxBufferMs: Allow scaling cache up to 50s if the server history permits it
                        1_500,  // bufferForPlaybackMs: Fast initial start (Wait for 1.5s of data instead of forcing 10s)
                        2_500   // bufferForPlaybackAfterRebufferMs: Balanced rebuffer recovery threshold
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
        DefaultLivePlaybackSpeedControl liveSpeedControl = new DefaultLivePlaybackSpeedControl.Builder()
                .setFallbackMinPlaybackSpeed(0.95f)
                .setFallbackMaxPlaybackSpeed(1.05f)
                .build();

        this.player = new ExoPlayer.Builder(appCtx, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
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
    /**
     * Universally determines the media container type by evaluating URL structures 
     * and performing background network sniffing when extensions are completely absent.
     */
    private void universallyResolveAndPrepare(@NonNull PlayableItem sourceItem, @NonNull Uri uri, final long generation) {
        if (generation != activeStreamId.get()) return;

        String scheme = uri.getScheme();
        if (scheme == null) scheme = "http";
        scheme = scheme.toLowerCase().trim();

        String urlString = uri.toString().toLowerCase();
        String path = uri.getPath() != null ? uri.getPath().toLowerCase() : "";

        Log.d("ExoPlayerEngine", "Universal Route Matrix checking protocol scheme: [" + scheme + "]");

        // Matrix 1: Custom Decentralized Peer-to-Peer / IPTV Channels
        if (scheme.equals("p2p") || scheme.equals("p3p")) {
            applyMediaSource(sourceItem, uri, androidx.media3.common.MimeTypes.APPLICATION_M3U8);
            return;
        }

        // Matrix 2: Local Playback Hooks (Sandbox Storage / Android Content Providers)
        if (scheme.equals("file") || scheme.equals("content")) {
            if (path.contains(".m3u8")) {
                applyMediaSource(sourceItem, uri, androidx.media3.common.MimeTypes.APPLICATION_M3U8);
            } else if (path.contains(".mpd")) {
                applyMediaSource(sourceItem, uri, androidx.media3.common.MimeTypes.APPLICATION_MPD);
            } else {
                applyMediaSource(sourceItem, uri, null); // Progressive Engine fallback
            }
            return;
        }

        // Matrix 3: Legacy Broadcast Stream Standards
        if (scheme.equals("rtsp") || scheme.equals("rtmp")) {
            applyMediaSource(sourceItem, uri, androidx.media3.common.MimeTypes.APPLICATION_RTSP);
            return;
        }

        // Matrix 4: INSTANT FAST-PATH FILE SIGNATURE EVALUATION
        if (path.contains(".m3u8") || urlString.contains("format=m3u8") || urlString.contains("type=m3u8") || urlString.contains(".ts")) {
            applyMediaSource(sourceItem, uri, androidx.media3.common.MimeTypes.APPLICATION_M3U8);
            return;
        }
        if (path.contains(".mpd") || urlString.contains("format=mpd")) {
            applyMediaSource(sourceItem, uri, androidx.media3.common.MimeTypes.APPLICATION_MPD);
            return;
        }
        if (path.contains(".ism") || urlString.contains("format=ism")) {
            applyMediaSource(sourceItem, uri, androidx.media3.common.MimeTypes.APPLICATION_SS);
            return;
        }
        // Matrix 5: ASYNCHRONOUS NETWORK SNIFFING
        asyncIoExecutor.execute(() -> {
            if (generation != activeStreamId.get()) return;

            String inferredMimeType = null;
            java.net.HttpURLConnection conn = null;
            try {
                java.net.URL url = new java.net.URL(uri.toString());
                conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36");

                int responseCode = conn.getResponseCode();
                if (responseCode == java.net.HttpURLConnection.HTTP_BAD_METHOD || responseCode == 405) {
                    conn.disconnect();
                    if (generation != activeStreamId.get()) return;
                    conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(2000);
                    conn.setReadTimeout(2000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36");
                }

                String contentTypeHeader = conn.getContentType();
                if (contentTypeHeader != null) {
                    String[] parts = contentTypeHeader.toLowerCase().split(";");
                    String contentType = parts.length > 0 ? parts[0].trim() : "";

                    if (contentType.contains("mpegurl") || contentType.contains("apple.mpegurl") || contentType.contains("mpeg.url") || contentType.contains("application/vnd.apple.mpegurl")) {
                        inferredMimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8;
                    } else if (contentType.contains("dash+xml")) {
                        inferredMimeType = androidx.media3.common.MimeTypes.APPLICATION_MPD;
                    } else if (contentType.contains("video/mp2t") || contentType.contains("video/mpeg")) {
                        inferredMimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8;
                    }
                }
            } catch (Exception e) {
                Log.w("ExoPlayerEngine", "Network sniffing encountered an issue: " + e.getMessage() + ". Launching fallback port scans.");
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }
            }

            // Matrix 6: IPTV SIGNATURE PORT SCAVENGER NODE
            if (inferredMimeType == null) {
                int port = uri.getPort();
                if (urlString.contains("/live/") || urlString.contains("/stream/") || urlString.contains("playlist") || urlString.contains("get.php") 
                    || port == 8000 || port == 8080 || port == 8880 || port == 3999 || port == 9000) {
                    inferredMimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8;
                }
            }

            if (generation != activeStreamId.get()) return;

            final String finalMime = inferredMimeType;
            my.app.utils.app.App.get().run(() -> {
                if (generation == activeStreamId.get()) {
                    applyMediaSource(sourceItem, uri, finalMime);
                }
            });
        });
    }
    @SuppressLint("SwitchIntDef")
    @Override
    public void prepare(@NonNull PlayableItem source) {
        // Isolation Protection: Stop the active player state BEFORE acquiring the structural engine lock.
        // This instantly signals stuck network sockets to terminate, preventing UI thread ANRs.
        final long currentGeneration = activeStreamId.incrementAndGet();
        
        if (player != null) {
            player.stop(); 
        }

        synchronized (engineLock) {
            this.source = source;
            accessor.sourceChanged(source);
            this.preparing = true;
            this.buffering = false;
            this.hasSuccessfullyRendered = false; // Reset verification flag for this new preparation sequence

            Uri uri = source.getLocation();
            this.isHls = Util.inferContentType(uri) == C.CONTENT_TYPE_HLS;

            final int uriHash = uri.hashCode();
            asyncIoExecutor.execute(() -> {
                if (currentGeneration != activeStreamId.get()) return;
                AudioEffects fx = getAudioEffects();
                if (fx != null) {
                    String channelIdentifier = "exo_file_" + uriHash;
                    fx.loadAndApplyPersistedSettingsForChannel(App.get(), channelIdentifier);
                }
            });

            // Route through the universal resolution module with generation guard tracking
            universallyResolveAndPrepare(source, uri, currentGeneration);
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
        if (BuildConfig.AUTO && !src.isVideo() && (listener instanceof MediaSessionCallback)) {
            MediaSessionCallback cb = (MediaSessionCallback) listener;
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
                    this.hasSuccessfullyRendered = true; // Stream validated successfully
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
        final long currentGeneration = activeStreamId.get();

        asyncIoExecutor.execute(() -> {
            if (currentGeneration != activeStreamId.get()) return;

            boolean isVanishedChunk = error.getCause() instanceof HttpDataSource.InvalidResponseCodeException;
            boolean isNetworkFailure = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                    || error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                    || error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED;

            // INFINITE BACKGROUND RETRY LOOP: Only activates if the channel has a proven active track record
            if (hasSuccessfullyRendered && (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW || isVanishedChunk || isNetworkFailure)) {
                Log.w("ExoPlayerEngine", "Active stream connection interrupted. Initiating background loopback retry [Gen ID: " + currentGeneration + "].");
                
                // Keep your existing loading wheel spinning smoothly on screen
                my.app.utils.app.App.get().run(() -> {
                    synchronized (engineLock) {
                        if (currentGeneration == activeStreamId.get() && player != null) {
                            this.buffering = true;
                            Optional.ofNullable(listener).ifPresent(l -> l.onEngineBuffering(this, player.getBufferedPercentage()));
                        }
                    }
                });

                try {
                    Thread.sleep(2000); // Non-blocking background delay isolated from UI thread
                } catch (InterruptedException ignored) {}

                if (currentGeneration != activeStreamId.get()) return;

                synchronized (engineLock) {
                    if (currentGeneration == activeStreamId.get() && source != null) {
                        this.preparing = true;
                        this.buffering = false;
                        universallyResolveAndPrepare(source, source.getLocation(), currentGeneration);
                    }
                }
                return;
            }

            // Unproven dead links bypass the retry engine loops completely and fail fast down to user space
            Log.e("ExoPlayerEngine", "Terminal stream breakdown exposed: " + error.getMessage());
            my.app.utils.app.App.get().run(() -> {
                synchronized (engineLock) {
                    if (currentGeneration != activeStreamId.get()) return;
                    this.preparing = false;
                    this.buffering = false;
                    Optional.ofNullable(listener).ifPresent(l -> l.onEngineError(this, error));
                }
            });
        });
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
                String[] parts = r.split("\\\\\\|", -1);
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
    // =====================================================================================
    // AUDIO PROCESSOR WRAPPER INTERFACE LIFE-CYCLE INTERCEPTOR
    // =====================================================================================
    private static class PendingLoadAudioProcessor implements androidx.media3.common.audio.AudioProcessor {
        private final Accessor accessor;
        private androidx.media3.common.audio.AudioProcessor.AudioFormat inputAudioFormat;
        private androidx.media3.common.audio.AudioProcessor.AudioFormat outputAudioFormat;
        private java.nio.ByteBuffer buffer;
        private java.nio.ByteBuffer outputBuffer;
        private boolean inputEnded;

        public PendingLoadAudioProcessor(Accessor accessor) {
            this.accessor = accessor;
            this.inputAudioFormat = androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET;
            this.outputAudioFormat = androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET;
            this.buffer = EMPTY_BUFFER;
            this.outputBuffer = EMPTY_BUFFER;
        }

        @Override
        public androidx.media3.common.audio.AudioProcessor.AudioFormat configure(
                androidx.media3.common.audio.AudioProcessor.AudioFormat inputAudioFormat)
                throws androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException {
            this.inputAudioFormat = inputAudioFormat;
            this.outputAudioFormat = inputAudioFormat;
            return inputAudioFormat;
        }

        @Override
        public boolean isActive() {
            return inputAudioFormat != androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET;
        }

        @Override
        public void queueInput(java.nio.ByteBuffer inputBuffer) {
            int remaining = inputBuffer.remaining();
            if (remaining == 0) return;

            if (buffer.capacity() < remaining) {
                buffer = java.nio.ByteBuffer.allocateDirect(remaining).order(java.nio.ByteOrder.nativeOrder());
            } else {
                buffer.clear();
            }

            buffer.put(inputBuffer);
            buffer.flip();
            outputBuffer = buffer;

            if (remaining > 0 && accessor != null) {
                my.app.utils.app.App.get().run(accessor::drainBuffer);
            }
        }
        @Override
        public void queueEndOfStream() {
            inputEnded = true;
        }

        @Override
        public java.nio.ByteBuffer getOutput() {
            java.nio.ByteBuffer output = outputBuffer;
            outputBuffer = EMPTY_BUFFER;
            return output;
        }

        @Override
        public boolean isEnded() {
            return inputEnded && outputBuffer == EMPTY_BUFFER;
        }

        @Override
        public void flush() {
            outputBuffer = EMPTY_BUFFER;
            inputEnded = false;
        }

        @Override
        public void reset() {
            flush();
            buffer = EMPTY_BUFFER;
            inputAudioFormat = androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET;
            outputAudioFormat = androidx.media3.common.audio.AudioProcessor.AudioFormat.NOT_SET;
        }
    }

    private void applyMediaSource(@NonNull PlayableItem sourceItem, @NonNull Uri uri, @androidx.annotation.Nullable String mimeType) {
        androidx.media3.common.MediaItem.Builder mediaItemBuilder = new androidx.media3.common.MediaItem.Builder()
                .setUri(uri);
                
        if (mimeType != null) {
            mediaItemBuilder.setMimeType(mimeType);
        }
        
        androidx.media3.common.MediaItem mediaItem = mediaItemBuilder.build();
        
        if (this.player != null) {
            this.player.setMediaItem(mediaItem);
            this.player.prepare(); 
        }
    }
}
