package com.radixdlt.tempo.actions.messaging;

import com.radixdlt.tempo.LogicalClockCursor;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.messages.IterativeDiscoveryRequestMessage;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardSpace;

import java.util.Objects;

public class ReceiveIterativeDiscoveryRequestAction implements TempoAction {
	private final LogicalClockCursor cursor;
	private final Peer peer;

	public ReceiveIterativeDiscoveryRequestAction(LogicalClockCursor cursor, Peer peer) {
		this.cursor = Objects.requireNonNull(cursor, "cursor is required");
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public LogicalClockCursor getCursor() {
		return cursor;
	}

	public Peer getPeer() {
		return peer;
	}

	public static ReceiveIterativeDiscoveryRequestAction from(IterativeDiscoveryRequestMessage message, Peer peer) {
		return new ReceiveIterativeDiscoveryRequestAction(message.getCursor(), peer);
	}
}
