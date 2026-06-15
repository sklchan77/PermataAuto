package my.app.permata.auto;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarActivityService;

import my.app.permata.media.service.PermataMediaServiceConnection;
import my.app.permata.media.service.MediaSessionCallback;
import my.app.utils.log.Log;

/**
 * @author sklchan77
 */
public class CarService extends CarActivityService {

	@Override
	public Class<? extends CarActivity> getCarActivity() {
		return MainCarActivity.class;
	}

	@Override
	public void onCreate() {
		Log.d("Creating CarService: " + this);
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		Log.d("Destroying CarService: " + this);
		PermataMediaServiceConnection s = MainCarActivity.service;
		if (s == null) return;
		MainCarActivity.service = null;
		MediaSessionCallback cb = s.getMediaSessionCallback();
		if ((cb != null) && cb.isPlaying()) cb.onPause();
		s.disconnect();
		super.onDestroy();
	}
}
