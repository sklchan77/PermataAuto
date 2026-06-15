package my.app.utils.ui.fragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import my.app.utils.function.BooleanConsumer;
import my.app.utils.ui.activity.ActivityDelegate;
import my.app.utils.ui.view.FloatingButton;
import my.app.utils.ui.view.NavBarView;
import my.app.utils.ui.view.ToolBarView;

/**
 * @author sklchan77
 */
public abstract class ActivityFragment extends Fragment {

	public abstract int getFragmentId();

	public CharSequence getTitle() {
		return "";
	}

	public ToolBarView.Mediator getToolBarMediator() {
		return ToolBarView.Mediator.BackTitle.instance;
	}

	public NavBarView.Mediator getNavBarMediator() {
		return NavBarView.Mediator.instance;
	}

	public FloatingButton.Mediator getFloatingButtonMediator() {
		return FloatingButton.Mediator.Back.instance;
	}

	public boolean isRootPage() {
		return true;
	}

	public boolean onBackPressed() {
		return false;
	}

	public void onRefresh(BooleanConsumer refreshing) {
	}

	public boolean canScrollUp() {
		return true;
	}

	public void navBarItemReselected(int itemId) {
	}

	public ActivityDelegate getActivityDelegate() {
		return ActivityDelegate.get(getContext());
	}

	public void switchingFrom(@Nullable ActivityFragment currentFragment) {
	}

	public void switchingTo(@NonNull ActivityFragment newFragment) {
	}

	public void setInput(Object input) {
		throw new UnsupportedOperationException();
	}
}
