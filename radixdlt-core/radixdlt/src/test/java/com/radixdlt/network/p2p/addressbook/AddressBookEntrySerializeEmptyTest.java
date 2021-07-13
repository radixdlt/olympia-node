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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.p2p.NodeId;
import org.radix.serialization.SerializeMessageObject;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;

public class AddressBookEntrySerializeEmptyTest extends SerializeMessageObject<AddressBookEntry> {
	public AddressBookEntrySerializeEmptyTest() {
		super(AddressBookEntry.class, AddressBookEntrySerializeEmptyTest::get);
	}

	private static AddressBookEntry get() {
		final var rnd = new Random();
		final var keyPair = ECKeyPair.generateNew();
		final var bannedUntil = rnd.nextBoolean()
			? Optional.of(Instant.ofEpochMilli(Math.abs(rnd.nextLong())))
			: Optional.<Instant>empty();
		return new AddressBookEntry(NodeId.fromPublicKey(keyPair.getPublicKey()), bannedUntil, ImmutableSet.of());
	}
}
