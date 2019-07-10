package org.radix.universe;

import com.radixdlt.universe.Universe;
import org.radix.atoms.Atom;

public final class UniverseValidator {
	public static void validate(Universe universe) {
		// Check signature
		if (!universe.getCreator().verify(universe.getHash(), universe.getSignature())) {
			throw new IllegalStateException("Invalid universe signature");
		}

		// Check if it has a temporal proof (RLAU-467)
		boolean missingTemporalProofs = universe.getGenesis().stream()
			.anyMatch(atom -> !(atom instanceof Atom) || ((Atom) atom).getTemporalProof().isEmpty());

		if (missingTemporalProofs) {
			throw new IllegalStateException("All atoms in genesis need to have non-empty temporal proofs");
		}
	}
}
