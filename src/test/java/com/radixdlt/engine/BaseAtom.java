package com.radixdlt.engine;

import com.radixdlt.constraintmachine.CMInstruction;
import java.util.Objects;

public class BaseAtom implements RadixEngineAtom {
	private final CMInstruction cmInstruction;

	BaseAtom(CMInstruction cmInstruction) {
		this.cmInstruction = Objects.requireNonNull(cmInstruction);
	}

	@Override
	public CMInstruction getCMInstruction() {
		return cmInstruction;
	}
}
