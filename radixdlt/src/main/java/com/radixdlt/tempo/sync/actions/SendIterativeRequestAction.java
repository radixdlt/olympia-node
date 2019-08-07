package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.sync.IterativeCursor;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.messages.IterativeRequestMessage;
import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardRange;

public class SendIterativeRequestAction implements SyncAction {
	private final ShardRange shards;
	private final IterativeCursor cursor;
	private final Peer peer;

	public SendIterativeRequestAction(ShardRange shards, IterativeCursor cursor, Peer peer) {
		this.shards = shards;
		this.cursor = cursor;
		this.peer = peer;
	}

	public ShardRange getShards() {
		return shards;
	}

	public IterativeCursor getCursor() {
		return cursor;
	}

	public Peer getPeer() {
		return peer;
	}

	public Message toMessage() {
		return new IterativeRequestMessage(shards, cursor);
	}
}
