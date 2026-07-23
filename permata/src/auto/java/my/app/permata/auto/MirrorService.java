package my.app.permata.auto;

import android.content.Intent;
import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarActivityService;
import my.app.utils.log.Log;

/**
 * @author sklchan77
 */
public class MirrorService extends CarActivityService {
	private MirrorDisplay md;

	@Override
	public void onCreate() {
		super.onCreate();
		md = MirrorDisplay.get();
	}

	@Override
	public void onDestroy() {
		if (md != null) {
			md.release();
			md = null;
		}
		super.onDestroy();
	}

	// NEW SWIPE-AWAY KILL SWITCH
	@Override
	public void onTaskRemoved(Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
		Log.i("App swiped away from Recents. Killing MirrorService.");
		
		if (md != null) {
			md.release();
			md = null;
		}
		
		stopSelf();
	}

	@Override
	public Class<? extends CarActivity> getCarActivity() {
		return MirrorActivity.class;
	}
}