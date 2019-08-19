package com.radixdlt.tempo.messages;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

import java.util.Map;
import java.util.Set;

@SerializerId2("tempo.sync.discovery.position.response")
public class PositionDiscoveryResponseMessage extends Message {
	@JsonProperty("aids")
	@DsonOutput(DsonOutput.Output.ALL)
	private ImmutableMap<Long, AID> aids;

	private PositionDiscoveryResponseMessage() {
		// For serializer
		aids = ImmutableMap.of();
	}

	public PositionDiscoveryResponseMessage(ImmutableMap<Long, AID> aids) {
		this.aids = aids;
	}

	public ImmutableMap<Long, AID> getAids() {
		return aids;
	}

	@Override
	public String getCommand() {
		return "tempo.sync.discovery.position.response";
	}
}
