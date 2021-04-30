/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.environment;

import java.util.Objects;
import java.util.Optional;

/**
 * Registration for a remote event processor to run on a certain runner
 * @param <T> the class of the remote event
 */
public final class RemoteEventProcessorOnRunner<T> {
    private final String runnerName;
    private final Class<T> eventClass;
    private final RemoteEventProcessor<T> processor;
    private final long rateLimitDelayMs;

    public RemoteEventProcessorOnRunner(String runnerName, Class<T> eventClass, RemoteEventProcessor<T> processor) {
        this(runnerName, eventClass, processor, 0);
    }

    public RemoteEventProcessorOnRunner(String runnerName, Class<T> eventClass, RemoteEventProcessor<T> processor, long rateLimitDelayMs) {
        this.runnerName = Objects.requireNonNull(runnerName);
        this.eventClass = Objects.requireNonNull(eventClass);
        this.processor = Objects.requireNonNull(processor);
        if (rateLimitDelayMs < 0) {
            throw new IllegalArgumentException("rateLimitDelayMs must be >= 0.");
        }
        this.rateLimitDelayMs = rateLimitDelayMs;
    }

    public long getRateLimitDelayMs() {
        return rateLimitDelayMs;
    }

    public String getRunnerName() {
        return runnerName;
    }

    public <U> Optional<RemoteEventProcessor<U>> getProcessor(Class<U> c) {
        if (c.isAssignableFrom(eventClass)) {
            return Optional.of((RemoteEventProcessor<U>) processor);
        }

        return Optional.empty();
    }

    public Class<T> getEventClass() {
        return eventClass;
    }

    @Override
    public String toString() {
        return String.format("%s Processor %s", this.runnerName, this.processor);
    }
}
