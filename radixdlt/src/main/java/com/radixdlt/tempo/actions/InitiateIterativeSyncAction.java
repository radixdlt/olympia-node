package com.radixdlt.tempo.actions;

import com.radixdlt.tempo.TempoAction;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class InitiateIterativeSyncAction implements TempoAction {
	private final Peer peer;

	public InitiateIterativeSyncAction(Peer peer) {
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public Peer getPeer() {
		return peer;
	}
}
