package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.AddNodeAction;
import com.radixdlt.client.core.network.actions.DiscoverMoreNodesAction;
import com.radixdlt.client.core.network.actions.GetNodeDataRequestAction;
import com.radixdlt.client.core.network.actions.GetUniverseRequestAction;
import com.radixdlt.client.core.network.actions.GetUniverseResponseAction;
import com.radixdlt.client.core.network.actions.NodeUniverseMismatch;

import io.reactivex.Observable;
import java.util.Arrays;

/**
 * Epic which bootstraps to a single well known node
 */
public class DiscoverSingleNodeEpic implements RadixNetworkEpic {
	private final RadixNode node;
	private final RadixUniverseConfig config;

	public DiscoverSingleNodeEpic(RadixNode node, RadixUniverseConfig config) {
		this.node = node;
		this.config = config;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> updates, Observable<RadixNetworkState> networkState) {
		Observable<RadixNodeAction> getUniverse = updates
			.ofType(DiscoverMoreNodesAction.class)
			.map(a -> GetUniverseRequestAction.of(node));

		// TODO: Store universes in node table instead and filter out node in FindANodeEpic
		Observable<RadixNodeAction> seedUniverseMismatch = updates
			.ofType(GetUniverseResponseAction.class)
			.filter(u -> !u.getResult().equals(config))
			.map(u -> new NodeUniverseMismatch(u.getNode(), config, u.getResult()));

		Observable<RadixNode> connected = updates
			.ofType(GetUniverseResponseAction.class)
			.filter(u -> u.getResult().equals(config))
			.filter(u -> u.getNode().equals(node))
			.map(GetUniverseResponseAction::getNode)
			.publish()
			.autoConnect(2);


		Observable<RadixNodeAction> addNode = connected.map(AddNodeAction::of);
		Observable<RadixNodeAction> addData = connected.map(GetNodeDataRequestAction::of);

		return Observable.merge(Arrays.asList(
			seedUniverseMismatch,
			getUniverse,
			addNode,
			addData
		));
	}
}
