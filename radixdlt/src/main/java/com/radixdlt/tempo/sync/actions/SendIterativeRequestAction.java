package com.radixdlt.tempo.sync.actions;

import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.messages.IterativeRequestMessage;
import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardSpace;

public class SendIterativeRequestAction implements SyncAction {
	private final ShardSpace shards;
	private final LedgerCursor cursor;
	private final Peer peer;

	public SendIterativeRequestAction(ShardSpace shards, LedgerCursor cursor, Peer peer) {
		this.shards = shards;
		this.cursor = cursor;
		this.peer = peer;
	}

	public ShardSpace getShards() {
		return shards;
	}

	public LedgerCursor getCursor() {
		return cursor;
	}

	public Peer getPeer() {
		return peer;
	}

	public Message toMessage() {
		return new IterativeRequestMessage(shards, cursor);
	}
}
