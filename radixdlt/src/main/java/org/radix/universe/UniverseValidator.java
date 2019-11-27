package org.radix.universe;

import com.radixdlt.universe.Universe;

public final class UniverseValidator {
	public static void validate(Universe universe) {
		// Check signature
		if (!universe.getCreator().verify(universe.getHash(), universe.getSignature())) {
			throw new IllegalStateException("Invalid universe signature");
		}
	}
}
