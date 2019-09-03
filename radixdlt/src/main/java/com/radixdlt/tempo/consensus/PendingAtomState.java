package com.radixdlt.tempo.consensus;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PendingAtomState {
	private final Map<AID, PendingAtom> pendingAtoms = new ConcurrentHashMap<>();

	public void put(TempoAtom atom, Set<LedgerIndex> indices) {
		this.pendingAtoms.put(atom.getAID(), new PendingAtom(atom, indices));
	}

	public void remove(AID aid) {
		this.pendingAtoms.remove(aid);
	}

	public boolean isPending(AID aid) {
		return pendingAtoms.containsKey(aid);
	}

	public void forEachPending(Consumer<TempoAtom> consumer) {
		pendingAtoms.forEach(((aid, pendingAtom) -> consumer.accept(pendingAtom.getAtom())));
	}

	public Set<LedgerIndex> getUniqueIndices(AID aid) {
		PendingAtom pendingAtom = pendingAtoms.get(aid);
		if (pendingAtom == null) {
			throw new TempoException("Pending atom '" + aid + " does not exist");
		}
		return pendingAtom.getUniqueIndices();
	}

	private static final class PendingAtom {
		private final TempoAtom atom;
		private final Set<LedgerIndex> uniqueIndices;

		private PendingAtom(TempoAtom atom, Set<LedgerIndex> uniqueIndices) {
			this.atom = atom;
			this.uniqueIndices = uniqueIndices;
		}

		private TempoAtom getAtom() {
			return atom;
		}

		private Set<LedgerIndex> getUniqueIndices() {
			return uniqueIndices;
		}
	}
}
