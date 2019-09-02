package com.radixdlt.tempo.consensus.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.EUID;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

import java.util.Map;
import java.util.Set;

@SerializerId2("tempo.consensus.sampling.request")
public class SampleRequestMessage extends Message {
	@JsonProperty("indices")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Map<EUID, Set<LedgerIndex>> requestedIndicesByTag;

	private SampleRequestMessage() {
		this.requestedIndicesByTag = ImmutableMap.of();
	}

	public SampleRequestMessage(Map<EUID, Set<LedgerIndex>> requestedIndicesByTag) {
		this.requestedIndicesByTag = requestedIndicesByTag;
	}

	public Map<EUID, Set<LedgerIndex>> getRequestedIndicesByTag() {
		return requestedIndicesByTag;
	}

	@Override
	public String getCommand() {
		return "tempo.consensus.sampling.request";
	}
}
