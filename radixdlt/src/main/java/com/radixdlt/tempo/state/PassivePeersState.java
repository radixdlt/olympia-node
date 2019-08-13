package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoState;
import org.radix.network.peers.Peer;

public final class PassivePeersState implements TempoState {
	private final ImmutableMap<EUID, Peer> selectedPeers;

	public PassivePeersState(ImmutableMap<EUID, Peer> selectedPeers) {
		this.selectedPeers = selectedPeers;
	}

	public ImmutableMap<EUID, Peer> getSelectedPeers() {
		return selectedPeers;
	}

	public boolean contains(EUID nid) {
		return selectedPeers.containsKey(nid);
	}

	public static PassivePeersState initial() {
		return new PassivePeersState(ImmutableMap.of());
	}
}
