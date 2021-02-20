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
 *
 */

package com.radixdlt.network.addressbook;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.identifiers.EUID;
import org.radix.universe.system.LocalSystem;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A Peers view using an address book
 */
public final class AddressBookPeersView implements PeersView {
	private final PeerWithSystem localPeer;
	private final AddressBook addressBook;

	@Inject
	public AddressBookPeersView(
		AddressBook addressBook,
		LocalSystem system
	) {
		this.addressBook = addressBook;
		this.localPeer = new PeerWithSystem(system);
	}

	@Override
	public List<BFTNode> peers() {
		final EUID self = this.localPeer.getNID();
		return this.addressBook.peers()
			.filter(Peer::hasSystem) // Only peers with systems (and therefore transports)
			.filter(p -> !self.equals(p.getNID())) // Exclude self, already sent
			.map(p -> BFTNode.create(p.getSystem().getKey()))
			.collect(Collectors.toList());
	}
}
