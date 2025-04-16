package org.digitalmodular.maruneko;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.jetbrains.annotations.Nullable;

import org.digitalmodular.utilities.AnsiConsole;
import org.digitalmodular.utilities.FileUtilities;

import org.digitalmodular.maruneko.database.Database;
import org.digitalmodular.maruneko.database.FileEntry;
import static org.digitalmodular.maruneko.database.FileType.REGULAR_FILE;

/**
 * @author Mark Jeronimus
 */
// Created 2023-11-02
@SuppressWarnings("CallToPrintStackTrace")
public final class FindOpticalDiskFilesMain {
	private static final Collection<Database> openDatabases = new ArrayList<>(64);

	public static void main(String... args) throws Exception {
		loadRecentFiles();

		while (true) {
			String drivePath;

			while (true) {
				drivePath = getDrivePath();
				if (!drivePath.isEmpty()) {
					break;
				}

				TimeUnit.MILLISECONDS.sleep(250);
			}

			processDisk(drivePath);

			AnsiConsole.setColor(Color.WHITE);
			System.out.println("Insert next disk");
			System.out.println();
			System.out.println();
			AnsiConsole.resetAll();

			while (true) {
				drivePath = getDrivePath();
				if (drivePath.isEmpty()) {
					break;
				}

				TimeUnit.MILLISECONDS.sleep(250);
			}
			System.out.println();
		}
	}

	private static String getDrivePath() {
		FileSystem fs = FileSystems.getDefault();

		@Nullable String gvfs = null;
		for (FileStore store : fs.getFileStores()) {
			String storeString = store.toString();

//			System.out.println(storeString);

			String type = store.type();
			if (type.equals("tmpfs")) {
				continue;
			}

			if (storeString.startsWith("/dev")) {
				continue;
			} else if (storeString.startsWith("/proc")) {
				continue;
			} else if (storeString.startsWith("/sys")) {
				continue;
			} else if (storeString.startsWith("/run/") && !storeString.startsWith("/run/user")) {
				continue;
			}

			int index = storeString.indexOf(" (");
			if (index < 0) {
				continue;
			}

			String path = storeString.substring(0, index);

//			System.out.printf("\t%-30s %-20s %-10s\n", path, store.name(), type);

			if (storeString.contains("/run/user/1000/gvfs")) {
				gvfs = path;
			} else if (type.equals("iso9660") || type.equals("udf")) {
				return path;
			}
		}

		if (gvfs != null) {
			return findAudioDisk(gvfs);
		}

		return "";
	}

	private static String findAudioDisk(String gvfs) {
		List<Path> fss = null;
		try {
			fss = Files.list(Paths.get(gvfs)).toList();
		} catch (IOException ex) {
			ex.printStackTrace();
			return "";
		}
		for (Path fs : fss) {
			if (fs.getFileName().toString().startsWith("cdda:")) {
				return fs.toString();
			}
		}

		return "";
	}

	private static void processDisk(String drivePath) throws IOException {
		Files.walkFileTree(Paths.get(drivePath), new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				String name = file.getFileName().toString();
				long   size = attrs.size();

				float match = findFile(name, size);

				if (match < 0.5f && name.toLowerCase().endsWith(".zip")) {
					handleZip(file);
				} else if (match >= -1.5f) {
					logResult(file.toString(), size, match);
				}

				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void handleZip(Path zip) throws IOException {
		try (ZipFile f = new ZipFile(zip.toFile(), StandardCharsets.ISO_8859_1)) {
			Enumeration<? extends ZipEntry> entries = f.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				handleZipEntry(zip, entry);
			}
		} catch (ZipException ex) {
			AnsiConsole.setColor(new Color(0xFF6B68));
			System.out.println(zip + ": " + ex.getMessage());
			AnsiConsole.resetAll();
			Thread.yield();
		}
	}

	private static void handleZipEntry(Path zip, ZipEntry entry) {
		String name = entry.getName();
		if (name.indexOf('/') >= 0) {
			Thread.yield();
		}

		float match = findFile(name, entry.getSize());

		if (match >= -1.5f) {
			logResult("\t" + zip + ':' + entry, entry.getSize(), match);
		}
	}

	private static void loadRecentFiles() throws SQLException, IOException {
		openDatabases.add(new Database(Paths.get("copy/Cecile-Elements.maru"), false));
		openDatabases.add(new Database(Paths.get("copy/Cecile-Systeem.maru"), false));
		openDatabases.add(new Database(Paths.get("copy/Cecile-USB-HDD.maru"), false));
		openDatabases.add(new Database(Paths.get("copy/Cecile-film.maru"), false));
		openDatabases.add(new Database(Paths.get("copy/Cecile-hutspot.maru"), false));
		openDatabases.add(new Database(Paths.get("copy/Cecile-muziek.maru"), false));
		openDatabases.add(new Database(Paths.get("copy/Cecile-rommel.maru"), false));
		openDatabases.add(new Database(Paths.get("copy/Cecile-werk.maru"), false));
		openDatabases.add(new Database(Paths.get("copy/Cecile-1_systeem.maru"), false));
		openDatabases.add(new Database(Paths.get("copy/Cecile-1_div.maru"), false));

//		openDatabases.add(new Database(Paths.get("nasu.maru"), false));
	}

	private static void logResult(String path, long size, float match) {
		Color color;
		if (match < 0.0f) {
			color = Color.MAGENTA;
		} else if (match < 0.5f) {
			color = new Color(1.0f, match * 2.0f, 0);
		} else {
			color = new Color(2.0f - match * 2.0f, 1.0f, 0);
		}

		AnsiConsole.setColor(color);
		System.out.printf("[%3d%%] %s (%d)\n", (int)Math.floor(match * 100.0f), path, size);
		AnsiConsole.resetAll();
	}

	private static float findFile(String name, long size) {
		if (size < 1000) {
			return -2.0f;
		}

		String lowerName = name;
		if (lowerName.equals("pspbrwse.jbf") ||
		    size < 10000 ||
		    lowerName.contains("applaus") ||
		    lowerName.contains("applauzen") ||
		    lowerName.endsWith(".bmp") ||
		    lowerName.endsWith(".css") ||
		    //lowerName.endsWith(".doc") ||
		    lowerName.endsWith(".htm") ||
		    lowerName.endsWith(".jpg") ||
		    lowerName.endsWith(".m3u") ||
		    lowerName.endsWith(".mht") ||
		    lowerName.endsWith(".pdf") ||
		    lowerName.endsWith(".rtf") ||
		    lowerName.endsWith(".txt") ||
		    lowerName.endsWith(".nfo")) {
			return -2.0f;
		}

		List<FileEntry> results = findByNameAndSize(name, size);
		int             n       = results.size();

		if (n == 1) {
			return 1.0f;
		} else if (n == 0) {
			float  bestMatch = 0.0f;
			String ext       = '.' + FileUtilities.splitDirFileExt(name)[2];

			results = findByExtAndSize(ext, size);
			if (!results.isEmpty()) {
				bestMatch = fuzzyMatch(results, name, size, Color.WHITE);
			}

			results = findByName(name);
			if (!results.isEmpty()) {
				Thread.yield();
				float match = fuzzyMatch(results, name, size, new Color(0.5f, 0.5f, 1.0f));
				bestMatch = Math.max(bestMatch, match);
			}

			return bestMatch;
		} else {
			Thread.yield(); // Should not happen due to removeDuplicates()
			return -1.0f;
		}
	}

	private static List<FileEntry> findByName(String name) {
		List<FileEntry> allResults = new ArrayList<>(256);
		try {
			for (Database openDatabase : openDatabases) {
				List<FileEntry> results = openDatabase.fileEntryTable.getByNameAndType(name, REGULAR_FILE);
				allResults.addAll(results);
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		}

		return removeDuplicates(allResults);
	}

	private static List<FileEntry> findByNameAndSize(String name, long size) {
		List<FileEntry> allResults = new ArrayList<>(256);
		try {
			for (Database openDatabase : openDatabases) {
				List<FileEntry> results = openDatabase.fileEntryTable.getByNameTypeSize(name, REGULAR_FILE, size);
				allResults.addAll(results);
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		}

		return removeDuplicates(allResults);
	}

	private static List<FileEntry> findByExtAndSize(String ext, long size) {
		List<FileEntry> results = findBySize(size);

		if (results.isEmpty()) {
			return Collections.emptyList();
		}

		return results.stream().filter(entry -> entry.name().endsWith(ext)).toList();
	}

	private static List<FileEntry> findBySize(long size) {
		List<FileEntry> allResults = new ArrayList<>(256);
		try {
			for (Database openDatabase : openDatabases) {
				List<FileEntry> results = openDatabase.fileEntryTable.getByTypeAndSize(REGULAR_FILE, size);
				allResults.addAll(results);
			}
		} catch (SQLException ex) {
			throw new RuntimeException(ex);
		}

		return removeDuplicates(allResults);
	}

	private static List<FileEntry> removeDuplicates(List<FileEntry> results) {
		for (int i = results.size() - 1; i > 0; i--) {
			FileEntry rhs = results.get(i);

			for (int j = i - 1; j >= 0; j--) {
				FileEntry lhs = results.get(j);

				if (rhs.size() == lhs.size() && rhs.name().equals(lhs.name())) {
					results.remove(i);
					break;
				}
			}
		}

		return results;
	}

	private static float fuzzyMatch(List<FileEntry> results, String otherName, long size, Color colorization) {
		try {
			float bestMatch = 0.0f;

			for (FileEntry entry : results) {
				String name  = entry.name();
				float  ratio = (float)(entry.size() / (double)size);
				ratio = (float)Math.pow(1 - Math.abs(1 - 3 * ratio / (1 + 2 * ratio)), 3);

				int   longest = Math.max(name.length(), otherName.length());
				float match   = slidingFilenameMatch(otherName, name) / (float)longest * ratio;

				bestMatch = Math.max(bestMatch, match);
			}

			if (bestMatch >= 0.5) {
				// Log single best match
				for (FileEntry entry : results) {
					String name  = entry.name();
					float  ratio = (float)(entry.size() / (double)size);
					ratio = (float)Math.pow(1 - Math.abs(1 - 3 * ratio / (1 + 2 * ratio)), 3);

					int   longest = Math.max(name.length(), otherName.length());
					float match   = slidingFilenameMatch(otherName, name) / (float)longest * ratio;

					if (match == bestMatch) {
						float c = 0.3f + 0.7f * match;
						AnsiConsole.setColor(new Color(c * colorization.getRed() / 255.0f,
						                               c * colorization.getGreen() / 255.0f,
						                               c * colorization.getBlue() / 255.0f));
						System.out.printf("\t[%3d%%] %s", (int)Math.floor(match * 100.0f), name);
						if (entry.size() == size) {
							AnsiConsole.setColor(Color.yellow);
						}
						System.out.printf(" (%d) ?\n", entry.size());
						break;
					}
				}
			} else {
				// Log all bad matches
				for (FileEntry entry : results) {
					String name  = entry.name();
					float  ratio = (float)(entry.size() / (double)size);
					ratio = (float)Math.pow(1 - Math.abs(1 - 3 * ratio / (1 + 2 * ratio)), 3);

					int   longest = Math.max(name.length(), otherName.length());
					float match   = slidingFilenameMatch(otherName, name) / (float)longest * ratio;

					float c = 0.3f + 0.7f * match;
					AnsiConsole.setColor(new Color(c * colorization.getRed() / 255.0f,
					                               c * colorization.getGreen() / 255.0f,
					                               c * colorization.getBlue() / 255.0f));
					System.out.printf("\t[%3d%%] %s", (int)Math.floor(match * 100.0f), name);
					if (entry.size() == size) {
						AnsiConsole.setColor(Color.yellow);
					}
					System.out.printf(" (%d) ?\n", entry.size());
				}
			}

			return bestMatch;
		} finally {
			AnsiConsole.resetAll();
		}
	}

	private static int slidingFilenameMatch(String s1, String s2) {
		int correlation = 0;

		while (!(s1.isEmpty() || s2.isEmpty())) {
			int maxRun    = 0;
			int maxRunAt1 = -1;
			int maxRunAt2 = -1;
			int runStart1 = 0;
			int runStart2 = 0;
			for (int i = 1 - s2.length(); i < s1.length(); i++) {
				int run = 0;
				runStart1 = Math.max(i, 0);
				runStart2 = Math.max(-i, 0);
				for (int s1i = 0; s1i < s1.length(); s1i++) {
					int s2i = s1i - i;
					if (s2i >= 0 && s2i < s2.length()) {
						if (s1.charAt(s1i) == s2.charAt(s2i)) {
							run++;
						} else {
							if (maxRun < run) {
								maxRun    = run;
								maxRunAt1 = runStart1;
								maxRunAt2 = runStart2;
							}
							runStart1 = s1i + 1;
							runStart2 = s2i + 1;
							run       = 0;
						}
					}
				}

				if (maxRun < run) {
					maxRun    = run;
					maxRunAt1 = runStart1;
					maxRunAt2 = runStart2;
				}
			}

			if (maxRun < 4) {
				return correlation;
			}

			correlation += maxRun;

//			System.out.println(s1);
//			System.out.println(" ".repeat(maxRunAt1) + "^".repeat(maxRun));
//			System.out.println(s2);
//			System.out.println(" ".repeat(maxRunAt2) + "^".repeat(maxRun));

			s1 = s1.substring(0, maxRunAt1) + s1.substring(maxRunAt1 + maxRun);
			s2 = s2.substring(0, maxRunAt2) + s2.substring(maxRunAt2 + maxRun);
//			System.out.println(s1 + "\n" + s2);
		}

		return correlation;
	}
}
