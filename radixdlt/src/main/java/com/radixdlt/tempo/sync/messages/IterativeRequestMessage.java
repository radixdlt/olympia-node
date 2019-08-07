package com.radixdlt.tempo.sync.messages;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.tempo.sync.IterativeCursor;
import org.radix.network.messaging.Message;
import org.radix.shards.ShardRange;

@SerializerId2("tempo.sync.iterative.request")
public class IterativeRequestMessage extends Message {
	@JsonProperty("shards")
	@DsonOutput(DsonOutput.Output.ALL)
	private ShardRange shards;

	@JsonProperty("cursor")
	@DsonOutput(DsonOutput.Output.ALL)
	private IterativeCursor cursor;

	IterativeRequestMessage() {
		// Serializer only
	}

	public IterativeRequestMessage(ShardRange shards, IterativeCursor cursor) {
		this.shards = shards;
		this.cursor = cursor;
	}

	public ShardRange getShards() {
		return shards;
	}

	public IterativeCursor getCursor() {
		return cursor;
	}

	@Override
	public String getCommand() {
		return "tempo.sync.iterative.request";
	}
}
