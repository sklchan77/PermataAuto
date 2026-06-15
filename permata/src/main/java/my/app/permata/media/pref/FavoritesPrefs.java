package my.app.permata.media.pref;

import androidx.annotation.NonNull;

import my.app.utils.function.Supplier;

import my.app.utils.pref.PreferenceStore;

/**
 * @author sklchan77
 */
public interface FavoritesPrefs extends BrowsableItemPrefs {
	Pref<Supplier<String[]>> FAVORITES = Pref.sa("FAVORITES", new String[0]);

	@NonNull
	PreferenceStore getFavoritesPreferenceStore();

	default String[] getFavoritesPref() {
		return getFavoritesPreferenceStore().getStringArrayPref(FAVORITES);
	}

	default void setFavoritesPref(String[] favorites) {
		getFavoritesPreferenceStore().applyStringArrayPref(FAVORITES, favorites);
	}
}
