package org.digitalmodular.maruneko;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.digitalmodular.utilities.ConfigManager;

import org.digitalmodular.maruneko.database.Database;
import org.digitalmodular.maruneko.database.FileEntry;

/**
 * @author Mark Jeronimus
 */
// Created 2023-11-02
@SuppressWarnings("ForLoopReplaceableByForEach")
public final class MPEGFindMain {
	private static final Pattern SPACE_SPLITTER   = Pattern.compile("(?<!\\\\) ");
	private static final Pattern REGEXIFY_PATTERN = Pattern.compile("[.*+?^${}()|\\[\\]\\\\]");
	private static final Pattern QUESTION_PATTERN = Pattern.compile("\\\\\\?");
	private static final Pattern ASTERISK_PATTERN = Pattern.compile("\\\\\\*");

	private static final Pattern YEAR_PATTERN = Pattern.compile("(.*) \\([0-9]{4}\\).*");
	private static final Pattern OF_PATTERN   = Pattern.compile("(.*) [0-9]+ of [0-9]+");
	private static final Pattern SUBS_PATTERN = Pattern.compile("(.*) [a-z]+ subs( .*)$");
	private static final Pattern PART_PATTERN = Pattern.compile("(.*) part [0-9]+$");
	private static final Pattern DASH_PATTERN = Pattern.compile("(.*) -");

	private static final Collection<Database> openDatabases = new ArrayList<>(64);

	public static void main(String... args) throws Exception {
		loadRecentFiles();

		Path         dir   = Paths.get("/home/zom-b/Winprog/MPEG Audio Collection 2.92");
		List<String> lines = Files.readAllLines(dir.resolve("report.txt"));
		try (BufferedWriter out = Files.newBufferedWriter(dir.resolve("report-not-found-round2.txt"))) {
			for (int i = 2; i < lines.size(); i++) {
				String line = lines.get(i);

				String sizeString      = line.substring(85, 95).trim();
				double approximateSize = Double.parseDouble(sizeString);

				if (approximateSize < 2) {
					continue;
				}

				String name = line.substring(5, 84).trim().toLowerCase();

				name = strip(name, YEAR_PATTERN);
				name = strip(name, OF_PATTERN);
				name = strip(name, SUBS_PATTERN);
				name = strip(name, PART_PATTERN);
				name = strip(name, DASH_PATTERN);
//			    System.out.println(name);

				String query = prepareQuery(name);

				boolean found = search(query, approximateSize);
				System.out.println(found + "\t" + name + '\t' + approximateSize);

				if (!found) {
					out.write(line);
					out.write('\n');
				}
			}
		}
	}

	private static String strip(String name, Pattern pattern) {
		Matcher matcher = pattern.matcher(name);

		if (matcher.find()) {
//			System.out.println(true + "\t" + matcher.group(1));
			return matcher.group(1);
		} else {
//			System.out.println(false + "\t" + name);
			return name;
		}
	}

	private static void loadRecentFiles() throws SQLException, IOException {
		int n = ConfigManager.getIntValue("RecentMaruFileCount", 0);

		for (int i = 0; i < n; i++) {
			//noinspection StringConcatenationMissingWhitespace
			String recent = ConfigManager.getValue("RecentMaruFile" + i, "");

			if (!recent.isEmpty()) {
				File file = new File(recent);
				if (file.exists()) {
					openDatabases.add(new Database(file.toPath(), false));
				}
			}
		}
	}

	private static String prepareQuery(String query) {
		String[] split = SPACE_SPLITTER.split(query);

		for (int i = 0; i < split.length; i++) {
			split[i] = REGEXIFY_PATTERN.matcher(split[i]).replaceAll("\\\\$0");
			split[i] = QUESTION_PATTERN.matcher(split[i]).replaceAll(".");
			split[i] = ASTERISK_PATTERN.matcher(split[i]).replaceAll(".*");
		}

		query = String.join(".*", split);

		if (query.startsWith(".*\\.")) {
			query += "$";
		}

		return '^' + query;
	}

	private static boolean search(String query, double approximateSize) throws SQLException {
		for (Database openDatabase : openDatabases) {
			List<FileEntry> hits = openDatabase.fileEntryTable.getByNameRegex(query);
			for (FileEntry hit : hits) {
				double sizeDifference = Math.abs(hit.size() / 1048576.0 - approximateSize);
				if (sizeDifference <= 0.005) {
					return true;
				}
			}
		}

		return false;
	}
}
