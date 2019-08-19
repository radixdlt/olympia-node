package com.radixdlt.tempo.actions.messaging;

import com.radixdlt.tempo.LogicalClockCursor;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.messages.CursorDiscoveryRequestMessage;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardSpace;

import java.util.Objects;

public class ReceiveCursorDiscoveryRequestAction implements TempoAction {
	private final ShardSpace shardSpace;
	private final LogicalClockCursor cursor;
	private final Peer peer;

	public ReceiveCursorDiscoveryRequestAction(ShardSpace shardSpace, LogicalClockCursor cursor, Peer peer) {
		this.shardSpace = Objects.requireNonNull(shardSpace, "shardSpace is required");
		this.cursor = Objects.requireNonNull(cursor, "cursor is required");
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public ShardSpace getShardSpace() {
		return shardSpace;
	}

	public LogicalClockCursor getCursor() {
		return cursor;
	}

	public Peer getPeer() {
		return peer;
	}

	public static ReceiveCursorDiscoveryRequestAction from(CursorDiscoveryRequestMessage message, Peer peer) {
		return new ReceiveCursorDiscoveryRequestAction(message.getShardSpace(), message.getCursor(), peer);
	}
}
