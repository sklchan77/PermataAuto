package my.app.permata.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.utils.ui.fragment.ActivityFragment;

/**
 * @author sklchan77
 */
public class SwipeRefresh extends SwipeRefreshLayout implements
		SwipeRefreshLayout.OnRefreshListener, SwipeRefreshLayout.OnChildScrollUpCallback {

	public SwipeRefresh(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		setOnRefreshListener(this);
		setOnChildScrollUpCallback(this);
	}

	@Override
	public void onRefresh() {
		ActivityFragment f = getActivity().getActiveFragment();
		if (f != null) f.onRefresh(this::setRefreshing);
	}

	@Override
	public boolean canChildScrollUp(@NonNull SwipeRefreshLayout parent, @Nullable View child) {
		MainActivityDelegate a = getActivity();
		if (a.isMenuActive()) return true;
		ActivityFragment f = a.getActiveFragment();
		return (f != null) && f.canScrollUp();
	}

	private MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}
}
