package my.app.utils.vfs.gdrive;

import androidx.annotation.NonNull;

import com.google.api.services.drive.Drive;

import java.util.List;

import my.app.utils.async.FutureSupplier;
import my.app.utils.log.Log;
import my.app.utils.vfs.VirtualFolder;
import my.app.utils.vfs.VirtualResource;

/**
 * @author sklchan77
 */
class GdriveFolder extends GdriveResource implements VirtualFolder {

	GdriveFolder(GdriveFileSystem fs, String id, String name) {
		super(fs, id, name);
	}

	GdriveFolder(GdriveFileSystem fs, String id, String name, VirtualFolder parent) {
		super(fs, id, name, parent);
	}

	@Override
	public FutureSupplier<List<VirtualResource>> getChildren() {
		return fs.useDrive(d -> {
			Drive.Files.List req = d.files().list().setQ('\'' + id + "' in parents and trashed = false")
					.setSpaces("drive")
					.setFields("nextPageToken, files(id, name, mimeType)");
			return fs.loadList(req, this, false);
		});
	}

	@Override
	public boolean canDelete() {
		return true;
	}

	@NonNull
	@Override
	public FutureSupplier<Boolean> delete() {
		return fs.useDrive(d-> {
			try {
				d.files().delete(id).execute();
				return true;
			} catch (Exception ex) {
				Log.e(ex, "Failed to delete folder ", getName());
				return false;
			}
		});
	}
}
