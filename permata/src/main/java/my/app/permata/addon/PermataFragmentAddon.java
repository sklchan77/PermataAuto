package my.app.permata.addon;

import androidx.annotation.NonNull;

import my.app.utils.ui.fragment.ActivityFragment;

/**
 * @author sklchan77
 */
public interface PermataFragmentAddon extends PermataAddon {

	@NonNull
	ActivityFragment createFragment();

	default int getFragmentId() {
		return getAddonId();
	}
}
