package org.digitalmodular.maruneko.dataView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import static org.digitalmodular.utilities.ValidatorUtilities.requireNonNull;

import org.digitalmodular.maruneko.database.FileEntry;
import org.digitalmodular.maruneko.database.Volume;

/**
 * @author Mark Jeronimus
 */
// Created 2012-11-14
public class FileNode {
	/**
	 * @author Mark Jeronimus
	 */
	// Created 2022-11-17
	public enum SizeFunction {
		NUM_CHILDREN(FileNode::getNumChildren),
		TREE_SIZE(FileNode::getTreeSize),
		LARGEST_SUBTREE(FileNode::getLargestSubtree),
		FILE_SIZE(FileNode::getFileSizeOfTree),
		OCCUPIED_SIZE(FileNode::getFileSizeOnDiskOfTree);

		private final ToLongFunction<FileNode> getter;

		SizeFunction(ToLongFunction<FileNode> getter) {
			this.getter = getter;
		}

		public long apply(FileNode fileNode) {
			return getter.applyAsLong(fileNode);
		}
	}

	/**
	 * @author Mark Jeronimus
	 */
	// Created 2022-11-17
	public enum SortFunction {
		NAME(Comparator.comparing(fileNode -> fileNode.fileEntry.name())),
		TYPE_THEN_NAME(Comparator.<FileNode>comparingInt(fileNode -> fileNode.fileEntry.fileTypeID())
		                         .thenComparing(NAME.getComparator())),
		NUM_CHILDREN(Comparator.<FileNode>comparingLong(fileNode -> fileNode.children.size())
		                       .thenComparing(NAME.getComparator())),
		TREE_SIZE(Comparator.<FileNode>comparingLong(fileNode -> fileNode.treeSize)
		                    .thenComparing(NAME.getComparator())),
		LARGEST_SUBTREE(Comparator.<FileNode>comparingLong(fileNode -> fileNode.largestSubtree)
		                          .thenComparing(NAME.getComparator())),
		FILE_SIZE(Comparator.<FileNode>comparingLong(fileNode -> fileNode.fileSizeOfTree)
		                    .thenComparing(NAME.getComparator())),
		OCCUPIED_SIZE(Comparator.<FileNode>comparingLong(fileNode -> fileNode.fileSizeOnDiskOfTree)
		                        .thenComparing(NAME.getComparator()));

		private final Comparator<FileNode> comparator;

		SortFunction(Comparator<FileNode> comparator) {
			this.comparator = comparator;
		}

		public void sortChildren(FileNode fileNode) {
			fileNode.children.sort(getComparator());
		}

		public Comparator<FileNode> getComparator() {
			return comparator;
		}
	}

	private final Volume    volume;
	private final FileEntry fileEntry;

	private final @Nullable FileNode       parent;
	private final           List<FileNode> children = new ArrayList<>(16);

	private int  treeSize             = 1;
	private int  largestSubtree       = 1;
	private long fileSize             = 0;
	private long fileSizeOnDisk       = 0;
	private long fileSizeOfTree       = 0;
	private long fileSizeOnDiskOfTree = 0;

	public FileNode(Volume volume, FileEntry fileEntry, @Nullable FileNode parent) {
		this.volume = requireNonNull(volume, "volume");
		this.fileEntry = requireNonNull(fileEntry, "fileEntry");
		this.parent = parent;

		fileSize = fileEntry.size();
		fileSizeOnDisk = calcClusterSize(fileEntry.size(), volume.blockSize());
		fileSizeOfTree = fileSize;
		fileSizeOnDiskOfTree = fileSizeOnDisk;
	}

	public void addChild(FileNode child) {
		requireNonNull(child, "child");
		children.add(child);

		treeSize++;
		largestSubtree = Math.max(largestSubtree, children.size());
		fileSizeOfTree += child.getFileSizeOfTree();
		fileSizeOnDiskOfTree += child.getFileSizeOnDiskOfTree();

		if (parent != null) {
			parent.addSizes(largestSubtree, child.getFileSizeOfTree(), child.getFileSizeOnDiskOfTree());
		}
	}

	public void removeChild(FileNode child) {
		requireNonNull(child, "child");
		children.remove(child);

		// Best effort to get some approximate values. To regain accurate values, rebuild the tree from scratch.
		treeSize--;
		fileSizeOfTree -= child.getFileSizeOfTree();
		fileSizeOnDiskOfTree -= child.getFileSizeOnDiskOfTree();
	}

	public void removeChildren() {
		for (FileNode child : children) {
			// Best effort to get some approximate values. To regain accurate values, rebuild the tree from scratch.
			treeSize--;
			fileSizeOfTree -= child.getFileSizeOfTree();
			fileSizeOnDiskOfTree -= child.getFileSizeOnDiskOfTree();
		}

		children.clear();
	}

	@SuppressWarnings("TailRecursion") // Let each object modify itself (hence more logical with recursion)
	private void addSizes(int childLargestSubtree, long fileSize, long fileSizeOnDisk) {
		treeSize++;
		largestSubtree = Math.max(largestSubtree, childLargestSubtree);
		fileSizeOfTree += fileSize;
		fileSizeOnDiskOfTree += fileSizeOnDisk;

		if (parent != null) {
			parent.addSizes(largestSubtree, fileSize, fileSizeOnDisk);
		}
	}

	public void sort(Comparator<FileNode> comparator) {
		children.sort(comparator);
	}

	public Volume getVolume() {
		return volume;
	}

	public FileEntry getFileEntry() {
		return fileEntry;
	}

	public @Nullable FileNode getParent() {
		return parent;
	}

	public List<FileNode> getChildren() {
		return Collections.unmodifiableList(children);
	}

	public int getNumChildren() {
		return children.size();
	}

	public int getTreeSize() {
		return treeSize;
	}

	public int getLargestSubtree() {
		return largestSubtree;
	}

	public long getFileSize() {
		return fileSize;
	}

	public long getFileSizeOnDisk() {
		return fileSizeOnDisk;
	}

	public long getFileSizeOfTree() {
		return fileSizeOfTree;
	}

	public long getFileSizeOnDiskOfTree() {
		return fileSizeOnDiskOfTree;
	}

	private static long calcClusterSize(long size, long blockSize) {
		return (size + blockSize - 1) & -blockSize;
	}

	public Stream<FileNode> streamRecursively() {
		return Stream.concat(
				Stream.of(this),
				children.stream()
				        .sorted(SortFunction.TYPE_THEN_NAME.getComparator())
				        .flatMap(FileNode::streamRecursively));
	}

	public String toPathString() {
		StringBuilder sb = new StringBuilder(256);

		FileNode node = this;
		do {
			String name = node.fileEntry.name();

			if (!sb.isEmpty() && node.parent != null) {
				sb.insert(0, '/');
			}

			sb.insert(0, name);
			node = node.parent;
		} while (node != null);

		return sb.toString();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(256).append(getClass().getSimpleName()).append('{');
		sb.append(fileEntry.id()).append(", ");
		if (children.isEmpty()) {
			sb.append(fileEntry.name());
		} else {
			sb.append('[').append(fileEntry.name()).append(']');
			sb.append(", numChildren=").append(children.size());
		}
		sb.append(", treeSize=").append(treeSize);
		sb.append(", largestSubtree=").append(largestSubtree);
		sb.append(", fileSize=").append(fileSize);
		sb.append(", fileSizeOnDisk=").append(fileSizeOnDisk);
		if (!children.isEmpty()) {
			sb.append(", fileSizeOfTree=").append(fileSizeOfTree);
			sb.append(", fileSizeOnDiskOfTree=").append(fileSizeOnDiskOfTree);
		}
		return sb.append('}').toString();
	}
}
