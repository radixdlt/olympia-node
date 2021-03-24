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

package com.radixdlt.integration.distributed.simulation.monitors;

import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes;
import io.reactivex.rxjava3.core.Observable;

/**
 * Checks that an event of a certain class never occurs in a system
 * @param <T> the class of the event to check for
 */
public final class EventNeverOccursInvariant<T> implements TestInvariant {
	private final NodeEvents nodeEvents;
	private final Class<T> eventClass;

	public EventNeverOccursInvariant(NodeEvents nodeEvents, Class<T> eventClass) {
		this.nodeEvents = nodeEvents;
		this.eventClass = eventClass;
	}

	@Override
	public Observable<TestInvariantError> check(SimulationNodes.RunningNetwork network) {
		return Observable.<TestInvariant.TestInvariantError>create(
			emitter ->
				this.nodeEvents.addListener(
					(node, event) -> emitter.onNext(new TestInvariant.TestInvariantError(
						"Event " + event + " occurred at node " + node)
					),
					eventClass
				)
			)
			.serialize();
	}
}
