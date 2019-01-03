package com.radixdlt.client.core.network;

import com.jakewharton.rx.ReplayingShare;
import io.reactivex.Observable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.subjects.ReplaySubject;
import java.sql.Time;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.radix.common.tuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * RadixNetwork manages the state of peers and connections.
 */
public final class RadixNetwork {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixNetwork.class);

	private final Object lock = new Object();

	/**
	 * Cached observable for keeping track of Radix Peers
	 */
	private final ReplaySubject<RadixPeer> peers;

	/**
	 * Hot observable which updates subscribers of new connection events
	 */
	private final ConnectableObservable<Pair<RadixPeer, RadixClientStatus>> statusUpdates;
	private final Observable<RadixNetworkState> networkState;

	private final ConcurrentHashMap<RadixPeer, WebSocketClient> websockets = new ConcurrentHashMap<>();

	public RadixNetwork() {
		this.peers = ReplaySubject.create();

		this.statusUpdates = this.peers
			.flatMap(peer -> websockets.get(peer).getState().map(state -> new Pair<>(peer, state)))
			.publish();
		this.statusUpdates.connect();

		this.networkState = this.peers
			.flatMap(peer -> websockets.get(peer).getState().map(state -> new Pair<>(peer, state)))
			.doOnNext(status -> LOGGER.debug(String.format("Peer status changed: %s", status)))
			.scan(new RadixNetworkState(Collections.emptyMap()), (previousState, update) -> {
				LinkedHashMap<RadixPeer, RadixClientStatus> currentPeers = new LinkedHashMap<>(previousState.getPeers());
				currentPeers.put(update.getFirst(), update.getSecond());
				return new RadixNetworkState(currentPeers);
			})
			.compose(ReplayingShare.instance());
	}

	public WebSocketClient getWsChannel(RadixPeer peer) {
		return websockets.get(peer);
	}

	public Observable<Pair<RadixPeer, RadixClientStatus>> connectAndGetStatusUpdates() {
		this.peers.subscribe();
		return this.getStatusUpdates();
	}

	/**
	 * Returns a hot observable of the status of peers
	 *
	 * @return a hot Observable of status of peers
	 */
	public Observable<Pair<RadixPeer, RadixClientStatus>> getStatusUpdates() {
		return this.statusUpdates;
	}

	/**
	 * Returns a cold observable of network state
	 *
	 * @return a cold observable of network state
	 */
	public Observable<RadixNetworkState> getNetworkState() {
		return this.networkState;
	}

	public void connect(RadixPeer peer) {
		websockets.get(peer).connect();
	}

	public void addPeer(RadixPeer peer) {
		synchronized (lock) {
			websockets.computeIfAbsent(
				peer,
				p -> new WebSocketClient(listener -> HttpClients.getSslAllTrustingClient().newWebSocket(p.getLocation(), listener))
			);
			peers.onNext(peer);
			LOGGER.debug(String.format("Added to peer list: %s", peer.getLocation()));
		}
	}

	/**
	 * Free resources cleanly to the best of our ability
	 */
	public void close() {
		// TODO: fix concurrency
		// TODO: Cleanup objects, etc.
	}
}
