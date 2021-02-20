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

package com.radixdlt.middleware2.network;

import com.radixdlt.consensus.Command;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageCentralMockProvider;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.radix.universe.system.RadixSystem;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MessageCentralMempoolTest {

    private AddressBook addressBook;
    private MessageCentral messageCentral;
    private MessageCentralMempool messageCentralMempool;

    @Before
    public void setUp() {
        this.addressBook = mock(AddressBook.class);
        this.messageCentral = MessageCentralMockProvider.get();
        this.messageCentralMempool = new MessageCentralMempool(0, messageCentral, addressBook);
    }

    @Test
    public void when_subscribed_to_mempool_commands__then_should_receive_mempool_commands() {
        Peer peer = mock(Peer.class);
        when(peer.hasSystem()).thenReturn(true);
        RadixSystem system = mock(RadixSystem.class);
        ECPublicKey key = mock(ECPublicKey.class);
        when(key.euid()).thenReturn(EUID.ONE);
        when(system.getKey()).thenReturn(key);
        when(peer.getSystem()).thenReturn(system);
        final var command1 = mock(Command.class);
        final var command2 = mock(Command.class);

        TestSubscriber<RemoteEvent<MempoolAdd>> testObserver = messageCentralMempool.mempoolComands().test();
        messageCentral.send(peer, new MempoolAtomAddMessage(0, command1));
        messageCentral.send(peer, new MempoolAtomAddMessage(0, command2));

        testObserver.awaitCount(2);
        testObserver.assertValueAt(0, v -> v.getEvent().getCommand().equals(command1));
        testObserver.assertValueAt(1, v -> v.getEvent().getCommand().equals(command2));
    }
}
