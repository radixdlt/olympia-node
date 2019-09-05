package com.radixdlt.tempo.consensus.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

import java.util.Objects;
import java.util.Set;

@SerializerId2("tempo.consensus.sampling.request")
public class SampleRequestMessage extends Message {
	@JsonProperty("tag")
	@DsonOutput(DsonOutput.Output.ALL)
	private final EUID tag;

	@JsonProperty("indices")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Set<LedgerIndex> requestedIndices;

	private SampleRequestMessage() {
		this.tag = null;
		this.requestedIndices = ImmutableSet.of();
	}

	public SampleRequestMessage(EUID tag, Set<LedgerIndex> requestedIndices) {
		this.tag = Objects.requireNonNull(tag);
		this.requestedIndices = Objects.requireNonNull(requestedIndices);
	}

	public EUID getTag() {
		return tag;
	}

	public Set<LedgerIndex> getRequestedIndices() {
		return requestedIndices;
	}

	@Override
	public String getCommand() {
		return "tempo.consensus.sampling.request";
	}
}
