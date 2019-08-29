package org.radix.network2.addressbook;

import java.util.stream.Stream;

import org.radix.network2.transport.TransportInfo;
import org.radix.universe.system.RadixSystem;

import com.radixdlt.common.EUID;

public interface AddressBook {

	/**
	 * Adds the specified peer to the address book.
	 * <p>
	 * This method is thread-safe.
	 *
	 * @param peer The {@link Peer} to add to the address book
	 * @return {@code true} if the peer was added, {@code false} if the peer was already
	 * 	present
	 */
	boolean addPeer(Peer peer);

	/**
	 * Removes the specified peer to the address book.
	 * <p>
	 * This method is thread-safe.
	 *
	 * @param peer The {@link Peer} to remove from the address book
	 * @return {@code true} if the peer was removed, {@code false} if the peer was not
	 * 	present
	 */
	boolean removePeer(Peer peer);

	/**
	 * Updates a peer's activity in the address book.
	 * Peers that have been inactive for a period of time are removed from the address
	 * book by a background scavenging process.
	 * <p>
	 * This method is thread-safe.
	 *
	 * @param peer The peer to update activity for
	 * @return {@code true} if the peer was updated, {@code false} if the peer was not
	 * 	present
	 */
	boolean updatePeer(Peer peer);

	// FIXME: Document
	Peer updatePeerSystem(Peer peer, RadixSystem system);

	/**
	 * Retrieve the {@link Peer} with the specified Node ID.
	 * <p>
	 * This method is thread-safe, although the returned {@link Peer}, if any, may
	 * change status at any time due to external events.
	 *
	 * @param nid The Node ID of the peer to retrieve
	 * @return the {@link Peer} with matching Node ID
	 */
	Peer peer(EUID nid);

	/**
	 * Retrieve or create the {@link Peer} with the specified URI.
	 * <p>
	 * This method is thread-safe, although the returned {@link Peer}, if any, may
	 * change status at any time due to external events.
	 *
	 * @param uri The URI of the peer to retrieve
	 * @return the {@link Peer} with the specified URI
	 */
	Peer peer(TransportInfo uri);

	/**
	 * Returns a stream of {@link Peer} objects that this address book knows about.
	 * <p>
	 * This method is thread-safe, although the returned {@link Peer} objects, if any,
	 * may change status at any time due to external events.
	 *
	 * @return A {@link Stream} of {@link Peer} objects
	 */
	Stream<Peer> peers();

	/**
	 * Returns a stream of {@link Peer} objects that this address book knows about
	 * and have been heard from recently.  Typically used to find connected peers.
	 * <p>
	 * This method is thread-safe, although the returned {@link Peer} objects, if any,
	 * may change status at any time due to external events.
	 *
	 * @return A {@link Stream} of {@link Peer} objects
	 */
	Stream<Peer> recentPeers();


}
