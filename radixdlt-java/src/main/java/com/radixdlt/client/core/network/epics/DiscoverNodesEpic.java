package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate;
import com.radixdlt.client.core.network.actions.GetLivePeers;
import com.radixdlt.client.core.network.actions.GetLivePeers.GetLivePeersType;
import com.radixdlt.client.core.network.actions.GetNodeData;
import com.radixdlt.client.core.network.actions.NodeUpdate;
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
			.autoConnect(3);

		Observable<RadixNodeAction> addSeeds = connectedSeeds.map(NodeUpdate::add);
		Observable<RadixNodeAction> addSeedData = connectedSeeds.map(GetNodeData::request);
		Observable<RadixNodeAction> addSeedSiblings = connectedSeeds.map(GetLivePeers::request);

		Observable<RadixNodeAction> addNodes = updates
			.filter(u -> u instanceof GetLivePeers)
			.map(GetLivePeers.class::cast)
			.filter(u -> u.getType().equals(GetLivePeersType.GET_LIVE_PEERS_RESULT))
			.flatMap(u ->
				Observable.fromIterable(u.getResult())
					.map(d -> NodeUpdate.add(new RadixNode(d.getIp(), u.getNode().isSsl(), u.getNode().getPort()), d))
			);

		return addSeeds.mergeWith(addSeedData).mergeWith(addSeedSiblings).mergeWith(addNodes);
	}
}
