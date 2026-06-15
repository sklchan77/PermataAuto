package my.app.permata.addon.tv.m3u;

import static my.app.permata.util.Utils.dynCtx;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.completedNull;

import android.content.Context;

import my.app.permata.addon.tv.R;
import my.app.permata.addon.tv.TvItem;
import my.app.permata.addon.tv.TvRootItem;
import my.app.permata.media.lib.M3uGroupItem;
import my.app.permata.media.lib.M3uItem;
import my.app.permata.media.lib.MediaLib.Item;
import my.app.utils.async.FutureSupplier;
import my.app.utils.text.SharedTextBuilder;

/**
 * @author sklchan77
 */
public class TvM3uGroupItem extends M3uGroupItem implements TvItem {
	public static final String SCHEME = "tvm3ug";

	protected TvM3uGroupItem(String id, M3uItem parent, String name, int groupId) {
		super(id, parent, name, groupId);
	}

	public static FutureSupplier<TvM3uGroupItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		int gstart = id.indexOf(':') + 1;
		int gend = id.indexOf(':', gstart);
		int gid = Integer.parseInt(id.substring(gstart, gend));
		int nstart = id.indexOf(':', gend + 1);
		SharedTextBuilder tb = SharedTextBuilder.get().append(TvM3uItem.SCHEME);
		String name;

		if (nstart > 0) {
			name = id.substring(nstart + 1);
			tb.append(id, gend, nstart);
		} else {
			name = null;
			tb.append(id, gend, id.length());
		}

		FutureSupplier<? extends Item> f = root.getItem(TvM3uItem.SCHEME, tb.releaseString());
		return (f == null) ? completedNull() : f.then(i -> {
			TvM3uItem m3u = (TvM3uItem) i;
			return (m3u != null) ? m3u.getGroup(gid, name) : completedNull();
		});
	}

	@Override
	public int getIcon() {
		return my.app.permata.R.drawable.tv;
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		Context ctx = dynCtx(getLib().getContext());
		String t = ctx.getResources().getString(R.string.sub_ch, tracks.size());
		return completed(t);
	}
}
