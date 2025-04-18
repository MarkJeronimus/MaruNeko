package org.digitalmodular.maruneko.diskscanner;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import static org.digitalmodular.utilities.ValidatorUtilities.requireNonNull;

import org.digitalmodular.maruneko.database.Database;
import org.digitalmodular.maruneko.database.FileEntry;
import org.digitalmodular.maruneko.database.FileType;
import org.digitalmodular.maruneko.database.Volume;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-14
public class DiskScanner {
	public static void breakpoint() {
		//noinspection CallToThreadYield
		Thread.yield();
	}

	private final Database database;

	public DiskScanner(Database database) {
		this.database = requireNonNull(database, "database");
	}

	/**
	 * Scans a file or directory tree, adding found entries to the database.
	 * <p>
	 * Symlinks are never followed.
	 *
	 * @param start The file to scan or directory to start scanning at
	 */
	public FileEntry scan(Path start) throws IOException {
		return scan(start, Integer.MAX_VALUE);
	}

	/**
	 * Scans a file or directory tree, adding found entries to the database.
	 * <p>
	 * Symlinks are never followed.
	 *
	 * @param start    The file to scan or directory to start scanning at
	 * @param maxDepth The number of levels to recurse (1 = scan specified Path only)
	 */
	public FileEntry scan(Path start, int maxDepth) throws IOException {
		Path searchRoot = start.toAbsolutePath();

		ProgressTracker progressTracker = new ProgressTracker();

		FileEntry firstEntry = addParents(searchRoot);

		int firstParentID = firstEntry.id();
		int firstVolumeID = getOrAddVolume(searchRoot).id();

		System.out.println("firstVolumeID = " + firstVolumeID);

		List<Integer> volumeStack = new ArrayList<>(32);
		List<Integer> parentStack = new ArrayList<>(32);
		volumeStack.add(firstVolumeID);
		parentStack.add(firstParentID);
//		System.out.println("### volumeStack=" + volumeStack + "\tparentStack=" + parentStack);

		Files.walkFileTree(searchRoot, Collections.emptySet(), maxDepth, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				if (dir.equals(searchRoot)) {
					return FileVisitResult.CONTINUE;
				}

				int volumeID = getOrAddVolume(dir).id();
				if (volumeID != firstVolumeID) {
					System.out.println("Skipping root " + dir + " (firstVolumeID = " + volumeID + ')');
					return FileVisitResult.SKIP_SUBTREE;
				}

				int       parentID = parentStack.get(parentStack.size() - 1);
				FileEntry entry    = addFileEntry(dir, volumeID, parentID, attrs);

				// FIXME: Don't rely on progress tracker for core logic
				if (progressTracker.recordProgress(entry)) {
					try {
						database.commit();
					} catch (SQLException ex) {
						throw new IOException(ex);
					}
				}

				volumeStack.add(volumeID);
				parentStack.add(entry.id());
//				System.out.println(">>> volumeStack=" + volumeStack + "\tparentStack=" + parentStack);

				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				int volumeID = volumeStack.get(volumeStack.size() - 1);
				int parentID = parentStack.get(parentStack.size() - 1);

				FileEntry entry = addFileEntry(file, volumeID, parentID, attrs);

				// FIXME: Don't rely on progress tracker for core logic
				if (progressTracker.recordProgress(entry)) {
					try {
						database.commit();
					} catch (SQLException ex) {
						throw new IOException(ex);
					}
				}

				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException ex) throws IOException {
				if (ex instanceof NoSuchFileException) {
					return FileVisitResult.CONTINUE;
				} else if (ex instanceof AccessDeniedException) {
					int volumeID = volumeStack.get(volumeStack.size() - 1);
					int parentID = parentStack.get(parentStack.size() - 1);

					addFileEntry(file, volumeID, parentID, null);
				} else if (ex instanceof FileSystemLoopException) {
					System.out.println("Skipping FileSystem loop " + file);
					return FileVisitResult.CONTINUE;
				} else {
					breakpoint();
				}

				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException ex) {
				parentStack.remove(parentStack.size() - 1);
				volumeStack.remove(volumeStack.size() - 1);
//				System.out.println("<<< volumeStack=" + volumeStack + "\tparentStack=" + parentStack);

				return FileVisitResult.CONTINUE;
			}
		});

		progressTracker.recordDone();
		try {
			database.commit();
		} catch (SQLException ex) {
			throw new IOException(ex);
		}

		return firstEntry;
	}

	private Volume getOrAddVolume(Path path) throws IOException {
		Volume volume = VolumeUtilities.getVolume(path);

		try {
			volume = database.volumeTable.updateValue(volume);
		} catch (SQLException ex) {
			throw new IOException(ex);
		}

//		System.out.println(volume);
		return volume;
	}

	private FileEntry addParents(Path path) throws IOException {
		List<Path> lineage = getLineage(path);

		assert !lineage.isEmpty();

		@Nullable FileEntry parent = null;

		for (int i = lineage.size() - 1; i >= 0; i--) {
			Path dir = lineage.get(i);

			int volumeID = getOrAddVolume(dir).id();
			BasicFileAttributes attrs = Files.readAttributes(dir,
			                                                 BasicFileAttributes.class,
			                                                 LinkOption.NOFOLLOW_LINKS);

			int parentID = parent == null ? 0 : parent.id();

			parent = addFileEntry(dir, volumeID, parentID, attrs);
		}

		return parent;
	}

	private static List<Path> getLineage(Path path) {
		List<Path> lineage = new ArrayList<>(path.getNameCount());

		if (path.getNameCount() == 0) {
			return Collections.singletonList(path);
		}

		do {
			lineage.add(path);

			path = path.getParent();
		} while (path != null);

		return lineage;
	}

	private FileEntry addFileEntry(Path file, int volumeID, int parentID, @Nullable BasicFileAttributes attrs)
			throws IOException {
		FileType fileType = getFileType(attrs);

		Path   fileName = file.getFileName();
		String name     = fileName == null ? "/" : fileName.toString();

		long size                  = attrs == null ? 0 : attrs.size();
		long creationTimestamp     = attrs == null ? 0 : attrs.creationTime().toMillis();
		long modificationTimestamp = attrs == null ? 0 : attrs.lastModifiedTime().toMillis();
		long accessTimestamp       = attrs == null ? 0 : attrs.lastAccessTime().toMillis();
		long firstSeenTimestamp    = System.currentTimeMillis();

		FileEntry entry = new FileEntry(database, 0,
		                                parentID,
		                                name,
		                                volumeID,
		                                fileType.id(),
		                                size,
		                                creationTimestamp,
		                                modificationTimestamp,
		                                accessTimestamp,
		                                firstSeenTimestamp,
		                                firstSeenTimestamp);

		try {
			entry = database.fileEntryTable.updateValue(entry);
		} catch (SQLException ex) {
			throw new IOException(ex);
		}

//		System.out.println(entry);
		return entry;
	}

	private static FileType getFileType(@Nullable BasicFileAttributes attrs) {
		FileType fileType;
		if (attrs == null) {
			fileType = FileType.UNKNOWN;
		} else if (attrs.isDirectory()) {
			fileType = FileType.DIRECTORY;
		} else if (attrs.isRegularFile()) {
			fileType = FileType.REGULAR_FILE;
		} else if (attrs.isSymbolicLink()) {
			fileType = FileType.SYMLINK;
		} else if (attrs.isOther()) {
			fileType = FileType.OTHER;
		} else {
			breakpoint();
			fileType = FileType.OTHER;
		}
		return fileType;
	}
}
