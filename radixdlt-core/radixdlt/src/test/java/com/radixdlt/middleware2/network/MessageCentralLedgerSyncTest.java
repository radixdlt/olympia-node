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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.ledger.DtoTxnsAndProof;
import com.radixdlt.network.messaging.MessageCentralMockProvider;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.sync.messages.remote.LedgerStatusUpdate;
import com.radixdlt.sync.messages.remote.StatusRequest;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.sync.messages.remote.SyncRequest;
import com.radixdlt.sync.messages.remote.SyncResponse;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;

public class MessageCentralLedgerSyncTest {
	private MessageCentralLedgerSync messageCentralLedgerSync;
	private MessageCentral messageCentral;

	@Before
	public void setup() {
		this.messageCentral = MessageCentralMockProvider.get();
		this.messageCentralLedgerSync = new MessageCentralLedgerSync(123, messageCentral);
	}

	@Test
	public void when_send_sync_request__then_magic_should_be_same_as_universe() {
		BFTNode node = mock(BFTNode.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(EUID.ONE);
		when(node.getKey()).thenReturn(key);
		messageCentralLedgerSync.syncRequestDispatcher().dispatch(node, mock(SyncRequest.class));
		verify(messageCentral, times(1)).send(eq(NodeId.fromPublicKey(node.getKey())), argThat(msg -> msg.getMagic() == 123));
	}

	@Test
	public void when_send_sync_response__then_magic_should_be_same_as_universe() {
		BFTNode node = mock(BFTNode.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(EUID.ONE);
		when(node.getKey()).thenReturn(key);
		messageCentralLedgerSync.syncResponseDispatcher().dispatch(node, mock(SyncResponse.class));
		verify(messageCentral, times(1)).send(eq(NodeId.fromPublicKey(node.getKey())), argThat(msg -> msg.getMagic() == 123));
	}

	@Test
	public void when_send_ledger_status_update__then_magic_should_be_same_as_universe() {
		BFTNode node = mock(BFTNode.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(key.euid()).thenReturn(EUID.ONE);
		when(node.getKey()).thenReturn(key);
		messageCentralLedgerSync.ledgerStatusUpdateDispatcher().dispatch(node, mock(LedgerStatusUpdate.class));
		verify(messageCentral, times(1)).send(eq(NodeId.fromPublicKey(node.getKey())), argThat(msg -> msg.getMagic() == 123));
	}

	@Test
	public void when_receive_sync_request__then_should_receive_it() {
		TestSubscriber<RemoteEvent<SyncRequest>> testObserver =
			this.messageCentralLedgerSync.syncRequests().test();
		final var peer = createPeer();
		SyncRequestMessage syncRequestMessage = mock(SyncRequestMessage.class);
		DtoLedgerProof header = mock(DtoLedgerProof.class);
		when(syncRequestMessage.getCurrentHeader()).thenReturn(header);
		messageCentral.send(peer, syncRequestMessage);
		testObserver.awaitCount(1);
		testObserver.assertValue(syncRequest ->
			syncRequest.getEvent().getHeader().equals(header)
				&& syncRequest.getOrigin().getKey().equals(peer.getPublicKey())
		);
	}

	@Test
	public void when_receive_sync_response__then_should_receive_it() {
		TestSubscriber<RemoteEvent<SyncResponse>> testObserver = this.messageCentralLedgerSync.syncResponses().test();
		final var peer = createPeer();
		SyncResponseMessage syncResponseMessage = mock(SyncResponseMessage.class);
		DtoTxnsAndProof commands = mock(DtoTxnsAndProof.class);
		when(syncResponseMessage.getCommands()).thenReturn(commands);
		messageCentral.send(peer, syncResponseMessage);
		testObserver.awaitCount(1);
		testObserver.assertValue(resp -> resp.getEvent().getTxnsAndProof().equals(commands));
	}

	@Test
	public void when_receive_status_request__then_should_receive_it() {
		TestSubscriber<RemoteEvent<StatusRequest>> testObserver =
			this.messageCentralLedgerSync.statusRequests().test();
		final var peer = createPeer();
		StatusRequestMessage statusRequestMessage = mock(StatusRequestMessage.class);
		messageCentral.send(peer, statusRequestMessage);
		testObserver.awaitCount(1);
		testObserver.assertValue(statusResponse ->
			statusResponse.getOrigin().getKey().equals(peer.getPublicKey()));
	}

	@Test
	public void when_receive_status_response__then_should_receive_it() {
		TestSubscriber<RemoteEvent<StatusResponse>> testObserver =
			this.messageCentralLedgerSync.statusResponses().test();
		final var peer = createPeer();
		final var header = mock(LedgerProof.class);
		StatusResponseMessage statusResponseMessage = mock(StatusResponseMessage.class);
		when(statusResponseMessage.getHeader()).thenReturn(header);
		messageCentral.send(peer, statusResponseMessage);
		testObserver.awaitCount(1);
		testObserver.assertValue(statusResponse ->
			statusResponse.getEvent().getHeader().equals(header)
				&& statusResponse.getOrigin().getKey().equals(peer.getPublicKey())
		);
	}

	@Test
	public void when_receive_ledger_update__then_should_receive_it() {
		final var testObserver =
			this.messageCentralLedgerSync.ledgerStatusUpdates().test();
		final var peer = createPeer();
		final var updateMsg = mock(LedgerStatusUpdateMessage.class);
		messageCentral.send(peer, updateMsg);
		testObserver.awaitCount(1);
		testObserver.assertValue(receivedMsg -> receivedMsg.getOrigin().getKey().equals(peer.getPublicKey()));
	}

	private NodeId createPeer() {
		final var key = ECKeyPair.generateNew().getPublicKey();
		return NodeId.fromPublicKey(key);
	}
}
