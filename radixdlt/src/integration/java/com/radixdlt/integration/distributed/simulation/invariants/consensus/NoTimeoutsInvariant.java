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

import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Checks that no local timeouts are occurring.
 * Only makes sense to check in networks where there are no failing nodes.
 */
public class NoTimeoutsInvariant implements TestInvariant {

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		List<Observable<Pair<BFTNode, Timeout>>> timeouts = network.getNodes().stream()
			.map(n -> network.timeouts())
			.collect(Collectors.toList());

		return Observable.merge(timeouts)
			.map(pair -> new TestInvariantError("Timeout at node " + pair.getFirst().getSimpleName() + " " + pair.getSecond()));
	}
}
