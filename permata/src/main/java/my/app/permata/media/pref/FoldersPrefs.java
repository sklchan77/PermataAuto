package my.app.permata.media.pref;

import androidx.annotation.NonNull;

import my.app.utils.function.Supplier;

import my.app.utils.pref.PreferenceStore;

/**
 * @author sklchan77
 */
public interface FoldersPrefs extends BrowsableItemPrefs {
	Pref<Supplier<String[]>> FOLDERS = Pref.sa("FOLDERS", new String[0]);

	@NonNull
	PreferenceStore getFoldersPreferenceStore();

	default String[] getFoldersPref() {
		return getFoldersPreferenceStore().getStringArrayPref(FOLDERS);
	}

	default void setFoldersPref(String[] folders) {
		getFoldersPreferenceStore().applyStringArrayPref(FOLDERS, folders);
	}
}
