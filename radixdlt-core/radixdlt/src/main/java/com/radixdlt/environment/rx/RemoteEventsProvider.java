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

package com.radixdlt.environment.rx;

import com.google.inject.Inject;
import com.google.inject.Provider;
import io.reactivex.rxjava3.core.Flowable;

import java.util.Objects;

/**
 * Provides remote event flowables from the rx environment
 * @param <T> the class of the remote event
 */
public final class RemoteEventsProvider<T> implements Provider<Flowable<RemoteEvent<T>>> {
    @Inject
    private Provider<RxRemoteEnvironment> rxEnvironmentProvider;
    private final Class<T> c;

    RemoteEventsProvider(Class<T> c) {
        this.c = Objects.requireNonNull(c);
    }

    @Override
    public Flowable<RemoteEvent<T>> get() {
        RxRemoteEnvironment e = rxEnvironmentProvider.get();
        return e.remoteEvents(c);
    }
}
