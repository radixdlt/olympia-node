package com.radixdlt.tempo.messages;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.tempo.LogicalClockCursor;
import com.radixdlt.tempo.store.CommitmentBatch;
import org.radix.network.messaging.Message;

@SerializerId2("tempo.sync.discovery.iterative.response")
public class IterativeDiscoveryResponseMessage extends Message {
	@JsonProperty("commitments")
	@DsonOutput(DsonOutput.Output.ALL)
	private ImmutableList<Hash> commitments;

	@JsonProperty("aids")
	@DsonOutput(DsonOutput.Output.ALL)
	private ImmutableList<AID> aids;

	@JsonProperty("cursor")
	@DsonOutput(DsonOutput.Output.ALL)
	private LogicalClockCursor cursor;

	IterativeDiscoveryResponseMessage() {
		// Serializer only
		commitments = ImmutableList.of();
		aids = ImmutableList.of();
	}

	public IterativeDiscoveryResponseMessage(ImmutableList<Hash> commitments, ImmutableList<AID> aids, LogicalClockCursor cursor) {
		this.commitments = commitments;
		this.aids = aids;
		this.cursor = cursor;
	}

	public ImmutableList<AID> getAids() {
		return aids;
	}

	public ImmutableList<Hash> getCommitments() {
		return commitments;
	}

	public LogicalClockCursor getCursor() {
		return cursor;
	}

	@Override
	public String getCommand() {
		return "tempo.sync.discovery.iterative.response";
	}
}
