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

package com.radixdlt.utils.streams;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.Test;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class RoundRobinBackpressuredProcessorTest {

    @Test
    public void test_messages_delivered_in_round_robin_manner() {
        final var publisher1 = PublishSubject.<String>create();
        final var publisher2 = PublishSubject.<String>create();
        final var publisher3 = PublishSubject.<String>create();

        final var processor = new RoundRobinBackpressuredProcessor<String>();
        processor.subscribeTo(publisher2.toFlowable(BackpressureStrategy.BUFFER));
        processor.subscribeTo(publisher1.toFlowable(BackpressureStrategy.BUFFER));
        processor.subscribeTo(publisher3.toFlowable(BackpressureStrategy.BUFFER));

        final var subscriber = Flowable.fromPublisher(processor).test(0);

        IntStream.range(0, 10).forEach(n -> publisher1.onNext("publisher1"));
        IntStream.range(0, 10).forEach(n -> publisher2.onNext("publisher2"));
        IntStream.range(0, 10).forEach(n -> publisher3.onNext("publisher3"));

        subscriber.request(9);
        subscriber.awaitCount(9);
        final var receivedMessages = subscriber.values();
        for (int i = 0; i < 9; i += 3) { // should get 3 groups of messages
            assertEquals("publisher2", receivedMessages.get(i));
            assertEquals("publisher1", receivedMessages.get(i + 1));
            assertEquals("publisher3", receivedMessages.get(i + 2));
        }
    }
}
