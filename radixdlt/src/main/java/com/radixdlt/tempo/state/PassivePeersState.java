package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoState;
import org.radix.network.peers.Peer;

public final class PassivePeerState implements TempoState {
	private final ImmutableMap<EUID, Peer> selectedPeers;

	public PassivePeerState(ImmutableMap<EUID, Peer> selectedPeers) {
		this.selectedPeers = selectedPeers;
	}

	public ImmutableMap<EUID, Peer> getSelectedPeers() {
		return selectedPeers;
	}
}
