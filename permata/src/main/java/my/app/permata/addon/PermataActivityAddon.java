package my.app.permata.addon;

import my.app.permata.ui.activity.MainActivityDelegate;

/**
 * @author sklchan77
 */
public interface PermataActivityAddon extends PermataAddon {

	default void onActivityCreate(MainActivityDelegate a) {
	}

	default void onActivityDestroy(MainActivityDelegate a) {
	}

	default void onActivityResume(MainActivityDelegate a) {
	}

	default void onActivityPause(MainActivityDelegate a) {
	}
}
