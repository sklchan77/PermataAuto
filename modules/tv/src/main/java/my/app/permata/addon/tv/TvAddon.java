package my.app.permata.addon.tv;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import my.app.permata.addon.AddonInfo;
import my.app.permata.addon.PermataAddon;
import my.app.permata.addon.MediaLibAddon;
import my.app.permata.media.lib.DefaultMediaLib;
import my.app.permata.media.lib.MediaLib.Item;
import my.app.utils.async.FutureSupplier;
import my.app.utils.ui.fragment.ActivityFragment;

/**
 * @author sklchan77
 */
@Keep
@SuppressWarnings("unused")
public class TvAddon implements MediaLibAddon {
	@NonNull
	private static final AddonInfo info = PermataAddon.findAddonInfo(TvAddon.class.getName());
	private static TvRootItem root;

	@IdRes
	@Override
	public int getAddonId() {
		return my.app.permata.R.id.tv_fragment;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new TvFragment();
	}

	@Override
	public boolean isSupportedItem(Item i) {
		return (i instanceof TvItem);
	}

	public TvRootItem getRootItem(DefaultMediaLib lib) {
		if ((root == null) || (root.getLib() != lib)) root = new TvRootItem(lib);
		return root;
	}

	@Nullable
	@Override
	public FutureSupplier<? extends Item> getItem(DefaultMediaLib lib, @Nullable String scheme,
																								String id) {
		return getRootItem(lib).getItem(scheme, id);
	}
}
