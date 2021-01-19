package com.radixdlt.utils.streams;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.Test;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class RoundRobinBackpressuredProcessorTest {

    @Test
    public void test_messages_delivered_in_round_robin_manner() throws InterruptedException {
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

        // should receive 3 groups of messages from each publisher, starting at 2 (subscribed first)
        for (int i = 0; i < 9; i += 3) {
            subscriber.request(3);
            subscriber.awaitCount(3 * (i + 1));
            final var receivedMessages = subscriber.values();
            assertEquals("publisher2", receivedMessages.get(i));
            assertEquals("publisher1", receivedMessages.get(i + 1));
            assertEquals("publisher3", receivedMessages.get(i + 2));
        }
    }
}
