package my.app.utils.vfs;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Set;

import my.app.utils.async.FutureSupplier;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.resource.Rid;

import static my.app.utils.async.Completed.completedEmptyList;

/**
 * @author sklchan77
 */
public interface VirtualFileSystem {

	@NonNull
	Provider getProvider();

	@NonNull
	FutureSupplier<? extends VirtualResource> getResource(Rid rid);

	default FutureSupplier<? extends VirtualFile> getFile(Rid rid) {
		return getResource(rid).map(r -> (r instanceof VirtualFile) ? (VirtualFile) r : null);
	}

	default FutureSupplier<? extends VirtualFolder> getFolder(Rid rid) {
		return getResource(rid).map(r -> (r instanceof VirtualFile) ? (VirtualFolder) r : null);
	}

	@NonNull
	default FutureSupplier<List<VirtualFolder>> getRoots() {
		return completedEmptyList();
	}

	default boolean isSupportedResource(Rid rid) {
		return getProvider().getSupportedSchemes().contains(rid.getScheme());
	}

	interface Provider {

		@NonNull
		Set<String> getSupportedSchemes();

		@NonNull
		FutureSupplier<VirtualFileSystem> createFileSystem(PreferenceStore ps);
	}
}
