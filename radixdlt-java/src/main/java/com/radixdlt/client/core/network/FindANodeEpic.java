package com.radixdlt.client.core.network;

import com.radixdlt.client.core.ledger.selector.RadixPeerSelector;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindANodeEpic implements AtomSubmissionEpic {
	private static final Logger LOGGER = LoggerFactory.getLogger(FindANodeEpic.class);
	private final RadixNetwork network;
	private final RadixPeerSelector selector;

	public FindANodeEpic(RadixNetwork network, RadixPeerSelector selector) {
		this.network = network;
		this.selector = selector;
	}

	// TODO: check shards
	private void findConnection(RadixNetworkState state) {
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

	private static List<RadixPeer> getConnectedNodes(RadixNetworkState state) {
		return state.getPeers().entrySet().stream()
			.filter(entry -> entry.getValue().equals(RadixClientStatus.CONNECTED))
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());
	}

	public Observable<AtomSubmissionUpdate> epic(Observable<AtomSubmissionUpdate> updates, Observable<RadixNetworkState> networkState) {
		return updates.filter(update -> update.getState().equals(AtomSubmissionState.SEARCHING_FOR_NODE))
			.flatMapSingle(searchUpdate -> {
				Observable<RadixNetworkState> syncNetState = networkState
					.replay(1)
					.autoConnect(2);

				Observable<List<RadixPeer>> connectedNodes = syncNetState
					.map(FindANodeEpic::getConnectedNodes)
					.replay(1)
					.autoConnect(2);

				// Try and connect if there are no nodes
				syncNetState.zipWith(connectedNodes.takeWhile(List::isEmpty), (s, n) -> s)
					.subscribe(this::findConnection);

				Single<RadixPeer> selectedNode = connectedNodes
					.filter(viablePeerList -> !viablePeerList.isEmpty())
					.firstOrError()
					.map(selector::apply);

				return selectedNode.map(n -> AtomSubmissionUpdate.create(searchUpdate.getAtom(), AtomSubmissionState.SUBMITTING, n));
			});
	}

}
