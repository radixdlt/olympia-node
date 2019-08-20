package com.radixdlt.tempo.actions.messaging;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.tempo.LogicalClockCursor;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.messages.IterativeDiscoveryResponseMessage;
import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class SendIterativeDiscoveryResponseAction implements TempoAction {
	private final ImmutableList<Hash> commitments;
	private final ImmutableList<AID> aids;
	private final LogicalClockCursor cursor;
	private final Peer peer;

	public SendIterativeDiscoveryResponseAction(ImmutableList<Hash> commitments, ImmutableList<AID> aids, LogicalClockCursor cursor, Peer peer) {
		this.commitments = Objects.requireNonNull(commitments, "commitments is required");
		this.aids = Objects.requireNonNull(aids, "aids is required");
		this.cursor = Objects.requireNonNull(cursor, "cursor is required");
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public ImmutableList<Hash> getCommitments() {
		return commitments;
	}

	public ImmutableList<AID> getAids() {
		return aids;
	}

	public LogicalClockCursor getCursor() {
		return cursor;
	}

	public Peer getPeer() {
		return peer;
	}

	public Message toMessage() {
		return new IterativeDiscoveryResponseMessage(commitments, aids, cursor);
	}
}
