package my.app.permata.addon.web.yt;

import static my.app.permata.media.pref.MediaPrefs.MEDIA_ENG_YT;
import static my.app.permata.util.Utils.dynCtx;
import static my.app.utils.async.Completed.completed;

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.media.AudioFocusRequestCompat;

import com.google.android.play.core.splitcompat.SplitCompat;

import my.app.permata.BuildConfig;
import my.app.permata.addon.web.R;
import my.app.permata.addon.web.yt.YoutubeAddon.VideoScale;
import my.app.permata.media.engine.MediaEngine;
import my.app.permata.media.lib.ExtPlayable;
import my.app.permata.media.lib.ExtRoot;
import my.app.permata.media.lib.MediaLib;
import my.app.permata.media.lib.MediaLib.BrowsableItem;
import my.app.permata.media.lib.MediaLib.PlayableItem;
import my.app.permata.media.service.MediaSessionCallback;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.view.VideoView;
import my.app.utils.async.FutureSupplier;
import my.app.utils.log.Log;
import my.app.utils.text.SharedTextBuilder;
import my.app.utils.ui.UiUtils;
import my.app.utils.ui.menu.OverlayMenu;
import my.app.utils.ui.menu.OverlayMenuItem;
import my.app.utils.vfs.VirtualResource;
import my.app.utils.vfs.generic.GenericFileSystem;

/**
 * @author sklchan77
 */
class YoutubeMediaEngine implements MediaEngine, OverlayMenu.SelectionHandler {
	private static final int VIDEO_QUALITY_MASK = 1 << 31;
	private static final String ID = "youtube";
	private static final String CURRENT_ID = ID + ":current";
	private static final String NEXT_ID = ID + ":next";
	private static final String PREV_ID = ID + ":prev";
	private static final String END_ID = ID + ":end";
	private final YoutubeWebView web;
	private final MediaSessionCallback cb;
	private final ExtRoot mediaRoot;
	private final YoutubeItem next;
	private final YoutubeItem prev;
	private final YoutubeItem end;
	private YoutubeItem current;
	private String qualityUrl;
	private boolean ignorePause;

	public YoutubeMediaEngine(YoutubeWebView web, MainActivityDelegate a) {
		this.web = web;
		cb = a.getMediaSessionCallback();
		mediaRoot = new ExtRoot("youtube", a.getLib());
		next = new YoutubeItem(NEXT_ID, mediaRoot, GenericFileSystem.getInstance().create("http://youtube.com/next"));
		prev = new YoutubeItem(PREV_ID, mediaRoot, GenericFileSystem.getInstance().create("http://youtube.com/prev"));
		end = new YoutubeItem(END_ID, mediaRoot, GenericFileSystem.getInstance().create("http://youtube.com/end")) {
			@NonNull
			@Override
			public FutureSupplier<PlayableItem> getNextPlayable() {
				return completed(next);
			}
		};
	}

	void playing(String url) {
		if (BuildConfig.AUTO && web.getAddon().skipAd()) {
			web.loadUrl("javascript:\n" +
					"if (document.querySelectorAll('.ad-showing').length > 0) {\n" +
					"  var video = document.querySelector('video');\n" +
					"  if (video != null) video.currentTime = video.duration;\n" +
					"}");
		}

		if (url.startsWith("blob:")) url = url.substring(5);
		current = new Current(url);
		if (!web.getAddon().autoHighestQuality()) {
			qualityUrl = null;
		} else if (!url.isEmpty() && !url.equals(qualityUrl)) {
			qualityUrl = url;
			web.setHighestVideoQuality();
		}
		cb.setEngine(this);
		cb.onEngineStarted(this);
	}

	void ended() {
		current = end;
		qualityUrl = null;
		cb.onEngineEnded(this);
	}

	void paused() {
		ignorePause = true;
		cb.onPause();
		ignorePause = false;
	}

	@Override
	public int getId() {
		return MEDIA_ENG_YT;
	}

	@Override
	public void prepare(PlayableItem source) {
		if (source == next) {
			web.next();
		} else if (source == prev) {
			web.prev();
		} else {
			cb.onEnginePrepared(this);
		}
	}

	@Override
	public void start() {
		web.play();
	}

	@Override
	public void stop() {
		if ((current == null) || (current == end)) return;
		current = null;
		qualityUrl = null;
		web.stop();
	}

	@Override
	public void pause() {
		if (!ignorePause) web.pause();
	}

	@Override
	public PlayableItem getSource() {
		return current;
	}

	@Override
	public FutureSupplier<Long> getDuration() {
		return web.getDuration();
	}

	@Override
	public FutureSupplier<Long> getPosition() {
		return web.getPosition();
	}

	@Override
	public void setPosition(long position) {
		web.setPosition(position);
	}

	@Override
	public FutureSupplier<Float> getSpeed() {
		return web.getSpeed();
	}

	@Override
	public void setSpeed(float speed) {
		web.setSpeed(speed);
	}

	@Override
	public void setVideoView(VideoView view) {
	}

	@Override
	public float getVideoWidth() {
		return 0;
	}

	@Override
	public float getVideoHeight() {
		return 0;
	}

	@Override
	public void close() {
	}

	@Override
	public boolean requestAudioFocus(@Nullable AudioManager audioManager, @Nullable AudioFocusRequestCompat audioFocusReq) {
		return true;
	}

	@Override
	public void releaseAudioFocus(@Nullable AudioManager audioManager, @Nullable AudioFocusRequestCompat audioFocusReq) {
	}

	@Override
	public boolean hasVideoMenu() {
		return true;
	}

	@Override
	public void contributeToMenu(OverlayMenu.Builder b) {
		Context ctx = dynCtx(web.getContext());
		Resources r = ctx.getResources();
		SplitCompat.install(ctx);
		b.addItem(R.id.video_quality,
				ResourcesCompat.getDrawable(r, R.drawable.video_quality, ctx.getTheme()),
				r.getString(R.string.video_quality)).setFutureSubmenu(this::videoQualityMenu);
		b.addItem(my.app.permata.R.id.video_scaling,
				ResourcesCompat.getDrawable(r, R.drawable.video_scaling, ctx.getTheme()),
				r.getString(my.app.permata.R.string.video_scaling)).setSubmenu(this::videoScalingMenu);
	}

	private FutureSupplier<Void> videoQualityMenu(OverlayMenu.Builder b) {
		b.setSelectionHandler(this);
		return web.getVideoQualities().timeout(1100).main()
				.onFailure(err -> Log.e(err, "Failed to load video qualities"))
				.map(qualities -> {
					if ((qualities == null) || (qualities.isEmpty())) {
						b.addItem(my.app.permata.R.id.auto, null, my.app.permata.R.string.auto)
								.setChecked(true, true);
						return null;
					}

					String[] all = qualities.split(";");
					for (int i = 0; i < all.length; i++) {
						String q = all[i];
						if (q.startsWith("*")) q = q.substring(1);
						//noinspection StringEquality
						b.addItem(UiUtils.getArrayItemId(i), null, q).setChecked(q != all[i], true)
								.setData(i | VIDEO_QUALITY_MASK);
					}
					return null;
				});
	}

	private void videoScalingMenu(OverlayMenu.Builder b) {
		VideoScale scale = web.getAddon().getScale();
		b.addItem(my.app.permata.R.id.video_scaling_best, null, my.app.permata.R.string.video_scaling_best)
				.setChecked(scale == VideoScale.CONTAIN, true);
		b.addItem(my.app.permata.R.id.video_scaling_fill, null, my.app.permata.R.string.video_scaling_fill)
				.setChecked(scale == VideoScale.FILL, true);
		b.addItem(R.id.video_scaling_fill_proportional, null, R.string.video_scaling_fill_proportional)
				.setChecked(scale == VideoScale.COVER, true);
		b.addItem(my.app.permata.R.id.video_scaling_orig, null, my.app.permata.R.string.video_scaling_orig)
				.setChecked(scale == VideoScale.NONE, true);
		b.setSelectionHandler(this);
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem item) {
		int itemId = item.getItemId();
		if (itemId == my.app.permata.R.id.video_scaling_best) {
			web.setScale(VideoScale.CONTAIN);
			return true;
		} else if (itemId == my.app.permata.R.id.video_scaling_fill) {
			web.setScale(VideoScale.FILL);
			return true;
		} else if (itemId == R.id.video_scaling_fill_proportional) {
			web.setScale(VideoScale.COVER);
			return true;
		} else if (itemId == my.app.permata.R.id.video_scaling_orig) {
			web.setScale(VideoScale.NONE);
			return true;
		} else if (item.getData() instanceof Integer) {
			int d = item.getData();
			if ((d & VIDEO_QUALITY_MASK) != 0) web.setVideoQuality(d & ~VIDEO_QUALITY_MASK);
		}
		return false;
	}

	@Override
	public boolean isSplitModeSupported() {
		return false;
	}

	static boolean isYoutubeItem(MediaLib.Item i) {
		return (i instanceof YoutubeItem);
	}

	private static class YoutubeItem extends ExtPlayable {
		public YoutubeItem(String id, @NonNull BrowsableItem parent, @NonNull VirtualResource resource) {
			super(id, parent, resource);
		}

		@Override
		public boolean isSeekable() {
			return true;
		}

		@Override
		public boolean isVideo() {
			return true;
		}

		@Override
		public int getVideoEnginePref() {
			return MEDIA_ENG_YT;
		}

		@Override
		public boolean equals(@Nullable Object obj) {
			return obj == this;
		}

		@Override
		protected String buildSubtitle(MediaMetadataCompat md, SharedTextBuilder tb) {
			return null;
		}
	}

	private final class Current extends YoutubeItem {

		public Current(String url) {
			super(CURRENT_ID, mediaRoot, GenericFileSystem.getInstance().create(url));
		}

		@NonNull
		@Override
		protected FutureSupplier<MediaMetadataCompat> loadMeta() {
			FutureSupplier<String> getTitle = web.getVideoTitle();
			return web.getDuration().then(dur -> getTitle.map(title -> {
				MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder();
				b.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
				b.putLong(MediaMetadata.METADATA_KEY_DURATION, dur);
				return b.build();
			}));
		}

		@NonNull
		@Override
		public FutureSupplier<PlayableItem> getPrevPlayable() {
			return completed(prev);
		}

		@NonNull
		@Override
		public FutureSupplier<PlayableItem> getNextPlayable() {
			return completed(next);
		}
	}
}
