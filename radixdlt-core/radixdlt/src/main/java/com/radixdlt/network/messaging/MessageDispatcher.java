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

import static com.radixdlt.network.messaging.MessagingErrors.MESSAGE_EXPIRED;
import static com.radixdlt.utils.functional.Unit.unit;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.network.messaging.router.MessageEnvelope;
import com.radixdlt.network.messaging.serialization.MessageSerialization;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerConnectionException;
import com.radixdlt.network.p2p.PeerManager;
import com.radixdlt.network.p2p.transport.PeerChannel;
import com.radixdlt.utils.TimeSupplier;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Unit;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class MessageDispatcher {
  private static final Logger log = LogManager.getLogger();

  private final NodeId self;
  private final long messageTtlMs;
  private final SystemCounters counters;
  private final MessageSerialization messageSerialization;
  private final TimeSupplier timeSource;
  private final PeerManager peerManager;

  MessageDispatcher(
      NodeId self,
      SystemCounters counters,
      MessageCentralConfiguration config,
      MessageSerialization messageSerialization,
      TimeSupplier timeSource,
      PeerManager peerManager) {
    this.self = Objects.requireNonNull(self);
    this.messageTtlMs = Objects.requireNonNull(config).messagingTimeToLive(30_000L);
    this.counters = Objects.requireNonNull(counters);
    this.messageSerialization = Objects.requireNonNull(messageSerialization);
    this.timeSource = Objects.requireNonNull(timeSource);
    this.peerManager = Objects.requireNonNull(peerManager);
  }

  CompletableFuture<Result<Unit>> send(final OutboundMessageEvent outboundMessage) {
    final var message = outboundMessage.message();
    final var receiver = outboundMessage.receiver();

    if (timeSource.currentTime() - message.getTimestamp() > messageTtlMs) {
      String msg =
          String.format(
              "TTL for %s message to %s has expired", message.getClass().getSimpleName(), receiver);
      log.warn(msg);
      this.counters.increment(CounterType.MESSAGES_OUTBOUND_ABORTED);
      return CompletableFuture.completedFuture(MESSAGE_EXPIRED.result());
    }

    /* first, we try to send the message directly to the receiver */
    return sendDirectly(outboundMessage.receiver(), outboundMessage.message())
        /* if no direct channel is available, we try using a configured proxy node */
        .exceptionallyCompose(
            ex ->
                // we want to try a non-direct channels only if the connection attempt failed
                // for a known reason (PeerChannelException), not to miss unexpected errors
                // the exception might be wrapped in future's wrapper exception (hence we also check
                // ex.getCause())
                (ex instanceof PeerConnectionException
                        || ex.getCause() instanceof PeerConnectionException)
                    ? sendViaConfiguredProxy(outboundMessage.receiver(), outboundMessage.message())
                    : CompletableFuture.failedFuture(ex))
        /* if no configured proxy is available, we try through a certified proxy node */
        .exceptionallyCompose(
            ex ->
                (ex instanceof PeerConnectionException
                        || ex.getCause() instanceof PeerConnectionException)
                    ? sendViaCertifiedProxy(outboundMessage.receiver(), outboundMessage.message())
                    : CompletableFuture.failedFuture(ex))
        /* update the counter if any of above send attempts succeeded and map to Result */
        .thenApply(
            res -> {
              this.counters.increment(CounterType.MESSAGES_OUTBOUND_SENT);
              return Result.ok(res);
            })
        /* log an error when message send fails */
        .exceptionally(
            ex -> logSendError(ex, outboundMessage.receiver(), outboundMessage.message()))
        /* recover any Result.failure from the wrapper exception or create a new Failure obj */
        .exceptionallyCompose(
            ex ->
                ex instanceof FailureException failureException
                    ? CompletableFuture.completedFuture(failureException.failure.result())
                    : CompletableFuture.completedFuture(
                        Result.fail(Failure.failure(1, ex.getMessage()))))
        /* in either success or failure case, update the "processed" counter */
        .whenComplete(
            (unused1, unused2) -> this.counters.increment(CounterType.MESSAGES_OUTBOUND_PROCESSED));
  }

  private CompletableFuture<Unit> sendDirectly(NodeId receiver, Message message) {
    return peerManager
        .findOrCreateDirectChannel(receiver)
        .thenCompose(channel -> serializeAndSend(channel, message));
  }

  private CompletableFuture<Unit> sendViaCertifiedProxy(NodeId receiver, Message message) {
    return peerManager
        .findOrCreateProxyChannel(receiver)
        .thenCompose(channel -> wrapWithEnvelopeAndSend(channel, receiver, message));
  }

  private CompletableFuture<Unit> sendViaConfiguredProxy(NodeId receiver, Message message) {
    return peerManager
        .findOrCreateConfiguredProxyChannel()
        .thenComposeAsync(channel -> wrapWithEnvelopeAndSend(channel, receiver, message));
  }

  private CompletableFuture<Unit> wrapWithEnvelopeAndSend(
      PeerChannel channel, NodeId receiver, Message message) {
    final var messageToSend =
        message instanceof MessageEnvelope
            ? message
            : MessageEnvelope.create(self, receiver, message);
    return serializeAndSend(channel, messageToSend);
  }

  private CompletableFuture<Unit> serializeAndSend(PeerChannel channel, Message message) {
    return toFuture(messageSerialization.serialize(message))
        .thenCompose(
            serializedMessage -> {
              this.counters.add(CounterType.NETWORKING_BYTES_SENT, serializedMessage.length);
              return toFuture(channel.send(serializedMessage));
            });
  }

  private Result<Unit> logSendError(Throwable ex, NodeId receiver, Message message) {
    final var msg =
        String.format("Send %s to %s failed", message.getClass().getSimpleName(), receiver);
    log.warn("{}: {}", msg, ex.getMessage());
    return Result.ok(unit());
  }

  private <T> CompletableFuture<T> toFuture(Result<T> res) {
    return res.fold(
        failure -> CompletableFuture.failedFuture(new FailureException(failure)),
        CompletableFuture::completedFuture);
  }

  /**
   * Local helper class for dealing with the trichotomy of Result.ok, Result.failure and
   * CompletableFuture exception. Makes it easier to work only with exceptions when composing
   * futures and then recover back to the Result with a correct Failure at the end.
   */
  private static final class FailureException extends Exception {
    final Failure failure;

    FailureException(Failure failure) {
      this.failure = failure;
    }
  }
}
