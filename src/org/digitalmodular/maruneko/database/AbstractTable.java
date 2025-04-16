package org.digitalmodular.maruneko.database;

import java.security.InvalidParameterException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import static org.digitalmodular.utilities.CollectionValidatorUtilities.requireValuesNonNull;
import static org.digitalmodular.utilities.StringValidatorUtilities.requireStringLengthAtLeast;
import static org.digitalmodular.utilities.ValidatorUtilities.requireNonNull;

/**
 * @param <V> The type that wraps rows of this table.
 * @author Mark Jeronimus
 */
// Created 2022-02-02
public abstract class AbstractTable<V> {
	private final Connection connection;
	private final String     tableName;

	private final PreparedStatement selectAllStatement;

	protected AbstractTable(Connection connection, String tableName) throws SQLException {
		this.connection = requireNonNull(connection, "connection");
		this.tableName  = requireStringLengthAtLeast(1, tableName, "tableName");

		selectAllStatement = connection.prepareStatement("SELECT * FROM " + tableName);
		selectAllStatement.setQueryTimeout(Database.QUERY_TIMEOUT);
	}

	protected Connection getConnection() {
		return connection;
	}

	public String getTableName() {
		return tableName;
	}

	protected abstract V constructValue(ResultSet resultSet) throws SQLException;

	protected void addValues(Iterable<V> values) throws SQLException {
		requireValuesNonNull(values, "values");

		for (V value : values) {
			addValue(value);
		}
	}

	public List<V> getAll() throws SQLException {
		return getTableEntries(selectAllStatement);
	}

	public abstract V addValue(V value) throws SQLException;

	protected static @Nullable Long getLong(PreparedStatement selectStatement, Object... parameters)
			throws SQLException {
		for (int i = 0; i < parameters.length; i++) {
			Object parameter = parameters[i];
			selectStatement.setObject(i + 1, parameter);
		}

		@Nullable Long result = null;

		try (ResultSet resultSet = selectStatement.executeQuery()) {
			while (resultSet.next()) {
				if (result != null) {
					throw new InvalidParameterException("Query returned multiple rows.");
				}

				result = resultSet.getLong(1);
			}
		}

		selectStatement.clearParameters();

		return result;
	}

	protected static List<Integer> getInts(PreparedStatement selectStatement, Object... parameters)
			throws SQLException {
		for (int i = 0; i < parameters.length; i++) {
			Object parameter = parameters[i];
			selectStatement.setObject(i + 1, parameter);
		}

		List<Integer> list = new ArrayList<>(1024);

		try (ResultSet resultSet = selectStatement.executeQuery()) {
			while (resultSet.next()) {
				list.add(resultSet.getInt(1));
			}
		}

		selectStatement.clearParameters();

		return list;
	}

	protected static List<String> getStrings(PreparedStatement selectStatement, Object... parameters)
			throws SQLException {
		for (int i = 0; i < parameters.length; i++) {
			Object parameter = parameters[i];
			selectStatement.setObject(i + 1, parameter);
		}

		List<String> list = new ArrayList<>(1024);

		try (ResultSet resultSet = selectStatement.executeQuery()) {
			while (resultSet.next()) {
				list.add(resultSet.getString(1));
			}
		}

		selectStatement.clearParameters();

		return list;
	}

	protected @Nullable V getTableEntry(PreparedStatement selectStatement, Object... parameters) throws SQLException {
		for (int i = 0; i < parameters.length; i++) {
			Object parameter = parameters[i];
			selectStatement.setObject(i + 1, parameter);
		}

		@Nullable V result = null;

		try (ResultSet resultSet = selectStatement.executeQuery()) {
			while (resultSet.next()) {
				if (result != null) {
					throw new InvalidParameterException("Query returned multiple rows.");
				}

				result = constructValue(resultSet);
			}
		}

		selectStatement.clearParameters();

		return result;
	}

	protected List<V> getTableEntries(PreparedStatement selectStatement, Object... parameters) throws SQLException {
		for (int i = 0; i < parameters.length; i++) {
			Object parameter = parameters[i];
			selectStatement.setObject(i + 1, parameter);
		}

		List<V> list = new ArrayList<>(1024);

		try (ResultSet resultSet = selectStatement.executeQuery()) {
			while (resultSet.next()) {
				list.add(constructValue(resultSet));
			}
		}

		selectStatement.clearParameters();

		return list;
	}
}
