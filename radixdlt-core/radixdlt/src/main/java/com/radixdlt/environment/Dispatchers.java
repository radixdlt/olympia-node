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
 */

package com.radixdlt.environment;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.counters.SystemCounters;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Helper class to set up environment with dispatched events
 */
public final class Dispatchers {
	private Dispatchers() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	private static class DispatcherProvider<T> implements Provider<EventDispatcher<T>> {
		@Inject
		private Provider<Environment> environmentProvider;

		@Inject
		private SystemCounters systemCounters;

		@Inject
		private Set<EventProcessorOnDispatch<?>> onDispatchProcessors;

		private final Class<T> c;
		private final Function<T, SystemCounters.CounterType> counterTypeMapper;

		DispatcherProvider(
			Class<T> c,
			@Nullable Function<T, SystemCounters.CounterType> counterTypeMapper
		) {
			this.c = c;
			this.counterTypeMapper = counterTypeMapper;
		}

		@Override
		public EventDispatcher<T> get() {
			final EventDispatcher<T> dispatcher = environmentProvider.get().getDispatcher(c);
			final Set<EventProcessor<T>> processors = onDispatchProcessors.stream()
				.flatMap(p -> p.getProcessor(c).stream())
				.collect(Collectors.toSet());
			return e -> {
				dispatcher.dispatch(e);
				processors.forEach(p -> p.process(e));
				if (counterTypeMapper != null) {
					systemCounters.increment(counterTypeMapper.apply(e));
				}
			};
		}
	}

	private static final class ScheduledDispatcherProvider<T> implements Provider<ScheduledEventDispatcher<T>> {
		@Inject
		private Provider<Environment> environmentProvider;
		private final Class<T> eventClass;
		private final TypeLiteral<T> eventLiteral;

		ScheduledDispatcherProvider(Class<T> eventClass) {
			this.eventClass = eventClass;
			this.eventLiteral = null;
		}

		ScheduledDispatcherProvider(TypeLiteral<T> eventLiteral) {
			this.eventClass = null;
			this.eventLiteral = eventLiteral;
		}

		@Override
		public ScheduledEventDispatcher<T> get() {
			Environment e = environmentProvider.get();
			if (eventClass != null) {
				return e.getScheduledDispatcher(eventClass);
			} else {
				return e.getScheduledDispatcher(eventLiteral);
			}
		}
	}

	private static final class RemoteDispatcherProvider<T> implements Provider<RemoteEventDispatcher<T>> {
		@Inject
		private Provider<Environment> environmentProvider;

		@Inject
		private SystemCounters systemCounters;

		@Inject
		@Self
		private BFTNode self;

		@Inject
		private Set<EventProcessorOnDispatch<?>> onDispatchProcessors;

		private final SystemCounters.CounterType counterType;
		private final Class<T> c;

		RemoteDispatcherProvider(Class<T> c) {
		    this(c, null);
		}

		RemoteDispatcherProvider(
			Class<T> c,
			@Nullable SystemCounters.CounterType counterType
		) {
			this.c = c;
			this.counterType = counterType;
		}

		@Override
		public RemoteEventDispatcher<T> get() {
			var remoteDispatcher = environmentProvider.get().getRemoteDispatcher(c);
			var localDispatcher = environmentProvider.get().getDispatcher(c);
			final Set<EventProcessor<T>> onDispatch = onDispatchProcessors.stream()
				.flatMap(p -> p.getProcessor(c).stream())
				.collect(Collectors.toSet());
			return (node, e) -> {
				if (node.equals(self)) {
					localDispatcher.dispatch(e);
				} else {
					remoteDispatcher.dispatch(node, e);
				}
				onDispatch.forEach(p -> p.process(e));
				if (counterType != null) {
					systemCounters.increment(counterType);
				}
			};
		}
	}

	public static <T> Provider<EventDispatcher<T>> dispatcherProvider(Class<T> c) {
		return new DispatcherProvider<>(c, null);
	}

	public static <T> Provider<EventDispatcher<T>> dispatcherProvider(
		Class<T> c,
		Function<T, SystemCounters.CounterType> counterTypeMapper
	) {
		return new DispatcherProvider<>(c, counterTypeMapper);
	}

	public static <T> Provider<ScheduledEventDispatcher<T>> scheduledDispatcherProvider(Class<T> c) {
		return new ScheduledDispatcherProvider<>(c);
	}

	public static <T> Provider<ScheduledEventDispatcher<T>> scheduledDispatcherProvider(TypeLiteral<T> t) {
		return new ScheduledDispatcherProvider<>(t);
	}

	public static <T> Provider<RemoteEventDispatcher<T>> remoteDispatcherProvider(Class<T> c) {
		return new RemoteDispatcherProvider<>(c);
	}

	public static <T> Provider<RemoteEventDispatcher<T>> remoteDispatcherProvider(Class<T> c, SystemCounters.CounterType counterType) {
		return new RemoteDispatcherProvider<>(c, counterType);
	}
}
