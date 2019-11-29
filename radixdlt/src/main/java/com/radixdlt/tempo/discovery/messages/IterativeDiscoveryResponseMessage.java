package com.radixdlt.tempo.discovery.messages;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.tempo.LogicalClockCursor;
import org.radix.network.messaging.Message;

@SerializerId2("tempo.sync.discovery.iterative.response")
public class IterativeDiscoveryResponseMessage extends Message {
	@JsonProperty("aids")
	@DsonOutput(DsonOutput.Output.ALL)
	private ImmutableList<AID> aids;

	@JsonProperty("cursor")
	@DsonOutput(DsonOutput.Output.ALL)
	private LogicalClockCursor cursor;

	IterativeDiscoveryResponseMessage() {
		// Serializer only
		aids = ImmutableList.of();
	}

	public IterativeDiscoveryResponseMessage(ImmutableList<AID> aids, LogicalClockCursor cursor) {
		this.aids = aids;
		this.cursor = cursor;
	}

	public ImmutableList<AID> getAids() {
		return aids;
	}

	public LogicalClockCursor getCursor() {
		return cursor;
	}

	@Override
	public String getCommand() {
		return "tempo.sync.discovery.iterative.response";
	}
}
