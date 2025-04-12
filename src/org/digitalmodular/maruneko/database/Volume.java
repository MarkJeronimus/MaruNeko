package org.digitalmodular.maruneko.database;

import static org.digitalmodular.utilities.StringValidatorUtilities.requireStringLengthAtLeast;
import static org.digitalmodular.utilities.ValidatorUtilities.requireAtLeast;
import static org.digitalmodular.utilities.ValidatorUtilities.requireThat;

/**
 * @author Mark Jeronimus
 */
// Created 2022-11-14
public record Volume(int id,
                     String mountPoint,
                     String device,
                     String fileSystem,
                     int blockSize,
                     long firstSeenTimestamp,
                     long lastSeenTimestamp) {
	public Volume {
		requireStringLengthAtLeast(1, mountPoint, "mountPoint");
		requireStringLengthAtLeast(1, device, "device");
		requireStringLengthAtLeast(1, fileSystem, "fileSystem");
		requireAtLeast(1, blockSize, "blockSize");
		requireThat((blockSize & (blockSize - 1)) == 0, () -> "'blockSize' is not a power of two: " + blockSize);
	}
}
