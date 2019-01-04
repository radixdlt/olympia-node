package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.ledger.selector.RadixPeerSelector;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.RadixClientStatus;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixPeer;
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

public class AtomSubmitFindANodeEpic implements RadixNetworkEpic {
	private static final Logger LOGGER = LoggerFactory.getLogger(AtomSubmitFindANodeEpic.class);
	private final RadixNetwork network;
	private final RadixPeerSelector selector;

	public AtomSubmitFindANodeEpic(RadixNetwork network, RadixPeerSelector selector) {
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

	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> updates, Observable<RadixNetworkState> networkState) {
		return updates
			.filter(u -> u instanceof AtomSubmissionUpdate)
			.map(AtomSubmissionUpdate.class::cast)
			.filter(update -> update.getState().equals(AtomSubmissionState.SEARCHING_FOR_NODE))
			.flatMapSingle(searchUpdate -> {
				Observable<RadixNetworkState> syncNetState = networkState
					.replay(1)
					.autoConnect(2);

				Observable<List<RadixPeer>> connectedNodes = syncNetState
					.map(AtomSubmitFindANodeEpic::getConnectedNodes)
					.replay(1)
					.autoConnect(2);

				// Try and connect if there are no nodes
				syncNetState.zipWith(connectedNodes.takeWhile(List::isEmpty), (s, n) -> s)
					.subscribe(this::findConnection);

				Single<RadixPeer> selectedNode = connectedNodes
					.filter(viablePeerList -> !viablePeerList.isEmpty())
					.firstOrError()
					.map(selector::apply);

				return selectedNode.map(n ->
					AtomSubmissionUpdate.submit(searchUpdate.getUuid(), searchUpdate.getAtom(), n)
				);
			});
	}

}
