package com.radixdlt.client.core.network;

import io.reactivex.Observable;

public interface PeerDiscovery {
	Observable<RadixPeer> findPeers();
}
