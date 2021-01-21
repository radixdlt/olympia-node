/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.utils.streams;

import com.google.common.collect.Sets;
import com.radixdlt.utils.Pair;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * A back-pressured processor that consumes upstream messages in a fair, round-robin manner.
 * It is not strict, i.e. if there are messages on A, but no messages on B, then it will continue to consume
 * messages from A (and will not block until any are available on B).
 * The processor maintains a buffer of at most one message for each upstream publisher.
 *
 * Multiple downstream subscribers are allowed to connect to this processor.
 * It doesn't maintain any cache for slower consumers though and, as a result, ALL downstream consumers
 * need to signal a demand in order for message to be offered (to all of them, at the same time).
 */
public class RoundRobinBackpressuredProcessor<T> implements Publisher<T> {

    private final Object lock = new Object();

    private final AtomicInteger upstreamSubscriptionIdCounter = new AtomicInteger();
    private final AtomicInteger downstreamSubscriberIdCounter = new AtomicInteger();

    private final Map<Integer, Subscriber<? super T>> downstreamSubscribers = new HashMap<>();
    private final Map<Integer, Long> downstreamDemand = new HashMap<>();

    private final Map<Integer, Subscription> upstreamSubscriptions = new HashMap<>();
    private final Set<Integer> requestedSubscriptions = new HashSet<>();

    private final Map<Integer, T> buffer = new HashMap<>();

    private int lastUsedSubscriptionId = -1;

    private boolean isProcessing = false;
    private final Queue<Pair<Integer, T>> unprocessedMessages = new LinkedList<>();
    private final Queue<Pair<Integer, Long>> unprocessedDownstreamRequests = new LinkedList<>();

    public void subscribeTo(Publisher<T> publisher) {
        final int subscriptionId = upstreamSubscriptionIdCounter.getAndIncrement();

        publisher.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                synchronized (lock) {
                    handleSubscribe(subscriptionId, subscription);
                }
            }

            @Override
            public void onNext(T el) {
                synchronized (lock) {
                    handleNext(subscriptionId, el);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                synchronized (lock) {
                    handleError(subscriptionId, throwable);
                }
            }

            @Override
            public void onComplete() {
                synchronized (lock) {
                    handleComplete(subscriptionId);
                }
            }
        });
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        final int subscriberId = downstreamSubscriberIdCounter.getAndIncrement();

        final Subscription downstreamSubscription = new Subscription() {
            @Override
            public void request(long n) {
                synchronized (lock) {
                    handleDownstreamRequest(subscriberId, n);
                }
            }

            @Override
            public void cancel() {
                synchronized (lock) {
                    handleDownstreamCancel(subscriberId);
                }
            }
        };

        synchronized (lock) {
            this.downstreamSubscribers.put(subscriberId, subscriber);
            subscriber.onSubscribe(downstreamSubscription);
        }
    }

    private void handleSubscribe(int subscriptionId, Subscription subscription) {
        this.upstreamSubscriptions.put(subscriptionId, subscription);
        this.requestedSubscriptions.add(subscriptionId);
        subscription.request(1);
    }

    private void handleNext(int subscriptionId, T el) {
        if (this.isProcessing) {
            // onNext was called while other event is still being processed (possibly a recursive call)
            // the message needs to be cached
            this.unprocessedMessages.offer(Pair.of(subscriptionId, el));
        } else {
            this.requestedSubscriptions.remove(subscriptionId);
            this.isProcessing = true;
            processMessage(subscriptionId, el);
            this.isProcessing = false;
            processQueuedEvents();
        }
    }

    private void processMessage(int subscriptionId, T el) {
        if (minDownstreamDemand() > 0) {
            reduceDemandForAll(1L);
            this.lastUsedSubscriptionId = subscriptionId;
            this.requestedSubscriptions.add(subscriptionId);
            this.downstreamSubscribers.values().forEach(s -> s.onNext(el));
            this.upstreamSubscriptions.get(subscriptionId).request(1);
        } else {
            if (this.buffer.containsKey(subscriptionId)) {
                throw new IllegalStateException("Buffer overflow");
            }

            this.buffer.put(subscriptionId, el);
        }
    }

    private long minDownstreamDemand() {
        return this.downstreamDemand.values().stream()
            .min(Long::compareTo).orElse(0L);
    }

    private void reduceDemandForAll(long n) {
        this.downstreamDemand.forEach((k, v) -> this.downstreamDemand.put(k, v - n));
    }

    private void handleError(int subscriptionId, Throwable throwable) {
        this.upstreamSubscriptions.remove(subscriptionId);
        this.requestedSubscriptions.remove(subscriptionId);
    }

    private void handleComplete(int subscriptionId) {
        this.upstreamSubscriptions.remove(subscriptionId);
        this.requestedSubscriptions.remove(subscriptionId);
    }

    private void handleDownstreamRequest(int subscriberId, long n) {
        if (this.isProcessing) {
            this.unprocessedDownstreamRequests.offer(Pair.of(subscriberId, n));
        } else {
            this.isProcessing = true;
            processDownstreamRequest(subscriberId, n);
            this.isProcessing = false;
            processQueuedEvents();
        }
    }

    private void processDownstreamRequest(int subscriberId, long n) {
        this.downstreamDemand.put(subscriberId, this.downstreamDemand.getOrDefault(subscriberId, 0L) + n);

        /*
        Sort in a round-robin way, the publisher lastUsedSubscriptionId+1 goes first, then lastUsedSubscriptionId+2, etc.
        IDs less or equal lastUsedSubscriptionId go last.
         */
        final var sortedBuffer = this.buffer.entrySet().stream()
            .sorted((fst, snd) -> {
                if (fst.getKey() > this.lastUsedSubscriptionId && snd.getKey() <= this.lastUsedSubscriptionId) {
                    return -1;
                } else if (fst.getKey() <= this.lastUsedSubscriptionId && snd.getKey() > this.lastUsedSubscriptionId) {
                    return 1;
                } else {
                    return fst.getKey() - snd.getKey();
                }
            });

        final var messagesToForward = sortedBuffer.limit(minDownstreamDemand()).collect(Collectors.toList());

        reduceDemandForAll(messagesToForward.size());

        messagesToForward.forEach(el -> {
            this.lastUsedSubscriptionId = el.getKey();
            this.buffer.remove(el.getKey());
            this.downstreamSubscribers.values().forEach(s -> s.onNext(el.getValue()));
        });

        final var subscriptionsToRequest =
            new HashSet<>(Sets.difference(
                this.upstreamSubscriptions.keySet(),
                Sets.union(this.requestedSubscriptions, this.buffer.keySet())));

        this.requestedSubscriptions.addAll(subscriptionsToRequest);

        subscriptionsToRequest.stream()
            .map(this.upstreamSubscriptions::get)
            .forEach(s -> s.request(1));
    }

    private void processQueuedEvents() {
        this.isProcessing = true;

        final var messagesToProcess = new ArrayList<>(this.unprocessedMessages);
        this.unprocessedMessages.clear();
        messagesToProcess.forEach(m -> processMessage(m.getFirst(), m.getSecond()));

        final var downstreamRequestsToProcess = new ArrayList<>(this.unprocessedDownstreamRequests);
        this.unprocessedDownstreamRequests.clear();
        downstreamRequestsToProcess.forEach(r -> processDownstreamRequest(r.getFirst(), r.getSecond()));

        this.isProcessing = false;

        if (!this.unprocessedMessages.isEmpty() || !this.unprocessedDownstreamRequests.isEmpty()) {
            processQueuedEvents();
        }
    }

    private void handleDownstreamCancel(int subscriberId) {
        this.downstreamDemand.remove(subscriberId);
        this.downstreamSubscribers.remove(subscriberId);

        if (this.downstreamSubscribers.isEmpty()) {
            this.upstreamSubscriptions.values().forEach(Subscription::cancel);
            this.upstreamSubscriptions.clear();
            this.requestedSubscriptions.clear();
        }
    }
}
