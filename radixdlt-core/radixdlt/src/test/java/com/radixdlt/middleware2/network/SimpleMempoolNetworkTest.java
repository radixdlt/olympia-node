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

package com.radixdlt.middleware2.network;

import com.radixdlt.consensus.Command;
import java.util.stream.Stream;

import com.radixdlt.mempool.messages.MempoolAtomAddedMessage;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.messaging.MessageCentralMockProvider;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.radix.universe.system.LocalSystem;

import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.universe.Universe;

import static org.mockito.Mockito.*;

public class SimpleMempoolNetworkTest {

	@Test
	public void testSendMempoolSubmission() {
		PeerWithSystem peer1 = mock(PeerWithSystem.class);
		when(peer1.hasSystem()).thenReturn(true);
		when(peer1.getNID()).thenReturn(EUID.ONE);
		PeerWithSystem peer2 = mock(PeerWithSystem.class);
		when(peer2.hasSystem()).thenReturn(true);
		when(peer2.getNID()).thenReturn(EUID.TWO);
		LocalSystem system = mock(LocalSystem.class);
		when(system.getNID()).thenReturn(EUID.TWO);
		Universe universe = mock(Universe.class);
		AddressBook addressBook = mock(AddressBook.class);
		when(addressBook.peers()).thenReturn(Stream.of(peer1, peer2));
		MessageCentral messageCentral = mock(MessageCentral.class);
		SimpleMempoolNetwork smn = new SimpleMempoolNetwork(system, universe, addressBook, messageCentral);

		Command command = mock(Command.class);
		smn.sendMempoolSubmission(command);

		verify(messageCentral, times(1)).send(any(), any());
	}

	@Test
	public void testCommandMessages() {
		LocalSystem system = mock(LocalSystem.class);
		when(system.getNID()).thenReturn(EUID.TWO);
		Universe universe = mock(Universe.class);
		AddressBook addressBook = mock(AddressBook.class);
		MessageCentral messageCentral = MessageCentralMockProvider.get();

		SimpleMempoolNetwork smn = new SimpleMempoolNetwork(system, universe, addressBook, messageCentral);

		TestSubscriber<Command> obs = smn.commands().test();

		Peer peer = mock(Peer.class);
		Command command = mock(Command.class);
		MempoolAtomAddedMessage message = mock(MempoolAtomAddedMessage.class);
		when(message.command()).thenReturn(command);
		messageCentral.send(peer, message);

		obs.awaitCount(1);
		obs.assertNoErrors();
		obs.assertValue(a -> a == command);
	}
}
