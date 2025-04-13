package org.digitalmodular.maruneko.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Pattern;

import org.digitalmodular.utilities.FileUtilities;
import static org.digitalmodular.utilities.ValidatorUtilities.requireNonNull;

import org.sqlite.Function;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * @author Zom-G
 */
// Created 2022-02-02
@SuppressWarnings("PublicField")
public class Database {
	public static final int QUERY_TIMEOUT = 1;

	private final Path       file;
	private final Connection connection;

	public final VolumeTable    volumeTable;
	public final FileTypeTable  fileTypeTable;
	public final FileEntryTable fileEntryTable;

	public Database(Path file, boolean forWriting) throws IOException, SQLException {
		this.file = requireNonNull(file, "file");
		System.out.println("Opening Database " + file);

		if (forWriting) {
			cycleBackups(file);
		} else if (!Files.exists(file)) {
			throw new IOException("File doesn't exist: " + file);
		}

		SQLiteConfig config = new SQLiteConfig();
		config.enforceForeignKeys(true);
		SQLiteDataSource dataSource = new SQLiteDataSource(config);
		dataSource.setUrl("jdbc:sqlite:" + file);
		connection = dataSource.getConnection();
		connection.setAutoCommit(false);

		Function.create(connection, "REGEXP", new Function() {
			@Override
			protected void xFunc() throws SQLException {
				String expression = value_text(0);
				String value      = value_text(1);
				if (value == null) {
					value = "";
				}

				Pattern pattern = Pattern.compile(expression);
				result(pattern.matcher(value.toLowerCase()).find() ? 1 : 0);
			}
		});

		try {
			VolumeTable.createTable(connection);
			FileTypeTable.createTable(connection);
			FileEntryTable.createTable(connection);

			volumeTable = new VolumeTable(connection);
			fileTypeTable = new FileTypeTable(connection);
			fileEntryTable = new FileEntryTable(this, connection, fileTypeTable);
		} finally {
			connection.commit();
		}
	}

	private static void cycleBackups(Path file) throws IOException {
		file = file.toAbsolutePath();

		Path     dir   = file.getParent();
		String[] parts = FileUtilities.splitDirFileExt(file.toString());

		Path bak1 = dir.resolve(parts[1] + ".bak." + parts[2]);
		Path bak2 = dir.resolve(parts[1] + ".bak2." + parts[2]);

		if (Files.exists(file)) {
			if (Files.exists(bak1)) {
				try {
					Files.delete(bak2);
				} catch (NoSuchFileException ignored) {
				}

				Files.move(bak1, bak2, StandardCopyOption.ATOMIC_MOVE);
			}

			Files.move(file, bak1, StandardCopyOption.ATOMIC_MOVE);
		}
	}

	public void commit() throws SQLException {
		connection.commit();
	}

	public void close() throws SQLException {
		connection.close();
	}

	@Override
	public String toString() {
		return file.toString();
	}
}
