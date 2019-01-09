package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.CloseWebSocketAction;
import com.radixdlt.client.core.network.actions.ConnectWebSocketAction;
import com.radixdlt.client.core.network.actions.DiscoverMoreNodesAction;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Epic which finds a connected sharded node when a FindANode request is received. If there are none found,
 * then the epic attempts to start connections.
 */
public final class FindANodeEpic implements RadixNetworkEpic {
	private static final int MAX_SIMULTANEOUS_CONNECTION_REQUESTS = 2;
	private static final int NEXT_CONNECTION_THROTTLE_TIMEOUT_SECS = 1;

	private static final Logger LOGGER = LoggerFactory.getLogger(FindANodeEpic.class);
	private final RadixPeerSelector selector;

	public FindANodeEpic(RadixPeerSelector selector) {
		this.selector = selector;
	}

	private Maybe<RadixNodeAction> nextConnectionRequest(Set<Long> shards, RadixNetworkState state) {
		final Map<WebSocketStatus, List<RadixNode>> statusMap = Arrays.stream(WebSocketStatus.values())
			.collect(Collectors.toMap(
				Function.identity(),
				s -> state.getNodes().entrySet().stream()
					.filter(entry -> entry.getValue().getShards().map(sh -> sh.intersects(shards)).orElse(false))
					.filter(e -> e.getValue().getStatus().equals(s))
					.map(Entry::getKey)
					.collect(Collectors.toList())
			));

		final long connectingNodeCount = statusMap.get(WebSocketStatus.CONNECTING).size();

		if (connectingNodeCount < MAX_SIMULTANEOUS_CONNECTION_REQUESTS) {

			List<RadixNode> disconnectedPeers = statusMap.get(WebSocketStatus.DISCONNECTED);
			if (disconnectedPeers.isEmpty()) {
				return Maybe.just(DiscoverMoreNodesAction.instance());
			} else {
				return Maybe.just(ConnectWebSocketAction.of(selector.apply(disconnectedPeers)));
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
		return actions.ofType(FindANodeRequestAction.class)
			.flatMap(a -> {
				Observable<List<RadixNode>> connectedNodes = stateObservable
					.map(state -> getConnectedNodes(a.getShards(), state))
					.replay(1)
					.autoConnect(2);

				// Stream to find node
				Observable<RadixNodeAction> selectedNode = connectedNodes
					.filter(viablePeerList -> !viablePeerList.isEmpty())
					.firstOrError()
					.map(selector::apply)
					.<RadixNodeAction>map(n -> new FindANodeResultAction(n, a))
					.cache()
					.toObservable();

				// Stream of new actions to find a new node
				Observable<RadixNodeAction> findConnectionActionsStream = connectedNodes
					.filter(List::isEmpty)
					.firstOrError()
					.ignoreElement()
					.andThen(
						Observable
							.interval(0, NEXT_CONNECTION_THROTTLE_TIMEOUT_SECS, TimeUnit.SECONDS)
							.withLatestFrom(stateObservable, (i, s) -> s)
							.flatMapMaybe(state -> nextConnectionRequest(a.getShards(), state))
					)
					.takeUntil(selectedNode)
					.replay(1)
					.autoConnect(2);

				// Cleanup and close connections which never worked out
				Observable<RadixNodeAction> cleanupConnections = findConnectionActionsStream
					.ofType(ConnectWebSocketAction.class)
					.flatMap(c -> {
						final RadixNode node = c.getNode();
						return selectedNode
							.map(RadixNodeAction::getNode)
							.filter(selected -> !node.equals(selected))
							.map(i -> CloseWebSocketAction.of(node));
					});

				return findConnectionActionsStream.concatWith(selectedNode).mergeWith(cleanupConnections);
			});
	}
}
