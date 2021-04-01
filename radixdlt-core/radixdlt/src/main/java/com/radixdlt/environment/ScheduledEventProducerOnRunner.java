/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.environment;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * An event producer registered to run on a runner.
 */
public final class ScheduledEventProducerOnRunner<T> {
    private final String runnerName;
    private final EventDispatcher<T> eventDispatcher;
    private final Supplier<T> eventSupplier;
    private final Duration initialDelay;
    private final Duration interval;

    public ScheduledEventProducerOnRunner(
        String runnerName,
        EventDispatcher<T> eventDispatcher,
        Supplier<T> eventSupplier,
        Duration initialDelay,
        Duration interval
    ) {
        this.runnerName = Objects.requireNonNull(runnerName);
        this.eventDispatcher = Objects.requireNonNull(eventDispatcher);
        this.eventSupplier = Objects.requireNonNull(eventSupplier);
        this.initialDelay = initialDelay;
        this.interval = interval;
    }

    public String getRunnerName() {
        return runnerName;
    }

    public EventDispatcher<T> getEventDispatcher() {
        return eventDispatcher;
    }

    public Supplier<T> getEventSupplier() {
        return eventSupplier;
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public Duration getInterval() {
        return interval;
    }
}
