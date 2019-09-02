package com.radixdlt.tempo.consensus.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.tempo.consensus.Sample;
import org.radix.network.messaging.Message;

import java.util.Map;

@SerializerId2("tempo.consensus.sampling.response")
public class SampleResponseMessage extends Message {
	@JsonProperty("samples")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Map<EUID, Sample> samplesByTag;

	private SampleResponseMessage() {
		// For serializer
		this.samplesByTag = ImmutableMap.of();
	}

	public SampleResponseMessage(Map<EUID, Sample> samplesByTag) {
		this.samplesByTag = samplesByTag;
	}

	@Override
	public String getCommand() {
		return "tempo.consensus.sampling.response";
	}
}
