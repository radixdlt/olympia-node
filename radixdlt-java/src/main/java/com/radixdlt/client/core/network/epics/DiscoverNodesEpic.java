package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNodeStatus;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate;
import com.radixdlt.client.core.network.actions.GetLivePeers;
import com.radixdlt.client.core.network.actions.GetNodeData;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import io.reactivex.Observable;

public class DiscoverNodesEpic implements RadixNetworkEpic {
	private final Observable<RadixNode> seeds;

	public DiscoverNodesEpic(Observable<RadixNode> seeds) {
		this.seeds = seeds;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> updates, Observable<RadixNetworkState> networkState) {
		Observable<RadixNode> connectedSeeds = updates
			.filter(u -> u instanceof AtomSubmissionUpdate || u instanceof AtomsFetchUpdate)
			.firstOrError()
			.flatMapObservable(i -> seeds)
			.publish()
			.autoConnect(2);

		Observable<RadixNodeAction> addSeeds = connectedSeeds.map(NodeUpdate::add);

		Observable<RadixNodeAction> addSeedSiblings = connectedSeeds.flatMapSingle(s ->
			networkState
				.filter(state -> state.getPeers().get(s) == RadixNodeStatus.CONNECTED)
				.firstOrError()
				.map(i -> GetLivePeers.request(s))
		);

		Observable<RadixNodeAction> getData = updates
			.filter(u -> u instanceof NodeUpdate)
			.map(NodeUpdate.class::cast)
			.filter(u -> u.getType().equals(NodeUpdateType.ADD_NODE))
			.map(NodeUpdate::getNode)
			.flatMapSingle(node ->
				networkState
					.filter(state -> state.getPeers().get(node) == RadixNodeStatus.CONNECTED)
					.firstOrError()
					.map(i -> GetNodeData.request(node))
			);

		return addSeeds.mergeWith(addSeedSiblings).mergeWith(getData);
	}
}
