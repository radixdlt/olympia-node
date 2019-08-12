package com.radixdlt.tempo.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

import java.util.Collection;

@SerializerId2("tempo.sync.delivery.request")
public class DeliveryRequestMessage extends Message {
	@JsonProperty("aids")
	@DsonOutput(Output.ALL)
	private ImmutableList<AID> aids;

	DeliveryRequestMessage() {
		// Serializer only
		this.aids = ImmutableList.of();
	}

	public DeliveryRequestMessage(Collection<AID> aids) {
		this.aids = ImmutableList.copyOf(aids);
	}

	@Override
	public String getCommand() {
		return "tempo.sync.delivery.request";
	}

	public Collection<AID> getAids() {
		return this.aids;
	}
}
