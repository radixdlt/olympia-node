package com.radixdlt.tempo.sync.actions;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.tempo.sync.SyncAction;
import org.radix.network.peers.Peer;

public class AcceptPassivePeersAction implements SyncAction {
	private final ImmutableSet<Peer> passivePeers;

	public AcceptPassivePeersAction(ImmutableSet<Peer> passivePeers) {
		this.passivePeers = passivePeers;
	}

	public ImmutableSet<Peer> getPassivePeers() {
		return passivePeers;
	}
}
