package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.sync.IterativeCursor;
import com.radixdlt.tempo.sync.SyncAction;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class RequestIterativeSyncAction implements SyncAction {
	private final Peer peer;
	private final IterativeCursor cursor;

	public RequestIterativeSyncAction(Peer peer, IterativeCursor cursor) {
		this.peer = Objects.requireNonNull(peer, "peer is required");
		this.cursor = Objects.requireNonNull(cursor, "cursor is required");
	}

	public Peer getPeer() {
		return peer;
	}

	public IterativeCursor getCursor() {
		return cursor;
	}
}
