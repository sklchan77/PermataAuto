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
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
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
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import my.app.permata.BuildConfig;
import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.permata.addon.AddonManager;
import my.app.permata.addon.PermataAddon;
import my.app.permata.addon.PermataMediaServiceAddon;
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
	public static final String ACTION_WEB_MEDIA_PLAYING = "my.app.permata.action.WEB_MEDIA_PLAYING"; 
	public static final String ACTION_WEB_MEDIA_PAUSED = "my.app.permata.action.WEB_MEDIA_PAUSED"; 
	
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
	
	private DefaultMediaLib lib;
	private MediaSessionCompat session;
	MediaSessionCallback callback;
	private BroadcastReceiver intentReceiver;
	private BroadcastReceiver carDisconnectReceiver;
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
	private AudioManager.OnAudioFocusChangeListener focusChangeListener;
	private long lastWebMediaPlayingTime = 0;
    
	// VOLUME TRACKER: Saves the phone's original volume before maxing it out.
	private volatile int originalMediaVolume = -1;

	public MediaLib getLib() {
		return lib;
	}

	@SuppressLint("UnspecifiedRegisterReceiverFlag")
	@Override
	public void onCreate() {
		super.onCreate();
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

		Intent mediaButtonIntent =
				new Intent(Intent.ACTION_MEDIA_BUTTON, null, ctx, MediaButtonReceiver.class);
		session.setMediaButtonReceiver(
				PendingIntent.getBroadcast(ctx, 0, mediaButtonIntent, FLAG_IMMUTABLE));
		notifColor = Color.parseColor(DEFAULT_NOTIF_COLOR);
		App.get().getScheduler().schedule(lib::cleanUpPrefs, 1, TimeUnit.HOURS);

		registerCarDisconnectWatchdog();
		
		Log.d("PermataMediaService created");
		for (PermataAddon a : AddonManager.get().getAddons()) {
			if (a instanceof PermataMediaServiceAddon)
				((PermataMediaServiceAddon) a).onServiceCreate(callback);
		}
	}

	private void registerCarDisconnectWatchdog() {
		carDisconnectReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action) || 
					UiModeManager.ACTION_EXIT_CAR_MODE.equals(action) ||
					Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
					executeNuclearTeardown();
				}
			}
		};
		IntentFilter filter = new IntentFilter();
		filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
		filter.addAction(UiModeManager.ACTION_EXIT_CAR_MODE);
		filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
		registerReceiver(carDisconnectReceiver, filter);
	}

	private void executeNuclearTeardown() {
		stopSilentAudioAnchor();
		releaseAudioFocus();
		
		// VOLUME RESTORATION: Restore the original phone media volume upon teardown
		if (originalMediaVolume != -1) {
			try {
				AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				if (am != null) {
					am.setStreamVolume(AudioManager.STREAM_MUSIC, originalMediaVolume, 0);
				}
			} catch (Exception ignored) {}
			originalMediaVolume = -1; // Reset tracker
		}

		if (session != null) {
			session.setActive(false);
			updatePlaybackState(STATE_STOPPED);
		}
		stopSelf();
	}

	@Override
	public void onDestroy() {
		executeNuclearTeardown();
		if (intentReceiver != null) {
			unregisterReceiver(intentReceiver);
			intentReceiver = null;
		}
		if (carDisconnectReceiver != null) {
			unregisterReceiver(carDisconnectReceiver);
			carDisconnectReceiver = null;
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
		if (session != null) {
			session.release();
			session = null;
		}
		lib = null;
		focusChangeListener = null;
		NotificationManagerCompat.from(this).cancel(NOTIF_ID);
		super.onDestroy();
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
		if (PermataApplication.get().isConnectedToAuto()) return;
		executeNuclearTeardown();
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

	private void onWebMediaPlaying() {
		long now = SystemClock.uptimeMillis();
		if (now - lastWebMediaPlayingTime < 300) {
			return;
		}
		lastWebMediaPlayingTime = now;

		if (PermataApplication.get().isConnectedToAuto()) {
			try {
				AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				if (am != null) {
					// VOLUME TRACKER: Cache the user's current volume before forcing max
					if (originalMediaVolume == -1) {
						originalMediaVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
					}
					
					int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
					am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0); 
				}
			} catch (Exception ignored) {}
		}
		
		hijackCarAudioAndSteering();
	}

	private void onWebMediaPaused() {
		if (session != null) {
			updatePlaybackState(STATE_PLAYING);
		}
	}

	private void hijackCarAudioAndSteering() {
		if (audioHardwareExecutor == null || audioHardwareExecutor.isShutdown()) return;
		
		if (session != null) {
			session.setMetadata(new MediaMetadataCompat.Builder()
					.putString(MediaMetadataCompat.METADATA_KEY_TITLE, "IHU Web Mode Active")
					.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Permata")
					.build());
			session.setActive(true);
			updatePlaybackState(STATE_PLAYING);
		}

		audioHardwareExecutor.execute(() -> {
			try {
				AudioTrack currentTrack = silentAudioTrackRef.get();
				if (currentTrack != null && currentTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
					return;
				}

				AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				if (audioManager != null) {
					if (focusChangeListener == null) {
						focusChangeListener = focusChange -> {
							if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
								Log.w("PermataMediaService: Car Radio stole focus. GOD MODE Active: Ignoring loss.");
							}
						};
					}
					audioManager.requestAudioFocus(
							focusChangeListener, 
							AudioManager.STREAM_MUSIC, 
							AudioManager.AUDIOFOCUS_GAIN
					);
				}

				startSilentAudioAnchorInternal();
			} catch (Exception e) {
				Log.e(e, "PermataMediaService: Failed to execute audio hijack.");
			}
		});
	}

	private void startSilentAudioAnchorInternal() {
		try {
			int sampleRate = 44100;
			int bufferSize = sampleRate * 2 * 2; 

			AudioTrack newTrack = new AudioTrack(
					AudioManager.STREAM_MUSIC,
					sampleRate,
					AudioFormat.CHANNEL_OUT_STEREO,
					AudioFormat.ENCODING_PCM_16BIT,
					bufferSize,
					AudioTrack.MODE_STATIC
			);

			byte[] silentBuffer = new byte[bufferSize];
			newTrack.write(silentBuffer, 0, silentBuffer.length);
			newTrack.setLoopPoints(0, sampleRate, -1);
			newTrack.play();

			AudioTrack oldTrack = silentAudioTrackRef.getAndSet(newTrack);
			if (oldTrack != null) {
				oldTrack.stop();
				oldTrack.release();
			}
		} catch (Exception ignored) {}
	}

	private void releaseAudioFocus() {
		if (focusChangeListener != null) {
			try {
				AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				if (audioManager != null) {
					audioManager.abandonAudioFocus(focusChangeListener);
				}
			} catch (Exception ignored) {}
		}
	}

	private void stopSilentAudioAnchor() {
		if (audioHardwareExecutor == null || audioHardwareExecutor.isShutdown()) {
			AudioTrack track = silentAudioTrackRef.getAndSet(null);
			if (track != null) {
				try { track.stop(); track.release(); } catch (Exception ignored) {}
			}
			return;
		}

		audioHardwareExecutor.execute(() -> {
			AudioTrack track = silentAudioTrackRef.getAndSet(null);
			if (track != null) {
				try { track.stop(); track.release(); } catch (Exception ignored) {}
			}
		});
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
		if (BuildConfig.D || !intent.hasExtra(INTENT_ATTR_NOTIF_COLOR)) {
			var poi = PermataApplication.get().getAddonManager().getAddon("poi");
			if (poi != null) poi.start();
		}
		if (ACTION_MEDIA_SERVICE.equals(intent.getAction())) {
			notifColor = intent.getIntExtra(INTENT_ATTR_NOTIF_COLOR, notifColor);
			return new ServiceBinder();
		}
		return super.onBind(intent);
	}

	@Override
	public boolean onUnbind(Intent intent) {
		if (BuildConfig.D || !intent.hasExtra(INTENT_ATTR_NOTIF_COLOR)) {
			var poi = PermataApplication.get().getAddonManager().getAddon("poi");
			if (poi != null) poi.stop();
		}
		return super.onUnbind(intent);
	}

	@Override
	public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, Bundle rootHints) {
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
		if (lib != null) lib.clearCache();
	}

	@SuppressLint("SwitchIntDef")
	void updateNotification(int st, PlayableItem currentItem) {
		switch (st) {
			case STATE_NONE, STATE_STOPPED, STATE_ERROR -> stopForeground(true);
			case STATE_PAUSED -> {
				if (ActivityCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
					return;
				}
				NotificationManagerCompat.from(this).notify(NOTIF_ID, createNotification(st, currentItem));
				stopForeground(false);
			}
			case STATE_PLAYING -> startForeground(NOTIF_ID, createNotification(st, currentItem));
			default -> {
			}
		}
	}

	private Notification createNotification(int st, PlayableItem i) {
		notificationInit();
		Context ctx = this;
		if (session == null) return new NotificationCompat.Builder(ctx, NOTIF_CHANNEL_ID).build();
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