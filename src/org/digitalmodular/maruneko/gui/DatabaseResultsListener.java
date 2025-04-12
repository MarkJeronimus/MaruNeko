package org.digitalmodular.maruneko.gui;

import java.nio.file.Path;
import java.util.List;

import org.digitalmodular.maruneko.database.FileEntry;

/**
 * @author Mark Jeronimus
 */
// Created 2023-10-14
public interface DatabaseResultsListener {
	void databaseOpened(Path file, FileEntry root);

	void databaseOpenError(Path file, String error);

	void databaseClosed(Path file);

	void offerSearchResult(List<FileEntry> fileTree);
}
