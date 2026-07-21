package my.app.permata.addon.web;

import android.content.Context;
import android.content.Intent;
import android.webkit.JavascriptInterface;

import androidx.annotation.Keep;

import my.app.permata.media.service.PermataMediaService;
import my.app.utils.log.Log;

/**
 * Universal JavaScript Bridge that captures HTML5 Video/Audio events
 * across ALL media websites and anchors them to Android Auto's pipeline via Intents.
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
		try {
			Intent playIntent = new Intent(context, PermataMediaService.class);
			playIntent.setAction(PermataMediaService.ACTION_WEB_MEDIA_PLAYING);
			context.startService(playIntent);
		} catch (Exception e) {
			Log.e(e, "UniversalMediaBridge: Failed to dispatch PLAY intent.");
		}
	}

	@JavascriptInterface
	public void onMediaPause() {
		Log.i("UniversalMediaBridge: Universal HTML5 Media PAUSE detected.");
		try {
			Intent pauseIntent = new Intent(context, PermataMediaService.class);
			pauseIntent.setAction(PermataMediaService.ACTION_WEB_MEDIA_PAUSED);
			context.startService(pauseIntent);
		} catch (Exception e) {
			Log.e(e, "UniversalMediaBridge: Failed to dispatch PAUSE intent.");
		}
	}
}