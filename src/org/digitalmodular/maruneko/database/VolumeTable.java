package org.digitalmodular.maruneko.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.jetbrains.annotations.Nullable;

import static org.digitalmodular.utilities.StringValidatorUtilities.requireStringLengthAtLeast;
import static org.digitalmodular.utilities.ValidatorUtilities.requireAtLeast;

import static org.digitalmodular.maruneko.diskscanner.DiskScanner.breakpoint;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-14
public final class VolumeTable extends AbstractTable<Volume> {
	public static final String TABLE_NAME = "Volume";

	private final PreparedStatement insertStatement;
	private final PreparedStatement selectStatementID;
	private final PreparedStatement selectStatementMaxID;
	private final PreparedStatement selectStatementMountPoint;

	public static void createTable(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(Database.QUERY_TIMEOUT);

			statement.executeUpdate("CREATE TABLE IF NOT EXISTS Volume (" +
			                        "id                 INTEGER PRIMARY KEY," +
			                        "mountPoint         TEXT    NOT NULL CHECK(LENGTH(mountPoint) > 0) UNIQUE," +
			                        "device             TEXT    NOT NULL CHECK(LENGTH(device) > 0)," +
			                        "fileSystem         TEXT    NOT NULL CHECK(LENGTH(fileSystem) > 0)," +
			                        "blockSize          INTEGER NOT NULL CHECK(blockSize > 0)," +
			                        "firstSeenTimestamp INTEGER NOT NULL," +
			                        "lastSeenTimestamp  INTEGER NOT NULL)");
		}
	}

	public VolumeTable(Connection connection) throws SQLException {
		super(connection, TABLE_NAME);

		insertStatement = connection.prepareStatement("REPLACE INTO Volume VALUES (?,?,?,?,?,?,?)");
		insertStatement.setQueryTimeout(Database.QUERY_TIMEOUT);
		selectStatementID = connection.prepareStatement("SELECT * FROM Volume WHERE id=?");
		selectStatementID.setQueryTimeout(Database.QUERY_TIMEOUT);
		selectStatementMaxID = connection.prepareStatement("SELECT MAX(id) FROM Volume");
		selectStatementMaxID.setQueryTimeout(Database.QUERY_TIMEOUT);
		selectStatementMountPoint = connection.prepareStatement("SELECT * FROM Volume WHERE mountPoint=?");
		selectStatementMountPoint.setQueryTimeout(Database.QUERY_TIMEOUT);
	}

	@Override
	protected Volume constructValue(ResultSet resultSet) throws SQLException {
		return new Volume(resultSet.getInt("id"),
		                  resultSet.getString("mountPoint"),
		                  resultSet.getString("device"),
		                  resultSet.getString("fileSystem"),
		                  resultSet.getInt("blockSize"),
		                  resultSet.getLong("firstSeenTimestamp"),
		                  resultSet.getLong("lastSeenTimestamp"));
	}

	@Override
	public Volume addValue(Volume value) throws SQLException {
		int    id                 = value.id();
		String mountPoint         = value.mountPoint();
		String device             = value.device();
		String fileSystem         = value.fileSystem();
		int    blockSize          = value.blockSize();
		long   firstSeenTimestamp = value.firstSeenTimestamp();
		long   lastSeenTimestamp  = value.lastSeenTimestamp();

		if (id == 0) {
			id = getMaxID(0) + 1;
			value = new Volume(id, mountPoint, device, fileSystem, blockSize, firstSeenTimestamp, lastSeenTimestamp);
		}

		insertStatement.setObject(1, id);
		insertStatement.setObject(2, mountPoint);
		insertStatement.setObject(3, device);
		insertStatement.setObject(4, fileSystem);
		insertStatement.setObject(5, blockSize);
		insertStatement.setObject(6, firstSeenTimestamp);
		insertStatement.setObject(7, lastSeenTimestamp);
		insertStatement.executeUpdate();
		insertStatement.clearParameters();

		return value;
	}

	public Volume getByID(int id) throws SQLException {
		requireAtLeast(1, id, "id");

		@Nullable Volume volume = getTableEntry(selectStatementID, id);
		if (volume == null) {
			throw new SQLException("Volume with this id doesn't exist: " + id);
		}

		return volume;
	}

	public int getMaxID(int defaultValue) throws SQLException {
		@Nullable Long maxID = getLong(selectStatementMaxID);
		return maxID == null ? defaultValue : maxID.intValue();
	}

	public @Nullable Volume getByMountPoint(String mountPoint) throws SQLException {
		requireStringLengthAtLeast(1, mountPoint, "mountPoint");

		return getTableEntry(selectStatementMountPoint, mountPoint);
	}

	public Volume updateValue(Volume volume) throws SQLException {
		@Nullable Volume existing = getByMountPoint(volume.mountPoint());

		if (existing != null) {
			if (volume.id() > 0) {
				breakpoint(); // Duplicate entry, which is not allowed
			}

			volume = new Volume(existing.id(),
			                    volume.mountPoint(),
			                    volume.device(),
			                    volume.fileSystem(),
			                    volume.blockSize(),
			                    existing.firstSeenTimestamp(),
			                    volume.lastSeenTimestamp());
		}

		return addValue(volume);
	}
}
