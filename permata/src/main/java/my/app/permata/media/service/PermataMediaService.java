package my.app.permata.media.service;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.support.v4.media.session.PlaybackStateCompat;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import my.app.permata.PermataApplication;
import my.app.utils.log.Log;

/**
 * Enterprise Extended Media Service extending BasePermataMediaService.
 * Contains DSP Anchor, Web Media Actions, and Audio Hijack logic.
 */
public class PermataMediaService extends BasePermataMediaService {

	public static final String ACTION_HIJACK_FOCUS = "my.app.permata.action.HIJACK_FOCUS";
	public static final String ACTION_WEB_MEDIA_PLAYING = "my.app.permata.action.WEB_MEDIA_PLAYING";
	public static final String ACTION_WEB_MEDIA_PAUSED = "my.app.permata.action.WEB_MEDIA_PAUSED";

	// Enterprise Hardening: DSP Hardware Reset Broadcast Actions
	public static final String ACTION_STOP_SILENT_ANCHOR = "my.app.permata.ACTION_STOP_SILENT_ANCHOR";
	public static final String ACTION_START_SILENT_ANCHOR = "my.app.permata.ACTION_START_SILENT_ANCHOR";

	private BroadcastReceiver dspAnchorReceiver;
	private AudioTrack silentAudioTrack;
	private volatile boolean isSilentTrackRunning = false;
	private final ScheduledExecutorService audioExecutor = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> audioTaskFuture;

	@SuppressLint("UnspecifiedRegisterReceiverFlag")
	@Override
	public void onCreate() {
		super.onCreate();

		// Enterprise Hardening: Register the DSP Anchor Receiver
		dspAnchorReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (intent != null && intent.getAction() != null) {
					if (ACTION_STOP_SILENT_ANCHOR.equals(intent.getAction())) {
						Log.i("PermataMediaService: Received DSP Flush -> STOP Anchor");
						stopSilentAudioAnchor();
					} else if (ACTION_START_SILENT_ANCHOR.equals(intent.getAction())) {
						Log.i("PermataMediaService: Received DSP Flush -> START Anchor");
						startSilentAudioAnchor();
					}
				}
			}
		};
		IntentFilter dspFilter = new IntentFilter();
		dspFilter.addAction(ACTION_STOP_SILENT_ANCHOR);
		dspFilter.addAction(ACTION_START_SILENT_ANCHOR);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			registerReceiver(dspAnchorReceiver, dspFilter, Context.RECEIVER_NOT_EXPORTED);
		} else {
			registerReceiver(dspAnchorReceiver, dspFilter);
		}

		Log.d("PermataMediaService (Subclass) initialized with custom features");
	}

	@Override
	public void onDestroy() {
		stopSilentAudioAnchor();
		audioExecutor.shutdownNow();
		if (dspAnchorReceiver != null) unregisterReceiver(dspAnchorReceiver);
		dspAnchorReceiver = null;
		super.onDestroy();
		Log.d("PermataMediaService (Subclass) destroyed");
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		super.onTaskRemoved(rootIntent);

		boolean isPlaying = callback != null && callback.isPlaying();
		boolean isAutoConnected = PermataApplication.get().isConnectedToAuto();

		if (isPlaying || isAutoConnected) {
			Log.i("App swiped away, but Media is playing or Auto is connected. Ignoring shutdown.");
			return;
		}

		Log.i("App swiped away from Recents. Killing PermataMediaService (Zombie #2).");
		stopSilentAudioAnchor();
		stopSelf();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();
			if (ACTION_HIJACK_FOCUS.equals(action)) {
				hijackCarAudioAndSteering();
			} else if (ACTION_WEB_MEDIA_PLAYING.equals(action)) {
				onWebMediaPlaying();
			} else if (ACTION_WEB_MEDIA_PAUSED.equals(action)) {
				onWebMediaPaused();
			}
		}
		return super.onStartCommand(intent, flags, startId);
	}

	public static void requestFocusAndAnchor(Context context) {
		try {
			Intent hijackIntent = new Intent(context, PermataMediaService.class);
			hijackIntent.setAction(ACTION_HIJACK_FOCUS);
			context.startService(hijackIntent);
		} catch (Exception e) {
			Log.e(e, "PermataMediaService: Failed static requestFocusAndAnchor.");
		}
	}

	private void onWebMediaPlaying() {
		Log.i("PermataMediaService: Web media is actively playing.");
		startSilentAudioAnchor();
		if (session != null) {
			session.setActive(true);
			updatePlaybackState(STATE_PLAYING);
		}
	}

	private void onWebMediaPaused() {
		Log.i("PermataMediaService: Web media is paused.");
		if (session != null) {
			updatePlaybackState(STATE_PAUSED);
		}
	}

	private void hijackCarAudioAndSteering() {
		try {
			AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			if (audioManager != null) {
				audioManager.requestAudioFocus(
						focusChange -> {
							if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
								stopSilentAudioAnchor();
							}
						},
						AudioManager.STREAM_MUSIC,
						AudioManager.AUDIOFOCUS_GAIN
				);
			}

			startSilentAudioAnchor();

			if (session != null) {
				session.setActive(true);
				updatePlaybackState(STATE_PLAYING);
				Log.i("PermataMediaService: Audio Focus & Steering Buttons successfully hijacked.");
			}
		} catch (Exception e) {
			Log.e(e, "PermataMediaService: Failed to execute audio hijack.");
		}
	}

	private synchronized void startSilentAudioAnchor() {
		if (isSilentTrackRunning) return;
		try {
			int sampleRate = 44100;
			int minBufferSize = AudioTrack.getMinBufferSize(
					sampleRate,
					AudioFormat.CHANNEL_OUT_STEREO,
					AudioFormat.ENCODING_PCM_16BIT
			);

			silentAudioTrack = new AudioTrack(
					AudioManager.STREAM_MUSIC,
					sampleRate,
					AudioFormat.CHANNEL_OUT_STEREO,
					AudioFormat.ENCODING_PCM_16BIT,
					Math.max(minBufferSize, 2048),
					AudioTrack.MODE_STREAM
			);

			byte[] silentBuffer = new byte[Math.max(minBufferSize, 2048)];
			silentAudioTrack.play();
			isSilentTrackRunning = true;

			audioTaskFuture = audioExecutor.scheduleAtFixedRate(() -> {
				AudioTrack track = silentAudioTrack;
				if (track != null && isSilentTrackRunning) {
					try {
						track.write(silentBuffer, 0, silentBuffer.length);
					} catch (Exception e) {
						Log.e(e, "Error in silent audio write loop");
					}
				}
			}, 0, 100, TimeUnit.MILLISECONDS);

			Log.i("PermataMediaService: Silent Audio Anchor active. AudioFocus permanently locked.");
		} catch (Exception e) {
			Log.e(e, "PermataMediaService: Failed to start Silent Audio Anchor.");
		}
	}

	private synchronized void stopSilentAudioAnchor() {
		isSilentTrackRunning = false;
		if (audioTaskFuture != null) {
			audioTaskFuture.cancel(false);
			audioTaskFuture = null;
		}
		if (silentAudioTrack != null) {
			try {
				silentAudioTrack.stop();
				silentAudioTrack.release();
			} catch (Exception ignored) {}
			silentAudioTrack = null;
			Log.i("PermataMediaService: Silent Audio Anchor stopped.");
		}
	}

	private void updatePlaybackState(int state) {
		if (session == null) return;
		PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
				.setActions(PlaybackStateCompat.ACTION_PLAY |
							PlaybackStateCompat.ACTION_PAUSE |
							PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
							PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
							PlaybackStateCompat.ACTION_STOP)
				.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f);
		session.setPlaybackState(stateBuilder.build());
	}
}