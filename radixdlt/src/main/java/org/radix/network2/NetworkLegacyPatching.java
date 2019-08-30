package org.radix.network2;

import org.radix.modules.Modules;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.transport.TransportInfo;
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
	public static Peer findPeer(TransportInfo info) {
		return Modules.isAvailable(AddressBook.class) ?  Modules.get(AddressBook.class).peer(info) : null;
	}

	/**
	 * Return true if we already have information about the given peer being banned.
	 * Note that if the peer is already banned according to our address book, the
	 * specified peer instance will have it's banned timestamp updated to match the
	 * known peer's banned time.
	 * <p>
	 * Eventually this will be replaced with something that uses AddressBook.
	 *
	 * @param peer the peer we are inquiring about
	 * @param peerNid the corresponding node ID of the peer
	 * @param timeSource a time source to use for checking expiration times
	 * @return {@code true} if the peer is currently banned, {@code false} otherwise
	 */
	public static boolean checkPeerBanned(Peer peer, EUID peerNid, TimeSupplier timeSource) {
		if (Modules.isAvailable(AddressBook.class)) {
			Peer knownPeer = Modules.get(AddressBook.class).peer(peerNid);

			if (knownPeer != null && knownPeer.getTimestamp(Timestamps.BANNED) > timeSource.currentTime()) {
				// Note that the next two commented out lines don't actually do anything, as the call to ban(...) overwrites the data
				//peer.setTimestamp(Timestamps.BANNED, knownPeer.getTimestamp(Timestamps.BANNED));
				//peer.setBanReason(knownPeer.getBanReason());
				peer.ban(String.format("Banned peer %s at %s", peerNid, peer.toString()));
				return true;
			}
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
