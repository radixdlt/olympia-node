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

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.GetVerticesErrorResponse;
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.bft.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.epoch.GetEpochRequest;
import com.radixdlt.consensus.epoch.GetEpochResponse;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware2.network.MessageCentralValidatorSync.MessageCentralGetVerticesRequest;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageListener;
import com.radixdlt.universe.Universe;
import io.reactivex.rxjava3.observers.TestObserver;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

public class MessageCentralValidatorSyncTest {
	private ECPublicKey selfKey;
	private AddressBook addressBook;
	private MessageCentral messageCentral;
	private MessageCentralValidatorSync sync;

	@Before
	public void setUp() {
		this.selfKey = ECKeyPair.generateNew().getPublicKey();
		Universe universe = mock(Universe.class);
		this.addressBook = mock(AddressBook.class);
		this.messageCentral = mock(MessageCentral.class);
		this.sync = new MessageCentralValidatorSync(selfKey, universe, addressBook, messageCentral);
	}


	@Test
	public void when_send_rpc_to_self__then_illegal_state_exception_should_be_thrown() {
		assertThatThrownBy(() -> sync.sendGetVerticesRequest(mock(Hash.class), selfKey, 1, new Object()))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_get_vertex_and_peer_doesnt_exist__should_receive_error() {
		ECPublicKey node = ECKeyPair.generateNew().getPublicKey();
		when(addressBook.peer(node.euid())).thenReturn(Optional.empty());
		assertThatThrownBy(() -> sync.sendGetVerticesRequest(mock(Hash.class), node, 1, new Object()))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_send_request_and_receive_response__then_should_receive_same_opaque() {
		Hash id = mock(Hash.class);
		ECPublicKey node = mock(ECPublicKey.class);
		when(node.euid()).thenReturn(EUID.ONE);
		Peer peer = mock(Peer.class);
		when(addressBook.peer(eq(EUID.ONE))).thenReturn(Optional.of(peer));
		int count = 1;
		Object opaque = mock(Object.class);
		sync.sendGetVerticesRequest(id, node, count, opaque);
		verify(messageCentral, times(1)).send(eq(peer), any(GetVerticesRequestMessage.class));

		AtomicReference<MessageListener<GetVerticesResponseMessage>> listener = new AtomicReference<>();

		doAnswer(invocation -> {
			listener.set(invocation.getArgument(1));
			return null;
		}).when(messageCentral).addListener(eq(GetVerticesResponseMessage.class), any());

		TestObserver<GetVerticesResponse> testObserver = sync.responses().test();

		GetVerticesResponseMessage responseMessage = mock(GetVerticesResponseMessage.class);
		Vertex vertex = mock(Vertex.class);
		when(vertex.getId()).thenReturn(id);
		when(responseMessage.getVertices()).thenReturn(ImmutableList.of(vertex));
		when(responseMessage.getVertexId()).thenReturn(id);
		listener.get().handleMessage(mock(Peer.class), responseMessage);

		testObserver.awaitCount(1);
		testObserver.assertValue(resp -> resp.getOpaque().equals(opaque));
	}

	@Test
	public void when_send_request_and_receive_error_response__then_should_receive_same_opaque() {
		Hash id = mock(Hash.class);
		ECPublicKey node = mock(ECPublicKey.class);
		when(node.euid()).thenReturn(EUID.ONE);
		Peer peer = mock(Peer.class);
		when(addressBook.peer(eq(EUID.ONE))).thenReturn(Optional.of(peer));
		int count = 1;
		Object opaque = mock(Object.class);
		sync.sendGetVerticesRequest(id, node, count, opaque);
		verify(messageCentral, times(1)).send(eq(peer), any(GetVerticesRequestMessage.class));

		AtomicReference<MessageListener<GetVerticesErrorResponseMessage>> listener = new AtomicReference<>();

		doAnswer(invocation -> {
			listener.set(invocation.getArgument(1));
			return null;
		}).when(messageCentral).addListener(eq(GetVerticesErrorResponseMessage.class), any());

		TestObserver<GetVerticesErrorResponse> testObserver = sync.errorResponses().test();

		GetVerticesErrorResponseMessage responseMessage = mock(GetVerticesErrorResponseMessage.class);
		Vertex vertex = mock(Vertex.class);
		when(vertex.getId()).thenReturn(id);
		when(responseMessage.getVertexId()).thenReturn(id);
		when(responseMessage.getHighestCommittedQC()).thenReturn(mock(QuorumCertificate.class));
		when(responseMessage.getHighestQC()).thenReturn(mock(QuorumCertificate.class));
		listener.get().handleMessage(mock(Peer.class), responseMessage);

		testObserver.awaitCount(1);
		testObserver.assertValue(resp -> resp.getOpaque().equals(opaque));
	}

	@Test
	public void when_send_response__then_message_central_will_send_response() {
		MessageCentralGetVerticesRequest request = mock(MessageCentralGetVerticesRequest.class);
		Peer peer = mock(Peer.class);
		when(request.getRequestor()).thenReturn(peer);
		Vertex vertex = mock(Vertex.class);
		when(vertex.getId()).thenReturn(mock(Hash.class));
		when(request.getVertexId()).thenReturn(mock(Hash.class));
		ImmutableList<Vertex> vertices = ImmutableList.of(vertex);
		sync.sendGetVerticesResponse(request, vertices);
		verify(messageCentral, times(1)).send(eq(peer), any(GetVerticesResponseMessage.class));
	}

	@Test
	public void when_send_error_response__then_message_central_will_send_error_response() {
		MessageCentralGetVerticesRequest request = mock(MessageCentralGetVerticesRequest.class);
		Peer peer = mock(Peer.class);
		when(request.getVertexId()).thenReturn(mock(Hash.class));
		when(request.getRequestor()).thenReturn(peer);
		QuorumCertificate qc = mock(QuorumCertificate.class);
		sync.sendGetVerticesErrorResponse(request, qc, qc);
		verify(messageCentral, times(1)).send(eq(peer), any(GetVerticesErrorResponseMessage.class));
	}

	@Test
	public void when_subscribed_to_rpc_requests__then_should_receive_requests() {
		Hash vertexId0 = mock(Hash.class);
		Hash vertexId1 = mock(Hash.class);
		doAnswer(inv -> {
			MessageListener<GetVerticesRequestMessage> messageListener = inv.getArgument(1);
			messageListener.handleMessage(mock(Peer.class), new GetVerticesRequestMessage(0, vertexId0, 1));
			messageListener.handleMessage(mock(Peer.class), new GetVerticesRequestMessage(0, vertexId1, 1));
			return null;
		}).when(messageCentral).addListener(eq(GetVerticesRequestMessage.class), any());

		TestObserver<GetVerticesRequest> testObserver = sync.requests().test();
		testObserver.awaitCount(2);
		testObserver.assertValueAt(0, v -> v.getVertexId().equals(vertexId0));
		testObserver.assertValueAt(1, v -> v.getVertexId().equals(vertexId1));
	}

	@Test
	public void when_send_get_epoch_request__then_message_central_will_send_get_epoch_request() {
		when(addressBook.peer(any(EUID.class))).thenReturn(Optional.of(mock(Peer.class)));
		ECPublicKey author = mock(ECPublicKey.class);
		when(author.euid()).thenReturn(mock(EUID.class));
		sync.sendGetEpochRequest(author, 12345);
		verify(messageCentral, times(1)).send(any(), any(GetEpochRequestMessage.class));
	}

	@Test
	public void when_send_get_epoch_response__then_message_central_will_send_get_epoch_response() {
		when(addressBook.peer(any(EUID.class))).thenReturn(Optional.of(mock(Peer.class)));
		ECPublicKey author = mock(ECPublicKey.class);
		when(author.euid()).thenReturn(mock(EUID.class));
		sync.sendGetEpochResponse(author, mock(VertexMetadata.class));
		verify(messageCentral, times(1)).send(any(), any(GetEpochResponseMessage.class));
	}

	@Test
	public void when_subscribed_to_epoch_requests__then_should_receive_requests() {
		ECPublicKey author = mock(ECPublicKey.class);
		doAnswer(inv -> {
			MessageListener<GetEpochRequestMessage> messageListener = inv.getArgument(1);
			messageListener.handleMessage(mock(Peer.class), new GetEpochRequestMessage(author, 12345, 1));
			return null;
		}).when(messageCentral).addListener(eq(GetEpochRequestMessage.class), any());

		TestObserver<GetEpochRequest> testObserver = sync.epochRequests().test();
		testObserver.awaitCount(1);
		testObserver.assertValueAt(0, r -> r.getEpoch() == 1 && r.getAuthor().equals(author));
	}

	@Test
	public void when_subscribed_to_epoch_responses__then_should_receive_responses() {
		ECPublicKey author = mock(ECPublicKey.class);
		VertexMetadata ancestor = mock(VertexMetadata.class);
		doAnswer(inv -> {
			MessageListener<GetEpochResponseMessage> messageListener = inv.getArgument(1);
			messageListener.handleMessage(mock(Peer.class), new GetEpochResponseMessage(author, 12345, ancestor));
			return null;
		}).when(messageCentral).addListener(eq(GetEpochResponseMessage.class), any());

		TestObserver<GetEpochResponse> testObserver = sync.epochResponses().test();
		testObserver.awaitCount(1);
		testObserver.assertValueAt(0, r -> r.getEpochAncestor().equals(ancestor) && r.getAuthor().equals(author));
	}
}