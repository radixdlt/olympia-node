package com.radixdlt.tempo.messages;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.tempo.LogicalClockCursor;
import com.radixdlt.tempo.store.CommitmentBatch;
import org.radix.network.messaging.Message;

@SerializerId2("tempo.sync.iterative.response")
public class IterativeDiscoveryResponseMessage extends Message {
	@JsonProperty("commitmentBatch")
	@DsonOutput(DsonOutput.Output.ALL)
	private CommitmentBatch commitmentBatch;

	@JsonProperty("cursor")
	@DsonOutput(DsonOutput.Output.ALL)
	private LogicalClockCursor cursor;

	IterativeDiscoveryResponseMessage() {
		// Serializer only
	}

	public IterativeDiscoveryResponseMessage(CommitmentBatch commitmentBatch, LogicalClockCursor cursor) {
		this.commitmentBatch = commitmentBatch;
		this.cursor = cursor;
	}

	public CommitmentBatch getCommitmentBatch() {
		return commitmentBatch;
	}

	public LogicalClockCursor getCursor() {
		return cursor;
	}

	@Override
	public String getCommand() {
		return "tempo.sync.iterative.response";
	}
}
