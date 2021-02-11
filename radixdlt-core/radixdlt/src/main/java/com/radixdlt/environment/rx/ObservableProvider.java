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

package com.radixdlt.environment.rx;

import com.google.inject.Inject;
import com.google.inject.Provider;
import io.reactivex.rxjava3.core.Observable;

import java.util.Objects;

/**
 * Helper class to hook up observables to the environment
 */
public final class ObservableProvider<T> implements Provider<Observable<T>> {
    @Inject
    private Provider<RxEnvironment> rxEnvironmentProvider;
    private final Class<T> c;

    ObservableProvider(Class<T> c) {
        this.c = Objects.requireNonNull(c);
    }

    @Override
    public Observable<T> get() {
        return rxEnvironmentProvider.get().getObservable(c);
    }
}
