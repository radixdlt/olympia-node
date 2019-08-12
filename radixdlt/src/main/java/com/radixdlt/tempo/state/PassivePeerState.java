package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.tempo.TempoState;
import org.radix.network.peers.Peer;

public final class PassivePeerState implements TempoState {
	private final ImmutableSet<Peer> selectedPeers;

	public PassivePeerState(ImmutableSet<Peer> selectedPeers) {
		this.selectedPeers = selectedPeers;
	}

	public ImmutableSet<Peer> getSelectedPeers() {
		return selectedPeers;
	}
}
