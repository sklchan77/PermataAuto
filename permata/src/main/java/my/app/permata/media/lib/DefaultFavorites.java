package my.app.permata.media.lib;

import static my.app.utils.async.Completed.completed;
import static my.app.utils.collection.CollectionUtils.mapToArray;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;

import my.app.permata.R;
import my.app.permata.media.lib.MediaLib.BrowsableItem;
import my.app.permata.media.lib.MediaLib.Favorites;
import my.app.permata.media.lib.MediaLib.Item;
import my.app.permata.media.lib.MediaLib.PlayableItem;
import my.app.permata.media.pref.FavoritesPrefs;
import my.app.utils.async.FutureSupplier;
import my.app.utils.collection.CollectionUtils;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.SharedPreferenceStore;


/**
 * @author sklchan77
 */
class DefaultFavorites extends ItemContainer<PlayableItem> implements Favorites, FavoritesPrefs {
	public static final String ID = "Favorites";
	public static final String SCHEME = "favorite";
	private final DefaultMediaLib lib;
	private final SharedPreferenceStore favoritesPrefStore;

	public DefaultFavorites(DefaultMediaLib lib) {
		super(ID, null, null);
		this.lib = lib;
		SharedPreferences prefs = lib.getContext().getSharedPreferences("favorites", Context.MODE_PRIVATE);
		favoritesPrefStore = SharedPreferenceStore.create(prefs, getLib().getPrefs());
	}

	@NonNull
	@Override
	public String getName() {
		return getLib().getContext().getString(R.string.favorites);
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getName());
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return lib;
	}

	@Override
	public BrowsableItem getParent() {
		return null;
	}

	@NonNull
	@Override
	public PreferenceStore getParentPreferenceStore() {
		return getLib();
	}

	@NonNull
	@Override
	public BrowsableItem getRoot() {
		return this;
	}

	@NonNull
	@Override
	public PreferenceStore getFavoritesPreferenceStore() {
		return favoritesPrefStore;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return getLib().getBroadcastEventListeners();
	}

	@Override
	public FutureSupplier<List<Item>> listChildren() {
		return listChildren(getFavoritesPreferenceStore(), FAVORITES);
	}

	@Override
	public boolean isFavoriteItem(PlayableItem i) {
		String id = toChildItemId(i.getOrigId());
		List<Item> list = getUnsortedChildren().peek();
		return (list != null) && CollectionUtils.contains(list, c -> id.equals(c.getId()));
	}

	@Override
	public boolean isFavoriteItemId(String id) {
		return isChildItemId(id);
	}

	@Override
	protected String getScheme() {
		return SCHEME;
	}

	@Override
	protected void saveChildren(List<PlayableItem> children) {
		setFavoritesPref(mapToArray(children, PlayableItem::getOrigId, String[]::new));
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
