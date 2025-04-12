package org.digitalmodular.maruneko.database;

import static org.digitalmodular.utilities.StringValidatorUtilities.requireStringLengthAtLeast;
import static org.digitalmodular.utilities.ValidatorUtilities.requireAtLeast;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-14
public record FileType(int id,
                       String name) {
	public static final FileType DIRECTORY    = new FileType(1, "directory");
	public static final FileType REGULAR_FILE = new FileType(2, "file");
	public static final FileType SYMLINK      = new FileType(3, "symlink");
	public static final FileType OTHER        = new FileType(4, "other");

	public FileType {
		requireAtLeast(1, id, "id");
		requireStringLengthAtLeast(1, name, "name");
	}
}
