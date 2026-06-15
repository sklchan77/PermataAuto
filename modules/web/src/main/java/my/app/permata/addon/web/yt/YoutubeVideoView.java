package my.app.permata.addon.web.yt;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import my.app.permata.ui.view.VideoInfoView;
import my.app.permata.ui.view.VideoView;

/**
 * @author sklchan77
 */
public class YoutubeVideoView extends VideoView {

	public YoutubeVideoView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void init(Context context) {
		addView(new FrameLayout(context), new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		addInfoView(context);
	}

	@NonNull
	@Override
	public VideoInfoView getVideoInfoView() {
		return (VideoInfoView) getChildAt(1);
	}

	@Nullable
	@Override
	public SurfaceView getSubtitleSurface() {
		return null;
	}
}
