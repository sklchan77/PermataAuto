package my.app.permata.media.lib;

import androidx.annotation.NonNull;

import my.app.permata.media.lib.MediaLib.BrowsableItem;
import my.app.utils.vfs.VirtualResource;

/**
 * @author sklchan77
 */
public class ExtPlayable extends PlayableItemBase {

	public ExtPlayable(String id, @NonNull BrowsableItem parent, @NonNull VirtualResource resource) {
		super(id, parent, resource);
	}

	@Override
	public boolean isVideo() {
		return false;
	}

	@Override
	public String getOrigId() {
		return getId();
	}

	@Override
	public boolean isExternal() {
		return true;
	}
}
