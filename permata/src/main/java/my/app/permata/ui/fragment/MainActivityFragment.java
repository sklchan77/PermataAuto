package my.app.permata.ui.fragment;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.google.android.play.core.splitcompat.SplitCompat;

import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.activity.VoiceCommand;
import my.app.utils.ui.fragment.ActivityFragment;
import my.app.utils.ui.menu.OverlayMenu;
import my.app.utils.ui.view.FloatingButton;

/**
 * @author sklchan77
 */
public abstract class MainActivityFragment extends ActivityFragment {

	@Override
	public void onAttach(@NonNull Context context) {
		super.onAttach(context);
		SplitCompat.install(context);
	}

	@Override
	public MainActivityDelegate getActivityDelegate() {
		return (MainActivityDelegate) super.getActivityDelegate();
	}

	@Override
	public NavBarMediator getNavBarMediator() {
		return getActivityDelegate().getNavBarMediator();
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return FloatingButtonMediator.instance;
	}

	@CallSuper
	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) discardSelection();
	}

	@Override
	public boolean onBackPressed() {
		discardSelection();
		return super.onBackPressed();
	}

	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
	}

	public void discardSelection() {
	}

	public boolean isVideoModeSupported() {
		return false;
	}

	public boolean isVoiceCommandsSupported() {
		return false;
	}

	public boolean startVoiceAssistant() {
		return false;
	}

	public void voiceCommand(VoiceCommand cmd) {
	}
}
