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
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.addressbook.PeersView;
import org.radix.Radix;
import org.radix.universe.system.RadixSystem;

import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockedAddressBookModule extends AbstractModule {

	private final ImmutableMap<Integer, ImmutableList<Integer>> addressBookNodes;

	public MockedAddressBookModule(ImmutableMap<Integer, ImmutableList<Integer>> addressBookNodes) {
		this.addressBookNodes = addressBookNodes;
	}

	@Provides
	public AddressBook addressBook(@Self BFTNode self, ImmutableList<BFTNode> nodes) {
		final var nodesFiltered = filterNodes(self, nodes);
		final var addressBook = mock(AddressBook.class);
		when(addressBook.hasBftNodePeer(any())).thenAnswer(arg -> nodesFiltered.contains(arg.getArgument(0)));
		when(addressBook.peers()).thenAnswer(inv -> nodesFiltered.stream().map(node ->
			new PeerWithSystem(new RadixSystem(
				node.getKey(),
				Radix.AGENT,
				Radix.AGENT_VERSION,
				Radix.PROTOCOL_VERSION,
				ImmutableList.of()
			))
		));

		return addressBook;
	}

	@Provides
	public PeersView peersView(@Self BFTNode self, ImmutableList<BFTNode> nodes) {
		final var nodesFiltered = filterNodes(self, nodes);
		return () -> nodesFiltered.stream()
			.filter(n -> !n.equals(self))
			.collect(Collectors.toList());
	}

	private ImmutableList<BFTNode> filterNodes(BFTNode self, ImmutableList<BFTNode> allNodes) {
		final var selfIndex = allNodes.indexOf(self);
		if (addressBookNodes != null && addressBookNodes.containsKey(selfIndex)) {
			return addressBookNodes.get(selfIndex).stream().map(allNodes::get).collect(ImmutableList.toImmutableList());
		} else {
			return allNodes;
		}
	}
}
