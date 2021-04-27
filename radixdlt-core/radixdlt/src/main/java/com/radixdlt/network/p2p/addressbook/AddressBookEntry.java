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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.RadixNodeUri;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static java.util.function.Predicate.not;

public final class AddressBookEntry {
	private final NodeId nodeId;
	private final boolean isBanned;
	private final ImmutableSet<PeerAddressEntry> knownAddresses;

	public static AddressBookEntry create(RadixNodeUri uri) {
		return create(uri, Optional.empty());
	}

	public static AddressBookEntry create(RadixNodeUri uri, Instant lastSuccessfulConnection) {
		return create(uri, Optional.of(lastSuccessfulConnection));
	}

	public static AddressBookEntry create(RadixNodeUri uri, Optional<Instant> lastSuccessfulConnection) {
		return new AddressBookEntry(
			uri.getNodeId(),
			false,
			ImmutableSet.of(new AddressBookEntry.PeerAddressEntry(uri, lastSuccessfulConnection))
		);
	}

	private AddressBookEntry(NodeId nodeId, boolean isBanned, ImmutableSet<PeerAddressEntry> knownAddresses) {
		this.nodeId = Objects.requireNonNull(nodeId);
		this.isBanned = isBanned;
		this.knownAddresses = knownAddresses;
	}

	public NodeId getNodeId() {
		return nodeId;
	}

	public boolean isBanned() {
		return isBanned;
	}

	public ImmutableSet<PeerAddressEntry> getKnownAddresses() {
		return knownAddresses;
	}

	public AddressBookEntry addUriIfNotExists(RadixNodeUri uri) {
		if (entryFor(uri).isPresent()) {
			return this;
		} else {
			final var newAddressEntry = new PeerAddressEntry(uri, Optional.empty());
			final var newKnownAddresses = ImmutableSet.<PeerAddressEntry>builder()
				.addAll(this.knownAddresses)
				.add(newAddressEntry)
				.build();
			return new AddressBookEntry(nodeId, isBanned, newKnownAddresses);
		}
	}

	public Optional<PeerAddressEntry> entryFor(RadixNodeUri uri) {
		return knownAddresses.stream()
			.filter(e -> e.getUri().equals(uri))
			.findAny();
	}

	public AddressBookEntry withLastSuccessfulConnectionFor(RadixNodeUri uri, Instant lastSuccessfulConnectionFor) {
		final var maybeExistingAddress = this.knownAddresses.stream()
			.filter(e -> e.getUri().equals(uri))
			.findAny();

		if (maybeExistingAddress.isPresent()) {
			final var updatedAddressEntry = maybeExistingAddress.get()
				.withLastSuccessfulConnection(lastSuccessfulConnectionFor);
			final var knownAddressesWithoutTheOldOne =
				this.knownAddresses.stream()
					.filter(not(e -> e.getUri().equals(uri)))
					.collect(ImmutableSet.toImmutableSet());
			final var newKnownAddresses = ImmutableSet.<PeerAddressEntry>builder()
				.addAll(knownAddressesWithoutTheOldOne)
				.add(updatedAddressEntry)
				.build();
			return new AddressBookEntry(nodeId, isBanned, newKnownAddresses);
		} else {
			final var newAddressEntry = new PeerAddressEntry(uri, Optional.of(lastSuccessfulConnectionFor));
			final var newKnownAddresses = ImmutableSet.<PeerAddressEntry>builder()
				.addAll(this.knownAddresses)
				.add(newAddressEntry)
				.build();
			return new AddressBookEntry(nodeId, isBanned, newKnownAddresses);
		}
	}

	@Override
	public String toString() {
		return String.format(
			"%s[nodeId=%s, isBanned=%s, knownAddresses=%s]", getClass().getSimpleName(),
			nodeId, isBanned, knownAddresses
		);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		AddressBookEntry that = (AddressBookEntry) o;
		return isBanned == that.isBanned
			&& Objects.equals(nodeId, that.nodeId)
			&& Objects.equals(knownAddresses, that.knownAddresses);
	}

	@Override
	public int hashCode() {
		return Objects.hash(nodeId, isBanned, knownAddresses);
	}

	public static final class PeerAddressEntry {
		private final RadixNodeUri uri;
		private final Optional<Instant> lastSuccessfulConnection;

		PeerAddressEntry(RadixNodeUri uri, Optional<Instant> lastSuccessfulConnection) {
			this.uri = Objects.requireNonNull(uri);
			this.lastSuccessfulConnection = Objects.requireNonNull(lastSuccessfulConnection);
		}

		public RadixNodeUri getUri() {
			return uri;
		}

		public Optional<Instant> getLastSuccessfulConnection() {
			return lastSuccessfulConnection;
		}

		public PeerAddressEntry withLastSuccessfulConnection(Instant lastSuccessfulConnection) {
			return new PeerAddressEntry(uri, Optional.of(lastSuccessfulConnection));
		}

		@Override
		public String toString() {
			return String.format(
				"%s[uri=%s, lastSuccessfulConnection=%s]", getClass().getSimpleName(),
				uri, lastSuccessfulConnection
			);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			PeerAddressEntry that = (PeerAddressEntry) o;
			return Objects.equals(uri, that.uri)
				&& Objects.equals(lastSuccessfulConnection, that.lastSuccessfulConnection);
		}

		@Override
		public int hashCode() {
			return Objects.hash(uri, lastSuccessfulConnection);
		}
	}
}
