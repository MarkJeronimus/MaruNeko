package org.digitalmodular.maruneko.database;

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
}
