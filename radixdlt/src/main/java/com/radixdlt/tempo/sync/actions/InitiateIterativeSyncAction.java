package com.radixdlt.tempo.sync.actions;

import com.radixdlt.tempo.sync.SyncAction;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class InitiateIterativeSyncAction implements SyncAction {
	private final Peer peer;

	public InitiateIterativeSyncAction(Peer peer) {
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public Peer getPeer() {
		return peer;
	}
}
