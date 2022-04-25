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

package com.radixdlt.network.messaging;

import static com.radixdlt.network.messaging.MessagingErrors.IO_ERROR;
import static com.radixdlt.network.messaging.MessagingErrors.MESSAGE_EXPIRED;
import static java.util.Optional.ofNullable;

import com.google.inject.Provider;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCounters.CounterType;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.networks.Addressing;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Compress;
import com.radixdlt.utils.TimeSupplier;
import com.radixdlt.utils.functional.Result;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.radixdlt.middleware2.network.Message;

/** Handles incoming messages. Deserializes raw messages and validates them. */
final class MessagePreprocessor {
  private static final Logger log = LogManager.getLogger();

  private final long messageTtlMs;
  private final SystemCounters counters;
  private final TimeSupplier timeSource;
  private final Serialization serialization;
  private final Provider<PeerControl> peerControl;
  private final Addressing addressing;

  MessagePreprocessor(
      SystemCounters counters,
      MessageCentralConfiguration config,
      TimeSupplier timeSource,
      Serialization serialization,
      Provider<PeerControl> peerControl,
      Addressing addressing) {
    this.messageTtlMs = Objects.requireNonNull(config).messagingTimeToLive(30_000L);
    this.counters = Objects.requireNonNull(counters);
    this.timeSource = Objects.requireNonNull(timeSource);
    this.serialization = Objects.requireNonNull(serialization);
    this.peerControl = Objects.requireNonNull(peerControl);
    this.addressing = Objects.requireNonNull(addressing);
  }

  Result<MessageFromPeer<Message>> process(InboundMessage inboundMessage) {
    final byte[] messageBytes = inboundMessage.message();
    this.counters.add(CounterType.NETWORKING_BYTES_RECEIVED, messageBytes.length);
    final var result =
        deserialize(inboundMessage, messageBytes)
            .flatMap(message -> processMessage(inboundMessage.source(), message));
    this.counters.increment(CounterType.MESSAGES_INBOUND_RECEIVED);
    result.fold(
        unused -> this.counters.increment(CounterType.MESSAGES_INBOUND_DISCARDED),
        unused -> this.counters.increment(CounterType.MESSAGES_INBOUND_PROCESSED));
    return result;
  }

  Result<MessageFromPeer<Message>> processMessage(NodeId source, Message message) {
    final var currentTime = timeSource.currentTime();

    if (currentTime - message.getTimestamp() > messageTtlMs) {
      return MESSAGE_EXPIRED.result();
    } else {
      return Result.ok(new MessageFromPeer<>(source, message));
    }
  }

  private Result<Message> deserialize(InboundMessage inboundMessage, byte[] in) {
    try {
      byte[] uncompressed = Compress.uncompress(in);

      return Result.fromOptional(
          IO_ERROR, ofNullable(serialization.fromDson(uncompressed, Message.class)));
    } catch (IOException e) {
      log.error(
          String.format(
              "Failed to deserialize message from peer %s",
              addressing.forNodes().of(inboundMessage.source().getPublicKey())),
          e);
      peerControl
          .get()
          .banPeer(
              inboundMessage.source(),
              Duration.ofMinutes(5),
              "Failed to deserialize inbound message");
      return IO_ERROR.result();
    }
  }
}
