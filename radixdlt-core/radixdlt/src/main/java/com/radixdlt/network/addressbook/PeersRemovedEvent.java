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

import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * An {@link AddressBookEvent} to let interested parties know that
 * newly discovered peers have been removed from the address book.
 */
public final class PeersRemovedEvent implements AddressBookEvent {

	private final ImmutableList<Peer> peers;

	PeersRemovedEvent(ImmutableList<Peer> peers) {
		this.peers = peers;
	}

	@Override
	public List<Peer> peers() {
		return this.peers;
	}

	@Override
	public String toString() {
		return String.format("%s%s", getClass().getSimpleName(), this.peers);
	}
}
