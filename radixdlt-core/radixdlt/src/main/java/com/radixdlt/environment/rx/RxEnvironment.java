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

import com.google.inject.TypeLiteral;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Environment which utilizes RXJava to distribute events from
 * dispatchers to processors.
 */
public final class RxEnvironment implements Environment {
	private final Map<Class<?>, Subject<?>> subjects;
	private final Map<TypeLiteral<?>, Subject<?>> typeLiteralSubjects;
	private final ScheduledExecutorService executorService;
	private final Map<Class<?>, RxRemoteDispatcher<?>> remoteDispatchers;

	public RxEnvironment(
		Set<TypeLiteral<?>> localEventTypeLiterals,
		Set<Class<?>> localEventClasses,
		ScheduledExecutorService executorService,
		Set<RxRemoteDispatcher<?>> remoteDispatchers
	) {
		this.typeLiteralSubjects = localEventTypeLiterals.stream()
			.collect(Collectors.toMap(c -> c, c -> ReplaySubject.createWithSize(5).toSerialized()));
		this.subjects = localEventClasses.stream()
			.collect(Collectors.toMap(c -> c, c -> ReplaySubject.createWithSize(5).toSerialized()));
		this.executorService = Objects.requireNonNull(executorService);
		this.remoteDispatchers = remoteDispatchers.stream()
			.collect(Collectors.toMap(RxRemoteDispatcher::eventClass, d -> d));
	}

	private <T> Optional<Subject<T>> getSubject(TypeLiteral<T> t) {
		@SuppressWarnings("unchecked")
		Subject<T> eventDispatcher = (Subject<T>) typeLiteralSubjects.get(t);

		return Optional.ofNullable(eventDispatcher);
	}

	private <T> Optional<Subject<T>> getSubject(Class<T> eventClass) {
		@SuppressWarnings("unchecked")
		Subject<T> eventDispatcher = (Subject<T>) subjects.get(eventClass);

		return Optional.ofNullable(eventDispatcher);
	}

	@Override
	public <T> EventDispatcher<T> getDispatcher(Class<T> eventClass) {
		return getSubject(eventClass).<EventDispatcher<T>>map(s -> s::onNext).orElse(e -> { });
	}

	@Override
	public <T> ScheduledEventDispatcher<T> getScheduledDispatcher(Class<T> eventClass) {
		return (e, millis) -> getSubject(eventClass)
			.ifPresent(s -> executorService.schedule(() -> s.onNext(e), millis, TimeUnit.MILLISECONDS));
	}

	@Override
	public <T> ScheduledEventDispatcher<T> getScheduledDispatcher(TypeLiteral<T> typeLiteral) {
		return (e, millis) -> getSubject(typeLiteral)
			.ifPresent(s -> executorService.schedule(() -> s.onNext(e), millis, TimeUnit.MILLISECONDS));
	}

	@Override
	public <T> RemoteEventDispatcher<T> getRemoteDispatcher(Class<T> eventClass) {
		if (!remoteDispatchers.containsKey(eventClass)) {
			throw new IllegalStateException("No dispatcher for " + eventClass);
		}

		@SuppressWarnings("unchecked")
		final RemoteEventDispatcher<T> dispatcher = (RemoteEventDispatcher<T>) remoteDispatchers.get(eventClass).dispatcher();
		return dispatcher;
	}

	public <T> Observable<T> getObservable(Class<T> eventClass) {
		return getSubject(eventClass)
			.orElseThrow(() -> new IllegalStateException(eventClass + " not registered as observable."));
	}

	public <T> Observable<T> getObservable(TypeLiteral<T> t) {
		return getSubject(t).orElseThrow();
	}
}
