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

import com.radixdlt.environment.RemoteEventDispatcher;
import java.util.Objects;

/**
 * Remote event dispatcher for rx environment
 * @param <T> the event class
 */
public final class RxRemoteDispatcher<T> {
	private final Class<T> eventClass;
	private final RemoteEventDispatcher<T> dispatcher;

	private RxRemoteDispatcher(Class<T> eventClass, RemoteEventDispatcher<T> dispatcher) {
		this.eventClass = eventClass;
		this.dispatcher = dispatcher;
	}

	public Class<T> eventClass() {
		return eventClass;
	}

	public RemoteEventDispatcher<T> dispatcher() {
		return dispatcher;
	}

	public static <T> RxRemoteDispatcher<T> create(Class<T> eventClass, RemoteEventDispatcher<T> dispatcher) {
		return new RxRemoteDispatcher<>(
			Objects.requireNonNull(eventClass),
			Objects.requireNonNull(dispatcher)
		);
	}
}
