package com.radixdlt.tempo.sync;

import com.radixdlt.common.EUID;
import com.radixdlt.tempo.AtomSynchroniser;
import org.radix.atoms.Atom;

import java.util.List;

public class TempoAtomSynchroniser implements AtomSynchroniser {
	@Override
	public Atom receive() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<EUID> selectEdges(Atom atom) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void synchronise(Atom atom) {
		throw new UnsupportedOperationException();
	}
}
