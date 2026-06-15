package my.app.utils.vfs;

import static my.app.utils.async.Completed.completed;
import static my.app.utils.async.Completed.completedNull;
import static my.app.utils.async.Completed.failed;
import static my.app.utils.collection.NaturalOrderComparator.compareNatural;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import my.app.utils.async.FutureSupplier;
import my.app.utils.resource.Rid;

/**
 * @author sklchan77
 */
public interface VirtualResource extends Comparable<VirtualResource> {

	@NonNull
	VirtualFileSystem getVirtualFileSystem();

	@NonNull
	String getName();

	@NonNull
	Rid getRid();

	default boolean isFile() {
		return false;
	}

	default boolean isFolder() {
		return false;
	}

	default boolean isLocalFile() {
		return getLocalFile() != null;
	}

	default boolean canDelete() {
		return false;
	}

	@Nullable
	default File getLocalFile() {
		return null;
	}

	default FutureSupplier<Long> getLastModified() {
		return completed(0L);
	}

	@NonNull
	default FutureSupplier<VirtualFolder> getParent() {
		return completedNull();
	}

	default boolean hasParent() {
		return getParent().peek() != null;
	}

	@NonNull
	default FutureSupplier<Boolean> exists() {
		return completed(true);
	}

	@NonNull
	default FutureSupplier<Boolean> create() {
		return failed(new UnsupportedOperationException());
	}

	@NonNull
	default FutureSupplier<Boolean> delete() {
		return failed(new UnsupportedOperationException());
	}

	@Override
	default int compareTo(@NonNull VirtualResource o) {
		if (isFolder()) {
			if (!o.isFolder()) return -1;
		} else if (o.isFolder()) {
			return 1;
		}

		return compareNatural(getName(), o.getName());
	}
}
