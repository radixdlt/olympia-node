package com.radixdlt.tempo.store;

import com.radixdlt.common.AID;
import com.radixdlt.tempo.Resource;
import org.radix.time.TemporalProof;

import java.util.Optional;

public interface SampleStore extends Resource {
	Optional<TemporalProof> getCollected(AID aid);

	Optional<TemporalProof> getLocal(AID aid);

	void addLocal(TemporalProof temporalProof);

	void addCollected(TemporalProof temporalProof);
}
