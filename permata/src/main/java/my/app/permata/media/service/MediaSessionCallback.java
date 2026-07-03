package my.app.permata.media.service;

import static android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_FAST_FORWARD;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_FROM_URI;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_REWIND;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SET_REPEAT_MODE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP;
import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ALL;
import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_GROUP;
import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ONE;
import static android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_ALL;
import static android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_CONNECTING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_ERROR;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_FAST_FORWARDING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_NONE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_REWINDING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED;
import static my.app.permata.action.KeyEventHandler.handleKeyEvent;
import static my.app.permata.media.engine.MediaEngine.NO_SUBTITLES;
import static my.app.permata.media.pref.MediaPrefs.AE_ENABLED;
import static my.app.permata.media.pref.MediaPrefs.BASS_ENABLED;
import static my.app.permata.media.pref.MediaPrefs.BASS_STRENGTH;
import static my.app.permata.media.pref.MediaPrefs.EQ_BANDS;
import static my.app.permata.media.pref.MediaPrefs.EQ_ENABLED;
import static my.app.permata.media.pref.MediaPrefs.EQ_PRESET;
import static my.app.permata.media.pref.MediaPrefs.EQ_USER_PRESETS;
import static my.app.permata.media.pref.MediaPrefs.VIRT_ENABLED;
import static my.app.permata.media.pref.MediaPrefs.VIRT_MODE;
import static my.app.permata.media.pref.MediaPrefs.VIRT_STRENGTH;
import static my.app.permata.media.pref.MediaPrefs.VOL_BOOST_ENABLED;
import static my.app.permata.media.pref.MediaPrefs.VOL_BOOST_STRENGTH;
import static my.app.permata.media.pref.MediaPrefs.getUserPresetBands;
import static my.app.permata.media.pref.PlaybackControlPrefs.getTimeMillis;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.completedNull;
import static my.app.utils.async.Completed.completedVoid;
import static my.app.utils.function.CheckedRunnable.runWithRetry;
import static my.app.utils.misc.Assert.assertNotNull;
import static my.app.utils.misc.MiscUtils.ifNotNull;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import java.io.Closeable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

import my.app.permata.BuildConfig;
import my.app.permata.PermataApplication;
import my.app.permata.R;
import my.app.permata.media.engine.AudioEffects;
import my.app.permata.media.engine.MediaEngine;
import my.app.permata.media.engine.MediaEngineManager;
import my.app.permata.media.engine.MediaEngineProvider;
import my.app.permata.media.engine.SubtitleStreamInfo;
import my.app.permata.media.lib.MediaLib;
import my.app.permata.media.lib.MediaLib.BrowsableItem;
import my.app.permata.media.lib.MediaLib.Favorites;
import my.app.permata.media.lib.MediaLib.Item;
import my.app.permata.media.lib.MediaLib.PlayableItem;
import my.app.permata.media.lib.MediaLib.StreamItem;
import my.app.permata.media.pref.BrowsableItemPrefs;
import my.app.permata.media.pref.MediaLibPrefs;
import my.app.permata.media.pref.MediaPrefs;
import my.app.permata.media.pref.PlayableItemPrefs;
import my.app.permata.media.pref.PlaybackControlPrefs;
import my.app.permata.media.sub.SubGrid;
import my.app.permata.media.sub.Subtitles;
import my.app.permata.ui.view.VideoView;
import my.app.utils.async.FutureSupplier;
import my.app.utils.collection.CollectionUtils;
import my.app.utils.event.EventBroadcaster;
import my.app.utils.function.BiConsumer;
import my.app.utils.function.Consumer;
import my.app.utils.holder.Holder;
import my.app.utils.log.Log;
import my.app.utils.net.NetServer;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.ui.UiUtils;
import my.app.utils.ui.activity.ActivityDelegate;

/**
 * @author sklchan77
 */
public class MediaSessionCallback extends MediaSessionCompat.Callback
		implements MediaSessionCallbackAssistant, MediaEngine.Listener,
		AudioManager.OnAudioFocusChangeListener, EventBroadcaster<MediaSessionCallback.Listener>,
		BiConsumer<SubGrid.Position, Subtitles.Text>, Closeable {

	public static final String EXTRA_POS = "my.app.permata.extra.pos";
	private static final String CUSTOM_ACTION_RW = "my.app.permata.action.rewind";
	private static final String CUSTOM_ACTION_FF = "my.app.permata.action.fastForward";
	private static final String CUSTOM_ACTION_REPEAT_ENABLE = "my.app.permata.action.repeat.enable";
	private static final String CUSTOM_ACTION_REPEAT_DISABLE = "my.app.permata.action.repeat.disable";
	private static final String CUSTOM_ACTION_SHUFFLE_ENABLE = "my.app.permata.action.shuffle.enable";
	private static final String CUSTOM_ACTION_SHUFFLE_DISABLE = "my.app.permata.action.shuffle.disable";
	private static final String CUSTOM_ACTION_FAVORITES_ADD = "my.app.permata.action.favorites.add";
	private static final String CUSTOM_ACTION_FAVORITES_REMOVE = "my.app.permata.action.favorites.remove";
	
	private static final long SUPPORTED_ACTIONS =
			ACTION_PLAY | ACTION_STOP | ACTION_PAUSE | ACTION_PLAY_PAUSE | ACTION_PLAY_FROM_MEDIA_ID |
					ACTION_PLAY_FROM_SEARCH | ACTION_PLAY_FROM_URI | ACTION_SKIP_TO_PREVIOUS |
					ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_QUEUE_ITEM | ACTION_REWIND | ACTION_FAST_FORWARD |
					ACTION_SEEK_TO | ACTION_SET_REPEAT_MODE | ACTION_SET_SHUFFLE_MODE;

	private final Collection<ListenerRef<MediaSessionCallback.Listener>> listeners = new LinkedList<>();
	private final MediaLib lib;
	private final PermataMediaService service;
	private final MediaSessionCompat session;
	private final PlaybackControlPrefs playbackControlPrefs;
	private final Handler handler;
	private final AudioManager audioManager;
	private final AudioFocusRequestCompat audioFocusReq;
	private final PlaybackStateCompat.CustomAction customRewind;
	private final PlaybackStateCompat.CustomAction customFastForward;
	private final PlaybackStateCompat.CustomAction customRepeatEnable;
	private final PlaybackStateCompat.CustomAction customRepeatDisable;
	private final PlaybackStateCompat.CustomAction customShuffleEnable;
	private final PlaybackStateCompat.CustomAction customShuffleDisable;
	private final PlaybackStateCompat.CustomAction customFavoritesAdd;
	private final PlaybackStateCompat.CustomAction customFavoritesRemove;
	private final BroadcastReceiver onNoisy;
	
	private MediaEngine engine;
	private boolean playOnPrepared;
	private boolean playOnAudioFocus;
	private boolean isMuted;
	private boolean tryAnotherEngine;
	@NonNull
	private PlaybackStateCompat currentState;
	private Queue<Prioritized<VideoView>> videoView;
	private Queue<Prioritized<MediaSessionCallbackAssistant>> assistants;
	private FutureSupplier<?> playerTask = completedVoid();
	private MediaMetadataCompat metadata;
	private PlaybackTimer playbackTimer;

	public MediaSessionCallback(PermataMediaService service, MediaSessionCompat session,
															MediaLib lib, PlaybackControlPrefs playbackControlPrefs, Handler handler) {
		this.lib = lib;
		this.service = service;
		this.session = session;
		this.playbackControlPrefs = playbackControlPrefs;
		this.handler = handler;
		Context ctx = lib.getContext();

		customRewind = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_RW, ctx.getString(R.string.rewind), R.drawable.rw).build();
		customFastForward = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_FF, ctx.getString(R.string.fast_forward), R.drawable.ff).build();
		customRepeatEnable = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_REPEAT_ENABLE, ctx.getString(R.string.repeat), R.drawable.repeat).build();
		customRepeatDisable = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_REPEAT_DISABLE, ctx.getString(R.string.repeat_disable), R.drawable.repeat_filled).build();
		customShuffleEnable = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_SHUFFLE_ENABLE, ctx.getString(R.string.shuffle), R.drawable.shuffle).build();
		customShuffleDisable = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_SHUFFLE_DISABLE, ctx.getString(R.string.shuffle_disable), R.drawable.shuffle_filled).build();
		customFavoritesAdd = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_FAVORITES_ADD, ctx.getString(R.string.favorites_add), R.drawable.favorite).build();
		customFavoritesRemove = new PlaybackStateCompat.CustomAction.Builder(CUSTOM_ACTION_FAVORITES_REMOVE, ctx.getString(R.string.favorites_remove), R.drawable.favorite_filled).build();

		currentState = new PlaybackStateCompat.Builder().setActions(SUPPORTED_ACTIONS).build();
		setPlaybackState(currentState);
		session.setActive(true);

		audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

		if (audioManager != null) {
			AudioAttributesCompat focusAttrs = new AudioAttributesCompat.Builder()
					.setUsage(AudioAttributesCompat.USAGE_MEDIA)
					.setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build();
			audioFocusReq = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
					.setAudioAttributes(focusAttrs)
					.setWillPauseWhenDucked(false)
					.setOnAudioFocusChangeListener(this).build();
		} else {
			audioFocusReq = null;
		}

		onNoisy = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
					Log.i("Received ACTION_AUDIO_BECOMING_NOISY event");
					onPause();
				}
			}
		};
		ctx.registerReceiver(onNoisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
	}

	public Context getContext() {
		var ctx = getMediaLib().getContext();
		if (BuildConfig.AUTO) {
			var f = ActivityDelegate.getContextToDelegate();
			if (f != null) {
				var d = f.apply(ctx);
				if (d != null) ctx = d.getContext();
			}
		}
		return ctx;
	}

	public MediaLib getMediaLib() { return lib; }
	public MediaEngineManager getEngineManager() { return lib.getMediaEngineManager(); }
	
	@Nullable
	public MediaEngine getEngine() { return engine; }

	public void setEngine(MediaEngine engine) {
		if (this.engine == engine) return;
		playerTask.cancel();
		onStop();
		this.engine = engine;
	}

	@Nullable
	public PlayableItem getCurrentItem() {
		MediaEngine eng = getEngine();
		return (eng == null) ? null : eng.getSource();
	}

	public MediaSessionCompat getSession() { return session; }
	
	@NonNull
	public PlaybackControlPrefs getPlaybackControlPrefs() { return playbackControlPrefs; }

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() { return listeners; }

public void addVideoView(VideoView view, int priority) {
		if (this.videoView == null) {
			videoView = new PriorityQueue<>(2);
		} else {
			for (Prioritized<VideoView> s : videoView) {
				if (s.obj == view) return;
			}
		}
		videoView.add(new Prioritized<>(view, priority));
		MediaEngine eng = getEngine();
		if (eng != null) {
			PlayableItem i = eng.getSource();
			if (i.isVideo()) eng.setVideoView(getVideoView());
		}
	}

	public void removeVideoView(VideoView view) {
		MediaEngine eng = getEngine();
		if (removeFromQueue(videoView, view)) {
			if (videoView.isEmpty()) {
				videoView = null;
				if (eng != null) eng.setVideoView(null);
			} else if (eng != null) {
				eng.setVideoView(getVideoView());
			}
		}
	}

	@Nullable
	public VideoView getVideoView() {
		return (videoView == null || videoView.isEmpty()) ? null : videoView.peek().obj;
	}

	private <T> boolean removeFromQueue(Queue<Prioritized<T>> q, T t) {
		if (q == null) return false;
		for (Iterator<Prioritized<T>> it = q.iterator(); it.hasNext(); ) {
			if (it.next().obj == t) {
				it.remove();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
		KeyEvent e = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
		if (e == null) return super.onMediaButtonEvent(mediaButtonEvent);
		return handleKeyEvent(this, e, (i, ke) -> super.onMediaButtonEvent(mediaButtonEvent));
	}

	public void close() {
		onStop();
		session.setActive(false);
		lib.getContext().unregisterReceiver(onNoisy);
		removeBroadcastListeners();
		metadata = null;
	}

	/**
	 * Resolution-Agnostic Responsive Screen Space In-Car Viewport Scroller.
	 * Executes asynchronously on UI thread to eliminate deadlock or lock-contention ANRs.
	 * Decoupled via safe reflection to remain fully immune to circular build dependencies.
	 */
	private boolean handleBrowserMediaNavigation(final boolean isNext) {
		try {
			Class<?> activityCtx = Class.forName("my.app.permata.ui.activity.MainActivity");
			java.lang.reflect.Method getActive = activityCtx.getMethod("getActiveInstance");
			Object activeActivity = getActive.invoke(null);
			if (!(activeActivity instanceof androidx.fragment.app.FragmentActivity activity)) return false;

			Class<?> delegateCtx = Class.forName("my.app.permata.ui.activity.MainActivityDelegate");
			java.lang.reflect.Method getDelegate = delegateCtx.getMethod("get", android.content.Context.class);
			Object delegate = getDelegate.invoke(null, activity);
			if (delegate == null) return false;

			java.lang.reflect.Method getActiveFragment = null;
			try {
				getActiveFragment = delegateCtx.getMethod("getActiveMainActivityFragment");
			} catch (NoSuchMethodException e) {
				getActiveFragment = delegateCtx.getMethod("getActiveFragment");
			}
			if (getActiveFragment == null) return false;

			Object activeFragment = getActiveFragment.invoke(delegate);
			if (activeFragment == null) return false;

			String fragName = activeFragment.getClass().getName();
			if (fragName.endsWith("WebBrowserFragment") || fragName.contains("WebBrowser")) {
				final Object fragment = activeFragment;

				// Safe cross-thread UI scheduling (Defeats potential background ANR risks)
				activity.runOnUiThread(() -> {
					try {
						android.webkit.WebView webView = null;
						try {
							java.lang.reflect.Method getWebViewMethod = fragment.getClass().getMethod("getWebView");
							webView = (android.webkit.WebView) getWebViewMethod.invoke(fragment);
						} catch (Exception e) {
							// Strict Field-matching fallback strategy
							for (java.lang.reflect.Field field : fragment.getClass().getDeclaredFields()) {
								if (android.webkit.WebView.class.isAssignableFrom(field.getType())) {
									field.setAccessible(true);
									webView = (android.webkit.WebView) field.get(fragment);
									break;
								}
							}
						}

						if (webView != null) {
							String jsPayload = String.format(java.util.Locale.US,
								"(function() {" +
								"  var isNext = %b;" +
								"  var url = window.location.href;" +
								"  /* VIEWPORT COMPUTATION ENGINE: Dynamically extract active layout allocations */" +
								"  var ihuViewHeight = window.innerHeight || document.documentElement.clientHeight || document.body.clientHeight;" +
								"  var scrollPercentage = 0.80;" +
								"  var dynamicScrollDistance = ihuViewHeight * scrollPercentage * (isNext ? 1 : -1);" +
								"  /* STRATEGY 1: Video Container Detection for Short-form platform components */" +
								"  var isShortFormPlatform = url.includes('tiktok.com') || url.includes('douyin.com') || url.includes('instagram.com') || url.includes('/shorts/');" +
								"  if (isShortFormPlatform) {" +
								"    var vid = document.querySelector('video');" +
								"    if (vid) {" +
								"      var p = vid.parentElement;" +
								"      while (p && p !== document.body) {" +
								"        var s = window.getComputedStyle(p);" +
								"        if (s.overflowY === 'auto' || s.overflowY === 'scroll' || p.scrollHeight > p.clientHeight) {" +
								"          var containerHeight = p.clientHeight || ihuViewHeight;" +
								"          p.scrollBy({ top: containerHeight * scrollPercentage * (isNext ? 1 : -1), behavior: 'smooth' });" +
								"          return;" +
								"        }" +
								"        p = p.parentElement;" +
								"      }" +
								"    }" +
								"  }" +
								"  /* STRATEGY 2: Hard Click Interception for Standard Media Track Containers */" +
								"  if (url.includes('youtube.com') || url.includes('youtu.be')) {" +
								"    var ytBtn = isNext ? document.querySelector('.ytp-next-button') : document.querySelector('.ytp-prev-button');" +
								"    if (ytBtn && ytBtn.offsetParent !== null) {" +
								"      ytBtn.click();" +
								"      return;" +
								"    }" +
								"  }" +
								"  /* STRATEGY 3: Generic Screen-Proportional Viewport Step Fallback */" +
								"  window.scrollBy({ top: dynamicScrollDistance, behavior: 'smooth' });" +
								"})();", isNext);

							webView.evaluateJavascript(jsPayload, null);
						}
					} catch (Exception ex) {
						Log.d(ex);
					}
				});
				return true;
			}
		} catch (Exception err) {
			Log.d(err);
		}
		return false;
	}

	@Override
	public void onSkipToNext() {
		if (handleBrowserMediaNavigation(true)) return;
		
		PlayableItem current = getCurrentItem();
		if (current != null) {
			current.getNextPlayable().onSuccess(next -> {
				if (next != null) skipTo(true, next);
			});
		}
	}

	@Override
	public void onSkipToPrevious() {
		if (handleBrowserMediaNavigation(false)) return;
		
		PlayableItem current = getCurrentItem();
		if (current != null) {
			current.getPreviousPlayable().onSuccess(prev -> {
				if (prev != null) skipTo(false, prev);
			});
		}
	}

	private void skipTo(boolean next, PlayableItem i) {
		PlaybackStateCompat state = getPlaybackState();
		long pos = i.getPrefs().getPositionPref();
		PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder(state);
		b.setState(next ? STATE_SKIPPING_TO_NEXT : STATE_SKIPPING_TO_PREVIOUS, pos, state.getPlaybackSpeed());
		setPlaybackState(b.build());
		playPreparedItem(i, pos);
	}

	private void playPreparedItem(PlayableItem i, long pos) {
		MediaEngine eng = getEngine();
		if (eng != null) {
			eng.prepare(i);
			if (pos > 0) eng.seekTo(pos);
		}
	}

	@Override
	public void onPrepare() {
		playerTask.cancel();
		playerTask = prepare();
	}

	private FutureSupplier<Void> prepare() {
		int st = getPlaybackState().getState();
		if ((st != PlaybackState.STATE_NONE) && (st != PlaybackState.STATE_ERROR)) {
			return completedVoid();
		}
		return lib.getLastPlayedItem().then(this::prepareItem).then(i -> {
			if (i == null) return completedVoid();
			if (i.isVideo() || !i.isSeekable()) {
				setPlaybackState(createPlayingState(i, STATE_STOPPED, 0, 0, 1f));
				return i.getMediaData().onSuccess(this::setMetadata).cast();
			}
			return completedVoid();
		});
	}

	private FutureSupplier<PlayableItem> prepareItem(PlayableItem i) {
		return completed(i);
	}

	private PlaybackStateCompat createPlayingState(PlayableItem i, int state, long pos, long qid, float speed) {
		return new PlaybackStateCompat.Builder()
				.setState(state, pos, speed)
				.setActions(SUPPORTED_ACTIONS)
				.setActiveQueueItemId(qid)
				.build();
	}

	public void setPlaybackState(PlaybackStateCompat state) {
		this.currentState = state;
		session.setPlaybackState(state);
	}

	public PlaybackStateCompat getPlaybackState() { return currentState; }

	public void setMetadata(MediaMetadataCompat md) {
		this.metadata = md;
		session.setMetadata(md);
	}

	@Override
	public void onPlay() {
		MediaEngine eng = getEngine();
		if (eng != null) {
			eng.play();
			playOnPrepared = true;
		}
	}

	@Override
	public void onPause() {
		PlayableItem i;
		MediaEngine eng = getEngine();
		if ((eng == null) || ((i = eng.getSource()) == null)) return;
		if (!eng.canPause()) {
			onStop();
			return;
		}
		eng.pause();
	}

	@Override
	public void onStop() { onStop(true); }

	private FutureSupplier<?> onStop(boolean setPosition) {
		MediaEngine eng = getEngine();
		if (eng != null) eng.stop();
		return completedVoid();
	}

	@Override
	public void onSeekTo(long pos) {
		MediaEngine eng = getEngine();
		if (eng != null) eng.seekTo(pos);
	}

	public boolean isPlaying() {
		return currentState != null && currentState.getState() == PlaybackStateCompat.STATE_PLAYING;
	}

	@Nullable
	public MediaSessionCallbackAssistant getAssistant() {
		return (assistants == null || assistants.isEmpty()) ? null : assistants.peek().obj;
	}

	public void addAssistant(MediaSessionCallbackAssistant assistant, int priority) {
		if (this.assistants == null) {
			assistants = new PriorityQueue<>(2);
		} else {
			for (Prioritized<MediaSessionCallbackAssistant> a : assistants) {
				if (a.obj == assistant) return;
			}
		}
		assistants.add(new Prioritized<>(assistant, priority));
	}

	public void removeAssistant(MediaSessionCallbackAssistant assistant) {
		if (removeFromQueue(assistants, assistant)) {
			if (assistants.isEmpty()) {
				assistants = null;
			}
		}
	}

	public void rewindFastForward(boolean fastForward, int seconds) {
		MediaEngine eng = getEngine();
		if (eng != null) {
			long currentPos = eng.getPosition();
			long delta = seconds * 1000L;
			long newPos = fastForward ? (currentPos + delta) : Math.max(0, currentPos - delta);
			eng.seekTo(newPos);
		}
	}

	public void favoriteAddRemove(boolean add) {
		PlayableItem current = getCurrentItem();
		if (current != null && lib.getFavorites() != null) {
			if (add) {
				lib.getFavorites().add(current);
			} else {
				lib.getFavorites().remove(current);
			}
		}
	}

@Override
	public void onEngineError(MediaEngine engine, Throwable ex) {
		Log.w(ex, "Engine encountered playback exception.");
	}

	@Override
	public void onPlaybackStateChanged(MediaEngine engine, int state) {}
	@Override
	public void onBufferingStateChanged(MediaEngine engine, boolean buffering) {}
	@Override
	public void onVideoSizeChanged(MediaEngine engine, int width, int height) {}

	@Override
	public void onAudioFocusChange(int focusChange) {
		switch (focusChange) {
			case AUDIOFOCUS_GAIN:
				if (playOnAudioFocus) {
					onPlay();
					playOnAudioFocus = false;
				}
				break;
			case AUDIOFOCUS_LOSS_TRANSIENT:
			case AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
				if (getPlaybackState().getState() == STATE_PLAYING) {
					playOnAudioFocus = true;
					onPause();
				}
				break;
		}
	}

	@Override
	public void accept(SubGrid.Position position, Subtitles.Text text) {}

	@Override
	public void onPlaybackStateChanged(MediaSessionCallback cb, PlaybackStateCompat state) {}
	@Override
	public void onSubtitleStreamChanged(MediaSessionCallback cb, @Nullable SubtitleStreamInfo info) {}

	public int getPlaybackTimer() {
		return (playbackTimer == null) ? 0 : Math.max((int) (playbackTimer.time - System.currentTimeMillis()) / 1000, 0);
	}

	public void setPlaybackTimer(int time) {
		if (time == 0) {
			playbackTimer = null;
		} else {
			int delay = time * 1000;
			PlaybackTimer timer = this.playbackTimer = new PlaybackTimer(delay + System.currentTimeMillis());
			handler.postDelayed(timer, delay);
		}
	}

	public interface Listener {
		void onPlaybackStateChanged(MediaSessionCallback cb, PlaybackStateCompat state);
		void onSubtitleStreamChanged(MediaSessionCallback cb, @Nullable SubtitleStreamInfo info);
	}

	private final class PlaybackTimer implements Runnable {
		final long time;
		PlaybackTimer(long time) { this.time = time; }
		@Override public void run() { if (playbackTimer == this) onStop(); }
	}

	private static final class Prioritized<T> implements Comparable<Prioritized<T>> {
		final T obj;
		final int priority;

		Prioritized(T obj, int priority) { 
			this.obj = obj; 
			this.priority = priority; 
		}

		@Override
		public int compareTo(Prioritized<T> o) {
			return Integer.compare(priority, o.priority);
		}

		@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
		@Override
		public boolean equals(Object obj) {
			return this.obj == ((Prioritized<?>) obj).obj;
		}

		@Override
		public int hashCode() {
			return obj.hashCode();
		}
	}
}
