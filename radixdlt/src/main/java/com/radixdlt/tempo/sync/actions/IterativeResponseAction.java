package com.radixdlt.tempo.sync.actions;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor;
import org.radix.network.peers.Peer;

public class IterativeResponseAction {
	private final ImmutableList<AID> aids;
	private final LedgerCursor cursor;
	private final Peer peer;

	public IterativeResponseAction(ImmutableList<AID> aids, LedgerCursor cursor, Peer peer) {
		this.aids = aids;
		this.cursor = cursor;
		this.peer = peer;
	}

	public ImmutableList<AID> getAids() {
		return aids;
	}

	public LedgerCursor getCursor() {
		return cursor;
	}

	public Peer getPeer() {
		return peer;
	}
}
