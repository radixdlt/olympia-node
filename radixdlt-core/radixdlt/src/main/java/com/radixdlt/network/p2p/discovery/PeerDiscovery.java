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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.network.p2p.PeerManager;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.addressbook.AddressBook;
import org.radix.network.discovery.SeedNodesConfigParser;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

/**
 * Discovers peers network addresses and adds them to the address book.
 * Initial (seed) peers are "discovered" from the config (bootstrapDiscovery)
 * and more peers are requested from the peers we're already connected to.
 */
@Singleton
public final class PeerDiscovery {
	private static final int MAX_PEERS_IN_RESPONSE = 50;
	private static final int MAX_REQUESTS_SENT_AT_ONCE = 5;

	private final RadixNodeUri selfUri;
	private final PeerManager peerManager;
	private final AddressBook addressBook;
	private final SeedNodesConfigParser seedNodesConfigParser;
	private final RemoteEventDispatcher<GetPeers> getPeersRemoteEventDispatcher;
	private final RemoteEventDispatcher<PeersResponse> peersResponseRemoteEventDispatcher;

	@Inject
	public PeerDiscovery(
		@Self RadixNodeUri selfUri,
		PeerManager peerManager,
		AddressBook addressBook,
		SeedNodesConfigParser seedNodesConfigParser,
		RemoteEventDispatcher<GetPeers> getPeersRemoteEventDispatcher,
		RemoteEventDispatcher<PeersResponse> peersResponseRemoteEventDispatcher
	) {
		this.selfUri = Objects.requireNonNull(selfUri);
		this.peerManager = Objects.requireNonNull(peerManager);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.seedNodesConfigParser = Objects.requireNonNull(seedNodesConfigParser);
		this.getPeersRemoteEventDispatcher = Objects.requireNonNull(getPeersRemoteEventDispatcher);
		this.peersResponseRemoteEventDispatcher = Objects.requireNonNull(peersResponseRemoteEventDispatcher);
	}

	public EventProcessor<DiscoverPeers> discoverPeersEventProcessor() {
		return unused -> {
			final var seedNodes = seedNodesConfigParser.getResolvedSeedNodes();
			this.addressBook.addUncheckedPeers(seedNodes);

			final var peersToAsk = new ArrayList<>(this.peerManager.activePeers());

			Collections.shuffle(peersToAsk);
			peersToAsk.stream()
				.limit(MAX_REQUESTS_SENT_AT_ONCE)
				.forEach(peer ->
					getPeersRemoteEventDispatcher.dispatch(
						BFTNode.create(peer.getRemoteNodeId().getPublicKey()), GetPeers.create())
				);

			this.tryConnectToSomeKnownPeers();
		};
	}

	private void tryConnectToSomeKnownPeers() {
		final var remainingSlots = this.peerManager.getRemainingOutboundSlots();
		final var maxSlotsToUse = Math.max(0, (remainingSlots / 2) - 2); // let's always leave some free slots
		this.addressBook.bestCandidatesToConnect()
			.filter(not(e -> peerManager.isPeerConnected(e.getNodeId())))
			.limit(maxSlotsToUse)
			.forEach(this.peerManager::tryConnect);
	}

	public RemoteEventProcessor<PeersResponse> peersResponseRemoteEventProcessor() {
		return (sender, peersResponse) -> {
			this.addressBook.addUncheckedPeers(peersResponse.getPeers());
		};
	}

	public RemoteEventProcessor<GetPeers> getPeersRemoteEventProcessor() {
		return (sender, unused) -> {
			final var peers =
				Stream.concat(
					Stream.of(selfUri),
					this.addressBook.bestCandidatesToConnect()
						.limit(MAX_PEERS_IN_RESPONSE - 1)
				).collect(ImmutableSet.toImmutableSet());

			peersResponseRemoteEventDispatcher.dispatch(sender, PeersResponse.create(peers));
		};
	}
}
