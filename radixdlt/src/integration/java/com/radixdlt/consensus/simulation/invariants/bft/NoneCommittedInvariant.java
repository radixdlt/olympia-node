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

package com.radixdlt.consensus.simulation.invariants.bft;

import com.radixdlt.consensus.simulation.TestInvariant;
import com.radixdlt.consensus.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Observable;
import java.util.stream.Collectors;

/**
 * Checks that the network never commits a new vertex
 */
public class NoneCommittedInvariant implements TestInvariant {
	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		return Observable.merge(
			network.getNodes().stream().map(
				node -> network.getVertexStoreEvents(node).committedVertices().map(v -> Pair.of(node, v)))
				.collect(Collectors.toList())
		).map(pair -> new TestInvariantError(pair.getFirst() + " node committed a vertex " + pair.getSecond()));
	}
}
