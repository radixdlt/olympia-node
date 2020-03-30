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

package org.radix.network2;

import com.radixdlt.identifiers.EUID;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import org.radix.time.Timestamps;

/**
 * Place for gathering stuff that will need to be resolved or disappear
 * at some point in the network refactor migration.
 */
public final class NetworkLegacyPatching {
	private NetworkLegacyPatching() {
		throw new IllegalStateException("Can't connect");
	}

	/**
	 * Return true if we already have information about the given peer being banned.
	 * Note that if the peer is already banned according to our address book, the
	 * specified peer instance will have it's banned timestamp updated to match the
	 * known peer's banned time.
	 *
	 * @param peer the peer we are inquiring about
	 * @param peerNid the corresponding node ID of the peer
	 * @param timeSource a time source to use for checking expiration times
	 * @param addressBook the address book
	 * @return {@code true} if the peer is currently banned, {@code false} otherwise
	 */
	public static boolean checkPeerBanned(Peer peer, EUID peerNid, TimeSupplier timeSource, AddressBook addressBook) {
		Peer knownPeer = addressBook.peer(peerNid);

		if (knownPeer != null && knownPeer.getTimestamp(Timestamps.BANNED) > timeSource.currentTime()) {
			// Note that the next two commented out lines don't actually do anything, as the call to ban(...) overwrites the data
			//peer.setTimestamp(Timestamps.BANNED, knownPeer.getTimestamp(Timestamps.BANNED));
			//peer.setBanReason(knownPeer.getBanReason());
			peer.ban(String.format("Banned peer %s at %s", peerNid, peer.toString()));
			return true;
		}
		return false;
	}
}
