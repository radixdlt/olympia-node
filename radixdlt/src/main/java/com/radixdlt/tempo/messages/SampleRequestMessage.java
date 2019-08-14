package com.radixdlt.tempo.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

@SerializerId2("tempo.sample.request")
public class SampleRequestMessage extends Message {
	@JsonProperty("aids")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableSet<AID> aids;

	private SampleRequestMessage() {
		// For serializer
		this.aids = ImmutableSet.of();
	}

	public SampleRequestMessage(ImmutableSet<AID> aids) {
		this.aids = aids;
	}

	public ImmutableSet<AID> getAids() {
		return aids;
	}

	@Override
	public String getCommand() {
		return "tempo.sample.request";
	}
}
