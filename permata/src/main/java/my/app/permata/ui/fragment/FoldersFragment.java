package my.app.permata.ui.fragment;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.os.Build.VERSION.SDK_INT;
import static my.app.permata.BuildConfig.ENABLE_GS;
import static my.app.permata.util.Utils.isSafSupported;
import static my.app.permata.vfs.PermataVfsManager.GDRIVE_ID;
import static my.app.permata.vfs.PermataVfsManager.M3U_ID;
import static my.app.permata.vfs.PermataVfsManager.SFTP_ID;
import static my.app.permata.vfs.PermataVfsManager.SMB_ID;
import static my.app.utils.async.Completed.completed;
import static my.app.utils.function.ResultConsumer.Cancel.isCancellation;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.play.core.install.InstallException;
import com.google.android.play.core.splitinstall.SplitInstallException;

import java.util.List;

import my.app.permata.R;
import my.app.permata.media.lib.MediaLib.BrowsableItem;
import my.app.permata.media.lib.MediaLib.Folders;
import my.app.permata.media.lib.MediaLib.Item;
import my.app.permata.media.pref.FoldersPrefs;
import my.app.permata.media.service.PermataServiceUiBinder;
import my.app.permata.ui.activity.MainActivityDelegate;
import my.app.permata.vfs.PermataVfsManager;
import my.app.utils.app.App;
import my.app.utils.async.FutureSupplier;
import my.app.utils.log.Log;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.ui.UiUtils;
import my.app.utils.ui.fragment.ActivityFragment;
import my.app.utils.ui.fragment.FilePickerFragment;
import my.app.utils.ui.menu.OverlayMenu;
import my.app.utils.ui.menu.OverlayMenuItem;
import my.app.utils.ui.view.FloatingButton;
import my.app.utils.vfs.VirtualFileSystem;
import my.app.utils.vfs.VirtualResource;
import my.app.utils.vfs.local.LocalFileSystem;

/**
 * @author sklchan77
 */
public class FoldersFragment extends MediaLibFragment {

	@Override
	protected ListAdapter createAdapter(PermataServiceUiBinder b) {
		return new FoldersAdapter(getMainActivity(), b.getLib().getFolders());
	}

	@Override
	public int getFragmentId() {
		return R.id.folders_fragment;
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(R.string.folders);
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
		if (isRootFolder()) {
			builder.addItem(R.id.folders_add, R.drawable.add_folder, R.string.add_folder)
					.setHandler(this::navBarMenuItemSelected);
		} else {
			super.contributeToNavBarMenu(builder);
			FoldersAdapter a = getAdapter();

			if (a.getListView().isSelectionActive() && a.hasSelectable() && a.hasSelected()) {
				OverlayMenu.Builder b = builder.withSelectionHandler(this::navBarMenuItemSelected);
				b.addItem(R.id.favorites_add, R.drawable.favorite, R.string.favorites_add);
				getMainActivity().addPlaylistMenu(b, completed(a.getSelectedItems()));
			}
		}
	}

	protected boolean navBarMenuItemSelected(OverlayMenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.folders_add) {
			addFolder();
			return true;
		}
		return super.navBarMenuItemSelected(item);
	}

	public void navBarItemReselected(int itemId) {
		getAdapter().setParent(getLib().getFolders());
	}

	@Override
	protected boolean isRefreshSupported() {
		return true;
	}

	@Override
	protected boolean isRescanSupported() {
		return true;
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) return;

		FoldersAdapter a = getAdapter();
		if (a != null) a.animateAddButton(a.getParent());
	}

	@Override
	public void switchingTo(@NonNull ActivityFragment newFragment) {
		super.switchingTo(newFragment);
		getMainActivity().getFloatingButton().clearAnimation();
	}

	public void addFolder() {
		MainActivityDelegate a = getMainActivity();
		OverlayMenu menu = a.getContextMenu();
		menu.show(b -> {
			b.setTitle(R.string.add_folder);
			b.setSelectionHandler(this::addFolder);
			if (isSafSupported(a)) {
				if ((SDK_INT < Build.VERSION_CODES.TIRAMISU) ||
						App.get().hasManifestPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)) {
					b.addItem(R.id.vfs_file_system, R.string.vfs_file_system);
				}
				b.addItem(R.id.vfs_content, R.string.vfs_content);
			} else {
				b.addItem(R.id.vfs_file_system, R.string.vfs_file_system);
			}
			b.addItem(R.id.vfs_sftp, R.string.vfs_sftp);
			b.addItem(R.id.vfs_smb, R.string.vfs_smb);
			if (ENABLE_GS) b.addItem(R.id.vfs_gdrive, R.string.vfs_gdrive);
			b.addItem(R.id.m3u_playlist, R.string.m3u_playlist);
		});
	}

	private boolean addFolder(OverlayMenuItem item) {
		int itemId = item.getItemId();

		if (itemId == R.id.vfs_file_system) {
			addFolderPicker();
			return true;
		} else if (itemId == R.id.vfs_content) {
			addFolderIntent();
			return true;
		} else if (ENABLE_GS && (itemId == R.id.vfs_gdrive)) {
			addFolderVfs(GDRIVE_ID, R.string.vfs_gdrive);
			return true;
		} else if (itemId == R.id.vfs_sftp) {
			addFolderVfs(SFTP_ID, R.string.vfs_sftp);
			return true;
		} else if (itemId == R.id.vfs_smb) {
			addFolderVfs(SMB_ID, R.string.vfs_smb);
			return true;
		} else if (itemId == R.id.m3u_playlist) {
			addFolderVfs(M3U_ID, R.string.m3u_playlist);
			return true;
		}

		return false;
	}

	private void addFolderIntent() {
		try {
			getMainActivity().startActivityForResult(() -> new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
					.onSuccess(this::addFolderResult);
		} catch (ActivityNotFoundException ex) {
			String msg = ex.getLocalizedMessage();
			UiUtils.showAlert(getContext(),
					getString(R.string.err_failed_add_folder, (msg != null) ? msg : ex.toString()));
		}
	}

	public void addFolderPicker() {
		addFolderPicker(LocalFileSystem.getInstance());
	}

	private void addFolderPicker(VirtualFileSystem fs) {
		if (!(getMainActivity().showFragment(
				my.app.utils.R.id.file_picker) instanceof FilePickerFragment f)) return;
		f.setMode(FilePickerFragment.FOLDER);
		f.setFileSystem(fs);
		f.setFileConsumer(this::addFolderResult);
	}

	public boolean canScrollUp() {
		View v = getView();
		return (v != null) && (v.getScrollY() > 0);
	}

	private void addFolderVfs(String provId, @StringRes int name) {
		PermataVfsManager mgr = getLib().getVfsManager();
		mgr.getProvider(provId).then(p -> p.select(getMainActivity(), mgr.getFileSystems(provId)))
				.main().onFailure(fail -> failedToLoadModule(name, fail)).onSuccess(this::addFolderResult);
	}

	private void failedToLoadModule(@StringRes int name, Throwable ex) {
		getMainActivity().showFragment(R.id.folders_fragment);
		if (isCancellation(ex)) return;

		App.get().getHandler().post(() -> {
			String n = getString(name);
			Log.e(ex, "Failed to add folder: ", name);

			if (ex instanceof InstallException || ex instanceof SplitInstallException) {
				UiUtils.showAlert(getContext(), getString(R.string.err_failed_install_module, n));
			} else {
				String msg = ex.getLocalizedMessage();
				UiUtils.showAlert(getContext(),
						getString(R.string.err_failed_add_folder, (msg != null) ? msg : ex.toString()));
			}
		});
	}

	private void addFolderResult(Intent data) {
		if (data == null) return;

		Uri uri = data.getData();
		if (uri == null) return;

		getMainActivityDelegate().onSuccess(a -> {
			a.getContext().getContentResolver()
					.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION);
			Folders folders = getLib().getFolders();
			folders.addItem(uri).main().thenRun(() -> getAdapter().setParent(folders));
		});
	}

	private void addFolderResult(VirtualResource r) {
		getMainActivityDelegate().onSuccess(a -> {
			if (r != null) {
				Folders folders = a.getLib().getFolders();
				folders.addItem(r.getRid().toAndroidUri()).main()
						.thenRun(() -> getAdapter().setParent(folders));
			}
			a.showFragment(getFragmentId());
		});
	}

	@Override
	protected boolean isSupportedItem(Item i) {
		return getFolders().isFoldersItemId(i.getId());
	}

	private Folders getFolders() {
		return getLib().getFolders();
	}

	private boolean isRootFolder() {
		BrowsableItem p = getAdapter().getParent();
		return (p == null) || (p instanceof Folders);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		FoldersAdapter a = getAdapter();
		if (a.isCallbackCall()) return;
		if (prefs.contains(FoldersPrefs.FOLDERS) && isRootFolder()) a.reload();
		else super.onPreferenceChanged(store, prefs);
	}

	private final class FoldersAdapter extends ListAdapter {

		FoldersAdapter(MainActivityDelegate activity, BrowsableItem parent) {
			super(activity, parent);
			animateAddButton(parent);
		}

		@Override
		public FutureSupplier<?> setParent(BrowsableItem parent, boolean userAction) {
			return super.setParent(parent, userAction).onSuccess(v -> animateAddButton(parent));
		}

		public boolean isLongPressDragEnabled() {
			return isRootFolder();
		}

		@Override
		protected void onItemDismiss(int position) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof Folders) ((Folders) i).removeItem(position);
			super.onItemDismiss(position);
		}

		@Override
		protected boolean onItemMove(int fromPosition, int toPosition) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof Folders) ((Folders) i).moveItem(fromPosition, toPosition);
			return super.onItemMove(fromPosition, toPosition);
		}

		private void animateAddButton(BrowsableItem parent) {
			if (!(parent instanceof Folders)) return;

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
