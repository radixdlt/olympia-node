package com.radixdlt.client.core.network;

import io.reactivex.Observable;
import io.reactivex.observables.ConnectableObservable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;

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
	private final ConnectableObservable<SimpleImmutableEntry<RadixPeer, RadixClientStatus>> statusUpdates;
	private final Observable<RadixNetworkState> networkState;

	public RadixNetwork(PeerDiscovery peerDiscovery) {
		Objects.requireNonNull(peerDiscovery);

		this.peers = peerDiscovery.findPeers()
			.retryWhen(new IncreasingRetryTimer(WebSocketException.class))
			.doOnNext(peer -> LOGGER.info("Added to peer list: " + peer.getLocation()))
				.doOnSubscribe(s -> LOGGER.info("subscribe to peers"))
			.replay().autoConnect(2);

		this.statusUpdates = peers
			.flatMap(
				peer -> peer.getRadixClient().getStatus()
						.doOnNext(status -> LOGGER.info("Peer status changed: " + status))
						.map(status -> new SimpleImmutableEntry<>(peer, status)
				)
			)
				.doOnSubscribe(s -> LOGGER.info("subscribed to status updates"))
			.publish();
		this.statusUpdates.connect();

		this.networkState = peers.flatMap(peer -> peer.status().map(status -> new SimpleImmutableEntry<>(peer, status)))
				.scan(new RadixNetworkState(Collections.emptyMap()), (previousState, update) -> {
			LinkedHashMap<RadixPeer, RadixPeerState> currentPeers = new LinkedHashMap<>(previousState.peers);
			currentPeers.put(update.getKey(), update.getValue());

			return new RadixNetworkState(currentPeers);
		});
	}

	public Observable<SimpleImmutableEntry<RadixPeer, RadixClientStatus>> connectAndGetStatusUpdates() {
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
	public Observable<SimpleImmutableEntry<RadixPeer, RadixClientStatus>> getStatusUpdates() {
		return statusUpdates;
	}

	/**
	 * Returns a cold observable of network state
	 *
	 * @return a cold observable of network state
	 */
	public Observable<RadixNetworkState> getNetworkState() {
		return networkState;
	}

	/**
	 * Free resources cleanly to the best of our ability
	 */
	public void close() {
		// TODO: fix concurrency
		// TODO: Cleanup objects, etc.
	}
}
