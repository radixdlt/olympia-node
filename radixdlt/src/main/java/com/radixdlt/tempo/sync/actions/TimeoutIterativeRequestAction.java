package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.sync.IterativeCursor;
import com.radixdlt.tempo.sync.SyncAction;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class TimeoutIterativeRequestAction implements SyncAction {
	private final Peer peer;
	private final IterativeCursor requestedCursor;

	public TimeoutIterativeRequestAction(Peer peer, IterativeCursor requestedCursor) {
		this.peer = Objects.requireNonNull(peer, "peer is required");
		this.requestedCursor = Objects.requireNonNull(requestedCursor, "requestedCursor is required");
	}

	public Peer getPeer() {
		return peer;
	}

	public IterativeCursor getRequestedCursor() {
		return requestedCursor;
	}
}
