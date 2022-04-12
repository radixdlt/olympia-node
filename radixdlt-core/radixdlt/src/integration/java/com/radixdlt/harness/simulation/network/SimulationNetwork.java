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

package com.radixdlt.harness.simulation.network;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.environment.rx.RxRemoteEnvironment;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple simulated network implementation that just sends messages to itself with a configurable
 * latency.
 */
public class SimulationNetwork {
  public static final int DEFAULT_LATENCY = 50;

  public static final class MessageInTransit {
    private final Object content;
    private final BFTNode sender;
    private final BFTNode receiver;
    private final long delay;
    private final long delayAfterPrevious;

    private MessageInTransit(
        Object content, BFTNode sender, BFTNode receiver, long delay, long delayAfterPrevious) {
      if (content instanceof RemoteEvent) {
        throw new IllegalArgumentException("Message in transit should not be RemoteEvent");
      }

      this.content = Objects.requireNonNull(content);
      this.sender = sender;
      this.receiver = receiver;
      this.delay = delay;
      this.delayAfterPrevious = delayAfterPrevious;
    }

    private static MessageInTransit newMessage(Object content, BFTNode sender, BFTNode receiver) {
      return new MessageInTransit(content, sender, receiver, 0, 0);
    }

    public <T> Maybe<T> localEvent(Class<T> eventClass) {
      if (sender.equals(receiver) && eventClass.isInstance(content)) {
        return Maybe.just(eventClass.cast(content));
      }

      return Maybe.empty();
    }

    public <T> Maybe<RemoteEvent<T>> remoteEvent(Class<T> eventClass) {
      if (!sender.equals(receiver) && eventClass.isInstance(content)) {
        return Maybe.just(RemoteEvent.create(sender, eventClass.cast(content)));
      }

      return Maybe.empty();
    }

    MessageInTransit delayed(long delay) {
      return new MessageInTransit(content, sender, receiver, delay, delay);
    }

    MessageInTransit delayAfterPrevious(long delayAfterPrevious) {
      return new MessageInTransit(content, sender, receiver, delay, delayAfterPrevious);
    }

    public long getDelayAfterPrevious() {
      return delayAfterPrevious;
    }

    public long getDelay() {
      return delay;
    }

    public Object getContent() {
      return this.content;
    }

    public BFTNode getSender() {
      return sender;
    }

    public BFTNode getReceiver() {
      return receiver;
    }

    @Override
    public String toString() {
      return String.format(
          "%s %s -> %s %d %d",
          content, sender.getSimpleName(), receiver.getSimpleName(), delay, delayAfterPrevious);
    }
  }

  public interface ChannelCommunication {
    Observable<MessageInTransit> transform(
        BFTNode sender, BFTNode receiver, Observable<MessageInTransit> messages);
  }

  private final Subject<MessageInTransit> receivedMessages;
  private final Map<BFTNode, SimulatedNetworkImpl> receivers = new ConcurrentHashMap<>();
  private final ChannelCommunication channelCommunication;

  @Inject
  public SimulationNetwork(ChannelCommunication channelCommunication) {
    this.channelCommunication = Objects.requireNonNull(channelCommunication);
    this.receivedMessages =
        ReplaySubject.<MessageInTransit>createWithSize(1024) // To catch startup timing issues
            .toSerialized();
  }

  public class SimulatedNetworkImpl implements RxRemoteEnvironment {
    private final Flowable<MessageInTransit> myMessages;
    private final BFTNode thisNode;

    private SimulatedNetworkImpl(BFTNode node) {
      this.thisNode = node;
      // filter only relevant messages (appropriate target and if receiving is allowed)
      this.myMessages =
          Flowable.fromObservable(
                  receivedMessages
                      .filter(msg -> msg.receiver.equals(node))
                      .groupBy(MessageInTransit::getSender)
                      .serialize()
                      .flatMap(
                          groupedObservable ->
                              channelCommunication.transform(
                                  groupedObservable.getKey(), node, groupedObservable))
                      .publish()
                      .refCount(),
                  BackpressureStrategy.BUFFER)
              .onBackpressureBuffer(255, false, true /* unbounded */);
    }

    public <T> Observable<T> localEvents(Class<T> eventClass) {
      return myMessages.flatMapMaybe(m -> m.localEvent(eventClass)).toObservable();
    }

    @Override
    public <T> Flowable<RemoteEvent<T>> remoteEvents(Class<T> eventClass) {
      return myMessages.flatMapMaybe(m -> m.remoteEvent(eventClass));
    }

    public <T> RemoteEventDispatcher<T> remoteEventDispatcher(Class<T> eventClass) {
      return this::sendRemoteEvent;
    }

    private <T> void sendRemoteEvent(BFTNode node, T event) {
      receivedMessages.onNext(MessageInTransit.newMessage(event, thisNode, node));
    }
  }

  public SimulatedNetworkImpl getNetwork(BFTNode forNode) {
    return receivers.computeIfAbsent(forNode, SimulatedNetworkImpl::new);
  }
}
