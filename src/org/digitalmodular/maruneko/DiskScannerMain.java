package org.digitalmodular.maruneko;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

import org.digitalmodular.maruneko.database.Database;
import org.digitalmodular.maruneko.database.FileEntry;
import org.digitalmodular.maruneko.diskscanner.DiskScanner;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-14
public class DiskScannerMain {
	public static void main(String... args) throws IOException, SQLException {
		scan(Paths.get("/"), Paths.get("root.maru"));
		scan(Paths.get("/home"), Paths.get("home.maru"));
	}

	private static void scan(Path start, Path maruFile) throws IOException, SQLException {
		if (Files.exists(maruFile)) {
			Files.move(maruFile, Paths.get(maruFile + ".bak"));
		}

		Database database = new Database(maruFile, true);

		FileEntry firstEntry = new DiskScanner(database).scan(start);

		System.out.println("Done:");
		System.out.println(firstEntry);
	}
}
