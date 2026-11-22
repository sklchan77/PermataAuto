package my.app.permata.media.service;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_ERROR;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED;
import static java.util.Objects.requireNonNull;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import my.app.permata.BuildConfig;
import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.permata.addon.AddonManager;
import my.app.permata.addon.PermataAddon;
import my.app.permata.addon.PermataMediaServiceAddon;
import my.app.permata.auto.MirrorDisplay;
import my.app.permata.media.lib.DefaultMediaLib;
import my.app.permata.media.lib.MediaLib;
import my.app.permata.media.lib.MediaLib.PlayableItem;
import my.app.permata.media.pref.PlaybackControlPrefs;
import my.app.permata.util.Utils;
import my.app.utils.app.App;
import my.app.utils.log.Log;
import my.app.utils.ui.UiUtils;

public class PermataMediaService extends MediaBrowserServiceCompat {
	public static final String ACTION_MEDIA_SERVICE = "my.app.permata.action.MediaService";
	public static final String ACTION_HIJACK_FOCUS = "my.app.permata.action.HIJACK_FOCUS";
	public static final String ACTION_FORCE_SHUTDOWN = "my.app.permata.action.FORCE_SHUTDOWN";
	public static final String ACTION_WEB_MEDIA_PLAYING = "my.app.permata.action.WEB_MEDIA_PLAYING";
	public static final String ACTION_WEB_MEDIA_PAUSED = "my.app.permata.action.WEB_MEDIA_PAUSED";
	public static final String ACTION_RECLAIM_SESSION = "my.app.permata.action.RECLAIM_SESSION";
	public static final String ACTION_AGGRESSIVE_RECLAIM = "my.app.permata.action.AGGRESSIVE_RECLAIM";

	public static final String INTENT_ATTR_NOTIF_COLOR = "my.app.permata.notif.color";
	public static final String DEFAULT_NOTIF_COLOR = "#3D2562";
	private static final String CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED";
	private static final String CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT";
	private static final String CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT";
	private static final int CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1;
	private static final String INTENT_PREV = "my.app.permata.action.prev";
	private static final String INTENT_RW = "my.app.permata.action.rw";
	private static final String INTENT_STOP = "my.app.permata.action.stop";
	private static final String INTENT_PLAY = "my.app.permata.action.play";
	private static final String INTENT_PAUSE = "my.app.permata.action.pause";
	private static final String INTENT_FF = "my.app.permata.action.ff";
	private static final String INTENT_NEXT = "my.app.permata.action.next";
	private static final String INTENT_FAVORITE_ADD = "my.app.permata.action.favorite.add";
	private static final String INTENT_FAVORITE_REMOVE = "my.app.permata.action.favorite.remove";
	private static final String EXTRA_MEDIA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED";
	private static final int NOTIF_ID = 1;
	private static final String NOTIF_CHANNEL_ID = "Permata";
	private static final long AGGRESSIVE_RECLAIM_COOLDOWN_MS = 2500L;

	private DefaultMediaLib lib;
	private MediaSessionCompat session;
	MediaSessionCallback callback;
	private BroadcastReceiver intentReceiver;
	private int notifColor;
	private PendingIntent notifContentIntent;
	private MediaStyle notifStyle;

	private Action actionPrev;
	private Action actionRw;
	private Action actionPlay;
	private Action actionPause;
	private Action actionFf;
	private Action actionNext;
	private Action actionFavAdd;
	private Action actionFavRm;
	private Bitmap defaultAudioIcon;
	private Bitmap defaultVideoIcon;

	private ExecutorService audioHardwareExecutor;
	private final AtomicReference<AudioTrack> silentAudioTrackRef = new AtomicReference<>(null);
	private final AtomicLong lastAggressiveReclaimTime = new AtomicLong(0L);

	private final Object audioLock = new Object();
	private volatile boolean isTeardownActive = false;
	private volatile boolean isForYouBrowseSession = false;

	private AudioManager.OnAudioFocusChangeListener focusChangeListener;
	private AudioFocusRequest audioFocusRequestOreo;

	// === [ABSOLUTE ZERO TIMERS] ===
	private final Handler lifecycleHandler = new Handler(Looper.getMainLooper());
	private volatile boolean hasEstablishedSession = false;
	private BroadcastReceiver disconnectReceiver;

	private final Runnable manualSwipeRunnable = () -> {
		Log.w(sessionTag() + "[DECISION] Absolute Zero 6s Manual Swipe Timeout | action=NUCLEAR_TEARDOWN");
		executeNuclearTeardown();
	};

	private final Runnable hardwareDropRunnable = () -> {
		Log.w(sessionTag() + "[DECISION] Absolute Zero 10s Hardware Drop Timeout | action=NUCLEAR_TEARDOWN");
		executeNuclearTeardown();
	};
	// ======================================

	private boolean isBluetoothA2dpActive() {
		try {
			AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			return am != null && am.isBluetoothA2dpOn();
		} catch (Exception ignored) {
			return false;
		}
	}

	private String sessionTag() {
		var app = PermataApplication.get();
		if (app != null && app.isMirroringMode()) return "[MIRROR]";
		if (app != null && app.isConnectedToAuto()) return "[CAR]";
		if (isBluetoothA2dpActive()) return "[BT]";
		if (isForYouBrowseSession) return "[FOR_YOU]";
		return "[SVC]";
	}

	private String connectionSnapshot() {
		var app = PermataApplication.get();
		boolean auto = app != null && app.isConnectedToAuto();
		boolean mirror = app != null && app.isMirroringMode();
		boolean bt = isBluetoothA2dpActive();
		return "auto=" + auto + ", mirror=" + mirror + ", bt=" + bt;
	}

	public MediaLib getLib() {
		return lib;
	}

	@SuppressLint("UnspecifiedRegisterReceiverFlag")
	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(sessionTag() + "[LIFECYCLE] PermataMediaService.onCreate | " + connectionSnapshot());
		Context ctx = this;
		audioHardwareExecutor = Executors.newSingleThreadExecutor();

		lib = new DefaultMediaLib(PermataApplication.get());
		session = new MediaSessionCompat(this, "PermataMediaService");
		setSessionToken(session.getSessionToken());
		callback = new MediaSessionCallback(this, session, lib,
				PlaybackControlPrefs.create(PermataApplication.get().getDefaultSharedPreferences()),
				PermataApplication.get().getHandler());
		callback.onPrepare();

		session.setFlags(
				MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
						MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
		);
		session.setCallback(callback);
		session.setActive(false);

		Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null, ctx, MediaButtonReceiver.class);
		session.setMediaButtonReceiver(PendingIntent.getBroadcast(ctx, 0, mediaButtonIntent, FLAG_IMMUTABLE));
		notifColor = Color.parseColor(DEFAULT_NOTIF_COLOR);
		App.get().getScheduler().schedule(lib::cleanUpPrefs, 1, TimeUnit.HOURS);

		for (PermataAddon a : AddonManager.get().getAddons()) {
			if (a instanceof PermataMediaServiceAddon)
				((PermataMediaServiceAddon) a).onServiceCreate(callback);
		}

		// [ABSOLUTE ZERO] Register Global Disconnect & BT Monitor
		disconnectReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (action != null) {
					boolean isBtEvent = android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
							|| android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action);
					String tag = isBtEvent ? "[BT]" : sessionTag();
					Log.i(tag + "[LIFECYCLE] OS Connection Broadcast | action=" + action + ", " + connectionSnapshot());
					evaluateConnectionState();
				}
			}
		};
		IntentFilter disconnectFilter = new IntentFilter();
		disconnectFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
		disconnectFilter.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED);
		disconnectFilter.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED);
		disconnectFilter.addAction("androidx.car.app.connection.action.CAR_CONNECTION_UPDATED");

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			registerReceiver(disconnectReceiver, disconnectFilter, Context.RECEIVER_NOT_EXPORTED);
		} else {
			registerReceiver(disconnectReceiver, disconnectFilter);
		}
	}

	// [ABSOLUTE ZERO] Evaluates if the phone has an active link to the car
	private boolean isCarOrBluetoothActive() {
		boolean active = false;
		if (PermataApplication.get().isMirroringMode() || PermataApplication.get().isConnectedToAuto()) {
			active = true;
		} else if (isBluetoothA2dpActive()) {
			active = true;
		}

		if (active) hasEstablishedSession = true;
		return active;
	}

	// [ABSOLUTE ZERO] Triggers the 10-second death timer if the hardware connection drops
	private void evaluateConnectionState() {
		if (isCarOrBluetoothActive()) {
			Log.i(sessionTag() + "[DECISION] Connection Active | " + connectionSnapshot()
					+ " -> action=CANCEL_TEARDOWN_TIMERS");
			lifecycleHandler.removeCallbacks(manualSwipeRunnable);
			lifecycleHandler.removeCallbacks(hardwareDropRunnable);
		} else {
			// Only trigger the 10-second hardware drop if a session was actually established first
			if (hasEstablishedSession) {
				Log.w("[BT][DECISION] Hardware/BT Session Lost | " + connectionSnapshot()
						+ " -> action=START_10S_ABSOLUTE_ZERO_COUNTDOWN");
				lifecycleHandler.removeCallbacks(manualSwipeRunnable);
				lifecycleHandler.removeCallbacks(hardwareDropRunnable);
				lifecycleHandler.postDelayed(hardwareDropRunnable, 10_000);
			}
		}
	}

	// [ABSOLUTE ZERO] The Maximum Destructive Cleanup Protocol
	private void executeNuclearTeardown() {
		if (isTeardownActive) return;
		isTeardownActive = true;
		Log.w(sessionTag() + "[LIFECYCLE] Nuclear Teardown Executing | " + connectionSnapshot());

		// 1. Force restore physical phone attributes (Brightness, Orientation, Overlays)
		try {
			MirrorDisplay.close();
		} catch (Exception ignored) {}

		// 2. Release Audio hardware hooks
		stopSilentAudioAnchor();
		releaseAudioFocus();

		// 3. Deactivate Media Session bindings (defer nulling to onDestroy to prevent bound NPEs)
		if (session != null) {
			try {
				session.setPlaybackState(new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_NONE, 0, 0.0f).build());
				session.setActive(false);

				// [SURGICAL FIX]: Ghost Session Bypass
				// If the car is still physically connected, DO NOT release the session.
				// This prevents the OS from routing steering wheel clicks into a black hole.
				if (!isCarOrBluetoothActive()) {
					session.release();
				} else {
					Log.w(sessionTag() + "[DECISION] Ghost Session Bypass | " + connectionSnapshot()
							+ " -> action=KEEP_MEDIASESSION_ALIVE");
				}
			} catch (Exception ignored) {}
		}

		// 4. Kill auxiliary child services
		try {
			stopService(new Intent(this, my.app.permata.auto.ProjectionService.class));
			stopService(new Intent(this, my.app.permata.auto.XposedEventDispatcherService.class));
		} catch (Exception ignored) {}

		// 5. Purge UI and Notifications
		try {
			NotificationManagerCompat.from(this).cancel(NOTIF_ID);
		} catch (Exception ignored) {}

		// 6. Stop Self (Let the OS gracefully cache the process to keep Accessibility alive)
		stopSelf();
	}

	@Override
	public void onDestroy() {
		Log.i(sessionTag() + "[LIFECYCLE] PermataMediaService.onDestroy | " + connectionSnapshot());
		isForYouBrowseSession = false;
		lifecycleHandler.removeCallbacks(manualSwipeRunnable);
		lifecycleHandler.removeCallbacks(hardwareDropRunnable);

		if (disconnectReceiver != null) {
			try { unregisterReceiver(disconnectReceiver); } catch (Exception ignored) {}
			disconnectReceiver = null;
		}

		executeNuclearTeardown();
		session = null;

		if (intentReceiver != null) {
			try { unregisterReceiver(intentReceiver); } catch (Exception ignored) {}
			intentReceiver = null;
		}
		if (audioHardwareExecutor != null) {
			audioHardwareExecutor.shutdownNow();
			audioHardwareExecutor = null;
		}
		PermataApplication.get().getHandler().removeCallbacksAndMessages(null);
		for (PermataAddon a : AddonManager.get().getAddons()) {
			if (a instanceof PermataMediaServiceAddon)
				((PermataMediaServiceAddon) a).onServiceDestroy(callback);
		}
		if (callback != null) {
			callback.close();
			callback = null;
		}
		lib = null;
		focusChangeListener = null;
		super.onDestroy();
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		super.onTaskRemoved(rootIntent);

		// If swiped away but still connected to car/BT, survive!
		if (isCarOrBluetoothActive()) {
			Log.i(sessionTag() + "[DECISION] onTaskRemoved | " + connectionSnapshot()
					+ " -> action=PRESERVE_AUDIO_SESSION");
			return;
		}

		Log.w("[SVC][DECISION] onTaskRemoved | " + connectionSnapshot()
				+ " -> action=START_6S_ABSOLUTE_ZERO_COUNTDOWN");
		lifecycleHandler.removeCallbacks(manualSwipeRunnable);
		lifecycleHandler.removeCallbacks(hardwareDropRunnable);
		lifecycleHandler.postDelayed(manualSwipeRunnable, 6_000);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			String action = intent.getAction();

			if (ACTION_HIJACK_FOCUS.equals(action)) {
				Log.i(sessionTag() + "[LIFECYCLE] onStartCommand | action=HIJACK_FOCUS, " + connectionSnapshot());
				// Cancel Absolute Zero shutdown and revive session if focus is requested again
				isTeardownActive = false;
				lifecycleHandler.removeCallbacks(manualSwipeRunnable);
				lifecycleHandler.removeCallbacks(hardwareDropRunnable);
				hijackCarAudioAndSteering();
			} else if (!isTeardownActive) {
				if (ACTION_FORCE_SHUTDOWN.equals(action)) {
					// Prevent accidental suicide if a BT session is still active
					if (isCarOrBluetoothActive()) {
						Log.i(sessionTag() + "[DECISION] FORCE_SHUTDOWN Blocked | " + connectionSnapshot()
								+ " -> action=PRESERVE_CAR_BT_SESSION");
						return START_NOT_STICKY;
					}
					Log.i("[SVC][DECISION] FORCE_SHUTDOWN Accepted | " + connectionSnapshot()
							+ " -> action=NUCLEAR_TEARDOWN");
					executeNuclearTeardown();
					return START_NOT_STICKY;
				} else if (ACTION_RECLAIM_SESSION.equals(action)) {
					Log.i(sessionTag() + "[AUDIO] onStartCommand | action=RECLAIM_SESSION");
					if (session != null) {
						session.setActive(true);
						updatePlaybackState(STATE_PLAYING);
					}
				} else if (ACTION_AGGRESSIVE_RECLAIM.equals(action)) {
					Log.i(sessionTag() + "[AUDIO] onStartCommand | action=AGGRESSIVE_RECLAIM");
					if (session != null) {
						session.setMetadata(new MediaMetadataCompat.Builder()
								.putString(MediaMetadataCompat.METADATA_KEY_TITLE, "IHU Web Mode Active")
								.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Permata")
								.build());
						session.setActive(true);
						updatePlaybackState(STATE_PLAYING);
					}
					forceAggressiveReclaim();
				} else if (ACTION_WEB_MEDIA_PLAYING.equals(action)) {
					onWebMediaPlaying();
				} else if (ACTION_WEB_MEDIA_PAUSED.equals(action)) {
					onWebMediaPaused();
				}
			}
		}
		if (session != null) {
			MediaButtonReceiver.handleIntent(session, intent);
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

	private void forceAggressiveReclaim() {
		if (audioHardwareExecutor == null || audioHardwareExecutor.isShutdown()) return;
		if (isTeardownActive) return;

		long now = SystemClock.uptimeMillis();
		long prev = lastAggressiveReclaimTime.get();
		if (now - prev < AGGRESSIVE_RECLAIM_COOLDOWN_MS) {
			Log.i(sessionTag() + "[AUDIO] Aggressive Reclaim Skipped | reason=COOLDOWN_ACTIVE(" + (now - prev) + "ms)");
			return;
		}
		if (!lastAggressiveReclaimTime.compareAndSet(prev, now)) {
			return;
		}

		try {
			audioHardwareExecutor.execute(() -> {
				if (isTeardownActive) return;
				try {
					AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
					if (audioManager != null) {

						AudioManager.OnAudioFocusChangeListener pulseListener = focusChange -> {};
						AudioFocusRequest duckRequest = null;

						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
							AudioAttributes playbackAttributes = new AudioAttributes.Builder()
									.setUsage(AudioAttributes.USAGE_MEDIA)
									.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
									.build();

							duckRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
									.setAudioAttributes(playbackAttributes)
									.setAcceptsDelayedFocusGain(false)
									.setWillPauseWhenDucked(false)
									.setOnAudioFocusChangeListener(pulseListener)
									.build();
							audioManager.requestAudioFocus(duckRequest);
						} else {
							//noinspection deprecation
							audioManager.requestAudioFocus(pulseListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
						}
						Log.i(sessionTag() + "[AUDIO] Aggressive Reclaim | action=TRANSIENT_DUCK_AND_SILENT_ANCHOR");

						startSilentAudioAnchorInternal();

						final AudioFocusRequest finalDuckReq = duckRequest;
						new Handler(Looper.getMainLooper()).postDelayed(() -> {
							try {
								if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && finalDuckReq != null) {
									audioManager.abandonAudioFocusRequest(finalDuckReq);
								} else {
									//noinspection deprecation
									audioManager.abandonAudioFocus(pulseListener);
								}
								Log.i(sessionTag() + "[AUDIO] Aggressive Reclaim Complete | action=FOCUS_RELEASED");
							} catch (Exception ignored) {}
						}, 1500);
					}
				} catch (Exception e) {
					Log.e(e, "Aggressive reclaim failed.");
				}
			});
		} catch (Exception rejectedExecution) {
			Log.e(rejectedExecution, "Aggressive execution queue rejected task.");
		}
	}

	private void onWebMediaPlaying() {
		if (isTeardownActive) return;

		String activePkg = my.app.permata.auto.AccessibilityEventDispatcherService.getLastActivePackage();
		boolean isPermataOrAA = activePkg != null && (activePkg.startsWith("my.app.permata") || activePkg.equals("com.google.android.projection.gearhead"));

		if (!isPermataOrAA) {
			Log.i(sessionTag() + "[DECISION] WebMedia PLAY Blocked | activePkg=" + activePkg);
			return;
		}

		if (PermataApplication.get().isMirroringMode() && PermataApplication.get().isConnectedToAuto()) {
			MirrorDisplay.shutdownForCarMedia();
		}

		if (isCarOrBluetoothActive()) {
			Log.i(sessionTag() + "[AUDIO] WebMedia PLAYING | " + connectionSnapshot()
					+ " -> action=KEEP_SILENT_ANCHOR_ALIVE");
		} else {
			Log.i("[PHONE][AUDIO] WebMedia PLAYING | " + connectionSnapshot()
					+ " -> action=YIELD_AUDIO_FOCUS_TO_WEBVIEW");
			stopSilentAudioAnchor();
			releaseAudioFocus();
		}

		if (session != null) {
			session.setActive(true);
			updatePlaybackState(STATE_PLAYING);
		}
	}

	private void onWebMediaPaused() {
		if (isTeardownActive) return;

		String activePkg = my.app.permata.auto.AccessibilityEventDispatcherService.getLastActivePackage();
		boolean isPermataOrAA = activePkg != null && (activePkg.startsWith("my.app.permata") || activePkg.equals("com.google.android.projection.gearhead"));

		if (!isPermataOrAA) {
			Log.i(sessionTag() + "[DECISION] WebMedia PAUSE Blocked | activePkg=" + activePkg);
			return;
		}

		Log.i(sessionTag() + "[AUDIO] WebMedia PAUSED | action=REENGAGE_SILENT_ANCHOR");
		if (session != null) {
			updatePlaybackState(STATE_PLAYING);
		}
		hijackCarAudioAndSteering();
	}

	private void hijackCarAudioAndSteering() {
		if (audioHardwareExecutor == null || audioHardwareExecutor.isShutdown()) return;
		if (isTeardownActive) return;

		if (session != null) {
			session.setMetadata(new MediaMetadataCompat.Builder()
					.putString(MediaMetadataCompat.METADATA_KEY_TITLE, "IHU Web Mode Active")
					.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Permata")
					.build());
			session.setActive(true);
			updatePlaybackState(STATE_PLAYING);
		}

		try {
			audioHardwareExecutor.execute(() -> {
				if (isTeardownActive) return;
				try {
					AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
					if (audioManager != null) {
						if (focusChangeListener == null) {
							focusChangeListener = focusChange -> {
								if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
									Log.w(sessionTag() + "[AUDIO] Focus Changed | state=LOSS");
								} else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
									Log.i(sessionTag() + "[AUDIO] Focus Changed | state=LOSS_TRANSIENT_CAN_DUCK");
								} else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
									Log.i(sessionTag() + "[AUDIO] Focus Changed | state=GAIN -> action=REANCHOR_SESSION");
									if (session != null && !isTeardownActive) {
										session.setActive(true);
										updatePlaybackState(STATE_PLAYING);
									}
								}
							};
						}

						Log.i(sessionTag() + "[AUDIO] Requesting AUDIOFOCUS_GAIN for Silent Anchor | " + connectionSnapshot());
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
							if (audioFocusRequestOreo == null) {
								AudioAttributes playbackAttributes = new AudioAttributes.Builder()
										.setUsage(AudioAttributes.USAGE_MEDIA)
										.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
										.build();
								audioFocusRequestOreo = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
										.setAudioAttributes(playbackAttributes)
										.setAcceptsDelayedFocusGain(true)
										.setWillPauseWhenDucked(false)
										.setOnAudioFocusChangeListener(focusChangeListener)
										.build();
							}
							audioManager.requestAudioFocus(audioFocusRequestOreo);
						} else {
							//noinspection deprecation
							audioManager.requestAudioFocus(
									focusChangeListener,
									AudioManager.STREAM_MUSIC,
									AudioManager.AUDIOFOCUS_GAIN
							);
						}
					}

					AudioTrack currentTrack = silentAudioTrackRef.get();
					if (currentTrack != null && currentTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
						return;
					}

					startSilentAudioAnchorInternal();
					Log.i(sessionTag() + "[AUDIO] Silent AudioTrack Started (16kHz Mono)");
				} catch (Exception t) {
					Log.e(t, "PermataMediaService: Audio Hardware thread encountered an exception.");
				}
			});
		} catch (Exception rejectedExecution) {
			Log.e(rejectedExecution, "PermataMediaService: Audio execution queue rejected task.");
		}
	}

	private void startSilentAudioAnchorInternal() {
		try {
			int sampleRate = 16000;
			int bufferSize = sampleRate * 2; // 16-bit mono = 2 bytes/frame (32 KB)

			AudioTrack newTrack = new AudioTrack(
					AudioManager.STREAM_MUSIC,
					sampleRate,
					AudioFormat.CHANNEL_OUT_MONO,
					AudioFormat.ENCODING_PCM_16BIT,
					bufferSize,
					AudioTrack.MODE_STATIC
			);

			byte[] silentBuffer = new byte[bufferSize];
			newTrack.write(silentBuffer, 0, silentBuffer.length);
			newTrack.setLoopPoints(0, sampleRate, -1);

			synchronized (audioLock) {
				if (isTeardownActive) {
					newTrack.release();
					return;
				}

				newTrack.play();
				AudioTrack oldTrack = silentAudioTrackRef.getAndSet(newTrack);
				if (oldTrack != null) {
					oldTrack.stop();
					oldTrack.release();
				}
			}
		} catch (Exception ignored) {}
	}

	private void releaseAudioFocus() {
		if (focusChangeListener != null) {
			try {
				AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				if (audioManager != null) {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequestOreo != null) {
						audioManager.abandonAudioFocusRequest(audioFocusRequestOreo);
					} else {
						//noinspection deprecation
						audioManager.abandonAudioFocus(focusChangeListener);
					}
					Log.i(sessionTag() + "[AUDIO] OS Audio Focus Abandoned");
				}
			} catch (Exception ignored) {}
		}
	}

	private void stopSilentAudioAnchor() {
		synchronized (audioLock) {
			AudioTrack track = silentAudioTrackRef.getAndSet(null);
			if (track != null) {
				try {
					track.stop();
					track.release();
					Log.i(sessionTag() + "[AUDIO] Silent AudioTrack Stopped");
				} catch (Exception ignored) {}
			}
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

	@Override
	public IBinder onBind(Intent intent) {
		String action = intent != null ? intent.getAction() : null;
		if (SERVICE_INTERFACE.equals(action)) {
			isForYouBrowseSession = true;
			Log.i("[FOR_YOU][LIFECYCLE] PermataMediaService.onBind | action=" + action);
		} else {
			if (ACTION_MEDIA_SERVICE.equals(action)) isForYouBrowseSession = false;
			Log.i(sessionTag() + "[LIFECYCLE] PermataMediaService.onBind | action=" + action);
		}
		if (BuildConfig.D || (intent != null && !intent.hasExtra(INTENT_ATTR_NOTIF_COLOR))) {
			var poi = PermataApplication.get().getAddonManager().getAddon("poi");
			if (poi != null) poi.start();
		}
		if (ACTION_MEDIA_SERVICE.equals(action)) {
			notifColor = intent.getIntExtra(INTENT_ATTR_NOTIF_COLOR, notifColor);
			return new ServiceBinder();
		}
		return super.onBind(intent);
	}

	@Override
	public boolean onUnbind(Intent intent) {
		String action = intent != null ? intent.getAction() : null;
		String tag = SERVICE_INTERFACE.equals(action) ? "[FOR_YOU]" : sessionTag();
		Log.i(tag + "[LIFECYCLE] PermataMediaService.onUnbind | action=" + action);
		if (BuildConfig.D || (intent != null && !intent.hasExtra(INTENT_ATTR_NOTIF_COLOR))) {
			var poi = PermataApplication.get().getAddonManager().getAddon("poi");
			if (poi != null) poi.stop();
		}
		return super.onUnbind(intent);
	}

	@Override
	public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, Bundle rootHints) {
		isForYouBrowseSession = true;
		Log.i("[FOR_YOU][BROWSE] onGetRoot | client=" + clientPackageName);
		Bundle extras = new Bundle();
		extras.putBoolean(EXTRA_MEDIA_SEARCH_SUPPORTED, true);
		extras.putBoolean(CONTENT_STYLE_SUPPORTED, true);
		extras.putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE);
		extras.putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE);
		return new BrowserRoot(getLib().getRootId(), extras);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onLoadChildren(@NonNull String parentMediaId, @NonNull Result<List<MediaItem>> result) {
		getLib().getChildren(parentMediaId, result);
	}

	@Override
	public void onLoadItem(String itemId, @NonNull Result<MediaItem> result) {
		getLib().getItem(itemId, result);
	}

	@Override
	public void onSearch(@NonNull String query, Bundle extras, @NonNull Result<List<MediaItem>> result) {
		getLib().search(query, result);
	}

	@Override
	public void onLowMemory() {
		Log.w(sessionTag() + "[LIFECYCLE] onLowMemory | action=CLEAR_CACHE");
		if (lib != null) lib.clearCache();
	}

	@SuppressLint("SwitchIntDef")
	void updateNotification(int st, PlayableItem currentItem) {
		switch (st) {
			case STATE_NONE, STATE_STOPPED, STATE_ERROR -> {
				if (isForYouBrowseSession) {
					Log.i("[FOR_YOU][PLAYBACK] State Changed | state=" + st + ", action=STOP_FOREGROUND");
				}
				stopForeground(true);
			}
			case STATE_PAUSED -> {
				if (isForYouBrowseSession) {
					Log.i("[FOR_YOU][PLAYBACK] State Changed | state=PAUSED, title="
							+ (currentItem != null ? currentItem.getName() : "unknown"));
				}
				if (ActivityCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
					return;
				}
				NotificationManagerCompat.from(this).notify(NOTIF_ID, createNotification(st, currentItem));
				stopForeground(false);
			}
			case STATE_PLAYING -> {
				boolean promoted = false;
				try {
					ContextCompat.startForegroundService(this, new Intent(this, PermataMediaService.class));
					promoted = true;
				} catch (Exception e) {
					Log.e(e, "[FOR_YOU][PLAYBACK] Failed to promote service via startForegroundService");
				}
				startForeground(NOTIF_ID, createNotification(st, currentItem));
				if (isForYouBrowseSession) {
					Log.i("[FOR_YOU][PLAYBACK] Item Playing | title="
							+ (currentItem != null ? currentItem.getName() : "unknown")
							+ ", servicePromoted=" + promoted + ", " + connectionSnapshot());
				}
			}
			default -> {
			}
		}
	}

	private Notification createNotification(int st, PlayableItem i) {
		notificationInit();
		Context ctx = this;

		if (session == null) {
			return new NotificationCompat.Builder(ctx, NOTIF_CHANNEL_ID)
					.setSmallIcon(R.drawable.notification)
					.build();
		}

		MediaControllerCompat controller = session.getController();
		MediaMetadataCompat mediaMetadata = controller.getMetadata();
		NotificationCompat.Builder builder =
				new NotificationCompat.Builder(ctx, NOTIF_CHANNEL_ID).setContentIntent(notifContentIntent)
						.setDeleteIntent(pi(INTENT_STOP)).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
						.setStyle(notifStyle).setSmallIcon(R.drawable.notification).setColor(notifColor)
						.setPriority(NotificationCompat.PRIORITY_HIGH).setShowWhen(false)
						.setOnlyAlertOnce(true);

		if (mediaMetadata != null) {
			MediaDescriptionCompat description = mediaMetadata.getDescription();
			Bitmap largeIcon = description.getIconBitmap();
			builder.setContentTitle(description.getTitle()).setContentText(description.getSubtitle())
					.setSubText(description.getDescription());

			if (callback != null && callback.isDefaultImage(largeIcon)) {
				if (i != null && i.isVideo()) {
					if (defaultVideoIcon == null) defaultVideoIcon = createLargeIcon(R.drawable.video);
					largeIcon = defaultVideoIcon;
				} else {
					if (defaultAudioIcon == null) defaultAudioIcon = createLargeIcon(R.drawable.audiotrack);
					largeIcon = defaultAudioIcon;
				}
			}
			builder.setLargeIcon(largeIcon);
		}

		builder.addAction(actionPrev).addAction(actionRw)
				.addAction((st == STATE_PLAYING) ? actionPause : actionPlay).addAction(actionFf)
				.addAction(actionNext)
				.addAction(((i != null) && i.isFavoriteItem()) ? actionFavRm : actionFavAdd);

		return builder.build();
	}

	private Bitmap createLargeIcon(@DrawableRes int icon) {
		Resources res = getResources();
		int w = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
		int h = res.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
		int s = Math.max(w, h);
		int min = UiUtils.toIntPx(this, 16);
		int max = UiUtils.toIntPx(this, 128);
		if (s < min) s = min;
		else if (s > max) s = max;
		return UiUtils.drawBitmap(requireNonNull(AppCompatResources.getDrawable(this, icon)),
				notifColor, Utils.getLauncherColor(), s, s);
	}

	@SuppressLint("UnspecifiedRegisterReceiverFlag")
	public void notificationInit() {
		if (notifContentIntent != null) return;
		try {
			Intent i = new Intent(this, Class.forName("my.app.permata.ui.activity.MainActivity"));
			notifContentIntent =
					PendingIntent.getActivity(this, 0, i, FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT);
		} catch (ClassNotFoundException ex) {
			if (session != null) {
				notifContentIntent = session.getController().getSessionActivity();
			}
		}

		actionPrev = new Action(R.drawable.prev, getString(R.string.prev), pi(INTENT_PREV));
		actionRw = new Action(R.drawable.rw, getString(R.string.rewind), pi(INTENT_RW));
		actionPause = new Action(R.drawable.pause, getString(R.string.pause), pi(INTENT_PAUSE));
		actionPlay = new Action(R.drawable.play, getString(R.string.play), pi(INTENT_PLAY));
		actionFf = new Action(R.drawable.ff, getString(R.string.fast_forward), pi(INTENT_FF));
		actionNext = new Action(R.drawable.next, getString(R.string.next), pi(INTENT_NEXT));
		actionFavAdd =
				new Action(R.drawable.favorite, getString(R.string.favorites_add), pi(INTENT_FAVORITE_ADD));
		actionFavRm =
				new Action(R.drawable.favorite, getString(R.string.favorites_remove), pi(INTENT_FAVORITE_REMOVE));
		if (session != null) {
			notifStyle = new MediaStyle()
					.setMediaSession(session.getSessionToken())
					.setShowActionsInCompactView(0, 2, 4);
		}

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(
					NOTIF_CHANNEL_ID,
					"Permata Media",
					NotificationManager.IMPORTANCE_LOW
			);
			channel.setDescription("Media Playback Controls");
			channel.setShowBadge(false);
			NotificationManager notificationManager = getSystemService(NotificationManager.class);
			if (notificationManager != null) {
				notificationManager.createNotificationChannel(channel);
			}
		}

		intentReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (intent != null && intent.getAction() != null && callback != null) {
					if (isTeardownActive) return;
					callback.onCustomAction(intent.getAction(), intent.getExtras());
				}
			}
		};

		IntentFilter filter = new IntentFilter();
		filter.addAction(INTENT_PREV);
		filter.addAction(INTENT_RW);
		filter.addAction(INTENT_STOP);
		filter.addAction(INTENT_PLAY);
		filter.addAction(INTENT_PAUSE);
		filter.addAction(INTENT_FF);
		filter.addAction(INTENT_NEXT);
		filter.addAction(INTENT_FAVORITE_ADD);
		filter.addAction(INTENT_FAVORITE_REMOVE);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			registerReceiver(intentReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
		} else {
			registerReceiver(intentReceiver, filter);
		}
	}

	private PendingIntent pi(String action) {
		Intent intent = new Intent(action).setPackage(getPackageName());
		return PendingIntent.getBroadcast(this, 0, intent, FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT);
	}

	public class ServiceBinder extends Binder {
		public PermataMediaService getService() {
			return PermataMediaService.this;
		}

		public MediaSessionCallback getMediaSessionCallback() {
			return callback;
		}
	}
}