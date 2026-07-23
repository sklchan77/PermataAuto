package my.app.permata.auto;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import my.app.permata.PermataApplication;
import my.app.permata.media.service.PermataMediaServiceConnection;
import my.app.utils.log.Log;

public class MediaServiceStarter extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i("Received intent: ", intent);
		String action = intent.getAction();
		
		if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
			Log.i("Connected to bluetooth");
			PermataMediaServiceConnection.connect(null).onSuccess(c -> {
				Log.i("Media service started. Disconnecting wake-up binding to prevent zombie leak.");
				c.disconnect();
			}).onFailure(Log::e);
		} else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
			// === GUARD CHECK: DO NOT KILL PROJECTION IF ANDROID AUTO IS ACTIVE ===
			if (PermataApplication.get().isConnectedToAuto()) {
				Log.i("Bluetooth ACL disconnect event detected, but Android Auto is actively running. Ignoring teardown.");
				return;
			}

			Log.i("Disconnected from bluetooth and Auto inactive. Tearing down zombie mirror and projection.");
			MirrorDisplay.close();
			ProjectionService.stop();
			if (MirrorServiceFS.sc != null) {
				MirrorServiceFS.sc = null;
			}
			
			// Kill the root thread pool zombie
			Su.get().onSuccess(su -> su.shutdown());
		}
	}
}