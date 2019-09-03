package com.radixdlt.tempo.store;

import com.radixdlt.common.AID;
import com.radixdlt.tempo.Resource;
import org.radix.time.TemporalProof;

import java.util.Optional;

public interface SampleStore extends Resource {
	/**
	 * Gets the local temporal proof branch of this node of a certain {@link AID}.
	 * @param aid The {@link AID}.
	 * @return The temporal proof associated with the given {@link AID} (if any)
	 */
	Optional<TemporalProof> get(AID aid);

	/**
	 * Appends a temporal proof to this store.
	 * If a temporal proof with the same {@link AID} is already stored, the proofs will be merged.
	 *
	 * @param temporalProof The temporal proof
	 */
	void add(TemporalProof temporalProof);
}
