package my.app.permata.media.pref;

/**
 * @author sklchan77
 */
public interface StreamItemPrefs extends PlayableItemPrefs, BrowsableItemPrefs {

	@Override
	default int getSortByPref() {
		return SORT_BY_NONE;
	}
}
