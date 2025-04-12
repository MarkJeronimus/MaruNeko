package org.digitalmodular.maruneko.dataView;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import org.digitalmodular.utilities.StringUtilities;

import org.digitalmodular.maruneko.database.Database;
import org.digitalmodular.maruneko.fileapi.FileMapBuilder;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-23
public class FileDataFacade {
	private final Database database;
	private final FileNode root;

	public FileDataFacade(Path databaseFile) throws IOException, SQLException {
		try {
			database = new Database(databaseFile, false);
		} catch (IOException | SQLException ex) {
			throw new IOException(ex);
		}

		FileMapBuilder fileMapBuilder = new FileMapBuilder(database);
		root = fileMapBuilder.buildFileMap();
	}

	public FileNode getRoot() {
		return root;
	}

	public @Nullable FileNode getFile(Path path) {
		@Nullable Path parent = path.getParent();

		if (parent == null) {
			if (!root.getFileEntry().name().equals(path.toString())) {
				return null;
			}

			return root;
		} else {
			@Nullable FileNode parentNode = getFile(parent);
			if (parentNode == null) {
				return null;
			}

			@Nullable FileNode node = getFileNode(parentNode.getChildren(), path.getFileName().toString());
			if (node != null) {
				System.out.println(node.toPathString());
			}
			return node;
		}
	}

	private static @Nullable FileNode getFileNode(Iterable<FileNode> nodes, String filename) {
		for (FileNode child : nodes) {
			if (child.getFileEntry().name().equals(filename)) {
				return child;
			}
		}

		return null;
	}

	public List<FileNode> findNodes(String keyword) {
		String normalized = StringUtilities.collateASCII(keyword).toLowerCase();

		List<FileNode> nodes = root.streamRecursively()
		                           .filter(node -> node.getFileEntry().name().contains(normalized))
		                           .toList();
		return nodes;
	}
}
