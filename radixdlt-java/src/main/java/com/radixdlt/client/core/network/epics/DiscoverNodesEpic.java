package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixClientStatus;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixPeer;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate;
import com.radixdlt.client.core.network.actions.AddNodeAction;
import io.reactivex.Observable;

public class DiscoverNodesEpic implements RadixNetworkEpic {
	private final RadixNetwork network;
	private final Observable<RadixPeer> seeds;

	public DiscoverNodesEpic(RadixNetwork network, Observable<RadixPeer> seeds) {
		this.network = network;
		this.seeds = seeds;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> updates, Observable<RadixNetworkState> networkState) {
		Observable<RadixPeer> connectedSeeds = updates
			.filter(u -> u instanceof AtomSubmissionUpdate || u instanceof AtomsFetchUpdate)
			.firstOrError()
			.flatMapObservable(i -> seeds)
			.publish()
			.autoConnect(2);

		Observable<RadixNodeAction> addSeeds = connectedSeeds.map(AddNodeAction::new);

		Observable<RadixNodeAction> addSeedSiblings = connectedSeeds.flatMap(s ->
				networkState
					.filter(state -> state.getPeers().get(s) == RadixClientStatus.CONNECTED)
					.firstOrError()
					.flatMapObservable(i -> {
						RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(network.getWsChannel(s));
						return jsonRpcClient.getLivePeers()
							.toObservable()
							.flatMapIterable(p -> p)
							.map(data -> new RadixPeer(data.getIp(), s.isSsl(), s.getPort()))
							.map(AddNodeAction::new);
					})
			);

		return addSeeds.mergeWith(addSeedSiblings);
	}
}
