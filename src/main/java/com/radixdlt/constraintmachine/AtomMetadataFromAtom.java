package com.radixdlt.constraintmachine;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.crypto.Hash;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Helper for implementing {@link AtomMetadata} given an Atom of interest
 */
public class AtomMetadataFromAtom implements AtomMetadata {
	private final CMAtom atom;
	private final Map<RadixAddress, Boolean> isSignedByCache = new HashMap<>();

	public AtomMetadataFromAtom(CMAtom atom) {
		this.atom = Objects.requireNonNull(atom, "atom is required");
	}

	@Override
	public boolean isSignedBy(RadixAddress address) {
		return this.isSignedByCache.computeIfAbsent(address, this::verifySignedWith);
	}

	private boolean verifySignedWith(RadixAddress address) {
		if (atom.getSignatures().isEmpty()) {
			return false;
		}

		final Hash hash = atom.getAtomHash();
		final ECSignature signature = atom.getSignatures().get(address.getKey().getUID());
		return signature != null && address.getKey().verify(hash, signature);
	}
}
