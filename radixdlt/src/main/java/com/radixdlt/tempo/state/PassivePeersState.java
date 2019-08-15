package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoState;
import org.radix.network.peers.Peer;

import java.util.stream.Stream;

public final class PassivePeersState implements TempoState {
	private final ImmutableMap<EUID, Peer> selectedPeers;

	public PassivePeersState(ImmutableMap<EUID, Peer> selectedPeers) {
		this.selectedPeers = selectedPeers;
	}

	public ImmutableMap<EUID, Peer> getSelectedPeers() {
		return selectedPeers;
	}

	public Stream<Peer> peers() {
		return selectedPeers.values().stream();
	}

	public boolean contains(EUID nid) {
		return selectedPeers.containsKey(nid);
	}

	@Override
	public String toString() {
		return "PassivePeersState{" +
			"selectedPeers=" + selectedPeers +
			'}';
	}

	@Override
	public Object getDebugRepresentation() {
		return ImmutableMap.of(
			"selectedPeers", selectedPeers.keySet()
		);
	}

	public static PassivePeersState empty() {
		return new PassivePeersState(ImmutableMap.of());
	}
}
