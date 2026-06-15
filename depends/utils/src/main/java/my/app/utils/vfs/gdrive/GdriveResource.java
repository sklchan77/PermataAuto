package my.app.utils.vfs.gdrive;

import androidx.annotation.NonNull;

import java.util.List;

import my.app.utils.async.FutureSupplier;
import my.app.utils.resource.Rid;
import my.app.utils.vfs.VirtualFileSystem;
import my.app.utils.vfs.VirtualFolder;
import my.app.utils.vfs.VirtualResource;

import static my.app.utils.async.Completed.completed;
import static my.app.utils.vfs.gdrive.GdriveFileSystem.SCHEME_GDRIVE;

/**
 * @author sklchan77
 */
class GdriveResource implements VirtualResource {
	final GdriveFileSystem fs;
	final String id;
	private final String name;
	private Rid rid;
	private FutureSupplier<VirtualFolder> parent;
	private FutureSupplier<Long> lastModified;

	GdriveResource(GdriveFileSystem fs, String id, String name) {
		this.fs = fs;
		this.id = id;
		this.name = name;
	}

	GdriveResource(GdriveFileSystem fs, String id, String name, VirtualFolder parent) {
		this.fs = fs;
		this.id = id;
		this.name = name;
		this.parent = completed(parent);
	}

	@NonNull
	@Override
	public VirtualFileSystem getVirtualFileSystem() {
		return fs;
	}

	@NonNull
	@Override
	public String getName() {
		return name;
	}

	@NonNull
	@Override
	public Rid getRid() {
		if (rid == null) {
			rid = Rid.create(SCHEME_GDRIVE + "://" + fs.getEmail() + '/' + id + '#' + name);
		}
		return rid;
	}

	@Override
	public FutureSupplier<Long> getLastModified() {
		if (lastModified != null) return lastModified;
		return lastModified = fs.useDrive(d -> d.files().get(id).setFields("modifiedTime").execute()
				.getModifiedTime().getValue()).onSuccess(lm -> lastModified = completed(lm));
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualFolder> getParent() {
		if (parent == null) {
			parent = fs.useDrive(d -> {
				List<String> parents = d.files().get(id).setFields("parents").execute().getParents();

				if (parents != null) {
					for (String pid : parents) {
						if ("root".equals(pid)) continue;
						String name = d.files().get(pid).execute().getName();
						return new GdriveFolder(fs, pid, name);
					}
				}

				return null;
			});
		}

		return parent;
	}

	@NonNull
	@Override
	public FutureSupplier<Boolean> exists() {
		return fs.useDrive(d -> name.equals(d.files().get(id).setFields("name").execute().getName()))
				.ifFail(f -> false);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		GdriveResource that = (GdriveResource) o;
		return id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@NonNull
	@Override
	public String toString() {
		return getRid().toString();
	}
}
