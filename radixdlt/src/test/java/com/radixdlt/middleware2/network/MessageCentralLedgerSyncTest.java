/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware2.network;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.sync.RemoteSyncRequest;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageListener;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.sync.RemoteSyncResponse;
import com.radixdlt.universe.Universe;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.radix.universe.system.RadixSystem;

public class MessageCentralLedgerSyncTest {
	private MessageCentralLedgerSync messageCentralLedgerSync;
	private MessageCentral messageCentral;
	private AddressBook addressBook;

	@Before
	public void setup() {
		Universe universe = mock(Universe.class);
		when(universe.getMagic()).thenReturn(123);
		this.messageCentral = mock(MessageCentral.class);
		this.addressBook = mock(AddressBook.class);
		this.messageCentralLedgerSync = new MessageCentralLedgerSync(universe, addressBook, messageCentral);
	}

	@Test
	public void when_send_sync_request__then_magic_should_be_same_as_universe() {
		BFTNode node = mock(BFTNode.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(EUID.ONE);
		when(node.getKey()).thenReturn(key);
		PeerWithSystem peer = mock(PeerWithSystem.class);
		when(peer.hasSystem()).thenReturn(true);
		when(addressBook.peer(any(EUID.class))).thenReturn(Optional.of(peer));
		messageCentralLedgerSync.sendSyncRequest(node, mock(DtoLedgerHeaderAndProof.class));
		verify(messageCentral, times(1)).send(eq(peer), argThat(msg -> msg.getMagic() == 123));
	}

	@Test
	public void when_send_sync_response__then_magic_should_be_same_as_universe() {
		BFTNode node = mock(BFTNode.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(EUID.ONE);
		when(node.getKey()).thenReturn(key);
		PeerWithSystem peer = mock(PeerWithSystem.class);
		when(peer.hasSystem()).thenReturn(true);
		when(addressBook.peer(any(EUID.class))).thenReturn(Optional.of(peer));
		messageCentralLedgerSync.sendSyncResponse(node, mock(DtoCommandsAndProof.class));
		verify(messageCentral, times(1)).send(eq(peer), argThat(msg -> msg.getMagic() == 123));
	}

	@Test
	public void when_receive_sync_request__then_should_receive_it() {
		AtomicReference<MessageListener<SyncRequestMessage>> messageListenerAtomicReference = new AtomicReference<>();
		doAnswer(invocation -> {
			messageListenerAtomicReference.set(invocation.getArgument(1));
			return null;
		}).when(messageCentral).addListener(eq(SyncRequestMessage.class), any());

		TestObserver<RemoteSyncRequest> testObserver = this.messageCentralLedgerSync.syncRequests().test();
		Peer peer = mock(Peer.class);
		when(peer.hasSystem()).thenReturn(true);
		RadixSystem system = mock(RadixSystem.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(EUID.ONE);
		when(system.getKey()).thenReturn(key);
		when(peer.getSystem()).thenReturn(system);
		SyncRequestMessage syncRequestMessage = mock(SyncRequestMessage.class);
		DtoLedgerHeaderAndProof header = mock(DtoLedgerHeaderAndProof.class);
		when(syncRequestMessage.getCurrentHeader()).thenReturn(header);
		messageListenerAtomicReference.get().handleMessage(peer, syncRequestMessage);
		testObserver.awaitCount(1);
		testObserver.assertValue(syncRequest ->
			syncRequest.getCurrentHeader().equals(header) && syncRequest.getNode().getKey().equals(key)
		);
	}

	@Test
	public void when_receive_sync_response__then_should_receive_it() {
		AtomicReference<MessageListener<SyncResponseMessage>> messageListenerAtomicReference = new AtomicReference<>();
		doAnswer(invocation -> {
			messageListenerAtomicReference.set(invocation.getArgument(1));
			return null;
		}).when(messageCentral).addListener(eq(SyncResponseMessage.class), any());

		TestObserver<RemoteSyncResponse> testObserver = this.messageCentralLedgerSync.syncResponses().test();
		Peer peer = mock(Peer.class);
		when(peer.hasSystem()).thenReturn(true);
		RadixSystem system = mock(RadixSystem.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(EUID.ONE);
		when(system.getKey()).thenReturn(key);
		when(peer.getSystem()).thenReturn(system);
		SyncResponseMessage syncResponseMessage = mock(SyncResponseMessage.class);
		DtoCommandsAndProof commands = mock(DtoCommandsAndProof.class);
		when(syncResponseMessage.getCommands()).thenReturn(commands);
		messageListenerAtomicReference.get().handleMessage(peer, syncResponseMessage);
		testObserver.awaitCount(1);
		testObserver.assertValue(resp -> resp.getCommandsAndProof().equals(commands));
	}
}