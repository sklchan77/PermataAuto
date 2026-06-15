package my.app.permata.media.lib;

import androidx.annotation.Nullable;

import java.util.List;

import my.app.permata.media.lib.MediaLib.BrowsableItem;
import my.app.utils.async.FutureSupplier;
import my.app.utils.vfs.VirtualResource;

import static my.app.utils.async.Completed.completedEmptyList;

/**
 * @author sklchan77
 */
public abstract class ExtBrowsable extends BrowsableItemBase {

	public ExtBrowsable(String id, @Nullable BrowsableItem parent, @Nullable VirtualResource resource) {
		super(id, parent, resource);
	}

	@Override
	protected FutureSupplier<List<MediaLib.Item>> listChildren() {
		return completedEmptyList();
	}

	@Override
	public boolean isExternal() {
		return true;
	}
}
