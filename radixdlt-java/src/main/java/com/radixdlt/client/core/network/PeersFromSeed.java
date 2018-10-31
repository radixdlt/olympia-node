package com.radixdlt.client.core.network;

import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.ArrayList;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeersFromSeed implements PeerDiscovery {
	private static final Logger LOGGER = LoggerFactory.getLogger(PeersFromSeed.class);

	private final RadixPeer seed;

	public PeersFromSeed(RadixPeer seed) {
		this.seed = seed;
	}

	public Observable<RadixPeer> findPeers() {
		Single<RadixPeer> rawSeed = Single.just(seed).cache();
		Observable<RadixPeer> connectedSeed =
			rawSeed
				.doOnSuccess(seed -> seed.getRadixClient().getInfo().subscribe(
					seed::data,
					e -> LOGGER.warn("Unable to load seed info")
				))
				.toObservable();

		return Observable.concat(
			connectedSeed
				.map(RadixPeer::getRadixClient)
				.flatMapSingle(client -> client.getLivePeers().doFinally(client::tryClose))
				.doOnError(e -> LOGGER.warn("Unable to load seed peers"))
				.doOnNext(list -> LOGGER.info("Got peer list " + list))
				.flatMapIterable(list -> {
					ArrayList<NodeRunnerData> copyList = new ArrayList<>(list);
					Collections.shuffle(copyList);
					return copyList;
				})
				.map(data -> new RadixPeer(data.getIp(), seed.isSsl(), seed.getPort()).data(data)),
			rawSeed.toObservable()
		).distinct(RadixPeer::getLocation);
	}
}
