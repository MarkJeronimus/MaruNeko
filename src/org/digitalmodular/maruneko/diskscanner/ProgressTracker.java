package org.digitalmodular.maruneko.diskscanner;

import java.text.DecimalFormat;

import org.jetbrains.annotations.Nullable;

import org.digitalmodular.utilities.math.FileSizeFormatter;

import org.digitalmodular.maruneko.database.FileEntry;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-16
public class ProgressTracker {
	private           int       numFiles = 0;
	private           long      fileSize = 0;
	private @Nullable FileEntry lastEntry;

	private int  total             = 0;
	private int  lastNumFiles      = 0;
	private long nextDumpTimestamp = System.currentTimeMillis() + 1_000;

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

		System.out.print("\t" + speed + " files/sec\t" +
		                 FileSizeFormatter.formatFilesize(fileSize, new DecimalFormat("0.# "), true));

		if (lastEntry != null) {
			System.out.print('\t' + lastEntry.getFullPath());
		}

		System.out.println();

		lastNumFiles = numFiles;
		nextDumpTimestamp = System.currentTimeMillis() + 1_000;
	}
}
