package com.radixdlt.constraintmachine;

import com.radixdlt.crypto.ECPublicKey;

/**
 * Metadata of a relevant Atom
 * TODO: Move this logic into OS layer
 */
public interface WitnessData {
	/**
	 * Whether a certain address has signed the containing Atom
	 * @param publicKey The public key
	 * @return whether the given address has signed the Atom of interest
	 */
	boolean isSignedBy(ECPublicKey publicKey);
}
