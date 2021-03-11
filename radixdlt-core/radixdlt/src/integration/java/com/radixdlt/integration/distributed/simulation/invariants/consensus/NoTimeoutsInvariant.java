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

package com.radixdlt.integration.distributed.simulation.invariants.consensus;

import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.liveness.LocalTimeoutOccurrence;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.NodeEvents;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import io.reactivex.rxjava3.core.Observable;

/**
 * Checks that no local timeouts are occurring.
 * Only makes sense to check in networks where there are no failing nodes.
 */
public class NoTimeoutsInvariant implements TestInvariant {
	private final NodeEvents nodeTimeouts;

	public NoTimeoutsInvariant(NodeEvents nodeTimeouts) {
		this.nodeTimeouts = nodeTimeouts;
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		return Observable.<TestInvariantError>create(
			emitter -> {
				this.nodeTimeouts.addListener(
					(node, event) -> emitter.onNext(new TestInvariantError("Timeout at node " + node + " " + event)),
					EpochLocalTimeoutOccurrence.class
				);
				this.nodeTimeouts.addListener(
					(node, event) -> emitter.onNext(new TestInvariantError("Timeout at node " + node + " " + event)),
					LocalTimeoutOccurrence.class
				);
			})
			.serialize();
	}
}
