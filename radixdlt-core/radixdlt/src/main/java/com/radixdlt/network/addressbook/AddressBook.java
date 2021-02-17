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

import com.radixdlt.consensus.bft.BFTNode;
import org.radix.universe.system.RadixSystem;

import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.transport.TransportInfo;

import io.reactivex.rxjava3.core.Observable;

/**
 * Address book interface allowing client code to discover and add
 * {@link Peer} objects.
 */
public interface AddressBook {

	/**
	 * Removes the specified peer to the address book.
	 * <p>
	 * This method is thread-safe.
	 *
	 * @param nid The node ID of the peer to remove from the address book
	 * @return {@code true} if the peer was removed, {@code false} if the peer was not
	 * 	present
	 */
	boolean removePeer(EUID nid);

	/**
	 * Adds or updates a peer's details in the address book.
	 * <p>
	 * This method is thread-safe.
	 *
	 * @param oldPeer the peer to update, if it already exists
	 * @param system the system information to update the peer with
	 * @param source the source of the message causing the update
	 * @return the updated peer
	 */
	PeerWithSystem addOrUpdatePeer(Optional<PeerWithSystem> oldPeer, RadixSystem system, TransportInfo source);

	/**
	 * Retrieve the {@link PeerWithSystem} with the specified Node ID, if known.
	 * <p>
	 * This method is thread-safe, although the returned {@link PeerWithSystem},
	 * if any, may become stale at any time due to external events.
	 *
	 * @param nid The Node ID of the peer to retrieve
	 * @return the optional {@link PeerWithSystem} with matching Node ID
	 */
	Optional<PeerWithSystem> peer(EUID nid);

	/**
	 * Check whether there is a peer corresponding to given bft node in the address book.
	 * @return true if there is a peer corresponding to given bft node in the address book, false otherwise.
	 */
	boolean hasBftNodePeer(BFTNode bftNode);

	/**
	 * Retrieve the {@link PeerWithSystem} contactable using the specified
	 * transport, if any.
	 * <p>
	 * This method is thread-safe, although the returned {@link PeerWithSystem},
	 * if any, may become stale at any time due to external events.
	 *
	 * @param uri The transport of the peer to retrieve
	 * @return the optional {@link PeerWithSystem} with the specified transport
	 */
	Optional<PeerWithSystem> peer(TransportInfo uri);

	/**
	 * Returns a stream of {@link Peer} objects that this address book knows about,
	 * where we have knowledge of a suitable communication method for the peer.
	 * <p>
	 * This method is thread-safe, although the returned {@link Peer} objects, if any,
	 * may change become stale at any time due to external events.
	 *
	 * @return A {@link Stream} of {@link Peer} objects
	 */
	Stream<PeerWithSystem> peers();

	/**
	 * Returns a stream of {@link Peer} objects that this address book knows about
	 * where we have knowledge of a suitable communication method for the peer
	 * and have been heard from recently.  Typically used to find connected peers.
	 * <p>
	 * This method is thread-safe, although the returned {@link Peer} objects, if any,
	 * may change become stale at any time due to external events.
	 *
	 * @return A {@link Stream} of {@link Peer} objects
	 */
	Stream<PeerWithSystem> recentPeers();

	/**
	 * Returns a stream of {@link EUID} for node IDs that this address book knows about.
	 * <p>
	 * This method is thread-safe.
	 *
	 * @return A {@link Stream} of {@link EUID} objects representing node IDs
	 */
	Stream<EUID> nids();

	/**
	 * Returns an observable of {@link AddressBookEvent} objects for updates
	 * made to the address book.
	 *
	 * @return An observable of address book events
	 */
	Observable<AddressBookEvent> peerUpdates();

    /**
     * Closes this {@code AddressBook} and releases any system resources associated
     * with it. If the {@code AddressBook} is already closed then invoking this
     * method has no effect.
     *
     * @throws IOException if an I/O error occurs
     */
    void close() throws IOException;

}
