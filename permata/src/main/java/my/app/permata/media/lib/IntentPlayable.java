package my.app.permata.media.lib;

import static my.app.utils.security.SecurityUtils.md5;

import android.net.Uri;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;

import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.async.FutureSupplier;
import my.app.utils.resource.Rid;
import my.app.utils.vfs.generic.GenericFileSystem;

/**
 * @author sklchan77
 */
public class IntentPlayable extends PlayableItemBase {
	private final boolean video;

	public IntentPlayable(MainActivityDelegate a, Uri u) {
		super("intent://" + md5(u.toString()), new ExtRoot("intent_root", null) {
			@NonNull
			@Override
			public MediaLib getLib() {
				return a.getLib();
			}
		}, GenericFileSystem.getInstance().create(Rid.create(u)));
		String mime = a.getContext().getContentResolver().getType(u);
		video = (mime == null) || mime.startsWith("video/");
	}

	@NonNull
	@Override
	public FutureSupplier<MediaDescriptionCompat> getMediaDescription() {
		return super.getMediaDescription();
	}

	@Override
	public boolean isVideo() {
		return video;
	}

	@Override
	public boolean isExternal() {
		return true;
	}

	@Override
	public boolean isSeekable() {
		return true;
	}

	@Override
	public String getOrigId() {
		return getId();
	}
}
