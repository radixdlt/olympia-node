package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.GetLivePeersAction;
import com.radixdlt.client.core.network.actions.GetLivePeersAction.GetLivePeersActionType;
import com.radixdlt.client.core.network.actions.GetNodeDataAction;
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
			.filter(u -> u instanceof SubmitAtomAction || u instanceof FetchAtomsObservationAction)
			.firstOrError()
			.flatMapObservable(i -> seeds)
			.publish()
			.autoConnect(3);

		Observable<RadixNodeAction> addSeeds = connectedSeeds.map(NodeUpdate::add);
		Observable<RadixNodeAction> addSeedData = connectedSeeds.map(GetNodeDataAction::request);
		Observable<RadixNodeAction> addSeedSiblings = connectedSeeds.map(GetLivePeersAction::request);

		Observable<RadixNodeAction> addNodes = updates
			.filter(u -> u instanceof GetLivePeersAction)
			.map(GetLivePeersAction.class::cast)
			.filter(u -> u.getType().equals(GetLivePeersActionType.GET_LIVE_PEERS_RESULT))
			.flatMap(u ->
				Observable.fromIterable(u.getResult())
					.map(d -> NodeUpdate.add(new RadixNode(d.getIp(), u.getNode().isSsl(), u.getNode().getPort()), d))
			);

		return addSeeds.mergeWith(addSeedData).mergeWith(addSeedSiblings).mergeWith(addNodes);
	}
}
