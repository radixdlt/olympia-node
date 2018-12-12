package com.radixdlt.client.core.network;

import io.reactivex.Observable;
import io.reactivex.observables.ConnectableObservable;
import org.radix.common.tuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private final ConnectableObservable<RadixPeerState> statusUpdates;
	private final Observable<RadixNetworkState> networkState;

	public RadixNetwork(PeerDiscovery peerDiscovery) {
		Objects.requireNonNull(peerDiscovery);

		this.peers = peerDiscovery.findPeers()
			.retryWhen(new IncreasingRetryTimer(WebSocketException.class))
			.doOnNext(peer -> LOGGER.debug(String.format("Added to peer list: %s", peer.getLocation())))
			.replay()
			.autoConnect(2);

		// this will only give status updates when all data is available for RadixPeerState, see RadixPeer.status
		this.statusUpdates = peers
			.flatMap(RadixPeer::status)
			.publish();
		this.statusUpdates.connect();

		this.networkState = peers.flatMap(peer -> peer.status()
				.doOnNext(status -> LOGGER.debug(String.format("Peer status changed: %s", status)))
				.map(status -> new Pair<>(peer, status)))
				.scan(new RadixNetworkState(Collections.emptyMap()), (previousState, update) -> {
			LinkedHashMap<RadixPeer, RadixPeerState> currentPeers = new LinkedHashMap<>(previousState.getPeers());
			currentPeers.put(update.getFirst(), update.getSecond());

			return new RadixNetworkState(currentPeers);
		});
	}

	public Observable<RadixPeerState> connectAndGetStatusUpdates() {
		this.peers.subscribe();
		return this.getStatusUpdates();
	}

	/**
	 * Returns a hot observable of the status of peers
	 *
	 * @return a hot Observable of status of peers
	 */
	public Observable<RadixPeerState> getStatusUpdates() {
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
