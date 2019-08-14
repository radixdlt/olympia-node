package com.radixdlt.tempo.messages;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.tempo.IterativeCursor;
import org.radix.network.messaging.Message;

@SerializerId2("tempo.sync.iterative.response")
public class IterativeResponseMessage extends Message {
	@JsonProperty("aids")
	@DsonOutput(DsonOutput.Output.ALL)
	private ImmutableList<AID> aids;

	@JsonProperty("cursor")
	@DsonOutput(DsonOutput.Output.ALL)
	private IterativeCursor cursor;

	IterativeResponseMessage() {
		// Serializer only
		this.aids = ImmutableList.of();
	}

	public IterativeResponseMessage(ImmutableList<AID> aids, IterativeCursor cursor) {
		this.aids = aids;
		this.cursor = cursor;
	}

	public ImmutableList<AID> getAids() {
		return aids;
	}

	public IterativeCursor getCursor() {
		return cursor;
	}

	@Override
	public String getCommand() {
		return "tempo.sync.iterative.response";
	}
}
