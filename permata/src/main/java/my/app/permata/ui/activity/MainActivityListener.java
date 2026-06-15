package my.app.permata.ui.activity;

import my.app.utils.ui.activity.ActivityDelegate;
import my.app.utils.ui.activity.ActivityListener;

/**
 * @author sklchan77
 */
public interface MainActivityListener extends ActivityListener {
	byte MODE_CHANGED = (byte) (LAST << 1);

	void onActivityEvent(MainActivityDelegate a, long e);

	@Override
	default void onActivityEvent(ActivityDelegate a, long e) {
		onActivityEvent((MainActivityDelegate) a, e);
	}
}
