package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.sync.IterativeCursor;
import com.radixdlt.tempo.sync.SyncAction;
import org.radix.network.peers.Peer;

public class TimeoutIterativeRequestAction implements SyncAction {
	private final Peer peer;
	private final IterativeCursor requestedCursor;

	public TimeoutIterativeRequestAction(Peer peer, IterativeCursor requestedCursor) {
		this.peer = peer;
		this.requestedCursor = requestedCursor;
	}

	public Peer getPeer() {
		return peer;
	}

	public IterativeCursor getRequestedCursor() {
		return requestedCursor;
	}
}
