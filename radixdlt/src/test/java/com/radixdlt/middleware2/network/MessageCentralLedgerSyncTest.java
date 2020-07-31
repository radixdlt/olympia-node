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

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.sync.SyncRequest;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageListener;
import com.radixdlt.universe.Universe;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

public class MessageCentralLedgerSyncTest {
	private MessageCentralLedgerSync messageCentralLedgerSync;
	private MessageCentral messageCentral;

	@Before
	public void setup() {
		Universe universe = mock(Universe.class);
		when(universe.getMagic()).thenReturn(123);
		this.messageCentral = mock(MessageCentral.class);
		this.messageCentralLedgerSync = new MessageCentralLedgerSync(universe, messageCentral);
	}

	@Test
	public void when_send_sync_request__then_magic_should_be_same_as_universe() {
		Peer peer = mock(Peer.class);
		messageCentralLedgerSync.sendSyncRequest(peer, 1);
		verify(messageCentral, times(1)).send(eq(peer), argThat(msg -> msg.getMagic() == 123));
	}

	@Test
	public void when_send_sync_response__then_magic_should_be_same_as_universe() {
		Peer peer = mock(Peer.class);
		messageCentralLedgerSync.sendSyncResponse(peer, Collections.emptyList());
		verify(messageCentral, times(1)).send(eq(peer), argThat(msg -> msg.getMagic() == 123));
	}

	@Test
	public void when_receive_sync_request__then_should_receive_it() {
		AtomicReference<MessageListener<SyncRequestMessage>> messageListenerAtomicReference = new AtomicReference<>();
		doAnswer(invocation -> {
			messageListenerAtomicReference.set(invocation.getArgument(1));
			return null;
		}).when(messageCentral).addListener(eq(SyncRequestMessage.class), any());

		TestObserver<SyncRequest> testObserver = this.messageCentralLedgerSync.syncRequests().test();
		Peer peer = mock(Peer.class);
		SyncRequestMessage syncRequestMessage = mock(SyncRequestMessage.class);
		when(syncRequestMessage.getStateVersion()).thenReturn(12345L);
		messageListenerAtomicReference.get().handleMessage(peer, syncRequestMessage);
		testObserver.awaitCount(1);
		testObserver.assertValue(syncRequest ->
			syncRequest.getStateVersion() == 12345L && syncRequest.getPeer().equals(peer)
		);
	}

	@Test
	public void when_receive_sync_response__then_should_receive_it() {
		AtomicReference<MessageListener<SyncResponseMessage>> messageListenerAtomicReference = new AtomicReference<>();
		doAnswer(invocation -> {
			messageListenerAtomicReference.set(invocation.getArgument(1));
			return null;
		}).when(messageCentral).addListener(eq(SyncResponseMessage.class), any());

		TestObserver<ImmutableList<CommittedAtom>> testObserver = this.messageCentralLedgerSync.syncResponses().test();
		Peer peer = mock(Peer.class);
		SyncResponseMessage syncResponseMessage = mock(SyncResponseMessage.class);
		ImmutableList<CommittedAtom> atoms = ImmutableList.of(mock(CommittedAtom.class));
		when(syncResponseMessage.getAtoms()).thenReturn(atoms);
		messageListenerAtomicReference.get().handleMessage(peer, syncResponseMessage);
		testObserver.awaitCount(1);
		testObserver.assertValue(atoms);
	}
}