package org.digitalmodular.maruneko.fileapi;

import java.sql.SQLException;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import static org.digitalmodular.utilities.ValidatorUtilities.requireNonNull;

import org.digitalmodular.maruneko.dataView.FileNode;
import org.digitalmodular.maruneko.database.Database;
import org.digitalmodular.maruneko.database.FileEntry;
import org.digitalmodular.maruneko.database.Volume;
import org.digitalmodular.maruneko.diskscanner.ProgressTracker;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-16
public class FileMapBuilder {
	private final Database database;

	public FileMapBuilder(Database database) {
		this.database = requireNonNull(database, "database");
	}

	public @Nullable FileNode buildFileMap() throws SQLException {
		@Nullable FileEntry firstEntry = database.fileEntryTable.getByID(1);
		if (firstEntry == null) {
			return null;
		}

		return buildFrom(firstEntry);
	}

	public FileNode buildFrom(FileEntry start) throws SQLException {
		requireNonNull(start, "start");

		Volume volume = database.volumeTable.getByID(start.volumeID());

		FileNode root = new FileNode(volume, start, null);

		ProgressTracker progressTracker = new ProgressTracker();
		progressTracker.setTotal(database.fileEntryTable.getMaxID());

		addChildren(volume, root, start, progressTracker);

		return root;
	}

	private void addChildren(Volume volume, FileNode parentNode, FileEntry parentEntry, ProgressTracker progressTracker)
			throws SQLException {

		progressTracker.recordProgress(parentEntry);

		int id = parentEntry.id();

		List<FileEntry> children = database.fileEntryTable.getByParentID(id);

		for (FileEntry childEntry : children) {
			FileNode childNode = new FileNode(volume, childEntry, parentNode);
			parentNode.addChild(childNode);

			addChildren(volume, childNode, childEntry, progressTracker);
		}
	}
}
