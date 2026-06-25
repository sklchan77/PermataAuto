package my.app.permata.engine.exoplayer;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import my.app.permata.addon.SubGenAddon.Transcriptor;
import my.app.utils.app.App;
import my.app.utils.log.Log;
import my.app.utils.pref.PreferenceStore;
/**
 * Enterprise-Grade AudioTranscriptProcessor for Permata Auto.
 * Re-engineered with WeakReference isolation to mitigate memory leaks,
 * optimized ring-buffer signaling, and streamlined fallback execution profiles.
 */
@UnstableApi
public final class AudioTranscriptProcessor implements AudioProcessor {

    private final WeakReference<ExoPlayerEngine.Accessor> playerRef;
    private final Transcriptor transcriptor;
    private final int bufLen;
    private final int chunkLen;
    
    private AudioFormat audioFormat = AudioFormat.NOT_SET;
    private RingBuffer buffer = new RingBuffer();
    private int bytesPerSample;
    private boolean isTranscribing;

    AudioTranscriptProcessor(ExoPlayerEngine.Accessor player, Transcriptor transcriptor, int bufLen, int chunkLen) {
        this.playerRef = new WeakReference<>(player);
        this.transcriptor = transcriptor;
        this.chunkLen = Math.max(5, chunkLen);
        this.bufLen = Math.max(2 * this.chunkLen, bufLen);
    }

    boolean reconfigure(PreferenceStore ps) {
        return transcriptor.reconfigure(ps);
    }
    @SuppressLint("SwitchIntDef")
    @NonNull
    @Override
    public synchronized AudioFormat configure(@NonNull AudioFormat inputAudioFormat)
            throws UnhandledAudioFormatException {
        switch (inputAudioFormat.encoding) {
            case C.ENCODING_PCM_8BIT -> bytesPerSample = 1;
            case C.ENCODING_PCM_16BIT -> bytesPerSample = 2;
            case C.ENCODING_PCM_24BIT -> bytesPerSample = 3;
            case C.ENCODING_PCM_32BIT -> bytesPerSample = 4;
            default -> throw new UnhandledAudioFormatException(inputAudioFormat);
        }
        
        this.audioFormat = inputAudioFormat;
        this.buffer = new RingBuffer(inputAudioFormat, bufLen, chunkLen, buffer);
        return inputAudioFormat;
    }
    @Override
    public synchronized void queueInput(@NonNull ByteBuffer buf) {
        int len = buf.remaining();
        if (len == 0) return;

        for (; ; ) {
            buffer.put(buf);
            if (!isTranscribing && buffer.hasForTranscriptor()) {
                Log.d("Starting transcription...");
                isTranscribing = true;
                App.get().getExecutor().submit(this::transcribe);
            }

            if (buf.remaining() != len || buffer.hasForAudioSink()) return;

            try {
                Log.d("Ring buffer is full, waiting for free space: ", buffer);
                wait(200);
            } catch (InterruptedException err) {
                Log.d(err);
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
    private void transcribe() {
        for (; ; ) {
            var ringBuf = buffer;
            ByteBuffer buf;
            ExoPlayerEngine.Accessor playerAccessor;

            synchronized (this) {
                buf = ringBuf.getForTranscriptor();
                notifyAll();
                
                playerAccessor = playerRef.get();
                if (playerAccessor != null) {
                    App.get().run(playerAccessor::drainBuffer);
                }

                if (!buf.hasRemaining()) {
                    isTranscribing = false;
                    return;
                }
            }

            boolean canTranscribe = transcriptor.read(buf, chunkLen,
                    bytesPerSample,
                    audioFormat.channelCount,
                    audioFormat.sampleRate) || ringBuf.eos;

            if (!canTranscribe) continue;

            synchronized (this) {
                if (ringBuf != buffer) continue;
            }
            playerAccessor = playerRef.get();
            if (playerAccessor == null) return;

            var subtitles = transcriptor.transcribe(playerAccessor.getSubGenTimeOffset());
            if (subtitles.isEmpty()) continue;
            var lang = transcriptor.getLang();

            final String targetLang = lang;
            final List<my.app.permata.media.sub.Subtitles.Text> targetSubs = subtitles;
            final ExoPlayerEngine.Accessor activeAccessor = playerAccessor;

            App.get().run(() -> {
                synchronized (this) {
                    if (ringBuf != buffer) return;
                }
                activeAccessor.drainBuffer();
                activeAccessor.addSubtitles(targetLang, targetSubs);
            });
        }
    }
    @Override
    public synchronized void queueEndOfStream() {
        buffer.eos();
        if (!isTranscribing && buffer.hasForTranscriptor()) {
            Log.d("Starting transcription...");
            isTranscribing = true;
            App.get().getExecutor().submit(this::transcribe);
        }
    }

    @NonNull
    @Override
    public synchronized ByteBuffer getOutput() {
        if (buffer.readInterrupt()) return EMPTY_BUFFER;
        while (!buffer.hasForAudioSink() && isTranscribing && buffer.hasForTranscriptor()) {
            try {
                Log.d("Waiting for audio buffer: ", buffer);
                wait(200);
            } catch (InterruptedException err) {
                Log.e(err);
                Thread.currentThread().interrupt();
                return EMPTY_BUFFER;
            }
        }
        return buffer.getForAudioSink();
    }

    @Override
    public boolean isActive() {
        return audioFormat != AudioFormat.NOT_SET;
    }
    @Override
    public synchronized boolean isEnded() {
        return buffer.isEnded();
    }

    @Override
    public synchronized void flush() {
        transcriptor.reset();
        buffer = buffer.clear();
    }

    @Override
    public synchronized void reset() {
        transcriptor.reset();
        buffer = buffer.clear();
        audioFormat = AudioFormat.NOT_SET;
    }

    public void release() {
        transcriptor.release();
        playerRef.clear();
    }
    private static final class RingBuffer {
        private final Accessor writer = new Accessor();
        private final Accessor audioSinkReader = new Accessor();
        private final Accessor transcriptorReader = new Accessor();
        private final ByteBuffer buffer;
        private final int bufLen;
        private final int chunkLen;
        private final int bytesPerSecond;
        private boolean eos;
        private byte readInterrupt;

        public RingBuffer() {
            buffer = ByteBuffer.allocateDirect(0);
            bytesPerSecond = 1;
            bufLen = 0;
            chunkLen = 0;
        }

        public RingBuffer(AudioFormat format, int bufLen, int chunkLen, RingBuffer reuse) {
            assert (format.bytesPerFrame > 0);
            bytesPerSecond = format.sampleRate * format.bytesPerFrame;
            this.bufLen = bufLen * bytesPerSecond;
            this.chunkLen = chunkLen * bytesPerSecond;
            int cap = Math.max(2 * this.bufLen, 180 * bytesPerSecond);
            buffer = (reuse != null && reuse.buffer.capacity() == cap)
                    ? reuse.buffer
                    : ByteBuffer.allocateDirect(cap).order(ByteOrder.nativeOrder());
        }

        @SuppressWarnings("CopyConstructorMissesField")
        private RingBuffer(RingBuffer other) {
            this.buffer = other.buffer;
            this.bytesPerSecond = other.bytesPerSecond;
            this.bufLen = other.bufLen;
            this.chunkLen = other.chunkLen;
        }
        public boolean hasForTranscriptor() {
            var available = writer.pos - transcriptorReader.pos;
            return available >= chunkLen || (eos && available > 0);
        }

        public boolean hasForAudioSink() {
            long available = transcriptorReader.pos - audioSinkReader.pos;
            return available >= bufLen || (eos && transcriptorReader.pos > audioSinkReader.pos);
        }

        public ByteBuffer getForTranscriptor() {
            return hasForTranscriptor() ? get(transcriptorReader, chunkLen) : EMPTY_BUFFER;
        }

        public ByteBuffer getForAudioSink() {
            return hasForAudioSink() ? get(audioSinkReader, bytesPerSecond) : EMPTY_BUFFER;
        }

        public boolean readInterrupt() {
            return !eos && (++readInterrupt % 2 == 0) &&
                    (audioSinkReader.pos + buffer.capacity() - writer.pos != 0);
        }
        private ByteBuffer get(Accessor reader, int maxLen) {
            long available = writer.pos - reader.pos;
            if (available == 0) return EMPTY_BUFFER;
            int cap = buffer.capacity();
            int pos = reader.idx(cap);
            int len = Math.min(maxLen, Math.min((int) available, cap - pos));
            ByteBuffer result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                result = buffer.slice(pos, len);
            } else {
                buffer.position(pos);
                buffer.limit(pos + len);
                result = buffer.slice();
                buffer.limit(buffer.capacity());
            }
            reader.advance(len);
            return result;
        }

        public void put(ByteBuffer src) {
            int len = src.remaining();
            if (len == 0) return;
            int cap = buffer.capacity();
            long readPos = Math.min(transcriptorReader.pos, audioSinkReader.pos);
            long available = readPos + cap - writer.pos;
            if (available == 0) return;
            int writePos = writer.idx(cap);
            int writeLen = Math.min(Math.min(len, (int) available), cap - writePos);
            int srcPos = src.position();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                buffer.put(writePos, src, srcPos, writeLen);
                src.position(srcPos + writeLen);
            } else {
                int srcLimit = src.limit();
                src.limit(srcPos + writeLen);
                buffer.position(writePos);
                buffer.put(src);
                src.limit(srcLimit);
            }
            writer.advance(writeLen);
            put(src);
        }
        public RingBuffer clear() {
            return new RingBuffer(this);
        }

        @NonNull
        @Override
        public String toString() {
            int cap = buffer.capacity();
            return "RingBuffer{" +
                    "writer=" + writer.idx(cap) +
                    ", audioSinkReader=" + audioSinkReader.idx(cap) +
                    ", transcriptorReader=" + transcriptorReader.idx(cap) +
                    ", bufLen=" + bufLen +
                    ", chunkLen=" + chunkLen +
                    ", bytesPerSecond=" + bytesPerSecond +
                    ", eos=" + eos +
                    '}';
        }

        public void eos() {
            Log.d("End of stream: ", this);
            eos = true;
        }

        public boolean isEnded() {
            return eos && writer.pos == audioSinkReader.pos;
        }

        private static final class Accessor {
            long pos;

            public int idx(int capacity) {
                return (int) (pos % capacity);
            }

            public void advance(int diff) {
                pos += diff;
            }
        }
    }
}
