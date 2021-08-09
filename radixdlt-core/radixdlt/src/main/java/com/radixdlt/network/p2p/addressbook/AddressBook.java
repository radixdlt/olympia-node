/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.network.p2p.addressbook;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.PeerEvent.PeerBanned;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.addressbook.AddressBookEntry.PeerAddressEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

/**
 * Manages known peers network addresses and their metadata.
 */
public final class AddressBook {
	private static final Comparator<AddressBookEntry.PeerAddressEntry> entryComparator = (a, b) -> {
		final var aLastSuccess = a.getLastSuccessfulConnection().orElse(Instant.MIN);
		final var bLastSuccess = b.getLastSuccessfulConnection().orElse(Instant.MIN);
		return -aLastSuccess.compareTo(bLastSuccess);
	};

	private final RadixNodeUri self;
	private final EventDispatcher<PeerEvent> peerEventDispatcher;
	private final AddressBookPersistence persistence;
	private final Object lock = new Object();
	private final Map<NodeId, AddressBookEntry> knownPeers = new ConcurrentHashMap<>();

	@Inject
	public AddressBook(
		@Self RadixNodeUri self,
		EventDispatcher<PeerEvent> peerEventDispatcher,
		AddressBookPersistence persistence
	) {
		this.self = Objects.requireNonNull(self);
		this.peerEventDispatcher = Objects.requireNonNull(peerEventDispatcher);
		this.persistence = Objects.requireNonNull(persistence);
		persistence.getAllEntries().forEach(e -> knownPeers.put(e.getNodeId(), e));
		cleanup();
	}

	private void cleanup() {
		final var cleanedUpEntries = new ImmutableMap.Builder<NodeId, AddressBookEntry>();
		this.knownPeers.values().forEach(entry -> {
			final var filteredKnownAddresses = entry.getKnownAddresses().stream()
				.filter(addr -> sameNetworkHrp(addr.getUri()))
				.collect(ImmutableSet.toImmutableSet());

			if (filteredKnownAddresses.isEmpty() && !entry.isBanned()) {
				// there are no known addresses and no ban info for peer so just remove it
				this.persistence.removeEntry(entry.getNodeId());
			} else if (filteredKnownAddresses.size() != entry.getKnownAddresses().size()) {
				// some addresses got filtered out, need to persist a new entry
				final var updatedEntry = new AddressBookEntry(entry.getNodeId(), entry.bannedUntil(), filteredKnownAddresses);
				cleanedUpEntries.put(entry.getNodeId(), updatedEntry);
				persistEntry(updatedEntry);
			} else {
				cleanedUpEntries.put(entry.getNodeId(), entry);
			}
		});
		this.knownPeers.clear();
		this.knownPeers.putAll(cleanedUpEntries.build());
	}

	public void addUncheckedPeers(Set<RadixNodeUri> peers) {
		synchronized (lock) {
			peers
				.stream()
				.filter(not(uri -> uri.getNodeId().equals(this.self.getNodeId())))
				.filter(this::sameNetworkHrp)
				.forEach(uri -> {
					final var maybeExistingEntry = this.knownPeers.get(uri.getNodeId());
					if (maybeExistingEntry == null) {
						final var newEntry = AddressBookEntry.create(uri);
						this.knownPeers.put(uri.getNodeId(), newEntry);
						persistEntry(newEntry);
					} else {
						final var updatedEntry = maybeExistingEntry
							.cleanupExpiredBlacklsitedUris()
							.addUriIfNotExists(uri);
						if (!updatedEntry.equals(maybeExistingEntry)) {
							this.knownPeers.put(uri.getNodeId(), updatedEntry);
							persistEntry(updatedEntry);
						}
					}
				});
		}
	}

	private boolean sameNetworkHrp(RadixNodeUri uri) {
		return uri.getNetworkNodeHrp().equals(this.self.getNetworkNodeHrp());
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
				.filter(not(PeerAddressEntry::blacklisted))
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
				final var updatedEntry = maybeExistingEntry
					.cleanupExpiredBlacklsitedUris()
					.withLastSuccessfulConnectionFor(radixNodeUri, Instant.now());
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
			.filter(not(PeerAddressEntry::blacklisted))
			.sorted(entryComparator)
			.map(AddressBookEntry.PeerAddressEntry::getUri);
	}

	private void persistEntry(AddressBookEntry entry) {
		this.persistence.removeEntry(entry.getNodeId());
		this.persistence.saveEntry(entry);
	}

	void banPeer(NodeId nodeId, Duration banDuration) {
		synchronized (lock) {
			final var banUntil = Instant.now().plus(banDuration);
			final var maybeExistingEntry = findById(nodeId);
			if (maybeExistingEntry.isPresent()) {
				final var existingEntry = maybeExistingEntry.get();
				final var alreadyBanned = existingEntry.bannedUntil()
					.filter(bu -> bu.isAfter(banUntil)).isPresent();
				if (!alreadyBanned) {
					final var updatedEntry = existingEntry
						.cleanupExpiredBlacklsitedUris()
						.withBanUntil(banUntil);
					this.knownPeers.put(nodeId, updatedEntry);
					this.persistEntry(updatedEntry);
					this.peerEventDispatcher.dispatch(PeerBanned.create(nodeId));
				}
			} else {
				final var newEntry = AddressBookEntry.createBanned(nodeId, banUntil);
				this.knownPeers.put(nodeId, newEntry);
				this.persistEntry(newEntry);
				this.peerEventDispatcher.dispatch(PeerBanned.create(nodeId));
			}

		}
	}

	public ImmutableMap<NodeId, AddressBookEntry> knownPeers() {
		return ImmutableMap.copyOf(knownPeers);
	}

	public void blacklist(RadixNodeUri uri) {
		synchronized (lock) {
			final var blacklistUntil = Instant.now().plus(Duration.ofMinutes(30));
			final var maybeExistingEntry = this.knownPeers.get(uri.getNodeId());
			if (maybeExistingEntry == null) {
				final var newEntry = AddressBookEntry.createBlacklisted(uri, blacklistUntil);
				this.knownPeers.put(uri.getNodeId(), newEntry);
				persistEntry(newEntry);
			} else {
				final var updatedEntry = maybeExistingEntry
					.cleanupExpiredBlacklsitedUris()
					.withBlacklistedUri(uri, blacklistUntil);
				this.knownPeers.put(uri.getNodeId(), updatedEntry);
				persistEntry(updatedEntry);
			}
		}
	}

	public void clear() {
		synchronized (lock) {
			this.persistence.close();
			this.persistence.reset();
			this.persistence.open();
			this.knownPeers.clear();
		}
	}
}
