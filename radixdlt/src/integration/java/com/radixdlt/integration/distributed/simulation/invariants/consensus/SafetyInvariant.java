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

import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.NodeEvents.NodeEvent;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Observable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Checks that validator nodes do not commit on conflicting vertices
 */
public class SafetyInvariant implements TestInvariant {
	private final NodeEvents<BFTCommittedUpdate> commits;

	public SafetyInvariant(NodeEvents<BFTCommittedUpdate> commits) {
		this.commits = commits;
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		final SafetyChecker safetyChecker = new SafetyChecker(network.getNodes());
		return Observable.<NodeEvent<BFTCommittedUpdate>>create(emitter ->
			commits.addListener(emitter::onNext)
		).serialize()
			.flatMap(e -> safetyChecker.process(e).map(Observable::just).orElse(Observable.empty()));
	}
}
