package com.radixdlt.tempo.consensus;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import org.radix.time.TemporalProof;

import java.util.Map;
import java.util.Set;

/**
 * Sample of a certain set of {@link LedgerIndex}.
 * Contains a set of collected temporal proofs from {@link AID}s at those indices
 * and a set of indices that could not be found.
 */
@SerializerId2("tempo.consensus.sample")
public final class Sample {
	@JsonProperty("temporalProofs")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Map<LedgerIndex, Map<AID, TemporalProof>> temporalProofsByIndex;

	@JsonProperty("unavailableIndices")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Set<LedgerIndex> unavailableIndices;

	public Sample(Map<LedgerIndex, Map<AID, TemporalProof>> temporalProofsByIndex, Set<LedgerIndex> unavailableIndices) {
		this.temporalProofsByIndex = temporalProofsByIndex;
		this.unavailableIndices = unavailableIndices;
	}

	public Map<LedgerIndex, Map<AID, TemporalProof>> getTemporalProofsByIndex() {
		return temporalProofsByIndex;
	}

	public Set<LedgerIndex> getUnavailableIndices() {
		return unavailableIndices;
	}
}
