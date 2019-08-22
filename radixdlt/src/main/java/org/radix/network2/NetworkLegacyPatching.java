package org.radix.network2;

import java.io.IOException;
import java.net.URI;

import org.radix.modules.Modules;
import org.radix.network.Network;
import org.radix.network.Protocol;
import org.radix.network.peers.Peer;
import org.radix.network.peers.PeerStore;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.udp.UDPConstants;
import org.radix.time.Timestamps;
import com.radixdlt.common.EUID;
import com.radixdlt.universe.Universe;

/**
 * Place for gathering stuff that will need to be resolved or disappear
 * at some point in the network refactor migration.
 */
public final class NetworkLegacyPatching {
	private NetworkLegacyPatching() {
		throw new IllegalStateException("Can't connect");
	}

	/**
	 * Finds a peer, given transport information.
	 * <p>
	 * Eventually this will be replaced with something that uses AddressBook.
	 *
	 * @param info the {@link TransportInfo} for which to return a {@link Peer}
	 * @return the {@link Peer} or {@code null} if the system is not yet ready
	 *         to answer peer queries
	 * @throws IOException if an I/O error occurs
	 */
	public static Peer findPeer(TransportInfo info) throws IOException {
		if (Modules.isAvailable(PeerStore.class)) {
			TransportMetadata tm = info.metadata();
			String host = tm.get(UDPConstants.METADATA_UDP_HOST);
			String port = tm.get(UDPConstants.METADATA_UDP_PORT);
			URI uri = URI.create(Network.URI_PREFIX + host + ":" + port);
			return Network.getInstance().connect(uri, Protocol.UDP);
		}
		return null;
	}

	/**
	 * Return true if we already have information about the given peer being banned.
	 * Note that if the peer is already banned according to our address book, the
	 * specified peer instance will have it's banned timestamp updated to match the
	 * known peer's banned time.
<<<<<<< HEAD
	 * <p>
	 * Eventually this will be replaced with something that uses AddressBook.
=======
>>>>>>> Improved dependency management and added integration test for UDP transport
	 *
	 * @param peer the peer we are inquiring about
	 * @param peerNid the corresponding node ID of the peer
	 * @param timeSource a time source to use for checking expiration times
	 * @return {@code true} if the peer is currently banned, {@code false} otherwise
	 * @throws IOException if an I/O exception occurs while handling peers
	 */
	public static boolean checkPeerBanned(Peer peer, EUID peerNid, TimeSupplier timeSource) throws IOException {
		Peer knownPeer = Modules.get(PeerStore.class).getPeer(peerNid);

		if (knownPeer != null && knownPeer.getTimestamp(Timestamps.BANNED) > timeSource.currentTime()) {
			peer.setTimestamp(Timestamps.BANNED, knownPeer.getTimestamp(Timestamps.BANNED));
			peer.setBanReason(knownPeer.getBanReason());
			peer.ban(String.format("Banned peer %s at %s", peerNid, peer.toString()));
			return true;
		}
		return false;
	}

	/**
	 * Not really convinced we need to keep doing this.
	 *
	 * @return The default network port
	 */
	public static int defaultPort() {
		return Modules.get(Universe.class).getPort();
	}
}
