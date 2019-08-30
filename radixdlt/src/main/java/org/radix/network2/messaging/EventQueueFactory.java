package org.radix.network2.messaging;

import java.util.concurrent.BlockingQueue;

@FunctionalInterface
public interface EventQueueFactory<T> {
    BlockingQueue<T> createEventQueue(int queueSize);
}
