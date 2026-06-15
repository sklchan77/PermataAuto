package my.app.permata.addon.web.yt;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import my.app.permata.addon.web.PermataChromeClient;
import my.app.permata.addon.web.PermataWebView;
import my.app.permata.media.service.MediaSessionCallback;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.view.VideoView;

import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

/**
 * @author sklchan77
 */
public class YoutubeChromeClient extends PermataChromeClient {

	public YoutubeChromeClient(PermataWebView web, VideoView videoView) {
		super(web, videoView);
	}

	@Override
	public VideoView getFullScreenView() {
		return (VideoView) super.getFullScreenView();
	}

	protected void addCustomView(View view) {
		VideoView vv = getFullScreenView();
		((ViewGroup) vv.getChildAt(0)).addView(view, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		vv.setVisibility(VISIBLE);
	}

	protected void removeCustomView(View view) {
		VideoView vv = getFullScreenView();
		((ViewGroup) vv.getChildAt(0)).removeView(view);
		vv.setVisibility(GONE);
	}

	protected void setFullScreen(MainActivityDelegate a, boolean fullScreen) {
		a.setVideoMode(fullScreen, getFullScreenView());
	}

	@Override
	public void onShowCustomView(View view, CustomViewCallback callback) {
		getWebView().setVisibility(GONE);
		super.onShowCustomView(view, callback);
	}

	@Override
	public boolean canEnterFullScreen() {
		MainActivityDelegate a = MainActivityDelegate.get(getWebView().getContext());
		MediaSessionCallback cb = a.getMediaSessionCallback();
		if (!((cb.getEngine() instanceof YoutubeMediaEngine))) return false;
		int st = cb.getPlaybackState().getState();
		return (st == STATE_PLAYING) || (st == STATE_PAUSED);
	}

	protected boolean onTouchEvent(View v, MotionEvent event) {
		return isFullScreen() && getFullScreenView().onTouchEvent(event);
	}
}
