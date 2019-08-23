package com.radixdlt.atomos;

import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.engine.RadixEngineAtom;

public final class SimpleRadixEngineAtom implements RadixEngineAtom {
	private final CMInstruction	cmInstruction;
	private final ImmutableAtom atom;

	public SimpleRadixEngineAtom(ImmutableAtom atom, CMInstruction cmInstruction) {
		this.atom = atom;
		this.cmInstruction = cmInstruction;
	}

	@Override
	public CMInstruction getCMInstruction() {
		return cmInstruction;
	}

	public ImmutableAtom getAtom() {
		return atom;
	}
}
