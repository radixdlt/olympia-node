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

import java.util.function.Function;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic tests for implementors of {@link AddressBookEvent}.
 */
public class AddressBookEventsTest {
	@Test
	public void testPeersAdded() {
		testAddressBookEvent(PeersAddedEvent.class, PeersAddedEvent::new);
	}

	@Test
	public void testPeersUpdated() {
		testAddressBookEvent(PeersUpdatedEvent.class, PeersUpdatedEvent::new);
	}

	@Test
	public void testPeersRemoved() {
		testAddressBookEvent(PeersRemovedEvent.class, PeersRemovedEvent::new);
	}

	private <T extends AddressBookEvent> void testAddressBookEvent(Class<T> cls, Function<ImmutableList<Peer>, T> constructor) {
		T emptyEvent = constructor.apply(ImmutableList.of());
		assertTrue(emptyEvent.peers().isEmpty());

		Peer mockedPeer = mock(Peer.class);
		T nonEmptyEvent = constructor.apply(ImmutableList.of(mockedPeer));
		assertEquals(1, nonEmptyEvent.peers().size());
		assertSame(mockedPeer, nonEmptyEvent.peers().get(0));

		String s = nonEmptyEvent.toString();
		assertThat(s).startsWith(cls.getSimpleName() + "[");
		assertThat(s).contains(mockedPeer.toString());
	}
}
