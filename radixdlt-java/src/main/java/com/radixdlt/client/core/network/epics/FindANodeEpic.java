package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.ConnectWebSocketAction;
import com.radixdlt.client.core.network.actions.FindANodeRequestAction;
import com.radixdlt.client.core.network.actions.FindANodeResultAction;
import com.radixdlt.client.core.network.selector.RadixPeerSelector;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Epic which finds a connected sharded node when a FindANode request is received. If there are none found,
 * then the epic attempts to start connections.
 */
public class FindANodeEpic implements RadixNetworkEpic {

	private static final Logger LOGGER = LoggerFactory.getLogger(FindANodeEpic.class);
	private final RadixPeerSelector selector;

	public FindANodeEpic(RadixPeerSelector selector) {
		this.selector = selector;
	}

	private Maybe<RadixNodeAction> findConnection(Set<Long> shards, RadixNetworkState state) {
		final Map<WebSocketStatus, List<RadixNode>> statusMap = Arrays.stream(WebSocketStatus.values())
			.collect(Collectors.toMap(
				Function.identity(),
				s -> state.getNodes().entrySet().stream()
					.filter(entry -> entry.getValue().getShards().map(sh -> sh.intersects(shards)).orElse(false))
					.filter(e -> e.getValue().getStatus().equals(s))
					.map(Entry::getKey)
					.collect(Collectors.toList())
			));

		final long activeNodeCount = statusMap.get(WebSocketStatus.CONNECTED).size() + statusMap.get(WebSocketStatus.CONNECTING).size();

		if (activeNodeCount < 1) {
			LOGGER.info(String.format("Requesting more node connections, want %d but have %d active nodes", 1, activeNodeCount));

			List<RadixNode> disconnectedPeers = statusMap.get(WebSocketStatus.DISCONNECTED);
			if (disconnectedPeers.isEmpty()) {
				LOGGER.info("Could not connect to new peer, don't have any.");
			} else {
				return Maybe.just(ConnectWebSocketAction.of(disconnectedPeers.get(0)));
			}
		}

		return Maybe.empty();
	}

	private static List<RadixNode> getConnectedNodes(Set<Long> shards, RadixNetworkState state) {
		return state.getNodes().entrySet().stream()
			.filter(entry -> entry.getValue().getStatus().equals(WebSocketStatus.CONNECTED))
			.filter(entry -> entry.getValue().getShards().map(s -> s.intersects(shards)).orElse(false))
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> stateObservable) {
		return actions
			.filter(a -> a instanceof FindANodeRequestAction)
			.map(FindANodeRequestAction.class::cast)
			.flatMap(a -> {
				Observable<RadixNetworkState> syncNetState = stateObservable
					.replay(1)
					.autoConnect(2);

				Observable<List<RadixNode>> connectedNodes = syncNetState
					.map(state -> getConnectedNodes(a.getShards(), state))
					.replay(1)
					.autoConnect(2);

				// Try and connect if there are no nodes
				Observable<RadixNodeAction> newConnections = syncNetState
					.zipWith(connectedNodes.takeWhile(List::isEmpty), (s, n) -> s)
					.flatMapMaybe(state -> findConnection(a.getShards(), state));

				Observable<RadixNodeAction> selectedNode = connectedNodes
					.filter(viablePeerList -> !viablePeerList.isEmpty())
					.firstOrError()
					.map(selector::apply)
					.<RadixNodeAction>map(n -> new FindANodeResultAction(n, a))
					.cache()
					.toObservable();

				return newConnections.takeUntil(selectedNode).concatWith(selectedNode);
			});
	}
}
