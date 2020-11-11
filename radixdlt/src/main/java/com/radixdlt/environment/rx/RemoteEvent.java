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

import com.radixdlt.consensus.bft.BFTNode;
import io.reactivex.rxjava3.core.Maybe;
import java.util.Objects;

public final class RemoteEvent<T> {
	private final Class<T> eventClass;
	private final T event;
	private final BFTNode origin;

	private RemoteEvent(BFTNode origin, T event, Class<T> eventClass) {
		this.origin = origin;
		this.event = event;
		this.eventClass = eventClass;
	}

	public static <T> RemoteEvent<T> create(BFTNode origin, T event, Class<T> eventClass) {
		Objects.requireNonNull(origin);
		Objects.requireNonNull(event);
		Objects.requireNonNull(eventClass);

		return new RemoteEvent<>(origin, event, eventClass);
	}

	public static <T> Maybe<RemoteEvent<T>> ofEventType(RemoteEvent<?> event, Class<T> eventClass) {
		if (event.eventClass == eventClass) {
			@SuppressWarnings("unchecked")
			RemoteEvent<T> casted = (RemoteEvent<T>) event;
			return Maybe.just(casted);
		} else {
			return Maybe.empty();
		}
	}

	public BFTNode getOrigin() {
		return origin;
	}

	public T getEvent() {
		return event;
	}
}
