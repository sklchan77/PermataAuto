package my.app.permata.engine.exoplayer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.completedNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import my.app.permata.PermataApplication;
import my.app.permata.addon.SubGenAddon;
import my.app.utils.async.FutureSupplier;
import my.app.utils.log.Log;

/**
 * Enterprise-Grade PendingLoadAudioProcessor for Permata Auto.
 * Re-engineered with explicit WeakReference tracking to resolve heavy memory retention leaks
 * and clean state machine assertions across playlist track transitions.
 */
@UnstableApi
class PendingLoadAudioProcessor implements AudioProcessor {

    // Mitigation: Upgraded to WeakReference to break retain cycles with player instance context
    private final WeakReference<ExoPlayerEngine.Accessor> playerRef;
    private final AtomicInteger state = new AtomicInteger();
    
    private AudioFormat pendingConfiguration;
    private FutureSupplier<AudioTranscriptProcessor> delegate = completedNull();

    PendingLoadAudioProcessor(ExoPlayerEngine.Accessor player) {
        this.playerRef = new WeakReference<>(player);
    }
    @Override
    public long getDurationAfterProcessorApplied(long durationUs) {
        var d = delegate.peek();
        return (d != null) ? d.getDurationAfterProcessorApplied(durationUs) : durationUs;
    }

    @NonNull
    @Override
    public AudioFormat configure(@NonNull AudioFormat inputAudioFormat)
            throws UnhandledAudioFormatException {
        pendingConfiguration = null;
        
        var playerAccessor = playerRef.get();
        if (playerAccessor == null) {
            releaseDelegate();
            return inputAudioFormat;
        }

        var source = playerAccessor.getSource();
        if (source == null) {
            releaseDelegate();
            return inputAudioFormat;
        }

        var ps = source.getPrefs();
        if (ps.getBooleanPref(SubGenAddon.ENABLED)) {
            var atp = delegate.peek();
            if (atp != null) {
                if (!atp.reconfigure(ps)) {
                    atp.release();
                    createDelegate();
                }
            } else if (delegate.isDone()) {
                createDelegate();
            }
        } else {
            releaseDelegate();
        }

        var d = delegate.peek();
        if (d == null) {
            return pendingConfiguration = inputAudioFormat;
        } else {
            pendingConfiguration = null;
            return d.configure(inputAudioFormat);
        }
    }
    @Override
    public boolean isActive() {
        var d = get();
        return (d != null && d.isActive()) || !delegate.isDone();
    }

    @Override
    public void queueInput(@NonNull ByteBuffer inputBuffer) {
        var d = get();
        if (d == null && !delegate.isDone()) {
            try {
                d = delegate.get(500, MILLISECONDS);
            } catch (TimeoutException ignore) {
                // Bounded safe timeout to protect the audio timeline loop
            } catch (Exception err) {
                Log.e(err);
            }
        }
        if (d != null) {
            d.queueInput(inputBuffer);
        }
    }
    @Override
    public void queueEndOfStream() {
        var d = get();
        if (d != null) d.queueEndOfStream();
    }

    @NonNull
    @Override
    public ByteBuffer getOutput() {
        var d = get();
        return (d != null) ? d.getOutput() : EMPTY_BUFFER;
    }

    @Override
    public boolean isEnded() {
        var d = get();
        return (d != null) && d.isEnded();
    }

    @Override
    public void flush() {
        var d = get();
        if (d != null) d.flush();
    }
    @Override
    public void reset() {
        state.incrementAndGet();
        var d = delegate.peek();
        delegate = completedNull();
        if (d != null) {
            d.reset();
            d.release();
        }
    }

    private void createDelegate() {
        var st = state.incrementAndGet();
        delegate = PermataApplication.get().getAddonManager().getOrInstallAddon(SubGenAddon.class)
                .then(a -> {
                    if (a == null || state.get() != st) return completedNull();
                    var playerAccessor = playerRef.get();
                    if (playerAccessor == null) return completedNull();
                    var pi = playerAccessor.getSource();
                    if (pi == null || !pi.getPrefs().getBooleanPref(SubGenAddon.ENABLED)) {
                        return completedNull();
                    }
                    return a.getTranscriptor(pi.getPrefs());
                }).then(t -> {
                    if (t == null) return completedNull();
                    var playerAccessor = playerRef.get();
                    if (playerAccessor == null || state.get() != st) {
                        t.release();
                        return completedNull();
                    }
                    var pi = playerAccessor.getSource();
                    if (pi == null || !pi.getPrefs().getBooleanPref(SubGenAddon.ENABLED)) {
                        t.release();
                        return completedNull();
                    }
                    if (!t.reconfigure(pi.getPrefs())) {
                        t.release();
                        createDelegate();
                        return completedNull();
                    }
                    var ps = pi.getPrefs();
                    return delegate = completed(new AudioTranscriptProcessor(
                            playerAccessor, t,
                            ps.getIntPref(SubGenAddon.BUF_LEN),
                            ps.getIntPref(SubGenAddon.CHUNK_LEN)));
                });
    }
    private void releaseDelegate() {
        state.incrementAndGet();
        var atp = delegate.peek();
        if (atp != null) {
            atp.release();
            delegate = completedNull();
        }
    }

    @Nullable
    private AudioTranscriptProcessor get() {
        var atp = delegate.peek();
        if (atp == null) return null;
        if (pendingConfiguration != null) {
            try {
                atp.configure(pendingConfiguration);
            } catch (UnhandledAudioFormatException err) {
                throw new RuntimeException(err);
            }
            pendingConfiguration = null;
        }
        return atp;
    }
}
