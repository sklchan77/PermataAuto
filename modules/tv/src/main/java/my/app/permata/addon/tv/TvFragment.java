package my.app.permata.addon.tv;

import static java.util.Objects.requireNonNull;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.function.ResultConsumer.Cancel.isCancellation;

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.concurrent.CancellationException;

import my.app.permata.addon.AddonManager;
import my.app.permata.addon.tv.m3u.TvM3uFile;
import my.app.permata.addon.tv.m3u.TvM3uFileSystem;
import my.app.permata.addon.tv.m3u.TvM3uFileSystemProvider;
import my.app.permata.addon.tv.m3u.TvM3uItem;
import my.app.permata.media.lib.DefaultMediaLib;
import my.app.permata.media.lib.MediaLib;
import my.app.permata.media.lib.MediaLib.BrowsableItem;
import my.app.permata.media.lib.MediaLib.Item;
import my.app.permata.media.service.PermataServiceUiBinder;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.ui.fragment.MediaLibFragment;
import my.app.permata.ui.view.MediaItemMenuHandler;
import my.app.utils.app.App;
import my.app.utils.async.FutureSupplier;
import my.app.utils.log.Log;
import my.app.utils.ui.UiUtils;
import my.app.utils.ui.fragment.ActivityFragment;
import my.app.utils.ui.menu.OverlayMenu;
import my.app.utils.ui.menu.OverlayMenuItem;
import my.app.utils.ui.view.FloatingButton;

/**
 * @author sklchan77
 */
public class TvFragment extends MediaLibFragment {

	@Override
	protected ListAdapter createAdapter(PermataServiceUiBinder b) {
		return new TvAdapter(getMainActivity(), getRootItem());
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(my.app.permata.R.string.addon_name_tv);
	}

	@Override
	public int getFragmentId() {
		return my.app.permata.R.id.tv_fragment;
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return TvFloatingButtonMediator.instance;
	}

	public void navBarItemReselected(int itemId) {
		getAdapter().setParent(getRootItem());
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) return;

		TvAdapter a = getAdapter();
		if (a != null) a.animateAddButton(a.getParent());
	}

	@Override
	public void switchingTo(@NonNull ActivityFragment newFragment) {
		super.switchingTo(newFragment);
		getMainActivity().getFloatingButton().clearAnimation();
	}

	public void addSource() {
		TvM3uFileSystemProvider prov = new TvM3uFileSystemProvider();
		prov.select(getMainActivity(), Collections.singletonList(TvM3uFileSystem.getInstance())).main()
				.onFailure(this::failedToAddSource).onSuccess(this::addM3uSource);
	}

	public TvRootItem getRootItem() {
		return requireNonNull(AddonManager.get().getAddon(TvAddon.class)).getRootItem(
				(DefaultMediaLib) getMainActivity().getLib());
	}

	@Override
	public void contributeToContextMenu(OverlayMenu.Builder b, MediaItemMenuHandler h) {
		if (!(h.getItem() instanceof TvM3uItem)) return;
		b.addItem(my.app.permata.R.id.edit, my.app.permata.R.drawable.edit,
						my.app.permata.R.string.edit).setData(h.getItem())
				.setHandler(this::contextMenuItemSelected);
		b.addItem(my.app.permata.R.id.delete, my.app.permata.R.drawable.delete,
						my.app.permata.R.string.delete).setData(h.getItem())
				.setHandler(this::contextMenuItemSelected);
		super.contributeToContextMenu(b, h);
	}

	private boolean contextMenuItemSelected(OverlayMenuItem item) {
		int id = item.getItemId();
		if (id == my.app.permata.R.id.edit) {
			TvM3uItem i = item.getData();
			new TvM3uFileSystemProvider().edit(getMainActivity(), i.getResource())
					.onCompletion((ok, err) -> {
						if ((err != null) && !(err instanceof CancellationException)) {
							Log.e(err, "Failed to edit TV source ", i);
							UiUtils.showAlert(getContext(), err.getLocalizedMessage());
						}
						getMainActivity().showFragment(getFragmentId());
						if ((ok != null) && ok) i.refresh().thenRun(this::refresh);
					});
		} else if (id == my.app.permata.R.id.delete) {
			TvRootItem root = getRootItem();
			root.removeItem(item.getData()).onSuccess(v -> getAdapter().setParent(root));
		}
		return true;
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
		super.contributeToNavBarMenu(builder);
		if (isRootItem()) return;
		TvAdapter a = getAdapter();

		if (a.getListView().isSelectionActive() && a.hasSelectable() && a.hasSelected()) {
			OverlayMenu.Builder b = builder.withSelectionHandler(this::navBarMenuItemSelected);
			b.addItem(my.app.permata.R.id.favorites_add, my.app.permata.R.drawable.favorite,
					my.app.permata.R.string.favorites_add);
			getMainActivity().addPlaylistMenu(b, completed(a.getSelectedItems()));
		}
	}

	@Override
	protected boolean isSupportedItem(Item i) {
		return getRootItem().isChildItemId(i.getId());
	}

	@Override
	protected boolean isRefreshSupported() {
		return true;
	}

	private void addM3uSource(TvM3uFile m3u) {
		MainActivityDelegate a = getMainActivity();
		if (m3u != null) getRootItem().addSource(m3u);
		getAdapter().setParent(getRootItem());
		a.showFragment(getFragmentId());
	}

	private void failedToAddSource(Throwable ex) {
		getMainActivity().showFragment(my.app.permata.R.id.tv_fragment);
		if (isCancellation(ex)) return;

		App.get().getHandler().post(() -> {
			String msg = ex.getLocalizedMessage();
			UiUtils.showAlert(getContext(),
					getString(R.string.err_failed_to_add_tv_source, (msg != null) ? msg : ex.toString()));
		});
	}

	private boolean isRootItem() {
		BrowsableItem p = getAdapter().getParent();
		return (p == null) || (p instanceof TvRootItem);
	}

	private class TvAdapter extends ListAdapter {

		TvAdapter(MainActivityDelegate activity, BrowsableItem parent) {
			super(activity, parent);
			animateAddButton(parent);
		}

		@Override
		public FutureSupplier<?> setParent(BrowsableItem parent, boolean userAction) {
			return super.setParent(parent, userAction).onSuccess(v -> animateAddButton(parent));
		}

		public boolean isLongPressDragEnabled() {
			return isRootItem();
		}

		@Override
		protected void onItemDismiss(int position) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof TvRootItem) ((TvRootItem) i).removeItem(position);
			super.onItemDismiss(position);
		}

		@Override
		protected boolean onItemMove(int fromPosition, int toPosition) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof MediaLib.Folders) ((MediaLib.Folders) i).moveItem(fromPosition, toPosition);
			return super.onItemMove(fromPosition, toPosition);
		}

		private void animateAddButton(BrowsableItem parent) {
			if (!(parent instanceof TvRootItem)) return;

			parent.getUnsortedChildren().onSuccess(c -> {
				if (!c.isEmpty()) return;

				FloatingButton fb = getMainActivity().getFloatingButton();
				fb.requestFocus();
				Animation shake =
						AnimationUtils.loadAnimation(getContext(), my.app.utils.R.anim.shake_y_20);
				fb.startAnimation(shake);
			});
		}
	}
}
