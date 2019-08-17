package com.radixdlt.tempo.messages;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.tempo.LogicalClockCursor;
import org.radix.network.messaging.Message;
import org.radix.shards.ShardSpace;

@SerializerId2("tempo.sync.iterative.request")
public class IterativeDiscoveryRequestMessage extends Message {
	@JsonProperty("shards")
	@DsonOutput(DsonOutput.Output.ALL)
	private ShardSpace shardSpace;

	@JsonProperty("cursor")
	@DsonOutput(DsonOutput.Output.ALL)
	private LogicalClockCursor cursor;

	IterativeDiscoveryRequestMessage() {
		// Serializer only
	}

	public IterativeDiscoveryRequestMessage(ShardSpace shardSpace, LogicalClockCursor cursor) {
		this.shardSpace = shardSpace;
		this.cursor = cursor;
	}

	public ShardSpace getShardSpace() {
		return shardSpace;
	}

	public LogicalClockCursor getCursor() {
		return cursor;
	}

	@Override
	public String getCommand() {
		return "tempo.sync.iterative.request";
	}
}
