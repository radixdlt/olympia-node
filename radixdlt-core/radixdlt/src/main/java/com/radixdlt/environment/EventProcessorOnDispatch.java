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

package com.radixdlt.environment;

import java.util.Optional;

public final class EventProcessorOnDispatch<T> {
	private final Class<T> eventClass;
	private final EventProcessor<T> processor;

	public EventProcessorOnDispatch(Class<T> eventClass, EventProcessor<T> processor) {
		this.eventClass = eventClass;
		this.processor = processor;
	}

	public <U> Optional<EventProcessor<U>> getProcessor(Class<U> c) {
		if (c.equals(eventClass)) {
			return Optional.of((EventProcessor<U>) processor);
		}

		return Optional.empty();
	}

	public Class<T> getEventClass() {
		return eventClass;
	}
}
