package com.radixdlt.consensus;

import com.google.inject.Inject;
import com.radixdlt.common.Atom;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class DumbMemPool implements MemPool {
	private final BlockingDeque<Atom> parkedAtoms;

	@Inject
	public DumbMemPool() {
		this.parkedAtoms = new LinkedBlockingDeque<>();
	}

	@Override
	public List<Atom> getAtoms(int count) {
		if (count != 1) {
			throw new IllegalArgumentException();
		}

		Atom atom = parkedAtoms.poll();
		if (atom == null) {
			return Collections.emptyList();
		}

		return Collections.singletonList(atom);
	}


	@Override
	public void addAtom(Atom atom) {
		parkedAtoms.add(atom);
	}
}
