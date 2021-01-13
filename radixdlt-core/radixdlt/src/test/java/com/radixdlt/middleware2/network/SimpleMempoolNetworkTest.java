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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.radix.universe.system.LocalSystem;

import com.radixdlt.identifiers.EUID;
import com.radixdlt.mempool.messages.MempoolAtomAddedMessage;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageListener;
import com.radixdlt.universe.Universe;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

import io.reactivex.rxjava3.observers.TestObserver;

public class SimpleMempoolNetworkTest {
	@Test
	public void testCommandMessages() {
		LocalSystem system = mock(LocalSystem.class);
		when(system.getNID()).thenReturn(EUID.TWO);
		Universe universe = mock(Universe.class);
		AddressBook addressBook = mock(AddressBook.class);
		MessageCentral messageCentral = mock(MessageCentral.class);
		AtomicReference<MessageListener<MempoolAtomAddedMessage>> callbackRef = new AtomicReference<>();
		doAnswer(inv -> {
			callbackRef.set(inv.getArgument(1));
			return null;
		}).when(messageCentral).addListener(eq(MempoolAtomAddedMessage.class), any());

		SimpleMempoolNetwork smn = new SimpleMempoolNetwork(system, universe, addressBook, messageCentral);

		assertNotNull(callbackRef.get());
		MessageListener<MempoolAtomAddedMessage> callback = callbackRef.get();

		TestObserver<Command> obs = smn.commands().test();

		Peer peer = mock(Peer.class);
		Command command = mock(Command.class);
		MempoolAtomAddedMessage message = mock(MempoolAtomAddedMessage.class);
		when(message.command()).thenReturn(command);
		callback.handleMessage(peer, message);

		obs.awaitCount(1);
		obs.assertNoErrors();
		obs.assertValue(a -> a == command);
	}
}
