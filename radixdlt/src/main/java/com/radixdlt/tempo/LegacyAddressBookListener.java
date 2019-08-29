package com.radixdlt.tempo;

import org.radix.network.peers.Peer;

import java.util.Set;

public interface LegacyAddressBookListener {
	void onPeerAdded(Set<Peer> peers);

	void onPeerRemoved(Set<Peer> peers);
}
