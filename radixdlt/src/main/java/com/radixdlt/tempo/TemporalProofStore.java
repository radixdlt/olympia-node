package com.radixdlt.tempo;

import com.radixdlt.common.AID;
import org.radix.time.TemporalProof;

import java.util.Optional;

/**
 * A temporal proof store.
 */
public interface TemporalProofStore extends Store {
	/**
	 * Gets the temporal proof associated with a certain {@link AID}.
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
	void append(TemporalProof temporalProof);
}
