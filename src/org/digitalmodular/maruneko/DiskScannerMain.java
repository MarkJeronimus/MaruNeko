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
//		FileSystem     fs    = FileSystems.getDefault();
//		Iterable<Path> roots = fs.getRootDirectories();
//		for (FileStore store : fs.getFileStores()) {
//			Iterator<Path> i = roots.iterator();
//			while (i.hasNext()) {
//				if (store.name().startsWith(i.next().toString())) {
//					System.out.println(
//							"Name: " + store.name() + ", Read only: " + store.isReadOnly() + ", Type: " + store.type() +
//							", Block: " + store.getBlockSize() + " Bytes");
//				}
//			}
//		}
//		System.out.println();
//		System.out.println();

		scan(Paths.get("/"), Paths.get("root.maru"));
		scan(Paths.get("/home"), Paths.get("home.maru"));
	}

	private static void scan(Path start, Path maruFile) throws IOException, SQLException {
		if (Files.exists(maruFile)) {
			Files.delete(maruFile);
		}

		Database database = new Database(maruFile, true);

		FileEntry firstEntry = new DiskScanner(database).scan(start);

		System.out.println();
		System.out.println("firstEntry = " + firstEntry);
		System.out.println("start      = " + start);
		System.out.println("maruFile   = " + maruFile);
	}
}
