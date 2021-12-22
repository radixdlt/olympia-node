/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.sync.GetVerticesErrorResponse;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.network.messaging.MessageCentralMockProvider;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.utils.RandomHasher;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;

public class MessageCentralValidatorSyncTest {
  private BFTNode self;
  private MessageCentral messageCentral;
  private MessageCentralValidatorSync sync;
  private Hasher hasher;

  @Before
  public void setUp() {
    this.self = mock(BFTNode.class);
    ECPublicKey pubKey = mock(ECPublicKey.class);
    when(self.getKey()).thenReturn(pubKey);
    this.messageCentral = MessageCentralMockProvider.get();
    this.hasher = new RandomHasher();
    this.sync = new MessageCentralValidatorSync(messageCentral, hasher);
  }

  @Test
  public void when_send_response__then_message_central_will_send_response() {
    VerifiedVertex vertex = mock(VerifiedVertex.class);
    when(vertex.toSerializable()).thenReturn(mock(UnverifiedVertex.class));
    ImmutableList<VerifiedVertex> vertices = ImmutableList.of(vertex);

    BFTNode node = mock(BFTNode.class);
    ECPublicKey ecPublicKey = mock(ECPublicKey.class);
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
    when(node.getKey()).thenReturn(ecPublicKey);
    final var request = new GetVerticesRequest(HashUtils.random256(), 3);

    sync.verticesErrorResponseDispatcher()
        .dispatch(node, new GetVerticesErrorResponse(highQC, request));

    verify(messageCentral, times(1))
        .send(eq(NodeId.fromPublicKey(ecPublicKey)), any(GetVerticesErrorResponseMessage.class));
  }

  @Test
  public void when_subscribed_to_rpc_requests__then_should_receive_requests() {
    HashCode vertexId0 = mock(HashCode.class);
    HashCode vertexId1 = mock(HashCode.class);

    final var peer = NodeId.fromPublicKey(ECKeyPair.generateNew().getPublicKey());
    TestSubscriber<GetVerticesRequest> testObserver =
        sync.requests().map(RemoteEvent::getEvent).test();
    messageCentral.send(peer, new GetVerticesRequestMessage(vertexId0, 1));
    messageCentral.send(peer, new GetVerticesRequestMessage(vertexId1, 1));

    testObserver.awaitCount(2);
    testObserver.assertValueAt(0, v -> v.getVertexId().equals(vertexId0));
    testObserver.assertValueAt(1, v -> v.getVertexId().equals(vertexId1));
  }
}
