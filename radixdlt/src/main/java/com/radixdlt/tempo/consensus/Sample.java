package com.radixdlt.tempo.consensus;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Map;
import java.util.Set;

/**
 * Sample of a certain set of {@link LedgerIndex}.
 * Contains a set of collected temporal proofs from {@link AID}s at those indices
 * and a set of indices that could not be found.
 */
@SerializerId2("tempo.consensus.sample")
public final class Sample {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("preferencesByIndex")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Map<LedgerIndex, AID> preferencesByIndex;

	@JsonProperty("unavailableIndices")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Set<LedgerIndex> unavailableIndices;

	private Sample() {
		this.preferencesByIndex = ImmutableMap.of();
		this.unavailableIndices = ImmutableSet.of();
	}

	public Sample(Map<LedgerIndex, AID> preferencesByIndex, Set<LedgerIndex> unavailableIndices) {
		this.preferencesByIndex = preferencesByIndex;
		this.unavailableIndices = unavailableIndices;
	}

	public Map<LedgerIndex, AID> getPreferencesByIndex() {
		return preferencesByIndex;
	}

	public Set<LedgerIndex> getUnavailableIndices() {
		return unavailableIndices;
	}
}
