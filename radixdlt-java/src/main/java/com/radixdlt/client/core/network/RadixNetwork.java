package com.radixdlt.client.core.network;

import com.jakewharton.rx.ReplayingShare;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import io.reactivex.Observable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.subjects.ReplaySubject;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.soap.Node;
import org.radix.common.tuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * RadixNetwork manages the state of peers and connections.
 */
public final class RadixNetwork implements RadixNetworkEpic {
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

	/**
	 * Returns a cold observable of network state
	 *
	 * @return a cold observable of network state
	 */
	public Observable<RadixNetworkState> getNetworkState() {
		return this.networkState;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		return actions
			.filter(a -> a instanceof NodeUpdate)
			.map(NodeUpdate.class::cast)
			.filter(u -> u.getType().equals(NodeUpdateType.START_CONNECT))
			.doOnNext(u -> websockets.get(u.getNode()).connect())
			.ignoreElements()
			.toObservable();
	}

	public void reduce(RadixNodeAction action) {
		if (action instanceof NodeUpdate) {
			NodeUpdate nodeUpdate = (NodeUpdate) action;
			synchronized (lock) {
				if (nodeUpdate.getType().equals(NodeUpdateType.ADD_NODE)) {
					websockets.computeIfAbsent(action.getNode(),
						p -> new WebSocketClient(listener -> HttpClients.getSslAllTrustingClient().newWebSocket(p.getLocation(), listener)));
					peers.onNext(action.getNode());
					LOGGER.debug(String.format("Added to peer list: %s", action.getNode().getLocation()));
				} else if (nodeUpdate.getType().equals(NodeUpdateType.ADD_NODE_DATA)) {

				}
			}
		}
	}
}
