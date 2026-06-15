package my.app.permata.media.lib;

import static my.app.permata.ui.activity.MainActivityDelegate.INTENT_ACTION_UPDATE;
import static my.app.permata.ui.activity.MainActivityDelegate.intentUriToAction;
import static my.app.permata.ui.activity.MainActivityDelegate.intentUriToId;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import my.app.permata.media.service.PermataMediaServiceConnection;
import my.app.utils.log.Log;

/**
 * @author sklchan77
 */
public class AtvUpdateReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context ctx, Intent i) {
		if (!INTENT_ACTION_UPDATE.equals(intentUriToAction(i.getData()))) return;
		Log.d("Intent received: ", intentUriToId(i.getData()));
		PermataMediaServiceConnection.connect(null).onSuccess(b -> {
			b.getMediaSessionCallback().getMediaLib().getAtvInterface(a -> a.update(i));
			b.disconnect();
		});
	}
}
