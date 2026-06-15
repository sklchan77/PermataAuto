package my.app.utils.app;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.google.android.play.core.splitcompat.SplitCompat;

/**
 * @author sklchan77
 */
public class NetSplitCompatApp extends NetApp {

	@CallSuper
	@Override
	protected void attachBaseContext(Context context) {
		super.attachBaseContext(context);
		SplitCompat.install(this);
	}

	@CallSuper
	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		SplitCompat.install(this);
		super.onConfigurationChanged(newConfig);
	}
}
