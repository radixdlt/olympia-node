/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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

/** Environment which utilizes RXJava to distribute events from dispatchers to processors. */
public final class RxEnvironment implements Environment {
  private final Map<Class<?>, Subject<?>> subjects;
  private final Map<TypeLiteral<?>, Subject<?>> typeLiteralSubjects;
  private final ScheduledExecutorService executorService;
  private final Map<Class<?>, RxRemoteDispatcher<?>> remoteDispatchers;

  public RxEnvironment(
      Set<TypeLiteral<?>> localEventTypeLiterals,
      Set<Class<?>> localEventClasses,
      ScheduledExecutorService executorService,
      Set<RxRemoteDispatcher<?>> remoteDispatchers) {
    this.typeLiteralSubjects =
        localEventTypeLiterals.stream()
            .collect(Collectors.toMap(c -> c, c -> ReplaySubject.createWithSize(5).toSerialized()));
    this.subjects =
        localEventClasses.stream()
            .collect(Collectors.toMap(c -> c, c -> ReplaySubject.createWithSize(5).toSerialized()));
    this.executorService = Objects.requireNonNull(executorService);
    this.remoteDispatchers =
        remoteDispatchers.stream()
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
    return getSubject(eventClass).<EventDispatcher<T>>map(s -> s::onNext).orElse(e -> {});
  }

  @Override
  public <T> ScheduledEventDispatcher<T> getScheduledDispatcher(Class<T> eventClass) {
    return (e, millis) ->
        getSubject(eventClass)
            .ifPresent(
                s -> executorService.schedule(() -> s.onNext(e), millis, TimeUnit.MILLISECONDS));
  }

  @Override
  public <T> ScheduledEventDispatcher<T> getScheduledDispatcher(TypeLiteral<T> typeLiteral) {
    return (e, millis) ->
        getSubject(typeLiteral)
            .ifPresent(
                s -> executorService.schedule(() -> s.onNext(e), millis, TimeUnit.MILLISECONDS));
  }

  @Override
  public <T> RemoteEventDispatcher<T> getRemoteDispatcher(Class<T> eventClass) {
    if (!remoteDispatchers.containsKey(eventClass)) {
      throw new IllegalStateException("No dispatcher for " + eventClass);
    }

    @SuppressWarnings("unchecked")
    final RemoteEventDispatcher<T> dispatcher =
        (RemoteEventDispatcher<T>) remoteDispatchers.get(eventClass).dispatcher();
    return dispatcher;
  }

  public <T> Observable<T> getObservable(Class<T> eventClass) {
    return getSubject(eventClass)
        .orElseThrow(
            () -> new IllegalStateException(eventClass + " not registered as observable."));
  }

  public <T> Observable<T> getObservable(TypeLiteral<T> t) {
    return getSubject(t).orElseThrow();
  }
}
