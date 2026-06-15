package my.app.utils.db;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.io.Closeable;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import my.app.utils.async.FutureSupplier;
import my.app.utils.async.PromiseQueue;
import my.app.utils.function.CheckedConsumer;
import my.app.utils.function.CheckedFunction;
import my.app.utils.log.Log;

/**
 * @author sklchan77
 */
public class SQLite implements Closeable {
	private static final Map<File, DB> cache = new HashMap<>();
	private final DB db;
	private volatile boolean closed;

	private SQLite(DB db) {
		this.db = db;
	}

	public static SQLite get(File file) throws SQLiteException {
		synchronized (cache) {
			DB db = cache.get(file);

			if (db != null) {
				db.refCounter++;
				return new SQLite(db);
			}

			SQLiteDatabase sqlite;

			try {
				File dir = file.getParentFile();
				if ((dir != null) && !dir.isDirectory() && !dir.mkdirs()) {
					Log.w("Failed to create directory: ", dir);
				}
				sqlite = SQLiteDatabase.openOrCreateDatabase(file, null);
			} catch (Exception ex) {
				Log.w("Failed to open database: ", file, ": ", ex, ". Retrying ...");
				sqlite = SQLiteDatabase.openOrCreateDatabase(file, null);
			}

			db = new DB(file, sqlite);
			cache.put(file, db);
			return new SQLite(db);
		}
	}

	public static void delete(File file) throws SQLiteException {
		synchronized (cache) {
			DB db = cache.get(file);
			if (db == null) return;
			db.close();

			File f = db.file;
			File j = new File(f.getPath() + "-journal");
			if (f.isFile() && !f.delete()) Log.e("Failed to delete database file ", f);
			if (j.isFile() && !j.delete()) Log.e("Failed to delete database journal file ", j);
		}
	}

	public <T> FutureSupplier<T> query(CheckedFunction<SQLiteDatabase, T, Throwable> f) {
		return db.queue.enqueue(() -> f.apply(db.sqlite));
	}

	public FutureSupplier<Void> execute(CheckedConsumer<SQLiteDatabase, Throwable> f) {
		return db.queue.enqueue(() -> {
			f.accept(db.sqlite);
			return null;
		});
	}

	public boolean isClosed() {
		return closed || !db.sqlite.isOpen();
	}

	@Override
	public void close() {
		synchronized (this) {
			if (closed) return;
			closed = true;
		}

		synchronized (cache) {
			if (--db.refCounter == 0) {
				cache.remove(db.file);
				db.close();
			}
		}
	}

	@Override
	protected void finalize() {
		close();
	}

	@Nonnull
	@Override
	public String toString() {
		return "SQLite{" +
				"db=" + db.sqlite +
				", closed=" + closed +
				'}';
	}

	private static final class DB {
		final PromiseQueue queue = new PromiseQueue();
		final File file;
		final SQLiteDatabase sqlite;
		int refCounter = 1;

		private DB(File file, SQLiteDatabase sqlite) {
			this.file = file;
			this.sqlite = sqlite;
		}

		void close() {
			try {
				sqlite.close();
			} catch (Throwable ex) {
				Log.e(ex, "Failed to close database ", sqlite);
			}
		}

		@Override
		protected void finalize() {
			close();
		}
	}
}
