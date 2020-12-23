/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.actions.AddNodeAction;
import com.radixdlt.client.core.network.actions.DiscoverMoreNodesAction;
import com.radixdlt.client.core.network.actions.DiscoverMoreNodesErrorAction;
import com.radixdlt.client.core.network.actions.GetLivePeersRequestAction;
import com.radixdlt.client.core.network.actions.GetLivePeersResultAction;
import com.radixdlt.client.core.network.actions.GetNodeDataRequestAction;
import com.radixdlt.client.core.network.actions.GetUniverseRequestAction;
import com.radixdlt.client.core.network.actions.GetUniverseResponseAction;
import com.radixdlt.client.core.network.actions.NodeUniverseMismatch;
import io.reactivex.Observable;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

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
			.<RadixNodeAction>map(GetUniverseRequestAction::of)
			.onErrorReturn(DiscoverMoreNodesErrorAction::new);

		// TODO: Store universes in node table instead and filter out node in FindANodeEpic
		Observable<RadixNodeAction> seedUniverseMismatch = updates
			.ofType(GetUniverseResponseAction.class)
			.filter(u -> !u.getResult().equals(config))
			.map(u -> new NodeUniverseMismatch(u.getNode(), config, u.getResult()));

		Observable<RadixNode> connectedSeeds = updates
			.ofType(GetUniverseResponseAction.class)
			.filter(u -> u.getResult().equals(config))
			.map(GetUniverseResponseAction::getNode)
			.publish()
			.autoConnect(3);

		Observable<RadixNodeAction> addSeeds = connectedSeeds.map(AddNodeAction::of);
		Observable<RadixNodeAction> addSeedData = connectedSeeds.map(GetNodeDataRequestAction::of);
		Observable<RadixNodeAction> addSeedSiblings = connectedSeeds.map(GetLivePeersRequestAction::of);

		Observable<RadixNodeAction> addNodes = updates
			.ofType(GetLivePeersResultAction.class)
			.flatMap(u ->
				Observable.combineLatest(
					Observable.just(u.getResult()),
					Observable.concat(networkState.firstOrError().toObservable(), Observable.never()),
					(data, state) ->
						data.stream()
							.map(d -> {
								RadixNode node = new RadixNode(d.getIp(), u.getNode().isSsl(), u.getNode().getPort());
								return state.getNodeStates().containsKey(node) ? null : AddNodeAction.of(node, d);
							})
							.filter(Objects::nonNull)
							.collect(Collectors.toSet())
				).flatMapIterable(i -> i)
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
