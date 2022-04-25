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

package com.radixdlt.integration.targeted.serialization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.monitoring.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.integration.Slow;
import com.radixdlt.network.messaging.EventQueueFactory;
import com.radixdlt.network.messaging.InboundMessage;
import com.radixdlt.network.messaging.MessageCentralConfiguration;
import com.radixdlt.network.messaging.MessageCentralImpl;
import com.radixdlt.network.messaging.OutboundMessageEvent;
import com.radixdlt.network.messaging.SimplePriorityBlockingQueue;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.network.p2p.PeerManager;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Compress;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import com.radixdlt.middleware2.network.PeerPingMessage;
import com.radixdlt.middleware2.network.Message;
import com.radixdlt.utils.time.Time;

@Category(Slow.class)
public class MessageCentralFuzzyTest {
  private static final int MIN_MESSAGE_LEN = 1;
  private static final int MAX_MESSAGE_LEN = 1024 * 1024;
  private static final int NUM_TEST_MESSAGES = 1000;

  private final Random random = new Random();
  private final Serialization serialization = DefaultSerialization.getInstance();

  @Test
  @SuppressWarnings("unchecked")
  public void fuzzy_messaged_are_not_accepted() throws Exception {
    var inboundMessages = PublishSubject.<InboundMessage>create();
    var config = mock(MessageCentralConfiguration.class);
    var peerControl = mock(PeerControl.class);
    var peerManager = mock(PeerManager.class);
    var queueFactory = mock(EventQueueFactory.class);

    when(config.messagingOutboundQueueMax(anyInt())).thenReturn(1);
    when(config.messagingTimeToLive(anyLong())).thenReturn(30_000L);
    when(peerManager.messages()).thenReturn(inboundMessages);

    when(queueFactory.createEventQueue(anyInt(), any(Comparator.class)))
        .thenReturn(new SimplePriorityBlockingQueue<>(1, OutboundMessageEvent.comparator()));

    var messageCentral =
        new MessageCentralImpl(
            config,
            serialization,
            peerManager,
            Time::currentTimestamp,
            queueFactory,
            new SystemCountersImpl(),
            () -> peerControl,
            Addressing.ofNetwork(Network.LOCALNET));

    var counter = new AtomicLong(0);

    var disposable =
        messageCentral
            .messagesOf(Message.class)
            .subscribe(nextItem -> counter.incrementAndGet(), error -> fail(error.getMessage()));

    // Insert single valid message to ensure whole pipeline is working properly
    emitSingleValidMessage(inboundMessages);
    // Insert batch of randomly generated messages
    emitFuzzyMessages(inboundMessages);

    disposable.dispose();

    // Ensure that only one (valid) message passed through
    assertEquals(1L, counter.get());
  }

  private void emitSingleValidMessage(PublishSubject<InboundMessage> subject) {
    try {
      var bytes =
          Compress.compress(serialization.toDson(new PeerPingMessage(), DsonOutput.Output.WIRE));
      var valid = new InboundMessage(Time.currentTimestamp(), randomNodeId(), bytes);
      subject.onNext(valid);
    } catch (Exception e) {
      // Ignore
    }
  }

  private void emitFuzzyMessages(PublishSubject<InboundMessage> subject) {
    for (int i = 0; i < NUM_TEST_MESSAGES; i++) {
      subject.onNext(generateFuzzyMessage());
    }

    subject.onComplete();
  }

  private InboundMessage generateFuzzyMessage() {
    while (true) {
      try {
        var compressedMessage = Compress.compress(generateRandomBytes());
        return new InboundMessage(Time.currentTimestamp(), randomNodeId(), compressedMessage);
      } catch (Exception e) {
        // Ignore exception and generate new message
      }
    }
  }

  private NodeId randomNodeId() {
    return NodeId.fromPublicKey(ECKeyPair.generateNew().getPublicKey());
  }

  private byte[] generateRandomBytes() {
    var len = random.nextInt(MIN_MESSAGE_LEN, MAX_MESSAGE_LEN);
    var result = new byte[len];

    for (int i = 0; i < len; i++) {
      result[i] = (byte) random.nextInt(0, 255);
    }

    return result;
  }
}
