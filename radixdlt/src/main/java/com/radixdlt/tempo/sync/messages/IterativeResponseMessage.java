package com.radixdlt.tempo.sync.messages;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import org.radix.discovery.DiscoveryCursor;
import org.radix.network.messaging.Message;
import org.radix.shards.ShardSpace;

import java.util.List;
import java.util.Set;

@SerializerId2("atom.sync2.iterative.response")
public class IterativeResponseMessage extends Message {
	@JsonProperty("aids")
	@DsonOutput(DsonOutput.Output.ALL)
	private ImmutableList<AID> aids;

	@JsonProperty("cursor")
	@DsonOutput(DsonOutput.Output.ALL)
	private LedgerCursor cursor;

	IterativeResponseMessage() {
		// Serializer only
	}

	public IterativeResponseMessage(ImmutableList<AID> aids, LedgerCursor cursor) {
		this.aids = aids;
		this.cursor = cursor;
	}

	public ImmutableList<AID> getAids() {
		return aids;
	}

	public LedgerCursor getCursor() {
		return cursor;
	}

	@Override
	public String getCommand() {
		return "atom.sync2.iterative.request";
	}
}
