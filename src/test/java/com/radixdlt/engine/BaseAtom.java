package com.radixdlt.engine;

import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.crypto.Hash;
import java.util.Objects;

/**
 * Simple atom used for testing
 */
public final class BaseAtom implements RadixEngineAtom {
	private final CMInstruction cmInstruction;
	private final Hash witness;

	public BaseAtom(CMInstruction cmInstruction, Hash witness) {
		this.cmInstruction = Objects.requireNonNull(cmInstruction);
		this.witness = Objects.requireNonNull(witness);
	}

	@Override
	public CMInstruction getCMInstruction() {
		return cmInstruction;
	}

	@Override
	public Hash getWitness() {
		return witness;
	}
}
