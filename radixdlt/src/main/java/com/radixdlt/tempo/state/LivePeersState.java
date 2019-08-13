package com.radixdlt.tempo.state;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoState;
import org.radix.network.peers.Peer;

import java.util.Optional;

public final class LivePeersState implements TempoState {
	private final ImmutableMap<EUID, Peer> livePeers;
	private final ImmutableSet<EUID> nids;

	public LivePeersState(ImmutableMap<EUID, Peer> livePeers) {
		this(livePeers, livePeers.keySet());
	}

	public LivePeersState(ImmutableMap<EUID, Peer> livePeers, ImmutableSet<EUID> nids) {
		this.livePeers = livePeers;
		this.nids = nids;
	}

	public Optional<Peer> getPeer(EUID euid) {
		return Optional.ofNullable(livePeers.get(euid));
	}

	public ImmutableMap<EUID, Peer> getLivePeers() {
		return livePeers;
	}

	public ImmutableSet<EUID> getNids() {
		return nids;
	}

	public static LivePeersState initial() {
		return new LivePeersState(ImmutableMap.of());
	}
}
