package com.radixdlt.client.core.network;

import io.reactivex.Observable;
import java.net.InetAddress;
import java.util.Set;

public interface PeerDiscovery {
	Observable<RadixPeer> findPeers();
}
