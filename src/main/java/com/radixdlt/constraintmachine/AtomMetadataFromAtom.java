package com.radixdlt.constraintmachine;

import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.crypto.CryptoException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Helper for implementing {@link AtomMetadata} given an Atom of interest
 */
public class AtomMetadataFromAtom implements AtomMetadata {
	private final ImmutableAtom atom;
	private final Map<RadixAddress, Boolean> isSignedByCache = new HashMap<>();

	public AtomMetadataFromAtom(ImmutableAtom atom) {
		this.atom = Objects.requireNonNull(atom, "atom is required");
	}

	@Override
	public boolean isSignedBy(RadixAddress address) {
		return this.isSignedByCache.computeIfAbsent(address, this::verifySignedWith);
	}

	private boolean verifySignedWith(RadixAddress address) {
		try {
			return atom.verify(address.getKey());
		} catch (CryptoException e) {
			return false;
		}
	}
}
