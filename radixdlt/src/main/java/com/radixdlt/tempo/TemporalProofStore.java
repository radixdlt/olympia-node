package com.radixdlt.tempo;

import com.radixdlt.common.AID;
import org.radix.time.TemporalProof;

import java.util.Optional;

/**
 * A temporal proof store.
 */
public interface TemporalProofStore extends Store {
	/**
	 * Gets the aggregated temporal proofs associated with a certain {@link AID}.
	 * @param aid The {@link AID}.
	 * @return The temporal proof associated with the given {@link AID} (if any)
	 */
	Optional<TemporalProof> getCollected(AID aid);

	/**
	 * Gets the temporal proof branch of this node of a certain {@link AID}.
	 * @param aid The {@link AID}.
	 * @return The temporal proof associated with the given {@link AID} (if any)
	 */
	Optional<TemporalProof> getOwn(AID aid);

	/**
	 * Appends a temporal proof to this store.
	 * If a temporal proof with the same {@link AID} is already stored, the proofs will be merged.
	 *
	 * @param temporalProof The temporal proof
	 */
	void add(TemporalProof temporalProof);
}
