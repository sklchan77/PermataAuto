package my.app.permata.auto;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import my.app.permata.media.service.PermataMediaServiceConnection;
import my.app.utils.log.Log;

public class MediaServiceStarter extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i("Received intent: ", intent);
		if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
			Log.i("Connected to bluetooth");
			PermataMediaServiceConnection.connect(null).onSuccess(c -> {
				Log.i("Media service started");
			}).onFailure(Log::e);
		}
	}
}
