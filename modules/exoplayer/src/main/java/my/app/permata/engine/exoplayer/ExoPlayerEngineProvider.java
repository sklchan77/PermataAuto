package my.app.permata.engine.exoplayer;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import my.app.permata.media.engine.MediaEngine;
import my.app.permata.media.engine.MediaEngine.Listener;
import my.app.permata.media.engine.MediaEngineProvider;

/**
 * @author sklchan77
 */
@SuppressWarnings("unused")
public class ExoPlayerEngineProvider implements MediaEngineProvider {
	private Context ctx;

	@Override
	public void init(Context ctx) {
		this.ctx = ctx;
	}

	@OptIn(markerClass = UnstableApi.class)
	@Override
	public MediaEngine createEngine(Listener listener) {
		return new ExoPlayerEngine(ctx, listener);
	}
}
