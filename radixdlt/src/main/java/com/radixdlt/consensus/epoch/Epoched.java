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

package com.radixdlt.consensus.epoch;

import java.util.Objects;

/**
 * Epoch wrapper for events
 * @param <T> event which is wrapped
 * TODO: Move other epoch events into this kind of object
 */
public final class Epoched<T> {
	private final long epoch;
	private final T event;

	private Epoched(long epoch, T event) {
		this.epoch = epoch;
		this.event = event;
	}

	public static <T> Epoched<T> create(long epoch, T event) {
		Objects.requireNonNull(event);
		return new Epoched<>(epoch, event);
	}

	public static boolean isInstance(Object event, Class<?> eventClass) {
		if (event instanceof Epoched) {
			Epoched<?> epoched = (Epoched<?>) event;
			return eventClass.isInstance(epoched.event);
		}

		return false;
	}

	public long epoch() {
		return epoch;
	}

	public T event() {
		return event;
	}

	@Override
	public int hashCode() {
		return Objects.hash(epoch, event);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Epoched)) {
			return false;
		}

		Epoched<?> other = (Epoched<?>) o;
		return Objects.equals(other.event, this.event)
			&& other.epoch == this.epoch;
	}

	@Override
	public String toString() {
		return String.format("%s{epoch=%s event=%s}", this.getClass().getSimpleName(), epoch, event);
	}
}
