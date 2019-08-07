package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.sync.IterativeCursor;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.messages.IterativeRequestMessage;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardRange;
import org.radix.shards.ShardSpace;

public class ReceiveIterativeRequestAction implements SyncAction {
	private final ShardRange shards;
	private final IterativeCursor cursor;
	private final Peer peer;

	public ReceiveIterativeRequestAction(ShardRange shards, IterativeCursor cursor, Peer peer) {
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

	public static ReceiveIterativeRequestAction from(IterativeRequestMessage message, Peer peer) {
		return new ReceiveIterativeRequestAction(message.getShards(), message.getCursor(), peer);
	}
}
