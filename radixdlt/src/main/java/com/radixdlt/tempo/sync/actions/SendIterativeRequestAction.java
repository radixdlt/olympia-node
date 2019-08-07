package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.sync.IterativeCursor;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.messages.IterativeRequestMessage;
import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardRange;
import org.radix.shards.ShardSpace;

public class SendIterativeRequestAction implements SyncAction {
	private final ShardSpace shardSpace;
	private final IterativeCursor cursor;
	private final Peer peer;

	public SendIterativeRequestAction(ShardSpace shardSpace, IterativeCursor cursor, Peer peer) {
		this.shardSpace = shardSpace;
		this.cursor = cursor;
		this.peer = peer;
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
