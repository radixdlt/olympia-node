package com.radixdlt.tempo.consensus.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@SerializerId2("tempo.consensus.sampling.request")
public class SampleRequestMessage extends Message {
	@JsonProperty("tag")
	@DsonOutput(DsonOutput.Output.ALL)
	private final EUID tag;

	@JsonProperty("indices")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Map<AID, Set<LedgerIndex>> requestedIndicesByAids;

	private SampleRequestMessage() {
		this.tag = null;
		this.requestedIndicesByAids = ImmutableMap.of();
	}

	public SampleRequestMessage(EUID tag, Map<AID, Set<LedgerIndex>> requestedIndicesByAids) {
		this.tag = Objects.requireNonNull(tag);
		this.requestedIndicesByAids = Objects.requireNonNull(requestedIndicesByAids);

		if (requestedIndicesByAids.isEmpty()) {
			throw new IllegalArgumentException("Requested indices are empty");
		}
	}

	public EUID getTag() {
		return tag;
	}

	public Set<LedgerIndex> getRequestedIndices() {
		return requestedIndicesByAids.values().stream()
			.flatMap(Collection::stream)
			.collect(Collectors.toSet());
	}

	public Set<AID> getPreferredAids() {
		return requestedIndicesByAids.keySet();
	}

	@Override
	public String getCommand() {
		return "tempo.consensus.sampling.request";
	}
}
