package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.ECSignature;
import com.radixdlt.crypto.Hash;
import java.util.Objects;

/**
 * An instruction to be validated by a Constraint Machine
 */
public final class CMInstruction {
	private final ImmutableList<CMMicroInstruction> microInstructions;
	private final Hash witness;
	private final ImmutableMap<EUID, ECSignature> signatures;

	public CMInstruction(
		ImmutableList<CMMicroInstruction> microInstructions,
		Hash witness,
		ImmutableMap<EUID, ECSignature> signatures
	) {
		this.microInstructions = Objects.requireNonNull(microInstructions);
		this.witness = witness;
		this.signatures = Objects.requireNonNull(signatures);
	}

	public ImmutableList<CMMicroInstruction> getMicroInstructions() {
		return microInstructions;
	}

	public Hash getWitness() {
		return witness;
	}

	public ImmutableMap<EUID, ECSignature> getSignatures() {
		return signatures;
	}
}
