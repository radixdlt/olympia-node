package org.radix.network2.messaging;

import org.radix.events.Event;

import java.util.concurrent.PriorityBlockingQueue;

public interface EventQueueFactory<T extends Event> {
    PriorityBlockingQueue<T> createEventQueue(int queueSize);
}
