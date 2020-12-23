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

package org.radix.api.services;

import java.util.Optional;

import com.radixdlt.consensus.Sha256Hasher;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.radix.universe.system.LocalSystem;

import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NetworkServiceTest {

	private Serialization serialization;
	private LocalSystem localSystem;
	private AddressBook addressBook;
	private NetworkService services;

	@Before
	public void setUp() {
		this.serialization = mock(Serialization.class);
		this.localSystem = mock(LocalSystem.class);
		this.addressBook = mock(AddressBook.class);
		this.services = new NetworkService(serialization, localSystem, addressBook, Sha256Hasher.withDefaultSerialization());
	}

	@Test
	public void testGetSelf() {
		when(this.serialization.toJsonObject(any(), any())).thenReturn(new JSONObject());
		JSONObject json = this.services.getSelf();

		assertNotNull(json);
		assertTrue(json.has("system"));

		verify(this.serialization, times(1)).toJsonObject(this.localSystem, Output.WIRE);
	}

	@Test
	public void testGetPeerInvalid() {
		JSONObject json = this.services.getPeer("");

		assertNotNull(json);
		assertTrue(json.isEmpty());
	}

	@Test
	public void testGetPeerNotPresent() {
		when(this.addressBook.peer(any(EUID.class))).thenReturn(Optional.empty());
		JSONObject json = this.services.getPeer("0123456789abcdef0123456789abcdef");

		assertNotNull(json);
		assertTrue(json.isEmpty());

		verify(this.addressBook, times(1)).peer(any(EUID.class));
	}

	@Test
	public void testGetPeerPresent() {
		PeerWithSystem p = mock(PeerWithSystem.class);
		when(this.addressBook.peer(any(EUID.class))).thenReturn(Optional.of(p));
		JSONObject serializedPeer = new JSONObject();
		when(this.serialization.toJsonObject(any(), any())).thenReturn(serializedPeer);
		JSONObject json = this.services.getPeer("0123456789abcdef0123456789abcdef");

		assertNotNull(json);
		assertSame(serializedPeer, json);

		verify(this.addressBook, times(1)).peer(any(EUID.class));
		verify(this.serialization, times(1)).toJsonObject(p, Output.API);
	}

}
