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

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

import org.radix.universe.system.RadixSystem;

import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.transport.TransportInfo;

/**
 * Address book interface allowing client code to discover and add
 * {@link Peer} objects.
 */
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

	/**
	 * Updates a peer's {@link RadixSystem} in the address book.
	 * <p>
	 * This method is thread-safe.
	 *
	 * @param peer The peer to update the system for
	 * @return the updated peer
	 */
	Peer updatePeerSystem(Peer peer, RadixSystem system);

	/**
	 * Retrieve the {@link Peer} with the specified Node ID, if known.
	 * <p>
	 * This method is thread-safe, although the returned {@link Peer}, if any, may
	 * change status at any time due to external events.
	 *
	 * @param nid The Node ID of the peer to retrieve
	 * @return the optional {@link Peer} with matching Node ID
	 */
	Optional<Peer> peer(EUID nid);

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
	 * Returns a stream of {@link Peer} objects that this address book knows about,
	 * where we have knowledge of a suitable communication method for the peer.
	 * <p>
	 * This method is thread-safe, although the returned {@link Peer} objects, if any,
	 * may change status at any time due to external events.
	 *
	 * @return A {@link Stream} of {@link Peer} objects
	 */
	Stream<Peer> peers();

	/**
	 * Returns a stream of {@link Peer} objects that this address book knows about
	 * where we have knowledge of a suitable communication method for the peer
	 * and have been heard from recently.  Typically used to find connected peers.
	 * <p>
	 * This method is thread-safe, although the returned {@link Peer} objects, if any,
	 * may change status at any time due to external events.
	 *
	 * @return A {@link Stream} of {@link Peer} objects
	 */
	Stream<Peer> recentPeers();

	/**
	 * Returns a stream of {@link EUID} for node IDs that this address book knows about.
	 * <p>
	 * This method is thread-safe.
	 *
	 * @return A {@link Stream} of {@link EUID} objects representing node IDs
	 */
	Stream<EUID> nids();

    /**
     * Closes this {@code AddressBook} and releases any system resources associated
     * with it. If the {@code AddressBook} is already closed then invoking this
     * method has no effect.
     *
     * @throws IOException if an I/O error occurs
     */
    void close() throws IOException;
}
