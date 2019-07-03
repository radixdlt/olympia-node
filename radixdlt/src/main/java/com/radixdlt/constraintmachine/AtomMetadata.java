package com.radixdlt.constraintmachine;

import java.util.function.Predicate;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atoms.Particle;

/**
 * Metadata of a relevant Atom
 * TODO: Move this logic into OS layer
 */
public interface AtomMetadata {
	/**
	 * Whether a certain address has signed the containing Atom
	 * @param address The address
	 * @return whether the given address has signed the Atom of interest
	 */
	boolean isSignedBy(RadixAddress address);

	/**
	 * Check whether the atom contains the given particle predicate
	 * @param predicate particle particle predicate to check for
	 * @return whether the given particles is contained in the Atom
	 */
	boolean contains(Predicate<Particle> predicate);
}
