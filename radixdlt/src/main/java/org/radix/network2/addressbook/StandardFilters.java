package org.radix.network2.addressbook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Set;
import org.radix.Radix;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
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

public final class StandardFilters {
	private static final Logger log = Logging.getLogger();

	private StandardFilters() {
		throw new IllegalStateException("Can't construct");
	}

	public static PeerPredicate standardFilter() {
		return StandardFilters::standardPeerFilter;
	}

	public static PeerPredicate oneOf(Collection<EUID> nids) {
		return oneOf(ImmutableSet.copyOf(nids));
	}

	public static PeerPredicate oneOf(Set<EUID> nids) {
		return peer -> peer.hasNID() && nids.contains(peer.getNID());
	}

	public static PeerPredicate hasOverlappingShards() {
		return peer -> peer.hasSystem() && LocalSystem.getInstance().getShards().intersects(peer.getSystem().getShards());
	}

	public static PeerPredicate recentlyActive() {
		return peer -> (Time.currentTimestamp() - peer.getTimestamp(Timestamps.ACTIVE)) < Modules.get(Universe.class).getPlanck();
	}

	private static boolean standardPeerFilter(Peer peer) {
		try {
			// If we can't talk to the peer, give up
			if (peer.supportedTransports().count() == 0) {
				return false;
			}

			if (peer.supportedTransports().anyMatch(StandardFilters::isLocalAddress)) {
				return false;
			}

			if (peer.supportedTransports().anyMatch(StandardFilters::hostNotWhitelisted)) {
				return false;
			}

			if (peer.hasNID() && peer.getNID().equals(LocalSystem.getInstance().getNID())) {
				return false;
			}

			if (peer.hasSystem()) {
				RadixSystem system = peer.getSystem();
				if (system.getProtocolVersion() != 0 && system.getProtocolVersion() < Radix.PROTOCOL_VERSION) {
					return false;
				}

				if (system.getAgentVersion() != 0 && system.getAgentVersion() <= Radix.MAJOR_AGENT_VERSION) {
					return false;
				}
			}

			if (peer.isBanned()) {
				return false;
			}

		} catch (Exception ex) {
			log.error("Could not process filter on PeerFilter for Peer:"+peer.toString(), ex);
			return false;
		}
		return true;
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

	private static boolean isLocalAddress(TransportInfo ti) {
		try {
			String host = ti.metadata().get("host");
			if (host != null) {
				InetAddress address = InetAddress.getByName(host);
				if (Modules.get(Interfaces.class).isSelf(address)) {
					return true;
				}
			}
			return false;
		} catch (IOException e) {
			throw new UncheckedIOException("Error while checking for local address", e);
		}
	}
}
