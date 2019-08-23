package com.radixdlt.engine;

import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.constraintmachine.CMInstruction;

public class SimpleCMAtom implements CMAtom {
	private final CMInstruction	cmInstruction;
	private final ImmutableAtom atom;

	public SimpleCMAtom(ImmutableAtom atom, CMInstruction cmInstruction) {
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
