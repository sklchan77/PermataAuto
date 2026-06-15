package my.app.utils.vfs.content;

import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;

import java.util.Objects;

import my.app.utils.app.App;
import my.app.utils.async.FutureSupplier;
import my.app.utils.resource.Rid;
import my.app.utils.vfs.VirtualFileSystem;
import my.app.utils.vfs.VirtualFolder;
import my.app.utils.vfs.VirtualResource;

import static java.util.Objects.requireNonNull;
import static my.app.utils.async.Completed.completed;

/**
 * @author sklchan77
 */
class ContentResource implements VirtualResource {
	private final ContentFolder parent;
	private final String name;
	private final String id;
	private FutureSupplier<Long> lastModified;

	public ContentResource(ContentFolder parent, String name, String id) {
		this.parent = parent;
		this.name = name;
		this.id = id;
	}

	@NonNull
	@Override
	public VirtualFileSystem getVirtualFileSystem() {
		return getRoot().getVirtualFileSystem();
	}

	@NonNull
	@Override
	public String getName() {
		return name;
	}

	@NonNull
	@Override
	public Rid getRid() {
		return Rid.create(DocumentsContract.buildDocumentUriUsingTree(getRootUri(), getId()));
	}

	@Override
	public FutureSupplier<Long> getLastModified() {
		if (lastModified != null) return lastModified;
		return lastModified = App.get().execute(() -> queryLong(getRid().toAndroidUri(),
				DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0))
				.onSuccess(lm -> lastModified = completed(lm));
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualFolder> getParent() {
		return completed(parent);
	}

	String getId() {
		return id;
	}

	@NonNull
	ContentFolder getParentFolder() {
		return parent;
	}

	@NonNull
	Uri getRootUri() {
		return getRoot().getRid().toAndroidUri();
	}

	@NonNull
	ContentFolder getRoot() {
		return requireNonNull(getParentFolder()).getRoot();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ContentResource that = (ContentResource) o;
		return Objects.equals(parent, that.parent) &&
				Objects.equals(id, that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(parent, id);
	}

	@NonNull
	@Override
	public String toString() {
		return getRid().toString();
	}

	static long queryLong(Uri uri, String column, long defaultValue) {
		try (Cursor c = App.get().getContentResolver().query(uri, new String[]{column}, null, null, null)) {
			if ((c != null) && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
			else return defaultValue;
		}
	}
}
