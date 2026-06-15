package my.app.permata.addon;

import androidx.annotation.Nullable;

import my.app.permata.media.lib.DefaultMediaLib;
import my.app.permata.media.lib.MediaLib.Item;
import my.app.utils.async.FutureSupplier;

/**
 * @author sklchan77
 */
public interface MediaLibAddon extends PermataFragmentAddon {

	boolean isSupportedItem(Item i);

	Item getRootItem(DefaultMediaLib lib);

	@Nullable
	FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme, String id);
}
