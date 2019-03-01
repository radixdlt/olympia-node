package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.actions.AddNodeAction;
import com.radixdlt.client.core.network.actions.DiscoverMoreNodesAction;
import com.radixdlt.client.core.network.actions.GetLivePeersRequestAction;
import com.radixdlt.client.core.network.actions.GetLivePeersResultAction;
import com.radixdlt.client.core.network.actions.GetNodeDataRequestAction;
import com.radixdlt.client.core.network.actions.GetUniverseRequestAction;
import com.radixdlt.client.core.network.actions.GetUniverseResultAction;
import com.radixdlt.client.core.network.actions.NodeUniverseMismatch;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Epic which manages simple bootstrapping and discovers nodes one degree out from the initial seeds.
 */
public final class DiscoverNodesEpic implements RadixNetworkEpic {
	private final Observable<RadixNode> seeds;
	private final RadixUniverseConfig config;

	public DiscoverNodesEpic(Observable<RadixNode> seeds, RadixUniverseConfig config) {
		this.seeds = Objects.requireNonNull(seeds);
		this.config = Objects.requireNonNull(config);
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> updates, Observable<RadixNetworkState> networkState) {
		Observable<RadixNodeAction> getSeedUniverses = updates
			.ofType(DiscoverMoreNodesAction.class)
			.firstOrError()
			.flatMapObservable(i -> seeds)
			.map(GetUniverseRequestAction::of);

		// TODO: Store universes in node table instead and filter out node in FindANodeEpic
		Observable<RadixNodeAction> seedUniverseMismatch = updates
			.ofType(GetUniverseResultAction.class)
			.filter(u -> !u.getResult().equals(config))
			.map(u -> new NodeUniverseMismatch(u.getNode(), config, u.getResult()));

		Observable<RadixNode> connectedSeeds = updates
			.ofType(GetUniverseResultAction.class)
			.filter(u -> u.getResult().equals(config))
			.map(GetUniverseResultAction::getNode)
			.publish()
			.autoConnect(3);

		Observable<RadixNodeAction> addSeeds = connectedSeeds.map(AddNodeAction::of);
		Observable<RadixNodeAction> addSeedData = connectedSeeds.map(GetNodeDataRequestAction::of);
		Observable<RadixNodeAction> addSeedSiblings = connectedSeeds.map(GetLivePeersRequestAction::of);

		Observable<RadixNodeAction> addNodes = updates
			.ofType(GetLivePeersResultAction.class)
			.flatMap(u ->
				Observable.combineLatest(
					Observable.fromIterable(u.getResult()),
					networkState.firstOrError().toObservable(),
					(data, s) -> {
						RadixNode node = new RadixNode(data.getIp(), u.getNode().isSsl(), u.getNode().getPort());

						if (!s.getNodes().containsKey(node)) {
							return Maybe.just(AddNodeAction.of(node, data));
						} else {
							return Maybe.<RadixNodeAction>empty();
						}
					}
				).flatMapMaybe(i -> i)
			);

		return Observable.merge(Arrays.asList(
			addSeeds,
			addSeedData,
			addSeedSiblings,
			addNodes,
			getSeedUniverses,
			seedUniverseMismatch
		));
	}
}
