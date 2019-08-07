package com.radixdlt.tempo.sync.messages;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;
import org.radix.shards.ShardSpace;

@SerializerId2("atom.sync2.iterative.request")
public class IterativeRequestMessage extends Message {
	@JsonProperty("shards")
	@DsonOutput(DsonOutput.Output.ALL)
	private ShardSpace shards;

	@JsonProperty("cursor")
	@DsonOutput(DsonOutput.Output.ALL)
	private LedgerCursor cursor;

	IterativeRequestMessage() {
		// Serializer only
	}

	public IterativeRequestMessage(ShardSpace shards, LedgerCursor cursor) {
		this.shards = shards;
		this.cursor = cursor;
	}

	public ShardSpace getShards() {
		return shards;
	}

	public LedgerCursor getCursor() {
		return cursor;
	}

	@Override
	public String getCommand() {
		return "atom.sync2.iterative.request";
	}
}
