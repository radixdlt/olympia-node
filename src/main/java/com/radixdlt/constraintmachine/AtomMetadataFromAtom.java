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
	private final CMInstruction cmInstruction;
	private final Map<RadixAddress, Boolean> isSignedByCache = new HashMap<>();

	public AtomMetadataFromAtom(CMInstruction cmInstruction) {
		this.cmInstruction = Objects.requireNonNull(cmInstruction, "instruction is required");
	}

	@Override
	public boolean isSignedBy(RadixAddress address) {
		return this.isSignedByCache.computeIfAbsent(address, this::verifySignedWith);
	}

	private boolean verifySignedWith(RadixAddress address) {
		if (cmInstruction.getSignatures().isEmpty()) {
			return false;
		}

		final Hash hash = cmInstruction.getAtomHash();
		final ECSignature signature = cmInstruction.getSignatures().get(address.getKey().getUID());
		return signature != null && address.getKey().verify(hash, signature);
	}
}
