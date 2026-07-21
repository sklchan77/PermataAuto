package my.app.permata.addon.web;

import android.content.Context;
import android.webkit.JavascriptInterface;

import androidx.annotation.Keep;

import my.app.permata.media.service.PermataMediaService;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.log.Log;

/**
 * Universal JavaScript Bridge that captures HTML5 Video/Audio events
 * across ALL media websites (Douyin, TikTok, Instagram, Facebook, Bilibili, etc.)
 * and anchors them to Android Auto's AudioFocus & MediaSession pipeline.
 * 
 * @author sklchan77
 */
@Keep
public class UniversalMediaBridge {
	private final Context context;

	public UniversalMediaBridge(Context context) {
		this.context = context.getApplicationContext();
	}

	@JavascriptInterface
	public void onMediaPlay() {
		Log.i("UniversalMediaBridge: Universal HTML5 Media PLAY detected.");
		MainActivityDelegate.getActivityDelegate(context).onSuccess(delegate -> {
			if (delegate.getMediaSessionCallback() != null && 
					delegate.getMediaSessionCallback().getService() != null) {
				delegate.getMediaSessionCallback().getService().onWebMediaPlaying();
			} else {
				PermataMediaService.requestFocusAndAnchor(context);
			}
		});
	}

	@JavascriptInterface
	public void onMediaPause() {
		Log.i("UniversalMediaBridge: Universal HTML5 Media PAUSE detected.");
		MainActivityDelegate.getActivityDelegate(context).onSuccess(delegate -> {
			if (delegate.getMediaSessionCallback() != null && 
					delegate.getMediaSessionCallback().getService() != null) {
				delegate.getMediaSessionCallback().getService().onWebMediaPaused();
			}
		});
	}
}