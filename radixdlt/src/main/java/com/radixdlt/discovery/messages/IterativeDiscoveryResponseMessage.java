package com.radixdlt.discovery.messages;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.discovery.LogicalClockCursor;
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
		super(0);
		aids = ImmutableList.of();
	}

	public IterativeDiscoveryResponseMessage(ImmutableList<AID> aids, LogicalClockCursor cursor, int magic) {
		super(magic);
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
