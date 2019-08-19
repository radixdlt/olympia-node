package com.radixdlt.tempo.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

@SerializerId2("tempo.sync.discovery.position.request")
public class PositionDiscoveryRequestMessage extends Message {
	@JsonProperty("positions")
	@DsonOutput(DsonOutput.Output.ALL)
	private ImmutableSet<Long> positions;

	private PositionDiscoveryRequestMessage() {
		// For serializer
		positions = ImmutableSet.of();
	}

	public PositionDiscoveryRequestMessage(ImmutableSet<Long> positions) {
		this.positions = positions;
	}

	public ImmutableSet<Long> getPositions() {
		return positions;
	}

	@Override
	public String getCommand() {
		return "tempo.sync.discovery.position.request";
	}
}
