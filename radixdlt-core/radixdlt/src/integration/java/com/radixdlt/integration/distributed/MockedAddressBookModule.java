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
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerWithSystem;
import org.radix.Radix;
import org.radix.universe.system.RadixSystem;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockedAddressBookModule extends AbstractModule {

	@Provides
	public AddressBook addressBook(ImmutableList<BFTNode> nodes) {
		final var addressBook = mock(AddressBook.class);
		when(addressBook.hasBftNodePeer(any())).thenReturn(true);
		when(addressBook.peers()).thenAnswer(inv -> nodes.stream().map(node ->
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

}
