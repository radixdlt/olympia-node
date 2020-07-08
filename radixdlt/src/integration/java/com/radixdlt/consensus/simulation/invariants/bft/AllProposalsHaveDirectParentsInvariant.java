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
import com.radixdlt.consensus.ConsensusEventsRx;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.crypto.ECKeyPair;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Check that every proposal on the network has a direct parent.
 * This check only makes sense in networks where there are no failing nodes.
 */
public class AllProposalsHaveDirectParentsInvariant implements TestInvariant {

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		List<Observable<Vertex>> correctProposals = network.getNodes().stream()
			.map(ECKeyPair::getPublicKey)
			.map(network.getUnderlyingNetwork()::getNetworkRx)
			.map(ConsensusEventsRx::consensusEvents)
			.map(p -> p.ofType(Proposal.class).map(Proposal::getVertex))
			.collect(Collectors.toList());

		return Observable.merge(correctProposals)
			.concatMap(v -> {
				if (!v.hasDirectParent()) {
					return Observable.just(new TestInvariantError(String.format("Vertex %s has no direct parent", v)));
				} else {
					return Observable.empty();
				}
			});
	}
}
