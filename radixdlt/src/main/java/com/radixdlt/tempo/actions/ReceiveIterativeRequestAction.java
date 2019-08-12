package com.radixdlt.tempo.actions;

import com.radixdlt.tempo.sync.IterativeCursor;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.messages.IterativeRequestMessage;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardSpace;

import java.util.Objects;

public class ReceiveIterativeRequestAction implements TempoAction {
	private final ShardSpace shardSpace;
	private final IterativeCursor cursor;
	private final Peer peer;

	public ReceiveIterativeRequestAction(ShardSpace shardSpace, IterativeCursor cursor, Peer peer) {
		this.shardSpace = Objects.requireNonNull(shardSpace, "shardSpace is required");
		this.cursor = Objects.requireNonNull(cursor, "cursor is required");
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public ShardSpace getShardSpace() {
		return shardSpace;
	}

	public IterativeCursor getCursor() {
		return cursor;
	}

	public Peer getPeer() {
		return peer;
	}

	public static ReceiveIterativeRequestAction from(IterativeRequestMessage message, Peer peer) {
		return new ReceiveIterativeRequestAction(message.getShardSpace(), message.getCursor(), peer);
	}
}
