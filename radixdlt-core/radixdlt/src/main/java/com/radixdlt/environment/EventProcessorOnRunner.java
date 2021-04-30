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

import com.google.inject.TypeLiteral;

import java.util.Objects;
import java.util.Optional;

/**
 * An Event Processor registered to run on a runner.
 *
 * @param <T> The event class
 */
public final class EventProcessorOnRunner<T> {
	private final String runnerName;
	private final Class<T> eventClass;
	private final TypeLiteral<T> typeLiteral;
	private final EventProcessor<T> processor;
	private final long rateLimitDelayMs;

	public EventProcessorOnRunner(String runnerName, Class<T> eventClass, EventProcessor<T> processor) {
		this(runnerName, eventClass, null, processor, 0);
	}

	public EventProcessorOnRunner(String runnerName, TypeLiteral<T> typeLiteral, EventProcessor<T> processor) {
		this(runnerName, null, typeLiteral, processor, 0);
	}

	public EventProcessorOnRunner(String runnerName, Class<T> eventClass, EventProcessor<T> processor, long rateLimitDelayMs) {
		this(runnerName, eventClass, null, processor, rateLimitDelayMs);
	}

	private EventProcessorOnRunner(
		String runnerName,
		Class<T> eventClass,
		TypeLiteral<T> typeLiteral,
		EventProcessor<T> processor,
		long rateLimitDelayMs
	) {
		this.runnerName = Objects.requireNonNull(runnerName);
		this.eventClass = eventClass;
		this.typeLiteral = typeLiteral;
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

	public <U> Optional<EventProcessor<U>> getProcessor(Class<U> c) {
		if (eventClass.isAssignableFrom(c)) {
			return Optional.of((EventProcessor<U>) processor);
		}

		return Optional.empty();
	}

	public Optional<Class<T>> getEventClass() {
		return Optional.ofNullable(eventClass);
	}

	public Optional<TypeLiteral<T>> getTypeLiteral() {
		return Optional.ofNullable(typeLiteral);
	}
}
