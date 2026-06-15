package my.app.utils.ui.view;

import java.util.List;

import my.app.utils.pref.PreferenceStore;
import my.app.utils.pref.PreferenceStore.Pref;
import my.app.utils.ui.fragment.ActivityFragment;

/**
 * @author sklchan77
 */
public abstract class PrefNavBarMediator extends CustomizableNavBarMediator
		implements PreferenceStore.Listener {

	protected abstract PreferenceStore getPreferenceStore(NavBarView nb);

	protected abstract Pref<?> getPref(NavBarView nb);

	@Override
	public void enable(NavBarView nb, ActivityFragment f) {
		super.enable(nb, f);
		getPreferenceStore(nb).addBroadcastListener(this);
	}

	@Override
	public void disable(NavBarView nb) {
		super.disable(nb);
		getPreferenceStore(nb).removeBroadcastListener(this);
	}

	protected void reload(NavBarView nb) {
		super.disable(nb);
		super.enable(nb, nb.getActivity().getActiveFragment());
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		NavBarView nb = navBar;
		if ((nb != null) && prefs.contains(getPref(nb))) reload(nb);
	}
}
