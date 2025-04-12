package org.digitalmodular.maruneko.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import static org.digitalmodular.utilities.StringValidatorUtilities.requireStringNotEmpty;
import static org.digitalmodular.utilities.ValidatorUtilities.requireAtLeast;
import static org.digitalmodular.utilities.ValidatorUtilities.requireNonNull;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-14
public final class FileEntryTable extends AbstractTable<FileEntry> {
	public static final String TABLE_NAME = "FileEntry";

	private final Database database;

	private final FileTypeTable fileTypeTable;

	private final PreparedStatement insertStatement;
	private final PreparedStatement selectStatementMaxID;
	private final PreparedStatement selectStatementID;
	private final PreparedStatement deleteStatementID;
	private final PreparedStatement selectStatementParentID;
	private final PreparedStatement selectStatementParentCount;
	private final PreparedStatement selectStatementParentIDAndName;
	private final PreparedStatement selectStatementNameAndType;
	private final PreparedStatement selectStatementNameTypeSize;
	private final PreparedStatement selectStatementVolumeIDFirst;
	private final PreparedStatement selectStatementTypeAndSize;

	private volatile @Nullable PreparedStatement selectStatementNameRegex = null;

	private int maxEntry;

	public static void createTable(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(Database.QUERY_TIMEOUT);

			statement.executeUpdate("CREATE TABLE IF NOT EXISTS FileEntry (" +
			                        "id                    INTEGER NOT NULL CHECK(id > 0) PRIMARY KEY," +
			                        "parentID              INTEGER          CHECK(parentID >= 0)," +
			                        "name                  TEXT    NOT NULL CHECK(LENGTH(name) > 0)," +
			                        "volumeID              INTEGER NOT NULL CHECK(volumeID > 0)," +
			                        "fileTypeID            INTEGER NOT NULL," +
			                        "size                  INTEGER NOT NULL CHECK(size >= 0)," +
			                        "creationTimestamp     INTEGER NOT NULL," +
			                        "modificationTimestamp INTEGER NOT NULL," +
			                        "accessTimestamp       INTEGER NOT NULL," +
			                        "firstSeenTimestamp INTEGER NOT NULL," +
			                        "lastSeenTimestamp  INTEGER NOT NULL," +
			                        "CONSTRAINT pn UNIQUE (parentID, name)," +
			                        "FOREIGN KEY (parentID) REFERENCES FileEntry(id)," +
			                        "FOREIGN KEY (fileTypeID) REFERENCES " + FileTypeTable.TABLE_NAME + "(id))");
		}
	}

	public FileEntryTable(Database database, Connection connection, FileTypeTable fileTypeTable) throws SQLException {
		super(connection, TABLE_NAME);
		this.database = requireNonNull(database, "database");
		this.fileTypeTable = requireNonNull(fileTypeTable, "fileTypeTable");

		insertStatement = connection.prepareStatement("REPLACE INTO FileEntry VALUES (?,?,?,?,?,?,?,?,?,?,?)");
		insertStatement.setQueryTimeout(Database.QUERY_TIMEOUT);
		selectStatementMaxID = connection.prepareStatement("SELECT MAX(id) FROM FileEntry");
		selectStatementMaxID.setQueryTimeout(Database.QUERY_TIMEOUT);
		selectStatementID = connection.prepareStatement(
				"SELECT * FROM FileEntry WHERE id=?");
		selectStatementID.setQueryTimeout(Database.QUERY_TIMEOUT);
		deleteStatementID = connection.prepareStatement(
				"DELETE FROM FileEntry WHERE id=?");
		deleteStatementID.setQueryTimeout(Database.QUERY_TIMEOUT);
		selectStatementParentID = connection.prepareStatement(
				"SELECT * FROM FileEntry WHERE parentID=? ORDER BY name");
		selectStatementParentID.setQueryTimeout(Database.QUERY_TIMEOUT);
		selectStatementParentCount = connection.prepareStatement(
				"SELECT COUNT(*) FROM FileEntry WHERE parentID=?");
		selectStatementParentCount.setQueryTimeout(Database.QUERY_TIMEOUT);
		selectStatementParentIDAndName = connection.prepareStatement(
				"SELECT * FROM FileEntry WHERE IFNULL(parentID,'')=? AND name=?");
		selectStatementParentIDAndName.setQueryTimeout(Database.QUERY_TIMEOUT);
		selectStatementNameAndType = connection.prepareStatement(
				"SELECT * FROM FileEntry WHERE name=? AND fileTypeID=?");
		selectStatementNameAndType.setQueryTimeout(Database.QUERY_TIMEOUT);
		selectStatementNameTypeSize = connection.prepareStatement(
				"SELECT * FROM FileEntry WHERE name=? AND fileTypeID=? AND size=?");
		selectStatementNameTypeSize.setQueryTimeout(Database.QUERY_TIMEOUT);
		selectStatementVolumeIDFirst = connection.prepareStatement(
				"SELECT * FROM FileEntry WHERE volumeID=? LIMIT 1");
		selectStatementVolumeIDFirst.setQueryTimeout(Database.QUERY_TIMEOUT);
		selectStatementTypeAndSize = connection.prepareStatement(
				"SELECT * FROM FileEntry WHERE fileTypeID=? AND size=?");
		selectStatementTypeAndSize.setQueryTimeout(Database.QUERY_TIMEOUT);

		maxEntry = getMaxID();
	}

	@Override
	protected FileEntry constructValue(ResultSet resultSet) throws SQLException {
		int                fileTypeID = resultSet.getInt(5);
		@Nullable FileType fileType   = fileTypeTable.getByID(fileTypeID);
		if (fileType == null) {
			throw new IllegalStateException("fileType with id " + fileTypeID + " not found");
		}

		return new FileEntry(database,
		                     resultSet.getInt(1),
		                     resultSet.getInt(2),
		                     resultSet.getString(3),
		                     resultSet.getInt(4),
		                     fileTypeID,
		                     resultSet.getLong(6),
		                     resultSet.getLong(7),
		                     resultSet.getLong(8),
		                     resultSet.getLong(9),
		                     resultSet.getLong(10),
		                     resultSet.getLong(11));
	}

	@Override
	public FileEntry addValue(FileEntry value) throws SQLException {
		int    id                    = value.id();
		int    parentID              = value.parentID();
		String name                  = value.name();
		int    volumeID              = value.volumeID();
		int    fileTypeID            = value.fileTypeID();
		long   size                  = value.size();
		long   creationTimestamp     = value.creationTimestamp();
		long   modificationTimestamp = value.modificationTimestamp();
		long   accessTimestamp       = value.accessTimestamp();
		long   firstSeenTimestamp    = value.firstSeenTimestamp();
		long   lastSeenTimestamp     = value.lastSeenTimestamp();

		if (id == 0) {
			maxEntry++;
			id = maxEntry;

			value = new FileEntry(database, id,
			                      parentID,
			                      name,
			                      volumeID,
			                      fileTypeID,
			                      size,
			                      creationTimestamp,
			                      modificationTimestamp,
			                      accessTimestamp,
			                      firstSeenTimestamp,
			                      lastSeenTimestamp);
		}

		insertStatement.setObject(1, id);
		insertStatement.setObject(2, parentID == 0 ? null : parentID);
		insertStatement.setObject(3, name);
		insertStatement.setObject(4, volumeID);
		insertStatement.setObject(5, fileTypeID);
		insertStatement.setObject(6, size);
		insertStatement.setObject(7, creationTimestamp);
		insertStatement.setObject(8, modificationTimestamp);
		insertStatement.setObject(9, accessTimestamp);
		insertStatement.setObject(10, firstSeenTimestamp);
		insertStatement.setObject(11, lastSeenTimestamp);
		insertStatement.executeUpdate();
//		insertStatement.clearParameters();

		return value;
	}

	public FileEntry updateValue(FileEntry fileEntry) throws SQLException {
//		@Nullable FileEntry existing = getByParentIDAndName(fileEntry.parentID(), fileEntry.name());

//		if (existing != null) {
//			if (fileEntry.id() > 0) {
//				DiskScanner.breakpoint(); // Duplicate entry, which is not allowed
//			}
//
//			long minCreationTimestamp = Math.min(fileEntry.creationTimestamp(), existing.creationTimestamp());
//			long maxAccessTimestamp   = Math.min(fileEntry.accessTimestamp(), existing.accessTimestamp());
//
//			fileEntry = new FileEntry(existing.id(),
//			                          fileEntry.parentID(),
//			                          fileEntry.name(),
//			                          fileEntry.volumeID(),
//			                          fileEntry.fileTypeID(),
//			                          fileEntry.size(),
//			                          minCreationTimestamp,
//			                          fileEntry.modificationTimestamp(),
//			                          maxAccessTimestamp,
//			                          existing.firstSeenTimestamp(),
//			                          fileEntry.lastSeenTimestamp());
//		}

		return addValue(fileEntry);
	}

	public int getMaxID() throws SQLException {
		if (maxEntry > 0) {
			return maxEntry;
		}

		@Nullable Long maxID = getLong(selectStatementMaxID);
		return maxID == null ? 0 : maxID.intValue();
	}

	public @Nullable FileEntry getByID(int id) throws SQLException {
		requireAtLeast(0, id, "id");

		return getTableEntry(selectStatementID, id);
	}

	public void deleteByID(int id) throws SQLException {
		requireAtLeast(0, id, "id");

		deleteStatementID.setObject(1, id);
		deleteStatementID.execute();
		deleteStatementID.clearParameters();
	}

	public int getParentCount(int parentID) throws SQLException {
		@Nullable Long parentCount = getLong(selectStatementParentCount, parentID);
		return parentCount == null ? 0 : parentCount.intValue();
	}

	public List<FileEntry> getByParentID(int parentID) throws SQLException {
		requireAtLeast(0, parentID, "parentID");

		return getTableEntries(selectStatementParentID, parentID);
	}

	public @Nullable FileEntry getByParentIDAndName(int parentID, String name) throws SQLException {
		requireAtLeast(0, parentID, "parentID");
		requireStringNotEmpty(name, "name");

		return getTableEntry(selectStatementParentIDAndName, parentID == 0 ? "" : parentID, name);
	}

	public List<FileEntry> getByNameRegex(String query) throws SQLException {
		requireStringNotEmpty(query, "query");

		PreparedStatement statement = getConnection().prepareStatement("SELECT * FROM FileEntry WHERE name REGEXP ?");
		selectStatementNameRegex = statement;
		statement.setQueryTimeout(5);

		return getTableEntries(statement, query);
	}

	public List<FileEntry> getByNameAndType(String name, FileType fileType) throws SQLException {
		requireStringNotEmpty(name, "name");
		requireNonNull(fileType, "fileType");

		return getTableEntries(selectStatementNameAndType, name, fileType.id());
	}

	public List<FileEntry> getByNameTypeSize(String name, FileType fileType, long size) throws SQLException {
		requireStringNotEmpty(name, "name");
		requireNonNull(fileType, "fileType");
		requireAtLeast(0, size, "size");

		return getTableEntries(selectStatementNameTypeSize, name, fileType.id(), size);
	}

	public @Nullable FileEntry getFirstByVolumeID(int volumeID) throws SQLException {
		requireAtLeast(0, volumeID, "volumeID");

		return getTableEntry(selectStatementVolumeIDFirst, volumeID);
	}

	public List<FileEntry> getByTypeAndSize(FileType fileType, long size) throws SQLException {
		requireNonNull(fileType, "fileType");
		requireAtLeast(0, size, "size");

		return getTableEntries(selectStatementTypeAndSize, fileType.id(), size);
	}

	public void cancelTransaction() throws SQLException {
		PreparedStatement statement = selectStatementNameRegex;
		if (statement != null) {
			statement.cancel();
			selectStatementNameRegex = null;
		}
	}
}
