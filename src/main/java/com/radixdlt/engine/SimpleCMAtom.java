package com.radixdlt.engine;

import com.radixdlt.constraintmachine.CMInstruction;

public class SimpleCMAtom implements CMAtom {
	private final CMInstruction	cmInstruction;

	public SimpleCMAtom(CMInstruction cmInstruction) {
		this.cmInstruction = cmInstruction;
	}

	@Override
	public CMInstruction getCMInstruction() {
		return cmInstruction;
	}
}
