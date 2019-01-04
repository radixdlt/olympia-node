package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.ledger.selector.RadixPeerSelector;
import com.radixdlt.client.core.network.RadixClientStatus;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixPeer;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindANodeMiniEpic {

	private static final Logger LOGGER = LoggerFactory.getLogger(AtomSubmitFindANodeEpic.class);
	private final RadixNetwork network;
	private final RadixPeerSelector selector;

	public FindANodeMiniEpic(RadixNetwork network, RadixPeerSelector selector) {
		this.network = network;
		this.selector = selector;
	}

	// TODO: check shards
	private void findConnection(Set<Long> shards, RadixNetworkState state) {
		final Map<RadixClientStatus,List<RadixPeer>> statusMap = Arrays.stream(RadixClientStatus.values())
			.collect(Collectors.toMap(
				Function.identity(),
				s -> state.getPeers().entrySet().stream().filter(e -> e.getValue().equals(s)).map(Entry::getKey).collect(Collectors.toList())
			));

		final long activeNodeCount =
			statusMap.get(RadixClientStatus.CONNECTED).size()
				+ statusMap.get(RadixClientStatus.CONNECTING).size();

		if (activeNodeCount < 1) {
			LOGGER.info(String.format("Requesting more node connections, want %d but have %d active nodes", 1, activeNodeCount));

			List<RadixPeer> disconnectedPeers = statusMap.get(RadixClientStatus.DISCONNECTED);
			if (disconnectedPeers.isEmpty()) {
				LOGGER.info("Could not connect to new peer, don't have any.");
			} else {
				network.connect(disconnectedPeers.get(0));
			}
		}
	}

	private static List<RadixPeer> getConnectedNodes(Set<Long> shards, RadixNetworkState state) {
		return state.getPeers().entrySet().stream()
			.filter(entry -> entry.getValue().equals(RadixClientStatus.CONNECTED))
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());
	}

	public Single<RadixPeer> apply(Set<Long> shards, Observable<RadixNetworkState> networkState) {
		Observable<RadixNetworkState> syncNetState = networkState
			.replay(1)
			.autoConnect(2);

		Observable<List<RadixPeer>> connectedNodes = syncNetState
			.map(state -> getConnectedNodes(shards, state))
			.replay(1)
			.autoConnect(2);

		// Try and connect if there are no nodes
		syncNetState.zipWith(connectedNodes.takeWhile(List::isEmpty), (s, n) -> s)
			.subscribe(state -> findConnection(shards, state));

		return connectedNodes
			.filter(viablePeerList -> !viablePeerList.isEmpty())
			.firstOrError()
			.map(selector::apply);
	}
}
