package my.app.permata.media.service;

import static my.app.permata.media.service.PermataMediaService.ACTION_MEDIA_SERVICE;
import static my.app.permata.media.service.PermataMediaService.DEFAULT_NOTIF_COLOR;
import static my.app.permata.media.service.PermataMediaService.INTENT_ATTR_NOTIF_COLOR;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.IBinder;

import androidx.annotation.Nullable;

import my.app.permata.PermataApplication;
import my.app.utils.async.FutureSupplier;
import my.app.utils.async.Promise;
import my.app.utils.log.Log;
import my.app.utils.ui.activity.AppActivity;

/**
 * @author sklchan77
 */
public class PermataMediaServiceConnection implements ServiceConnection {
	private Promise<PermataMediaServiceConnection> promise;
	private PermataMediaService.ServiceBinder binder;

	public static FutureSupplier<PermataMediaServiceConnection> connect(@Nullable AppActivity a) {
		int notifColor = Color.parseColor(DEFAULT_NOTIF_COLOR);

		if (a != null) {
			TypedArray typedArray = a.getTheme().obtainStyledAttributes(new int[]{android.R.attr.statusBarColor});
			notifColor = typedArray.getColor(0, notifColor);
			typedArray.recycle();
		}

		Context ctx = PermataApplication.get();
		PermataMediaServiceConnection con = new PermataMediaServiceConnection();
		Promise<PermataMediaServiceConnection> p = con.promise = new Promise<>();
		Intent i = new Intent(ctx, PermataMediaService.class);
		i.setAction(ACTION_MEDIA_SERVICE);
		i.putExtra(INTENT_ATTR_NOTIF_COLOR, notifColor);
		Log.d("Binding service to context ", ctx);

		if (!ctx.bindService(i, con, Context.BIND_AUTO_CREATE)) {
			Exception ex = new IllegalStateException("Failed to bind to PermataMediaService");
			Log.e(ex, "Service connection failed");
			p.completeExceptionally(ex);
		}

		return p;
	}

	public PermataServiceUiBinder createBinder() {
		return new PermataServiceUiBinder(this);
	}

	public MediaSessionCallback getMediaSessionCallback() {
		PermataMediaService.ServiceBinder b = binder;
		return (b == null) ? null : b.getMediaSessionCallback();
	}

	public boolean isConnected() {
		PermataMediaService.ServiceBinder b = binder;
		return (b != null) && b.isBinderAlive();
	}

	public void disconnect() {
		if (!isConnected()) return;
		Log.d("Unbinding service from context ", PermataApplication.get());
		PermataApplication.get().unbindService(this);
		disconnected();
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		Promise<PermataMediaServiceConnection> p = promise;
		promise = null;
		binder = (PermataMediaService.ServiceBinder) service;
		p.complete(this);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		Log.d("Service disconnected");
		disconnected();
	}

	private void disconnected() {
		PermataMediaService.ServiceBinder b = binder;
		if (b == null) return;
		Promise<PermataMediaServiceConnection> p = promise;
		binder = null;
		promise = null;
		if (p != null)
			p.completeExceptionally(new IllegalStateException("PermataMediaService disconnected"));
	}
}
