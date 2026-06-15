package my.app.permata.addon;

import my.app.permata.media.service.PermataMediaService;
import my.app.permata.media.service.MediaSessionCallback;

/**
 * @author sklchan77
 */
public interface PermataMediaServiceAddon extends PermataAddon {

	default void onServiceCreate(MediaSessionCallback cb) {
	}

	default void onServiceDestroy(MediaSessionCallback cb) {
	}
}
