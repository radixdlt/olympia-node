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

import java.io.Closeable;
import java.util.function.Consumer;

import com.radixdlt.identifiers.EUID;

/**
 * Persistence interface for persisting peers in AddressBook.
 */
public interface PeerPersistence extends Closeable  {

	/**
	 * Saves peer to database.
	 *
	 * @param peer the peer to save.  The peer must have a valid NID.
	 * @return {@code true} if the peer was saved, {@code false} otherwise
	 */
	boolean savePeer(PeerWithSystem peer);

	/**
	 * Deletes peer identified by specified NID.
	 *
	 * @param NID of the peer to delete
	 * @return {@code true} if the peer was deleted, {@code false} otherwise
	 */
	boolean deletePeer(EUID nid);

	/**
	 * Calls the specified consumer with each persisted peer.
	 *
	 * @param c the consumer to call with each persisted peer
	 */
	void forEachPersistedPeer(Consumer<PeerWithSystem> c);
}
