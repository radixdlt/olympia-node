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

package com.radixdlt.integration.distributed.simulation.invariants.radix_engine;

import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.NodeEvents;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes;
import com.radixdlt.statecomputer.InvalidProposedCommand;
import io.reactivex.rxjava3.core.Observable;

public class NoInvalidProposedCommandsInvariant implements TestInvariant {
	private final NodeEvents nodeEvents;

	public NoInvalidProposedCommandsInvariant(NodeEvents nodeEvents) {
		this.nodeEvents = nodeEvents;
	}

	@Override
	public Observable<TestInvariant.TestInvariantError> check(SimulationNodes.RunningNetwork network) {
		return Observable.<TestInvariant.TestInvariantError>create(
			emitter -> {
				this.nodeEvents.addListener(
					(node, event) -> emitter.onNext(new TestInvariant.TestInvariantError(
						"Invalid proposed command at node " + node + " " + event)
					),
					InvalidProposedCommand.class
				);
			})
			.serialize();
	}
}
