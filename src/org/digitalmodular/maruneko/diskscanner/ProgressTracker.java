package org.digitalmodular.maruneko.diskscanner;

import java.text.NumberFormat;

import org.jetbrains.annotations.Nullable;

import org.digitalmodular.utilities.FormatterUtilities;
import org.digitalmodular.utilities.math.FileSizeFormatter;
import static org.digitalmodular.utilities.ValidatorUtilities.requireAtLeast;
import static org.digitalmodular.utilities.ValidatorUtilities.requireNonNull;

import org.digitalmodular.maruneko.database.FileEntry;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-16
public class ProgressTracker {
	public static final NumberFormat FILESIZE_FORMATTER = FormatterUtilities.getFixedPrecisionFormatter(4);

	/**
	 * A value of 0 means no known target, and renders the progress as indeterminate.
	 */
	@SuppressWarnings("FieldHasSetterButNoGetter")
	private int totalSizeTarget = 0;

	private           int       numFiles  = 0;
	private           long      totalSize = 0;
	private @Nullable FileEntry lastEntry = null;

	private final long startTimestamp    = System.currentTimeMillis();
	private       long lastDumpTimestamp = startTimestamp;
	private       int  lastDumpNumFiles  = 0;

	public void setTotalSizeTarget(int totalSizeTarget) {
		this.totalSizeTarget = requireAtLeast(1, totalSizeTarget, "totalSizeTarget");
	}

	public boolean recordProgress(FileEntry entry) {
		requireNonNull(entry, "entry");
		numFiles++;
		totalSize += entry.size();
		lastEntry = entry;

		long now     = System.currentTimeMillis();
		int  elapsed = getElapsed(now);

		if (elapsed >= 1000) {
			dumpProgress(elapsed, numFiles - lastDumpNumFiles);
			lastDumpTimestamp = now;
			lastDumpNumFiles  = numFiles;
			return true;
		}

		return false;
	}

	public void recordDone() {
		long now     = System.currentTimeMillis();
		int  elapsed = getElapsed(now);

		dumpProgress(elapsed, numFiles - lastDumpNumFiles);
		lastDumpTimestamp = now;
		lastDumpNumFiles  = numFiles;
	}

	private int getElapsed(long now) {
		if (lastDumpTimestamp == startTimestamp) {
			lastDumpTimestamp = now;
			return 0;
		} else {
			long elapsed = now - lastDumpTimestamp;
			return elapsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)elapsed;
		}
	}

	@SuppressWarnings("UseOfSystemOutOrSystemErr")
	private void dumpProgress(int elapsedMillis, int numFilesDelta) {
		System.out.print(numFiles);
		if (totalSizeTarget > 0) {
			float progress = numFiles * 100.0f / totalSizeTarget;
			System.out.printf("/%d files (%.1f%%)", totalSizeTarget, progress);
		} else {
			System.out.print(" files");
		}

		float speed = numFilesDelta * 1000.0f / elapsedMillis;
		System.out.printf("\t%.1f files/sec", speed);

		System.out.print('\t' + FileSizeFormatter.formatFilesize(totalSize, FILESIZE_FORMATTER, true));

		if (lastEntry != null) {
			System.out.print('\t' + lastEntry.getFullPath());
		}

		System.out.println();
	}
}
