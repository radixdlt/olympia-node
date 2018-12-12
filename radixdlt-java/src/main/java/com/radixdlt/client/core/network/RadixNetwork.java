package com.radixdlt.client.core.network;

import com.radixdlt.client.core.network.WebSocketClient.RadixClientStatus;
import io.reactivex.Observable;
import io.reactivex.observables.ConnectableObservable;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Radix Network manages connections to Node Runners for a given Universe.
 */
public final class RadixNetwork {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixNetwork.class);

	/**
	 * Cached observable for keeping track of Radix Peers
	 */
	private final Observable<RadixPeer> peers;

	/**
	 * Hot observable which updates subscribers of new connection events
	 */
	private final ConnectableObservable<SimpleImmutableEntry<String, RadixClientStatus>> statusUpdates;


	public RadixNetwork(PeerDiscovery peerDiscovery) {
		Objects.requireNonNull(peerDiscovery);

		this.peers = peerDiscovery.findPeers()
			.retryWhen(new IncreasingRetryTimer(WebSocketException.class))
			.doOnNext(peer -> LOGGER.info("Added to peer list: " + peer.getLocation()))
			.replay().autoConnect(2);

		this.statusUpdates = peers.map(RadixPeer::getRadixClient)
			.flatMap(
				client -> client.getStatus().map(
					status -> new SimpleImmutableEntry<>(client.getLocation(), status)
				)
			)
			.publish();
		this.statusUpdates.connect();
	}

	public Observable<SimpleImmutableEntry<String, RadixClientStatus>> connectAndGetStatusUpdates() {
		this.peers.subscribe();
		return this.getStatusUpdates();
	}

	public Observable<Map<String, RadixClientStatus>> getNetworkState() {
		return peers.map(RadixPeer::getRadixClient)
			.flatMap(
				client -> client.getStatus().map(
					status -> new SimpleImmutableEntry<>(client.getLocation(), status)
				)
			).scanWith(HashMap<String, RadixClientStatus>::new, (map, entry) -> {
			map.put(entry.getKey(), entry.getValue());
			return map;
		});
	}

	/**
	 * Returns a hot observable of the status of peers
	 *
	 * @return a hot Observable of status of peers
	 */
	public Observable<SimpleImmutableEntry<String, RadixClientStatus>> getStatusUpdates() {
		return statusUpdates;
	}

	public Observable<RadixJsonRpcClient> getRadixClients() {
		return peers.map(RadixPeer::getRadixClient);
	}

	public Observable<RadixJsonRpcClient> getRadixClients(Set<Long> shards) {
		return peers.flatMapMaybe(peer -> peer.servesShards(shards))
			.map(RadixPeer::getRadixClient)
			.flatMapMaybe(client -> client.checkAPIVersion().filter(b -> b).map(b -> client));
	}

	public Observable<RadixJsonRpcClient> getRadixClients(Long shard) {
		return this.getRadixClients(Collections.singleton(shard));
	}

	/**
	 * Free resources cleanly to the best of our ability
	 */
	public void close() {
		// TODO: fix concurrency
		// TODO: Cleanup objects, etc.
	}
}
