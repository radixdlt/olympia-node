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
import com.radixdlt.consensus.bft.GetVerticesResponse;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexStore.GetVerticesRequest;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware2.network.MessageCentralSyncVerticesRPCNetwork.MessageCentralGetVerticesRequest;
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

public class MessageCentralSyncVerticesRPCNetworkTest {
	private ECPublicKey selfKey;
	private AddressBook addressBook;
	private MessageCentral messageCentral;
	private MessageCentralSyncVerticesRPCNetwork network;

	@Before
	public void setUp() {
		this.selfKey = ECKeyPair.generateNew().getPublicKey();
		Universe universe = mock(Universe.class);
		this.addressBook = mock(AddressBook.class);
		this.messageCentral = mock(MessageCentral.class);
		this.network = new MessageCentralSyncVerticesRPCNetwork(selfKey, universe, addressBook, messageCentral);
	}


	@Test
	public void when_send_rpc_to_self__then_illegal_state_exception_should_be_thrown() {
		assertThatThrownBy(() -> network.sendGetVerticesRequest(mock(Hash.class), selfKey, 1, new Object()))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_get_vertex_and_peer_doesnt_exist__should_receive_error() {
		ECPublicKey node = ECKeyPair.generateNew().getPublicKey();
		when(addressBook.peer(node.euid())).thenReturn(Optional.empty());
		assertThatThrownBy(() -> network.sendGetVerticesRequest(mock(Hash.class), node, 1, new Object()))
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
		network.sendGetVerticesRequest(id, node, count, opaque);
		verify(messageCentral, times(1)).send(eq(peer), any(GetVerticesRequestMessage.class));

		AtomicReference<MessageListener<GetVerticesResponseMessage>> listener = new AtomicReference<>();

		doAnswer(invocation -> {
			listener.set(invocation.getArgument(1));
			return null;
		}).when(messageCentral).addListener(eq(GetVerticesResponseMessage.class), any());

		TestObserver<GetVerticesResponse> testObserver = network.responses().test();

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
	public void when_send_response__then_message_central_will_send_response() {
		MessageCentralGetVerticesRequest request = mock(MessageCentralGetVerticesRequest.class);
		Peer peer = mock(Peer.class);
		when(request.getRequestor()).thenReturn(peer);
		Vertex vertex = mock(Vertex.class);
		when(vertex.getId()).thenReturn(mock(Hash.class));
		when(request.getVertexId()).thenReturn(mock(Hash.class));
		ImmutableList<Vertex> vertices = ImmutableList.of(vertex);
		network.sendGetVerticesResponse(request, vertices);
		verify(messageCentral, times(1)).send(eq(peer), any(GetVerticesResponseMessage.class));
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

		TestObserver<GetVerticesRequest> testObserver = network.requests().test();
		testObserver.awaitCount(2);
		testObserver.assertValueAt(0, v -> v.getVertexId().equals(vertexId0));
		testObserver.assertValueAt(1, v -> v.getVertexId().equals(vertexId1));
	}
}