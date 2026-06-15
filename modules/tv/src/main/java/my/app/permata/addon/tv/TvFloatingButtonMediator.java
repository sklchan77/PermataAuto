package my.app.permata.addon.tv;

import android.view.View;

import androidx.annotation.Nullable;

import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.fragment.FloatingButtonMediator;
import my.app.utils.ui.fragment.ActivityFragment;
import my.app.utils.ui.view.FloatingButton;

/**
 * @author sklchan77
 */
class TvFloatingButtonMediator extends FloatingButtonMediator {
	static final TvFloatingButtonMediator instance = new TvFloatingButtonMediator();

	@Override
	public int getIcon(FloatingButton fb) {
		MainActivityDelegate a = MainActivityDelegate.get(fb.getContext());
		return (a.isVideoMode() || !a.isRootPage()) ? getBackIcon() : R.drawable.tv_add;
	}

	@Override
	public void onClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());

		if (a.isVideoMode() || !a.isRootPage()) {
			a.onBackPressed();
		} else {
			ActivityFragment f = a.getActiveFragment();
			if (f instanceof TvFragment) ((TvFragment) f).addSource();
		}
	}
}
