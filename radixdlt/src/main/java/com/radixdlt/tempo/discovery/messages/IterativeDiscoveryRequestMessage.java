package com.radixdlt.tempo.discovery.messages;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.tempo.LogicalClockCursor;
import org.radix.network.messaging.Message;
import org.radix.shards.ShardSpace;

@SerializerId2("tempo.sync.discovery.iterative.request")
public class IterativeDiscoveryRequestMessage extends Message {
	@JsonProperty("cursor")
	@DsonOutput(DsonOutput.Output.ALL)
	private LogicalClockCursor cursor;

	IterativeDiscoveryRequestMessage() {
		// Serializer only
	}

	public IterativeDiscoveryRequestMessage(LogicalClockCursor cursor) {
		this.cursor = cursor;
	}

	public LogicalClockCursor getCursor() {
		return cursor;
	}

	@Override
	public String getCommand() {
		return "tempo.sync.discovery.iterative.request";
	}
}
