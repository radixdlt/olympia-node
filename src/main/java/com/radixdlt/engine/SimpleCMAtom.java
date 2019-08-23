package com.radixdlt.engine;

import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.crypto.Hash;

public class SimpleCMAtom implements CMAtom {
	private final CMInstruction	cmInstruction;
	private final Hash powFeeHash;
	private final Hash atomHash;
	private final ImmutableAtom atom;

	public SimpleCMAtom(ImmutableAtom atom, CMInstruction cmInstruction) {
		this.atom = atom;
		this.cmInstruction = cmInstruction;

		this.atomHash = atom.getHash();
		this.powFeeHash = atom.copyExcludingMetadata(ImmutableAtom.METADATA_POW_NONCE_KEY).getHash();
	}

	@Override
	public CMInstruction getCMInstruction() {
		return cmInstruction;
	}

	public ImmutableAtom getAtom() {
		return atom;
	}

	public Hash getPowFeeHash() {
		return powFeeHash;
	}

	public Hash getAtomHash() {
		return atomHash;
	}
}
