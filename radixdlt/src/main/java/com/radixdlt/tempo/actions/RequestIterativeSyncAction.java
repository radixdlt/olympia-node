package com.radixdlt.tempo.actions;

import com.radixdlt.tempo.IterativeCursor;
import com.radixdlt.tempo.TempoAction;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class RequestIterativeSyncAction implements TempoAction {
	private final Peer peer;
	private final IterativeCursor cursor;
	private final boolean isNext;

	public RequestIterativeSyncAction(Peer peer, IterativeCursor cursor, boolean isNext) {
		this.peer = Objects.requireNonNull(peer, "peer is required");
		this.cursor = Objects.requireNonNull(cursor, "cursor is required");
		this.isNext = isNext;
	}

	public Peer getPeer() {
		return peer;
	}

	public IterativeCursor getCursor() {
		return cursor;
	}

	public boolean isNext() {
		return isNext;
	}
}
