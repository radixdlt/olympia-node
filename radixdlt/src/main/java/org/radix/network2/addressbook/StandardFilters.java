package org.radix.network2.addressbook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Set;
import org.radix.Radix;
import org.radix.modules.Modules;
import org.radix.network.Interfaces;
import org.radix.network.Network;
import org.radix.network2.transport.TransportInfo;
import org.radix.time.Time;
import org.radix.time.Timestamps;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.RadixSystem;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.universe.Universe;

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
	 * @param interfaces the interfaces to test against
	 */
	public static PeerPredicate standardFilter(Interfaces interfaces) {
		return hasTransports()
			.and(notLocalAddress(interfaces))
			.and(isWhitelisted())
			.and(notOurNID())
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
	 * Returns {@code true} if none of the peer's transports point to a local address.
	 *
	 * @return {@code true} if none of the peer's transports use a local address, {@code false} otherwise
	 * @param interfaces the interfaces to check against
	 */
	public static PeerPredicate notLocalAddress(Interfaces interfaces) {
		return peer -> peer.supportedTransports().noneMatch((TransportInfo ti) -> {
			return isLocalAddress(ti, interfaces);
		});
	}

	/**
	 * Returns {@code true} if all of the peer's transports are whitelisted.
	 *
	 * @return {@code true} if all of the peer's transports are whitelisted, {@code false} otherwise
	 */
	public static PeerPredicate isWhitelisted() {
		return peer -> peer.supportedTransports().noneMatch(StandardFilters::hostNotWhitelisted);
	}

	/**
	 * Returns {@code true} if the peer has a NID, and it's not the local NID.
	 *
	 * @return {@code true} if the peer has a NID, and it's not the local NID, {@code false} otherwise
	 */
	public static PeerPredicate notOurNID() {
		return peer -> !(peer.hasNID() && peer.getNID().equals(LocalSystem.getInstance().getNID()));
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
	 * Returns {@code true} if the peer has shard space overlap with local peer.
	 *
	 * @return {@code true} if the peer has shard space overlap with local peer, {@code false} otherwise
	 */
	public static PeerPredicate hasOverlappingShards() {
		return peer -> peer.hasSystem() && LocalSystem.getInstance().getShards().intersects(peer.getSystem().getShards());
	}

	/**
	 * Returns {@code true} if the peer been active within one planck period.
	 *
	 * @return {@code true} if the peer been active within one planck period, {@code false} otherwise
	 */
	public static PeerPredicate recentlyActive() {
		return peer -> (Time.currentTimestamp() - peer.getTimestamp(Timestamps.ACTIVE)) < Modules.get(Universe.class).getPlanck();
	}

	private static boolean hostNotWhitelisted(TransportInfo ti) {
		String host = ti.metadata().get("host");
		if (host != null) {
			if (!Network.getInstance().isWhitelisted(host)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isLocalAddress(TransportInfo ti, Interfaces interfaces) {
		try {
			String host = ti.metadata().get("host");
			if (host != null) {
				InetAddress address = InetAddress.getByName(host);
				if (interfaces.isSelf(address)) {
					return true;
				}
			}
			return false;
		} catch (IOException e) {
			throw new UncheckedIOException("Error while checking for local address", e);
		}
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
