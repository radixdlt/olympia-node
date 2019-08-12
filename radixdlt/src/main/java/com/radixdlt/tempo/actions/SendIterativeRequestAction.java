package com.radixdlt.tempo.actions;

import com.radixdlt.tempo.sync.IterativeCursor;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.messages.IterativeRequestMessage;
import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardSpace;

import java.util.Objects;

public class SendIterativeRequestAction implements TempoAction {
	private final ShardSpace shardSpace;
	private final IterativeCursor cursor;
	private final Peer peer;

	public SendIterativeRequestAction(ShardSpace shardSpace, IterativeCursor cursor, Peer peer) {
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

	public Message toMessage() {
		return new IterativeRequestMessage(shardSpace, cursor);
	}
}
