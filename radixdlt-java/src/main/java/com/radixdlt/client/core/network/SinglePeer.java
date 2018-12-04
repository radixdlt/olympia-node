package com.radixdlt.client.core.network;

import io.reactivex.Observable;
import io.reactivex.Single;

public class SinglePeer implements PeerDiscovery {
	private final String peer;
	private final boolean useSSL;
	private final int port;

	public SinglePeer(String peer, boolean useSSL, int port) {
		this.peer = peer;
		this.useSSL = useSSL;
		this.port = port;
	}

	public Observable<RadixPeer> findPeers() {
		return Single.fromCallable(() -> new RadixPeer(peer, useSSL, port))
			.flatMap(peer ->
				peer.getRadixClient().getInfo().map(data -> {
					peer.setData(data);
					return peer;
				})
			).toObservable();
	}
}
