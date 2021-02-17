package com.radixdlt.environment;

import java.util.Optional;

public final class ProcessorOnRunner<T> {
    private final String runnerName;
    private final Class<T> eventClass;
    private final EventProcessor<T> processor;

    public ProcessorOnRunner(String runnerName, Class<T> eventClass, EventProcessor<T> processor) {
        this.runnerName = runnerName;
        this.eventClass = eventClass;
        this.processor = processor;
    }

    public String getRunnerName() {
        return runnerName;
    }

    public <U> Optional<EventProcessor<U>> getProcessor(Class<U> c) {
        if (c.equals(eventClass)) {
            return Optional.of((EventProcessor<U>) processor);
        }

        return Optional.empty();
    }

    public Class<T> getEventClass() {
        return eventClass;
    }
}
