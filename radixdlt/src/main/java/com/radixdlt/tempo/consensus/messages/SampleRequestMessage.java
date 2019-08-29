package com.radixdlt.tempo.consensus.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

@SerializerId2("tempo.sample.request")
public class SampleRequestMessage extends Message {
	@JsonProperty("aids")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableSet<AID> aids;

	@JsonProperty("tag")
	@DsonOutput(DsonOutput.Output.ALL)
	private final EUID tag;

	private SampleRequestMessage() {
		// For serializer
		this.aids = ImmutableSet.of();
		this.tag = null;
	}

	public SampleRequestMessage(ImmutableSet<AID> aids, EUID tag) {
		this.aids = aids;
		this.tag = tag;
	}

	public ImmutableSet<AID> getAids() {
		return aids;
	}

	public EUID getTag() {
		return tag;
	}

	@Override
	public String getCommand() {
		return "tempo.sample.request";
	}
}
