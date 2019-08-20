package com.radixdlt.tempo.actions;

import com.radixdlt.tempo.reactive.TempoAction;
import org.radix.network.peers.Peer;

import java.util.Objects;

// TODO separate into initiateiterativediscovery (with initial commitments) and reinitiate (without initial commitment)
public class InitiateIterativeDiscoveryAction implements TempoAction {
	private final Peer peer;

	public InitiateIterativeDiscoveryAction(Peer peer) {
		this.peer = Objects.requireNonNull(peer, "peer is required");
	}

	public Peer getPeer() {
		return peer;
	}
}
