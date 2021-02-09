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

        private final Class<T> c;

        DispatcherProvider(Class<T> c) {
            this.c = c;
        }

        @Override
        public EventDispatcher<T> get() {
            return environmentProvider.get().getDispatcher(c);
        }
    }

    private static final class ScheduledDispatcherProvider<T> implements Provider<ScheduledEventDispatcher<T>> {
        @Inject
        private Provider<Environment> environmentProvider;
        private final Class<T> c;

        ScheduledDispatcherProvider(Class<T> c) {
            this.c = c;
        }

        @Override
        public ScheduledEventDispatcher<T> get() {
            return environmentProvider.get().getScheduledDispatcher(c);
        }
    }

    private static final class RemoteDispatcherProvider<T> implements Provider<RemoteEventDispatcher<T>> {
        @Inject
        private Provider<Environment> environmentProvider;
        private final Class<T> c;

        RemoteDispatcherProvider(Class<T> c) {
            this.c = c;
        }

        @Override
        public RemoteEventDispatcher<T> get() {
            return environmentProvider.get().getRemoteDispatcher(c);
        }
    }

    public static <T> Provider<EventDispatcher<T>> dispatcherProvider(Class<T> c) {
        return new DispatcherProvider<>(c);
    }

    public static <T> Provider<ScheduledEventDispatcher<T>> scheduledDispatcherProvider(Class<T> c) {
        return new ScheduledDispatcherProvider<>(c);
    }

    public static <T> Provider<RemoteEventDispatcher<T>> remoteDispatcherProvider(Class<T> c) {
        return new RemoteDispatcherProvider<>(c);
    }
}
