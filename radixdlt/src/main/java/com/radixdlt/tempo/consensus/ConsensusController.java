package com.radixdlt.tempo.consensus;

import com.google.inject.Inject;
import com.radixdlt.common.AID;
import com.radixdlt.tempo.AtomObserver;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.store.ConsensusStore;
import com.radixdlt.tempo.store.SampleStore;

import java.util.Objects;

public final class ConsensusController implements AtomObserver {
	private final ConsensusStore consensusStore;
	private final SampleStore sampleStore;

	@Inject
	public ConsensusController(
		ConsensusStore consensusStore,
		SampleStore sampleStore
	) {
		this.consensusStore = Objects.requireNonNull(consensusStore);
		this.sampleStore = Objects.requireNonNull(sampleStore);
	}

	@Override
	public void onAdopted(TempoAtom atom) {
		sampleStore.add(atom.getTemporalProof());
	}

	@Override
	public void onDeleted(AID aid) {
		consensusStore.delete(aid);
	}
}
