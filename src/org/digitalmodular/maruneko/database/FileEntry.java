package org.digitalmodular.maruneko.database;

import java.sql.SQLException;

import static org.digitalmodular.utilities.StringValidatorUtilities.requireStringLengthAtLeast;
import static org.digitalmodular.utilities.ValidatorUtilities.requireAtLeast;
import static org.digitalmodular.utilities.ValidatorUtilities.requireNonNull;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-14
public record FileEntry(Database database,
                        int id,
                        int parentID,
                        String name,
                        int volumeID,
                        int fileTypeID,
                        long size,
                        long creationTimestamp,
                        long modificationTimestamp,
                        long accessTimestamp,
                        long firstSeenTimestamp,
                        long lastSeenTimestamp
                        // crc32?
                        // comment?
                        // attributes/permissions?
                        // hash?
                        // hashedTimestamp?
                        // categories (using an XTable)?
) {
	public FileEntry {
		requireNonNull(database, "database");
		requireAtLeast(0, id, "id");
		requireAtLeast(0, parentID, "parentID");
		requireStringLengthAtLeast(1, name, "name");
		requireAtLeast(1, volumeID, "volumeID");
		requireAtLeast(1, fileTypeID, "fileTypeID");
		requireAtLeast(0, size, "size");
	}

	public String getFullPath() {
		StringBuilder path = new StringBuilder(288).append(name);

		try {
			FileEntry entry = this;
			while (entry.parentID() > 0) {
				entry = database.fileEntryTable.getByID(entry.parentID());
				if (entry == null) {
					path.insert(0, "<null>/");
					break;
				}

				String name = entry.name();
				if (name.equals("/")) {
					path.insert(0, '/');
				} else {
					path.insert(0, name + '/');
				}
			}
		} catch (SQLException ex) {
			path.insert(0, ex.getClass().getSimpleName() + '(' + ex.getMessage() + ")/");
		}

		return path.toString();
	}
}
