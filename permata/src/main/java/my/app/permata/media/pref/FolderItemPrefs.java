package my.app.permata.media.pref;

import my.app.utils.function.IntSupplier;

/**
 * @author sklchan77
 */
public interface FolderItemPrefs extends BrowsableItemPrefs {
	Pref<IntSupplier> FOLDER_SORT_BY = SORT_BY.withDefaultValue(() -> SORT_BY_FILE_NAME);

	@Override
	default Pref<IntSupplier> getSortByPrefKey() {
		return FOLDER_SORT_BY;
	}
}
