package org.digitalmodular.maruneko.diskscanner;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.digitalmodular.maruneko.database.Volume;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-15
public final class VolumeUtilities {
	@SuppressWarnings("StaticCollection") // Is unmodifiable
	private static final List<String> MOUNT_POINTS = listMountPoints();

	private VolumeUtilities() {
		throw new AssertionError();
	}

	public static Volume getVolume(Path file) throws IOException {
		file = file.toAbsolutePath();

		String    fullPath = file.toString();
		FileStore store    = Files.getFileStore(file);

		String mountPoint         = findMountPoint(fullPath);
		String device             = store.name();
		String fileSystem         = store.type();
		int    blockSize          = (int)store.getBlockSize();
		long   firstSeenTimestamp = ZonedDateTime.now(Clock.systemUTC()).toInstant().toEpochMilli();

//		// Old way of finding mount point
//
//		Path mountPoint = file;
//
//		while (true) {
//			file = file.getParent();
//			if (file == null) {
//				break;
//			}
//
//			if (store.equals(Files.getFileStore(file))) {
//				mountPoint = file;
//			} else {
//				break;
//			}
//		}

//		System.out.printf("%-51s", file);
//		System.out.printf(" MountPoint: %-20s", mountPoint);
//		System.out.printf(" Device: %-20s", device);
//		System.out.printf(" FileSystem: %-10s", fileSystem);
//		System.out.printf(" BlockSize: %-5s\n", blockSize);

		return new Volume(0, mountPoint, device, fileSystem, blockSize, firstSeenTimestamp, firstSeenTimestamp);
//		return new Volume(0, "/", "/", "x", 1, 0, 0);
	}

	private static String findMountPoint(String fullPath) throws IOException {
		for (String mountPoint : MOUNT_POINTS) {
			if (fullPath.startsWith(mountPoint)) {
				return mountPoint;
			}
		}

		throw new IOException("Mount point not found: " + fullPath);
	}

	private static List<String> listMountPoints() {
		Iterable<FileStore> fileStores = FileSystems.getDefault().getFileStores();

		List<String> mountPoints = new ArrayList<>(64);

		for (FileStore store : fileStores) {
			String storeString = store.toString();
//			if (storeString.startsWith("/dev") ||
//			    storeString.startsWith("/proc") ||
//			    storeString.startsWith("/sys")) {
//				continue;
//			} else if (storeString.startsWith("/run/") &&
//			           !storeString.startsWith("/run/user")) {
//				continue;
//			}

			int i = storeString.indexOf(" (");
			if (i <= 0) {
				System.err.println("MountPoint not found in the toString of the FileStore: " + storeString);
				continue;
			}

			String mountPoint = storeString.substring(0, i);
			i = storeString.indexOf(" (", i + 1);
			if (i > 0) {
				System.err.println("MountPoint not found in the toString of the FileStore: " + storeString);
				continue;
			}

			mountPoints.add(mountPoint);
		}

		mountPoints.sort(Comparator.comparingInt(String::length).reversed());

		return Collections.unmodifiableList(mountPoints);
	}
}
