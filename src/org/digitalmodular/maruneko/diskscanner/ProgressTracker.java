package org.digitalmodular.maruneko.diskscanner;

import java.sql.SQLException;

import org.jetbrains.annotations.Nullable;

import static org.digitalmodular.utilities.ValidatorUtilities.requireNonNull;

import org.digitalmodular.maruneko.database.Database;
import org.digitalmodular.maruneko.database.FileEntry;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-16
public class ProgressTracker {
	private final Database database;

	private           int       numFiles = 0;
	private           long      fileSize = 0;
	private @Nullable FileEntry lastEntry;

	private int  total             = 0;
	private int  lastNumFiles      = 0;
	private long nextDumpTimestamp = System.currentTimeMillis() + 1_000;

	public ProgressTracker(Database database) {
		this.database = requireNonNull(database, "database");
	}

	public void setTotal(int total) {
		this.total = total;
	}

	public boolean recordProgress(FileEntry entry) {
		numFiles++;
		fileSize += entry.size();
		lastEntry = entry;

		if (System.currentTimeMillis() - nextDumpTimestamp > 0) {
			dumpProgress();
			return true;
		}

		return false;
	}

	@SuppressWarnings("UseOfSystemOutOrSystemErr")
	public void dumpProgress() {
		int speed = numFiles - lastNumFiles;

		System.out.print(numFiles);
		if (total > 0) {
			System.out.printf("/%d files (%.1f%%)", total, numFiles * 100.0f / total);
		} else {
			System.out.print(" files");
		}
		System.out.print(" files\t" + speed + " files/sec\t");

		if (fileSize > 1099511627776000L) {
			System.out.print((fileSize >> 40) + " TB");
		} else if (fileSize > 1073741824000L) {
			System.out.print((fileSize >> 30) + " GB");
		} else if (fileSize > 1048576000L) {
			System.out.print((fileSize >> 20) + " MB");
		} else if (fileSize > 1024000) {
			System.out.print((fileSize >> 10) + " kB");
		} else {
			System.out.print(fileSize);
		}

		if (lastEntry != null) {
			StringBuilder path = new StringBuilder(288).append(lastEntry.name());

			try {
				FileEntry entry = lastEntry;
				while (entry.parentID() > 0) {
					entry = database.fileEntryTable.getByID(entry.parentID());
					if (entry == null) {
						path.insert(0, "<null>/");
						break;
					}

					String name = entry.name();
					if (name.equals("/")) {
						path.insert(0, '/');
					} else {
						path.insert(0, name + '/');
					}
				}
			} catch (SQLException ex) {
				path.insert(0, ex.getClass().getSimpleName() + '(' + ex.getMessage() + ")/");
			}

			System.out.print("\t[" + lastEntry.id() + "] " + path);
		}

		System.out.println();

		lastNumFiles = numFiles;
		nextDumpTimestamp = System.currentTimeMillis() + 1_000;
	}
}
