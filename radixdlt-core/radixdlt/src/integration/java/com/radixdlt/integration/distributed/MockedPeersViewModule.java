/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.distributed;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.network.p2p.PeersView;

public class MockedPeersViewModule extends AbstractModule {

	private final ImmutableMap<Integer, ImmutableList<Integer>> nodes;

	public MockedPeersViewModule(ImmutableMap<Integer, ImmutableList<Integer>> nodes) {
		this.nodes = nodes;
	}

	@Provides
	public PeersView peersView(@Self BFTNode self, ImmutableList<BFTNode> nodes) {
		final var nodesFiltered = filterNodes(self, nodes);
		return () -> nodesFiltered.stream()
			.filter(n -> !n.equals(self))
			.map(PeersView.PeerInfo::fromBftNode);
	}

	private ImmutableList<BFTNode> filterNodes(BFTNode self, ImmutableList<BFTNode> allNodes) {
		final var selfIndex = allNodes.indexOf(self);
		if (nodes != null && nodes.containsKey(selfIndex)) {
			return nodes.get(selfIndex).stream().map(allNodes::get).collect(ImmutableList.toImmutableList());
		} else {
			return allNodes;
		}
	}
}
