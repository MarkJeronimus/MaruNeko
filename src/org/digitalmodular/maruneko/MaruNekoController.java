package org.digitalmodular.maruneko;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.jetbrains.annotations.Nullable;

import org.digitalmodular.utilities.concurrent.SingleWorkerExecutor;

import org.digitalmodular.maruneko.database.Database;
import org.digitalmodular.maruneko.database.FileEntry;
import org.digitalmodular.maruneko.database.FileEntryTable;
import org.digitalmodular.maruneko.database.FileType;
import org.digitalmodular.maruneko.gui.DatabaseResultsListener;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

/**
 * @author Mark Jeronimus
 */
// Created 2023-10-14
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class MaruNekoController {
	private final Map<Path, Database> openDatabases = new HashMap<>(256);

	@SuppressWarnings("FieldHasSetterButNoGetter")
	private @Nullable DatabaseResultsListener listener = null;

	private final SingleWorkerExecutor executor = new SingleWorkerExecutor("db-thread");

	public void setListener(@Nullable DatabaseResultsListener listener) {
		this.listener = listener;
	}

	public synchronized void openDatabase(Path file) {
		if (openDatabases.containsKey(file)) {
			System.out.println("Database " + file + " already opened");
			return;
		}

		executor.submit(() -> {
			try {
				Database database = new Database(file, false);
				openDatabases.put(file, database);

				if (listener != null) {
					int rootID = findProbableRootNode(database);
					System.out.println(file + ": root=" + rootID);

					FileEntry root = database.fileEntryTable.getByID(rootID);
					assert root != null : rootID;

					listener.databaseOpened(file, root);
				}
			} catch (IOException | SQLException ex) {
				if (listener != null) {
					listener.databaseOpenError(file, ex.getMessage());
				} else {
					ex.printStackTrace();
				}
			}
		});
	}

	public synchronized void closeDatabases() {
		executor.submit(() -> {
			for (Map.Entry<Path, Database> entry : openDatabases.entrySet()) {
				Path               path     = entry.getKey();
				@Nullable Database database = entry.getValue();

				try {
					System.out.println("Closing Database " + path);
					database.close();

					if (listener != null) {
						listener.databaseClosed(path);
					}
				} catch (SQLException ex) {
					ex.printStackTrace();
				}
			}

			openDatabases.clear();
		});
	}

	private static int findProbableRootNode(Database database) throws IOException, SQLException {
		int maxID = database.volumeTable.getMaxID(0);
		if (maxID == 0) {
			throw new IOException("No volumes in table");
		}

		@Nullable FileEntry first = database.fileEntryTable.getFirstByVolumeID(2);

		return first == null ? 1 : 2;
	}

	public void performSearch(String regex) {
		DatabaseResultsListener listener = this.listener;
		if (listener == null) {
			return;
		}

		if (regex.isEmpty()) {
			return;
		}

		for (Database database : openDatabases.values()) {
			try {
				database.fileEntryTable.cancelTransaction();
			} catch (SQLException ex) {
				ex.printStackTrace();
			}
		}

		executor.submit(() -> performSearchImpl(regex, listener));
	}

	private void performSearchImpl(String regex, DatabaseResultsListener listener) {
		for (Database database : openDatabases.values()) {
			try {
				List<FileEntry> entries;
				entries = database.fileEntryTable.getByNameRegex(regex);

				for (FileEntry entry : entries) {
					handleSearchResult(database.fileEntryTable, entry, listener);
				}
			} catch (SQLiteException ex) {
				if (ex.getResultCode() == SQLiteErrorCode.SQLITE_INTERRUPT) {
					return;
				}

				ex.printStackTrace();
			} catch (SQLException ex) {
				ex.printStackTrace();
			}
		}
	}

	public void performPathSearch(FileEntry entry) {
		DatabaseResultsListener listener = this.listener;
		if (listener == null) {
			return;
		}

		Database database = entry.database();

		try {
			database.fileEntryTable.cancelTransaction();
		} catch (SQLException ex) {
			ex.printStackTrace();
		}

		Queue<FileEntry> remaining = new LinkedList<>();
		remaining.add(entry);

		executor.submit(() -> {
			while (true) {
				@Nullable FileEntry parent = remaining.poll();
				if (parent == null) {
					break;
				}

				try {
					List<FileEntry> children = database.fileEntryTable.getByParentID(parent.id());
					for (FileEntry child : children) {
						handleSearchResult(database.fileEntryTable, child, listener);

						if (child.fileTypeID() == FileType.DIRECTORY.id()) {
							remaining.add(child);
						}
					}
				} catch (SQLException ex) {
					ex.printStackTrace();
				}
			}
		});
	}

	private static void handleSearchResult(FileEntryTable fileEntryTable,
	                                       FileEntry entry,
	                                       DatabaseResultsListener listener)
			throws SQLException {
		List<FileEntry> path = new ArrayList<>(32);

		do {
			path.add(entry);

			if (entry.parentID() < 1) {
				break;
			}

			entry = fileEntryTable.getByID(entry.parentID());
		} while (entry != null);

		listener.offerSearchResult(path);
	}

	public static @Nullable FileEntry getParent(FileEntry entry) {
		try {
			Database database = entry.database();
			return database.fileEntryTable.getByID(entry.parentID());
		} catch (SQLException ex) {
			ex.printStackTrace();
			return null;
		}
	}
}
