package org.digitalmodular.maruneko.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.jetbrains.annotations.Nullable;

import static org.digitalmodular.utilities.StringValidatorUtilities.requireStringLengthAtLeast;
import static org.digitalmodular.utilities.ValidatorUtilities.requireAtLeast;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-14
public final class FileTypeTable extends AbstractTable<FileType> {
	public static final String TABLE_NAME = "FileType";

	private final PreparedStatement insertStatement;
	private final PreparedStatement selectStatementID;
	private final PreparedStatement selectStatementName;

	public static void createTable(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(Database.QUERY_TIMEOUT);

			statement.executeUpdate("CREATE TABLE IF NOT EXISTS FileType (" +
			                        "id   INTEGER NOT NULL PRIMARY KEY," +
			                        "name TEXT    NOT NULL CHECK(LENGTH(name) > 0) UNIQUE)");

		}
	}

	public FileTypeTable(Connection connection) throws SQLException {
		super(connection, TABLE_NAME);

		insertStatement = connection.prepareStatement("REPLACE INTO FileType VALUES (?,?)");
		insertStatement.setQueryTimeout(Database.QUERY_TIMEOUT);
		selectStatementID = connection.prepareStatement("SELECT * FROM FileType WHERE id=?");
		selectStatementID.setQueryTimeout(Database.QUERY_TIMEOUT);
		selectStatementName = connection.prepareStatement("SELECT * FROM FileType WHERE name=?");
		selectStatementName.setQueryTimeout(Database.QUERY_TIMEOUT);

		addDefaults();
	}

	@Override
	protected FileType constructValue(ResultSet resultSet) throws SQLException {
		return new FileType(resultSet.getInt("id"),
		                    resultSet.getString("name"));
	}

	private void addDefaults() throws SQLException {
		addValue(FileType.DIRECTORY);
		addValue(FileType.REGULAR_FILE);
		addValue(FileType.SYMLINK);
		addValue(FileType.OTHER);
	}

	@Override
	public FileType addValue(FileType value) throws SQLException {
		insertStatement.setObject(1, value.id());
		insertStatement.setObject(2, value.name());
		insertStatement.executeUpdate();
		insertStatement.clearParameters();

		return value;
	}

	public @Nullable FileType getByID(int id) throws SQLException {
		requireAtLeast(1, id, "id");

		return getTableEntry(selectStatementID, id);
	}

	public @Nullable FileType getByName(String name) throws SQLException {
		requireStringLengthAtLeast(1, name, "name");

		return getTableEntry(selectStatementName, name);
	}
}
