package com.radixdlt.tempo.sync.actions;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.tempo.sync.SyncAction;
import org.radix.network.peers.Peer;

import java.util.Objects;

public class AcceptPassivePeersAction implements SyncAction {
	private final ImmutableSet<Peer> passivePeers;

	public AcceptPassivePeersAction(ImmutableSet<Peer> passivePeers) {
		this.passivePeers = Objects.requireNonNull(passivePeers, "passivePeers is required");
	}

	public ImmutableSet<Peer> getPassivePeers() {
		return passivePeers;
	}
}
