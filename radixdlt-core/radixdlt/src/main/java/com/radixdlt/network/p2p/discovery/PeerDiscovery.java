/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.p2p.discovery;

import com.radixdlt.environment.EventProcessor;
import com.radixdlt.network.p2p.PeerManager;
import com.radixdlt.network.p2p.addressbook.AddressBook;
import org.radix.network.discovery.SeedNodesConfigParser;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Objects;

import static java.util.function.Predicate.not;

/**
 * Discovers peers network addresses and adds them to the address book.
 * Initial (seed) peers are "discovered" from the config (bootstrapDiscovery)
 * and more peers are requested from the peers we're already connected to.
 */
@Singleton
public final class PeerDiscovery {
	private final PeerManager peerManager;
	private final AddressBook addressBook;
	private final SeedNodesConfigParser seedNodesConfigParser;

	@Inject
	public PeerDiscovery(
		PeerManager peerManager,
		AddressBook addressBook,
		SeedNodesConfigParser seedNodesConfigParser
	) {
		this.peerManager = Objects.requireNonNull(peerManager);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.seedNodesConfigParser = Objects.requireNonNull(seedNodesConfigParser);
	}

	public EventProcessor<DiscoverPeers> discoverPeersEventProcessor() {
		return unused -> {
			final var seedNodes = seedNodesConfigParser.getResolvedSeedNodes();
			this.addressBook.addUncheckedPeers(seedNodes);

			this.tryConnectToSomeKnownPeers();
		};
	}

	private void tryConnectToSomeKnownPeers() {
		final var remainingSlots = this.peerManager.getRemainingOutboundSlots();
		final var maxSlotsToUse = (remainingSlots / 2) - 5; // let's always leave a few free slots
		this.addressBook.bestKnownEntries()
			.filter(not(e -> peerManager.isPeerConnected(e.getNodeId())))
			.limit(maxSlotsToUse)
			.forEach(this.peerManager::tryConnect);
	}
}
