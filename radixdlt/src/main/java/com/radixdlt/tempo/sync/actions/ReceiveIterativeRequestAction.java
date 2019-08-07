package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.sync.IterativeCursor;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.messages.IterativeRequestMessage;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardSpace;

public class ReceiveIterativeRequestAction implements SyncAction {
	private final ShardSpace shardSpace;
	private final IterativeCursor cursor;
	private final Peer peer;

	public ReceiveIterativeRequestAction(ShardSpace shardSpace, IterativeCursor cursor, Peer peer) {
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

	public static ReceiveIterativeRequestAction from(IterativeRequestMessage message, Peer peer) {
		return new ReceiveIterativeRequestAction(message.getShardSpace(), message.getCursor(), peer);
	}
}
