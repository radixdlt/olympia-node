package com.radixdlt.middleware;

import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.engine.RadixEngineAtom;

public final class SimpleRadixEngineAtom implements RadixEngineAtom {
	private final CMInstruction	cmInstruction;
	private final Atom atom;

	public SimpleRadixEngineAtom(Atom atom, CMInstruction cmInstruction) {
		this.atom = atom;
		this.cmInstruction = cmInstruction;
	}

	@Override
	public CMInstruction getCMInstruction() {
		return cmInstruction;
	}

	public Atom getAtom() {
		return atom;
	}
}
