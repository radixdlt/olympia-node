package com.radixdlt.client.core.network;

import com.radixdlt.client.core.network.RadixClient.RadixClientStatus;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.ArrayList;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeersFromSeed implements PeerDiscovery {
	private final static Logger logger = LoggerFactory.getLogger(PeersFromSeed.class);

	private final String seed;
	private final boolean useSSL;
	private final int port;

	public PeersFromSeed(String seed, int port) {
		this(seed,true, port);
	}

	public PeersFromSeed(String seed, boolean useSSL, int port) {
		this.seed = seed;
		this.useSSL = useSSL;
		this.port = port;
	}

	public Observable<RadixPeer> findPeers() {

		Single<RadixPeer> rawSeed = Single.just(new RadixPeer(seed, useSSL, port)).cache();
		Observable<RadixPeer> connectedSeed =
			rawSeed.flatMap(peer -> peer.getRadixClient().connect().toSingleDefault(peer))
				.doOnSuccess(peer -> peer.getRadixClient().getSelf().subscribe(peer::data))
				.toObservable();

		return Observable.concat(
			connectedSeed
				.map(RadixPeer::getRadixClient)
				.flatMapSingle(client -> client.getLivePeers().doFinally(client::tryClose))
				.doOnNext(list -> logger.info("Got peer list " + list))
				.flatMapIterable(list -> {
					ArrayList<NodeRunnerData> copyList = new ArrayList<>(list);
					Collections.shuffle(copyList);
					return copyList;
				})
				.map(data -> new RadixPeer(data.getIp(), useSSL, port).data(data)),
			rawSeed.toObservable()
		).distinct(RadixPeer::getLocation)
		;
	}
}
