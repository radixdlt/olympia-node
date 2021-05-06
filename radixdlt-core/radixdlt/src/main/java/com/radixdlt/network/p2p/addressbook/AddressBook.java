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

package com.radixdlt.network.p2p.addressbook;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.RadixNodeUri;

import javax.inject.Singleton;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

/**
 * Manages known peers network addresses and their metadata.
 */
@Singleton
public final class AddressBook {
	private static final Comparator<AddressBookEntry.PeerAddressEntry> entryComparator = (a, b) -> {
		final var aLastSuccess = a.getLastSuccessfulConnection().orElse(Instant.MIN);
		final var bLastSuccess = b.getLastSuccessfulConnection().orElse(Instant.MIN);
		return -aLastSuccess.compareTo(bLastSuccess);
	};

	private final NodeId selfNodeId;
	private final AddressBookPersistence persistence;
	private final Object lock = new Object();
	private final Map<NodeId, AddressBookEntry> knownPeers = new HashMap<>();

	@Inject
	public AddressBook(
		@Self BFTNode selfNodeId,
		AddressBookPersistence persistence
	) {
		this.selfNodeId = NodeId.fromPublicKey(Objects.requireNonNull(selfNodeId).getKey());
		this.persistence = Objects.requireNonNull(persistence);
		persistence.getAllEntries().forEach(e -> knownPeers.put(e.getNodeId(), e));
	}

	public void addUncheckedPeers(Set<RadixNodeUri> peers) {
		synchronized (lock) {
			peers
				.stream()
				.filter(not(uri -> uri.getNodeId().equals(this.selfNodeId)))
				.forEach(uri -> {
					final var maybeExistingEntry = this.knownPeers.get(uri.getNodeId());
					if (maybeExistingEntry == null) {
						final var newEntry = AddressBookEntry.create(uri);
						this.knownPeers.put(uri.getNodeId(), newEntry);
						persistEntry(newEntry);
					} else {
						final var updatedEntry = maybeExistingEntry.addUriIfNotExists(uri);
						if (!updatedEntry.equals(maybeExistingEntry)) {
							this.knownPeers.put(uri.getNodeId(), updatedEntry);
							persistEntry(updatedEntry);
						}
					}
				});
		}
	}

	public Optional<AddressBookEntry> findById(NodeId nodeId) {
		return Optional.ofNullable(this.knownPeers.get(nodeId));
	}

	public Optional<RadixNodeUri> findBestKnownAddressById(NodeId nodeId) {
		synchronized (lock) {
			return Optional.ofNullable(this.knownPeers.get(nodeId))
				.stream()
				.filter(not(AddressBookEntry::isBanned))
				.flatMap(e -> e.getKnownAddresses().stream())
				.sorted(entryComparator)
				.map(AddressBookEntry.PeerAddressEntry::getUri)
				.findFirst();
		}
	}

	public void addOrUpdateSuccessfullyConnectedPeer(RadixNodeUri radixNodeUri) {
		synchronized (lock) {
			final var maybeExistingEntry = this.knownPeers.get(radixNodeUri.getNodeId());
			if (maybeExistingEntry == null) {
				final var newEntry = AddressBookEntry.create(radixNodeUri, Instant.now());
				this.knownPeers.put(radixNodeUri.getNodeId(), newEntry);
				persistEntry(newEntry);
			} else {
				final var updatedEntry = maybeExistingEntry.withLastSuccessfulConnectionFor(radixNodeUri, Instant.now());
				this.knownPeers.put(radixNodeUri.getNodeId(), updatedEntry);
				persistEntry(updatedEntry);
			}
		}
	}

	public Stream<RadixNodeUri> bestCandidatesToConnect() {
		return this.knownPeers.values()
			.stream()
			.filter(not(AddressBookEntry::isBanned))
			.flatMap(e -> e.getKnownAddresses().stream())
			.sorted(entryComparator)
			.map(AddressBookEntry.PeerAddressEntry::getUri);
	}

	private void persistEntry(AddressBookEntry entry) {
		this.persistence.removeEntry(entry.getNodeId());
		this.persistence.saveEntry(entry);
	}
}
