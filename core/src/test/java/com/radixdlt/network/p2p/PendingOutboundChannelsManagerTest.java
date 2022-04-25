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

package com.radixdlt.network.p2p;

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.network.p2p.transport.PeerChannel;
import com.radixdlt.network.p2p.transport.PeerOutboundBootstrap;
import com.radixdlt.utils.properties.RuntimeProperties;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;

public final class PendingOutboundChannelsManagerTest {

  /*
   * Tests whether the PendingOutboundChannelsManager can be safely accessed from multiple threads by
   *   calling `connectTo` from two threads and ensuring that only a single outbound connection
   *   request was triggered, and a single CompletableFuture object was returned to both callers.
   */
  @Test
  public void
      pending_outbound_channels_manager_should_return_the_same_future_when_called_from_diff_threads()
          throws Exception {
    final var executorService = Executors.newFixedThreadPool(2);

    final var sut =
        new PendingOutboundChannelsManager(
            P2PConfig.fromRuntimeProperties(
                new RuntimeProperties(new JSONObject(), new String[] {})),
            mock(PeerOutboundBootstrap.class),
            rmock(ScheduledEventDispatcher.class),
            rmock(EventDispatcher.class));

    final var remoteNodeId = NodeId.fromPublicKey(ECKeyPair.generateNew().getPublicKey());
    final var remoteNodeUri =
        RadixNodeUri.fromPubKeyAndAddress(1, remoteNodeId.getPublicKey(), "127.0.0.1", 30000);

    // Using 200 iterations as this provides reasonable probability of failure
    //   in case of invalid concurrent access, but doesn't take too long in a happy scenario.
    for (int i = 0; i < 200; i++) {
      final var futureRef1 = new AtomicReference<CompletableFuture<PeerChannel>>();
      final var futureRef2 = new AtomicReference<CompletableFuture<PeerChannel>>();
      executorService.invokeAll(
          List.of(
              Executors.callable(() -> futureRef1.set(sut.connectTo(remoteNodeUri))),
              Executors.callable(() -> futureRef2.set(sut.connectTo(remoteNodeUri)))));

      assertNotNull(futureRef1.get());
      assertNotNull(futureRef2.get());

      // Assert the exact same future object is returned
      assertSame(futureRef1.get(), futureRef2.get());

      // Callback to PendingOutboundChannelsManager with successful connection so that it clears
      // pendingChannels
      final var peerChannel = mock(PeerChannel.class);
      when(peerChannel.getRemoteNodeId()).thenReturn(remoteNodeId);
      sut.peerEventProcessor().process(new PeerEvent.PeerConnected(peerChannel));

      // Just to make sure the Futures were completed
      assertTrue(futureRef1.get().isDone());
      assertTrue(futureRef2.get().isDone());
    }
  }
}
