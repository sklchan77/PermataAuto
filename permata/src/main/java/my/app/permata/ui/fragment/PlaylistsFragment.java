package my.app.permata.ui.fragment;

import java.util.List;

import my.app.permata.R;
import my.app.permata.media.lib.MediaLib;
import my.app.permata.media.lib.MediaLib.BrowsableItem;
import my.app.permata.media.lib.MediaLib.Playlist;
import my.app.permata.media.lib.MediaLib.Playlists;
import my.app.permata.media.pref.PlaylistPrefs;
import my.app.permata.media.pref.PlaylistsPrefs;
import my.app.permata.media.service.PermataServiceUiBinder;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.ui.menu.OverlayMenu;
import my.app.utils.ui.menu.OverlayMenuItem;

/**
 * @author sklchan77
 */
public class PlaylistsFragment extends MediaLibFragment {

	@Override
	protected ListAdapter createAdapter(PermataServiceUiBinder b) {
		return new PlaylistsAdapter(getMainActivity(), b.getLib().getPlaylists());
	}

	@Override
	public int getFragmentId() {
		return R.id.playlists_fragment;
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(R.string.playlists);
	}

	@Override
	public void navBarItemReselected(int itemId) {
		getAdapter().setParent(getLib().getPlaylists());
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
		super.contributeToNavBarMenu(builder);
		PlaylistsAdapter a = getAdapter();

		if (a.getListView().isSelectionActive() && a.hasSelected()) {
			OverlayMenu.Builder b = builder.withSelectionHandler(this::navBarMenuItemSelected);
			b.addItem(R.id.favorites_add, R.drawable.favorite, R.string.favorites_add);
			b.addItem(R.id.playlist_remove_item, R.drawable.playlist_remove, R.string.playlist_remove_item);
		}
	}

	public boolean navBarMenuItemSelected(OverlayMenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.playlist_remove_item) {
			getMainActivity().removeFromPlaylist((Playlist) getAdapter().getParent(), getAdapter().getSelectedItems());
			return true;
		}
		return super.navBarMenuItemSelected(item);
	}

	@Override
	protected boolean isSupportedItem(MediaLib.Item i) {
		return getPlaylists().isPlaylistsItemId(i.getId());
	}

	private Playlists getPlaylists() {
		return getLib().getPlaylists();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		PlaylistsAdapter a = getAdapter();
		if (a.isCallbackCall() || (a.getParent() == null)) return;

		if (prefs.contains(PlaylistsPrefs.PLAYLIST_IDS) && (a.getParent() == getLib().getPlaylists())) {
			a.reload();
		} else if (prefs.contains(PlaylistPrefs.PLAYLIST_ITEMS)) {
			a.reload();
		} else {
			super.onPreferenceChanged(store, prefs);
		}
	}

	private class PlaylistsAdapter extends ListAdapter {

		PlaylistsAdapter(MainActivityDelegate activity, BrowsableItem parent) {
			super(activity, parent);
		}

		@Override
		protected void onItemDismiss(int position) {
			BrowsableItem p = getParent();
			if (p instanceof Playlist) ((Playlist) p).removeItem(position);
			else ((Playlists) p).removeItem(position);
			super.onItemDismiss(position);
		}

		@Override
		protected boolean onItemMove(int fromPosition, int toPosition) {
			BrowsableItem p = getParent();
			if (p instanceof Playlist) ((Playlist) p).moveItem(fromPosition, toPosition);
			else ((Playlists) p).moveItem(fromPosition, toPosition);
			return super.onItemMove(fromPosition, toPosition);
		}
	}
}
