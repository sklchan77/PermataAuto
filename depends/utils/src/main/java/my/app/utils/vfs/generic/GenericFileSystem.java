package my.app.utils.vfs.generic;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.Set;

import my.app.utils.async.FutureSupplier;
import my.app.utils.pref.PreferenceStore;
import my.app.utils.resource.Rid;
import my.app.utils.vfs.VirtualFileSystem;
import my.app.utils.vfs.VirtualResource;

import static my.app.utils.async.Completed.completed;

/**
 * @author sklchan77
 */
public class GenericFileSystem implements VirtualFileSystem {
	private static final GenericFileSystem instance = new GenericFileSystem();

	public static GenericFileSystem getInstance() {
		return instance;
	}

	@Override
	public boolean isSupportedResource(Rid rid) {
		return true;
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualResource> getResource(Rid rid) {
		return completed(create(rid));
	}

	public GenericResource create(String rid) {
		return create(Rid.create(rid));
	}

	public GenericResource create(Rid rid) {
		return new GenericResource(rid);
	}

	@NonNull
	@Override
	public Provider getProvider() {
		return Provider.getInstance();
	}

	public static final class Provider implements VirtualFileSystem.Provider {
		private static final Provider instance = new Provider();

		private Provider() {
		}

		public static Provider getInstance() {
			return instance;
		}

		@NonNull
		@Override
		public Set<String> getSupportedSchemes() {
			return Collections.emptySet();
		}

		@NonNull
		@Override
		public FutureSupplier<VirtualFileSystem> createFileSystem(PreferenceStore ps) {
			return completed(GenericFileSystem.getInstance());
		}
	}
}
