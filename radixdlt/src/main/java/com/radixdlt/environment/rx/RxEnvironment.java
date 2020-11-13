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

package com.radixdlt.environment.rx;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Environment which utilizes RXJava to distribute events from
 * dispatchers to processors.
 */
public final class RxEnvironment implements Environment {
	private final ImmutableMap<Class<?>, Subject<?>> subjects;
	private final ScheduledExecutorService executorService;

	public RxEnvironment(
		ImmutableSet<Class<?>> localEventClasses,
		ScheduledExecutorService executorService
	) {
		this.subjects = localEventClasses.stream()
			.collect(ImmutableMap.toImmutableMap(c -> c, c -> BehaviorSubject.create().toSerialized()));
		this.executorService = Objects.requireNonNull(executorService);
	}

	private <T> Subject<T> getSubject(Class<T> eventClass) {
		@SuppressWarnings("unchecked")
		Subject<T> eventDispatcher = (Subject<T>) subjects.get(eventClass);

		if (eventDispatcher == null) {
			throw new IllegalStateException("RxEnvironment does not support event class: " + eventClass);
		}

		return eventDispatcher;
	}

	@Override
	public <T> EventDispatcher<T> getDispatcher(Class<T> eventClass) {
		return getSubject(eventClass)::onNext;
	}

	@Override
	public <T> ScheduledEventDispatcher<T> getScheduledDispatcher(Class<T> eventClass) {
		return (e, millis) -> executorService.schedule(() -> getSubject(eventClass).onNext(e), millis, TimeUnit.MILLISECONDS);
	}

	public <T> Observable<T> getObservable(Class<T> eventClass) {
		return getSubject(eventClass);
	}
}
