package my.app.permata.addon;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import my.app.permata.BuildConfig;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.misc.ChangeableCondition;
import my.app.utils.pref.PreferenceSet;
import my.app.utils.pref.PreferenceStore;

/**
 * @author sklchan77
 */
public interface PermataAddon {

	@IdRes
	int getAddonId();

	@NonNull
	AddonInfo getInfo();

	default void contributeSettings(Context ctx, PreferenceStore store, PreferenceSet set,
																	ChangeableCondition visibility) {
	}

	default void install() {
	}

	default void uninstall() {
		stop();
	}

	default void start() {
	}

	default void stop() {
	}

	default boolean handleIntent(MainActivityDelegate a, Intent intent) {
		return false;
	}

	@NonNull
	static AddonInfo findAddonInfo(String name) {
		boolean cn = name.indexOf('.') > 0;
		for (AddonInfo ai : BuildConfig.ADDONS) {
			if (name.equals(cn ? ai.className : ai.moduleName)) return ai;
		}
		throw new RuntimeException("Addon not found: " + name);
	}
}
