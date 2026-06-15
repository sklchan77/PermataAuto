package my.app.permata.media.lib;

import static java.util.Objects.requireNonNull;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.collection.CollectionUtils.mapToArray;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;

import my.app.permata.BuildConfig;
import my.app.permata.R;
import my.app.permata.media.lib.MediaLib.BrowsableItem;
import my.app.permata.media.lib.MediaLib.Item;
import my.app.permata.media.lib.MediaLib.PlayableItem;
import my.app.permata.media.lib.MediaLib.Playlist;
import my.app.permata.media.lib.MediaLib.Playlists;
import my.app.permata.media.pref.BrowsableItemPrefs;
import my.app.permata.media.pref.PlaylistPrefs;
import my.app.utils.async.FutureSupplier;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.SharedPreferenceStore;
import my.app.utils.text.SharedTextBuilder;

/**
 * @author sklchan77
 */
class DefaultPlaylist extends ItemContainer<PlayableItem> implements Playlist, PlaylistPrefs {
	private final int playlistId;
	private final SharedPreferenceStore playlistPrefStore;

	private DefaultPlaylist(String id, BrowsableItem parent, int playlistId) {
		super(id, parent, null);
		this.playlistId = playlistId;
		SharedPreferences prefs = getLib().getContext().getSharedPreferences("playlist_" + playlistId,
				Context.MODE_PRIVATE);
		playlistPrefStore = SharedPreferenceStore.create(prefs, getLib().getPrefs());
	}

	public static DefaultPlaylist create(String id, BrowsableItem parent, int playlistId, DefaultMediaLib lib) {
		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(id);

			if (i != null) {
				DefaultPlaylist pl = (DefaultPlaylist) i;
				if (BuildConfig.D && !parent.equals(pl.getParent())) throw new AssertionError();
				if (BuildConfig.D && !id.equals(pl.getId())) throw new AssertionError();
				return pl;
			} else {
				return new DefaultPlaylist(id, parent, playlistId);
			}
		}
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getName());
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return getUnsortedChildren().main().map(l ->
				getLib().getContext().getResources().getString(R.string.browsable_subtitle, l.size()));
	}

	@NonNull
	@Override
	public String getName() {
		return getPlaylistNamePref();
	}

	public int getPlaylistId() {
		return playlistId;
	}

	@NonNull
	@Override
	public Playlists getParent() {
		return (Playlists) requireNonNull(super.getParent());
	}

	@NonNull
	@Override
	public BrowsableItemPrefs getPrefs() {
		return this;
	}

	@NonNull
	@Override
	public PreferenceStore getPlaylistPreferenceStore() {
		return playlistPrefStore;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return getLib().getBroadcastEventListeners();
	}

	public FutureSupplier<List<Item>> listChildren() {
		return listChildren(getPlaylistPreferenceStore(), PLAYLIST_ITEMS);
	}

	@Override
	protected String getScheme() {
		return getId();
	}

	@Override
	public String toChildItemId(String id) {
		if (isChildItemId(id)) return id;
		SharedTextBuilder tb = SharedTextBuilder.get();
		return tb.append(getScheme()).append(id).releaseString();
	}

	@Override
	protected void saveChildren(List<PlayableItem> children) {
		setPlaylistItemsPref(mapToArray(children, PlayableItem::getOrigId, String[]::new));
	}

	@Override
	protected void itemAdded(PlayableItem i) {
		getLib().getAtvInterface(a -> a.addProgram(i));
	}

	@Override
	protected void itemRemoved(PlayableItem i) {
		super.itemRemoved(i);
		getLib().getAtvInterface(a -> a.removeProgram(i));
	}
}