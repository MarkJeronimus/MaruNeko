package org.digitalmodular.maruneko;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.digitalmodular.maruneko.dataView.FileDataFacade;
import org.digitalmodular.maruneko.dataView.FileNode;
import org.digitalmodular.maruneko.database.FileType;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-26
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToThreadYield"})
public final class FindDuplicatesMain {
	public static void main(String... args) throws SQLException, IOException {
		FileDataFacade fileData = new FileDataFacade(Paths.get("MaruNeko_root.sqlite"));

		Map<Long, List<FileNode>> groupedBySize = groupBySize(fileData);

		extracted(groupedBySize);

		Thread.yield();
	}

	@SuppressWarnings("CallToThreadYield")
	private static void extracted(Map<Long, List<FileNode>> groupedBySize) {
		List<Long> sizes = new ArrayList<>(groupedBySize.keySet());
		sizes.remove(0);
//		sizes.sort(Comparator.comparingLong(Long::longValue).reversed());
		Collections.shuffle(sizes, new Random(5));

		for (Long size : sizes) {
			List<FileNode> nodes = groupedBySize.get(size);

			Map<Long, List<FileNode>> groupedByContents = groupByContents(size, nodes);
			if (groupedByContents.isEmpty()) {
				continue;
			}

			dumpEquals(size, nodes, groupedByContents);

			Thread.yield();
		}
	}

	private static Map<Long, List<FileNode>> groupBySize(FileDataFacade fileData) {
		FileNode root = fileData.getRoot();
		Map<Long, List<FileNode>> groupedBySize =
				root.streamRecursively()
				    .filter(node -> node.getFileEntry().fileTypeID() == FileType.REGULAR_FILE.id())
				    .collect(() -> new HashMap<>(65536),
				             (map, node) -> map.computeIfAbsent(node.getFileEntry().size(),
				                                                key -> new ArrayList<>(16))
				                               .add(node),
				             HashMap::putAll);

		groupedBySize.values().removeIf(entries -> entries.size() == 1);

		return groupedBySize;
	}

	private static Map<Long, List<FileNode>> groupByContents(long size, List<FileNode> nodes) {
		if (size == 0) {
			return groupTrivially(nodes);
		} else if (size <= 8) {
			return groupByContentsTinyFiles(nodes);
		} else {
			return groupByContentsNotTinyFiles(size, nodes);
		}
	}

	private static Map<Long, List<FileNode>> groupTrivially(Collection<FileNode> nodes) {
		Map<Long, List<FileNode>> groupedTrivially = new HashMap<>(nodes.size());

		for (FileNode node : nodes) {
			Path path = Paths.get(node.toPathString());
			if (!Files.exists(path)) {
				System.out.println("Not found: " + path);
				continue;
			}

			groupedTrivially.computeIfAbsent(0L, key -> new ArrayList<>(nodes.size())).add(node);
		}

		groupedTrivially.values().removeIf(entries -> entries.size() == 1);

		return groupedTrivially;
	}

	private static Map<Long, List<FileNode>> groupByContentsTinyFiles(Collection<FileNode> nodes) {
		Map<Long, List<FileNode>> groupedByContents = new HashMap<>(nodes.size());

		for (FileNode node : nodes) {
			Path path = Paths.get(node.toPathString());
			if (!Files.exists(path)) {
				System.out.println("Not found: " + path);
				continue;
			}

			long signature = 0;
			try (InputStream in = Files.newInputStream(path)) {
				while (in.available() > 0) {
					signature <<= 8;
					signature |= in.read() & 0xFF;
				}
			} catch (AccessDeniedException ex) {
				System.out.println("Access denied: " + path);
				continue;
			} catch (IOException ex) {
				System.out.println("Can't read: " + path);
				Thread.yield();
				continue;
			}

			groupedByContents.computeIfAbsent(signature, key -> new ArrayList<>(nodes.size())).add(node);
		}

		groupedByContents.values().removeIf(entries -> entries.size() == 1);

		return groupedByContents;
	}

	private static Map<Long, List<FileNode>> groupByContentsNotTinyFiles(long size, List<FileNode> nodes) {
		Map<Long, List<FileNode>> groupedByHeader = groupByHeader(nodes);
		if (groupedByHeader.isEmpty()) {
			return Collections.emptyMap();
		}

		for (Map.Entry<Long, List<FileNode>> entry : groupedByHeader.entrySet()) {
			Long           signature = entry.getKey();
			List<FileNode> nodes2    = entry.getValue();

			System.out.println(
					size + " -> (" + nodes.size() + ") => " + signature + " -> " + nodes2.size());

			return groupByCompleteContents(size, nodes2);
		}

		Thread.yield();

		return Collections.emptyMap();
	}

	private static Map<Long, List<FileNode>> groupByCompleteContents(long size, Collection<FileNode> nodes) {
		long remaining = size - 8; // First 8 bytes are already checked

		// 256 MB worth of buffers, length a multiple of 8
		int bufferSize = 16;// Math.max(33554432 / nodes.size(), 1) * 8;

		List<BufferedInputStream> inputStreams = new ArrayList<>(nodes.size());
		byte[][]                  buffers      = new byte[nodes.size()][bufferSize];
		int[]                     groups       = new int[nodes.size()];

		Arrays.fill(groups, 0); // Start assuming all files are equal. They will be split in every round.
		int numGroups = 1;

		try {
			for (FileNode node : nodes) {
				Path path = Paths.get(node.toPathString());
				if (!Files.exists(path)) {
					System.out.println("Not found: " + path);
					continue;
				}

				try {
					inputStreams.add(new BufferedInputStream(Files.newInputStream(path)));
				} catch (IOException ignored) {
					System.out.println("Can't read: " + path);
					Thread.yield();
				}
			}

			for (BufferedInputStream in : inputStreams) {
				in.skip(8);
			}

			while (remaining > 0) {
				long readSize = Math.min(remaining, bufferSize);

				for (int i = 0; i < inputStreams.size(); i++) {
					inputStreams.get(i).read(buffers[i]);
				}

				numGroups = splitGroups(buffers, groups, numGroups);
				remaining -= readSize;
			}

			for (BufferedInputStream in : inputStreams) {
				try {
					in.close();
				} catch (IOException ignored) {
				}
			}
		} catch (IOException ex) {
			Thread.yield();
		}

		return Collections.emptyMap();
	}

	private static int splitGroups(byte[][] buffers, int[] groups, int numGroups) {
//		numGroups = 14;
//		for (int i = 0; i < groups.length; i++) {
//			groups[i] = ThreadLocalRandom.current().nextInt(numGroups);
//		}

		for (int group = numGroups - 1; group >= 0; group--) {
			// Assume all files are equal and do a fast depth-first comparison. This is by far the most common case.
			// When not all files appear to be equal, bail out and fall back to slow individual binning.

			boolean allEqual = checkAllEqual(buffers, groups, group);
			System.out.println("group " + group + (allEqual ? " are all equal" : " has differences"));

			if (allEqual) {
				continue;
			}

			numGroups = compareFully(buffers, groups, group, numGroups);
		}

		return numGroups;
	}

	private static int compareFully(byte[][] buffers, int[] groups, int group, int numGroups) {
		int numFiles = buffers.length;
		int bufLen   = buffers[0].length;
		for (int x = 0; x < bufLen; x++) {
			for (int y = 0; y < numFiles; y++) {
				if (groups[y] != group) {
					continue;
				}
			}

			Thread.yield();
		}
		return numGroups;
	}

	private static boolean checkAllEqual(byte[][] buffers, int[] groups, int group) {
		int first = -1;

		int numFiles = groups.length;
		for (int y = 0; y < numFiles; y++) {
			if (groups[y] != group) {
				continue;
			}

			if (first == -1) {
				first = y;
				continue;
			}

			boolean equal = Arrays.equals(buffers[first], buffers[y]);
			System.out.println(first + " <-> " + y + ": " + (equal ? "Equal" : "Different"));
			if (!equal) {
				return false;
			}
		}

		return true;
	}

	private static Map<Long, List<FileNode>> groupByHeader(Collection<FileNode> nodes) {
		Map<Long, List<FileNode>> groupedByHeader = new HashMap<>(nodes.size());

		for (FileNode node : nodes) {
			Path path = Paths.get(node.toPathString());
			if (!Files.exists(path)) {
				System.out.println("Not found: " + path);
				continue;
			}

			long signature;
			try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
				if (in.available() < 8) {
					System.out.println("File shrunk: " + path);
					Thread.yield();
					continue;
				}

				signature = in.readLong();
			} catch (AccessDeniedException ex) {
				System.out.println("Access denied: " + path);
				continue;
			} catch (IOException ex) {
				System.out.println("Can't read: " + path);
				Thread.yield();
				continue;
			}

			groupedByHeader.computeIfAbsent(signature, key -> new ArrayList<>(nodes.size())).add(node);
		}

		groupedByHeader.values().removeIf(entries -> entries.size() == 1);

		return groupedByHeader;
	}

	private static void dumpEquals(long size, List<FileNode> nodes, Map<Long, List<FileNode>> equals) {
		System.out.println("================================ " +
		                   size + " -> (" + nodes.size() +
		                   ") ================================>");
		for (Map.Entry<Long, List<FileNode>> entry : equals.entrySet()) {
			for (FileNode node : entry.getValue()) {
				System.out.println(node.getFileEntry().name());
			}
		}
	}
}
