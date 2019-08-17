package com.radixdlt.tempo.actions.messaging;

import com.radixdlt.tempo.LogicalClockCursor;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.messages.IterativeDiscoveryResponseMessage;
import com.radixdlt.tempo.store.CommitmentBatch;
import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class SendIterativeDiscoveryResponseAction implements TempoAction {
	private final CommitmentBatch commitments;
	private final LogicalClockCursor cursor;
	private final Peer peer;

	public SendIterativeDiscoveryResponseAction(CommitmentBatch commitments, LogicalClockCursor cursor, Peer peer) {
		this.commitments = Objects.requireNonNull(commitments, "commitments is required");
		this.cursor = Objects.requireNonNull(cursor, "cursor is required");
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public CommitmentBatch getCommitments() {
		return commitments;
	}

	public LogicalClockCursor getCursor() {
		return cursor;
	}

	public Peer getPeer() {
		return peer;
	}

	public Message toMessage() {
		return new IterativeDiscoveryResponseMessage(commitments, cursor);
	}
}
