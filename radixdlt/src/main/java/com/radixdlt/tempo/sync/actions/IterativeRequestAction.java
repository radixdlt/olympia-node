package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.sync.SyncAction;
import org.radix.discovery.DiscoveryCursor;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardSpace;

public class IterativeRequestAction implements SyncAction {
	private final ShardSpace shards;
	private final DiscoveryCursor cursor;
	private final Peer peer;

	public IterativeRequestAction(ShardSpace shards, DiscoveryCursor cursor, Peer peer) {
		this.shards = shards;
		this.cursor = cursor;
		this.peer = peer;
	}

	public ShardSpace getShards() {
		return shards;
	}

	public DiscoveryCursor getCursor() {
		return cursor;
	}

	public Peer getPeer() {
		return peer;
	}
}
