package com.radixdlt.constraintmachine;

import com.radixdlt.atomos.RadixAddress;

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
}
