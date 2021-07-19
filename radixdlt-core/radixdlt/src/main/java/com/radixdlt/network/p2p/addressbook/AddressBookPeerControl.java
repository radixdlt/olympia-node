/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.network.p2p.addressbook;

import com.google.inject.Inject;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerControl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.Objects;

public final class AddressBookPeerControl implements PeerControl {
	private static final Logger log = LogManager.getLogger();

	private final AddressBook addressBook;

	@Inject
	public AddressBookPeerControl(AddressBook addressBook) {
		this.addressBook = Objects.requireNonNull(addressBook);
	}

	public void banPeer(NodeId nodeId, Duration banDuration, String reason) {
		log.info("Banning peer {} for {} because of {}", nodeId, banDuration, reason);
		this.addressBook.banPeer(nodeId, banDuration);
	}
}
