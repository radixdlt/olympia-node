package com.radixdlt.tempo;

import com.radixdlt.common.AID;
import org.radix.atoms.Atom;

import java.util.Optional;

/**
 * A read-only view of a specific AtomStore
 */
public interface AtomStoreView {
	/**
	 * Checks whether the given aid is contained in this view
	 * @param aid The aid
	 * @return Whether the given aid is contained in this view
	 */
	boolean contains(AID aid);

	/**
	 * Gets the atom associated with a certain aid
	 * @param aid The aid
	 * @return The atom associated with the given aid (if any)
	 */
	Optional<Atom> get(AID aid);
}
