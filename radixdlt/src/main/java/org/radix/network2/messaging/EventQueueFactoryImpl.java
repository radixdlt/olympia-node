package org.radix.network2.messaging;

import org.radix.events.Event;

import java.util.concurrent.PriorityBlockingQueue;

public class EventQueueFactoryImpl<T extends Event> implements EventQueueFactory<T> {
    @Override
    public PriorityBlockingQueue<T> createEventQueue(int queueSize) {
        return new PriorityBlockingQueue<>(queueSize);
    }
}
