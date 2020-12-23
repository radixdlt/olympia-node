/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.network.addressbook;

import java.util.Collection;
import java.util.Set;
import org.radix.Radix;
import org.radix.network.discovery.Whitelist;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.transport.TransportInfo;

/**
 * Standard filters for peer streams.
 * Note that these predicates may be composed and inverted using
 * {@link PeerPredicate#and(java.util.function.Predicate)},
 * {@link PeerPredicate#or(java.util.function.Predicate)} and
 * {@link PeerPredicate#negate()}.
 */
public final class StandardFilters {
	private StandardFilters() {
		throw new IllegalStateException("Can't construct");
	}

	/**
	 * A filter that can be used for most peer lists.
	 * <p>
	 * Ensures that the included peers meet the following criteria:
	 * <ul>
	 *   <li>The peer supports at least one transport</li>
	 *   <li>None of the supported transports point to a local interface address</li>
	 *   <li>None of the supported transports is not included in the global whitelist</li>
	 *   <li>If the peer has a known NID, this NID is not the same as the local node's NID</li>
	 *   <li>If the peer has known system information, then the protocol and agent versions
	 *     are acceptable to the local node</li>
	 *   <li>The node is not banned</li>
	 * </ul>
	 *
	 * @return {@code true} if all the criteria are met, {@code false} otherwise
	 * @param self the EUID of this node
	 * @param interfaces the interfaces to test against
	 * @param whitelist the whitelist
	 */
	public static PeerPredicate standardFilter(EUID self, Whitelist whitelist) {
		return hasTransports()
			.and(isWhitelisted(whitelist))
			.and(notOurNID(self))
			.and(acceptableProtocol())
			.and(notBanned());
	}

	/**
	 * Returns {@code true} if the peer has any transports.
	 *
	 * @return {@code true} if the peer has any transports, {@code false} otherwise
	 */
	public static PeerPredicate hasTransports() {
		return peer -> peer.supportedTransports().findAny().isPresent();
	}

	/**
	 * Returns {@code true} if all of the peer's transports are whitelisted.
	 *
	 * @return {@code true} if all of the peer's transports are whitelisted, {@code false} otherwise
	 * @param whitelist the whitelist to use
	 */
	public static PeerPredicate isWhitelisted(Whitelist whitelist) {
		return peer -> peer.supportedTransports()
			.noneMatch(ti -> hostNotWhitelisted(ti, whitelist));
	}

	/**
	 * Returns {@code true} if the peer has a NID, and it's not the local NID.
	 *
	 * @return {@code true} if the peer has a NID, and it's not the local NID, {@code false} otherwise
	 * @param self the EUID of this node
	 */
	public static PeerPredicate notOurNID(EUID self) {
		return peer -> !(peer.hasNID() && peer.getNID().equals(self));
	}

	/**
	 * Returns {@code true} if the peer is not banned.
	 *
	 * @return {@code true} if the peer is not banned, {@code false} otherwise
	 */
	public static PeerPredicate notBanned() {
		return peer -> !peer.isBanned();
	}

	/**
	 * Returns {@code true} if the peer has an acceptable ledger protocol.
	 *
	 * @return {@code true} if the peer has an acceptable ledger protocol, {@code false} otherwise
	 */
	public static PeerPredicate acceptableProtocol() {
		return StandardFilters::acceptableProtocol;
	}

	/**
	 * Returns {@code true} if the peer has a NID in the specified collection.
	 *
	 * @return {@code true} if the peer has a NID in the specified collection, {@code false} otherwise
	 */
	public static PeerPredicate oneOf(Collection<EUID> nids) {
		return oneOf(ImmutableSet.copyOf(nids));
	}

	/**
	 * Returns {@code true} if the peer has a NID in the specified set.
	 *
	 * @return {@code true} if the peer has a NID in the specified set, {@code false} otherwise
	 */
	public static PeerPredicate oneOf(Set<EUID> nids) {
		return peer -> peer.hasNID() && nids.contains(peer.getNID());
	}

	/**
	 * Returns {@code true} if the peer been active within the specified time period.
	 *
	 * @return {@code true} if the peer been active within the specified time period, {@code false} otherwise
	 * @param recencyThreshold the recency threshold in millis
	 */
	public static PeerPredicate recentlyActive(long recencyThreshold) {
		return peer -> (Time.currentTimestamp() - peer.getTimestamp(Timestamps.ACTIVE)) < recencyThreshold;
	}

	private static boolean hostNotWhitelisted(TransportInfo ti, Whitelist whitelist) {
		String host = ti.metadata().get("host");
		return (host != null && !whitelist.isWhitelisted(host));
	}

	private static boolean acceptableProtocol(Peer peer) {
		if (peer.hasSystem()) {
			RadixSystem system = peer.getSystem();
			if (system.getProtocolVersion() != 0 && system.getProtocolVersion() < Radix.PROTOCOL_VERSION) {
				return false;
			}

			if (system.getAgentVersion() != 0 && system.getAgentVersion() <= Radix.MAJOR_AGENT_VERSION) {
				return false;
			}
		}
		// Just going to assume we can talk until we have a system and decide we can't
		return true;
	}
}
