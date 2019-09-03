package com.radixdlt.tempo.consensus;

import com.radixdlt.common.AID;
import com.radixdlt.tempo.AtomObserver;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.store.SampleStore;

public final class ConsensusController implements AtomObserver {
	private final SampleStore sampleStore;

	public ConsensusController(SampleStore sampleStore) {
		this.sampleStore = sampleStore;
	}

	@Override
	public void onAdopted(TempoAtom atom) {
		sampleStore.add(atom.getTemporalProof());
	}

	@Override
	public void onDeleted(AID aid) {

	}
}
