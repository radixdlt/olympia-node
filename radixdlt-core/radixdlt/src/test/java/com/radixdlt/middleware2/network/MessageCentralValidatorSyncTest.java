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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageCentralMockProvider;

import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.utils.RandomHasher;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.radix.universe.system.RadixSystem;

public class MessageCentralValidatorSyncTest {
	private BFTNode self;
	private MessageCentral messageCentral;
	private MessageCentralValidatorSync sync;
	private Hasher hasher;

	@Before
	public void setUp() {
		this.self = mock(BFTNode.class);
		EUID selfEUID = mock(EUID.class);
		ECPublicKey pubKey = mock(ECPublicKey.class);
		when(pubKey.euid()).thenReturn(selfEUID);
		when(self.getKey()).thenReturn(pubKey);
		this.messageCentral = MessageCentralMockProvider.get();
		this.hasher = new RandomHasher();
		this.sync = new MessageCentralValidatorSync(0, messageCentral, hasher);
	}

	@Test
	public void when_send_response__then_message_central_will_send_response() {
		VerifiedVertex vertex = mock(VerifiedVertex.class);
		when(vertex.toSerializable()).thenReturn(mock(UnverifiedVertex.class));
		ImmutableList<VerifiedVertex> vertices = ImmutableList.of(vertex);

		BFTNode node = mock(BFTNode.class);
		ECPublicKey ecPublicKey = mock(ECPublicKey.class);
		when(ecPublicKey.euid()).thenReturn(mock(EUID.class));
		when(node.getKey()).thenReturn(ecPublicKey);

		sync.verticesResponseDispatcher().dispatch(node, new GetVerticesResponse(vertices));
		verify(messageCentral, times(1)).send(any(), any(GetVerticesResponseMessage.class));
	}

	@Test
	public void when_send_error_response__then_message_central_will_send_error_response() {
		QuorumCertificate qc = mock(QuorumCertificate.class);
		HighQC highQC = mock(HighQC.class);
		when(highQC.highestQC()).thenReturn(qc);
		when(highQC.highestCommittedQC()).thenReturn(qc);
		BFTNode node = mock(BFTNode.class);
		ECPublicKey ecPublicKey = mock(ECPublicKey.class);
		when(ecPublicKey.euid()).thenReturn(mock(EUID.class));
		when(node.getKey()).thenReturn(ecPublicKey);
		final var request = new GetVerticesRequest(HashUtils.random256(), 3);

		sync.verticesErrorResponseDispatcher().dispatch(node, new GetVerticesErrorResponse(highQC, request));

		verify(messageCentral, times(1)).send(eq(NodeId.fromPublicKey(ecPublicKey)), any(GetVerticesErrorResponseMessage.class));
	}

	@Test
	public void when_subscribed_to_rpc_requests__then_should_receive_requests() {
		RadixSystem system = mock(RadixSystem.class);
		when(system.getKey()).thenReturn(ECKeyPair.generateNew().getPublicKey());
		HashCode vertexId0 = mock(HashCode.class);
		HashCode vertexId1 = mock(HashCode.class);

		final var peer = NodeId.fromPublicKey(ECKeyPair.generateNew().getPublicKey());
		TestSubscriber<GetVerticesRequest> testObserver = sync.requests().map(RemoteEvent::getEvent).test();
		messageCentral.send(peer, new GetVerticesRequestMessage(0, vertexId0, 1));
		messageCentral.send(peer, new GetVerticesRequestMessage(0, vertexId1, 1));

		testObserver.awaitCount(2);
		testObserver.assertValueAt(0, v -> v.getVertexId().equals(vertexId0));
		testObserver.assertValueAt(1, v -> v.getVertexId().equals(vertexId1));
	}

}
