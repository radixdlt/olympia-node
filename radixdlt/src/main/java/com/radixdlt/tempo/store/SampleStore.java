package com.radixdlt.tempo.store;

import com.radixdlt.common.AID;
import org.radix.time.TemporalProof;

import java.util.Optional;

public interface SampleStore extends Store {
	Optional<TemporalProof> getCollected(AID aid);

	Optional<TemporalProof> getLocal(AID aid);

	void addLocal(TemporalProof temporalProof);

	void addCollected(TemporalProof temporalProof);
}
