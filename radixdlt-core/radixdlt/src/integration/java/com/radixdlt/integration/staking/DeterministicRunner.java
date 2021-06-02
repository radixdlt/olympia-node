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

package com.radixdlt.integration.staking;

import com.google.inject.Inject;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;

import java.util.function.Predicate;

public final class DeterministicRunner {
	private static final int MAX_EVENTS_DEFAULT = 10000;

	private final DeterministicProcessor processor;
	private final DeterministicNetwork network;

	@Inject
	public DeterministicRunner(
		DeterministicProcessor processor,
		DeterministicNetwork network
	) {
		this.processor = processor;
		this.network = network;
	}

	public void start() {
		processor.start();
	}

	public <T> T runNextEventsThrough(Class<T> eventClass) {
		return runNextEventsThrough(eventClass, t -> true);
	}

	@SuppressWarnings("unchecked")
	public <T> T runNextEventsThrough(Class<T> eventClass, Predicate<T> eventPredicate) {
		for (int i = 0; i < MAX_EVENTS_DEFAULT; i++) {
			var msg = network.nextMessage().value();
			processor.handleMessage(msg.origin(), msg.message(), msg.typeLiteral());
			if (eventClass.isInstance(msg.message()) && eventPredicate.test((T) msg.message())) {
				return (T) msg.message();
			}
		}

		throw new RuntimeException("Reached " + MAX_EVENTS_DEFAULT + " events without finding " + eventClass);
	}
}
