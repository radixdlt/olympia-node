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

package com.radixdlt.integration.distributed.simulation.monitors.application;

import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.monitors.NodeEvents;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.statecomputer.forks.ForkManager;
import io.reactivex.rxjava3.core.Observable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Checks to make sure that commands have been committed in a certain amount
 * of time
 */
public class ForkRegistrationMonitor implements TestInvariant {
	private static final Logger log = LogManager.getLogger();

	private final int minEpoch = 20;

	private final Observable<Txn> submittedTxns;
	private final NodeEvents commits;

	public ForkRegistrationMonitor(Observable<Txn> submittedTxns, NodeEvents commits) {
		this.submittedTxns = Objects.requireNonNull(submittedTxns);
		this.commits = Objects.requireNonNull(commits);
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		return network.latestEpochChanges()
			.filter(epochChange -> epochChange.getEpoch() > minEpoch)
			.firstOrError()
			.map(epochChange -> {
				network.getNodes().forEach(node -> {
					final var key = network.getInstance(ECKeyOps.class, node).nodePubKey();
					final var forkManager = network.getInstance(ForkManager.class, node);
					final var action = new RegisterValidator(
						key,
						node.getSimpleName(),
						"",
						Optional.of(forkManager.latestKnownFork().getHash())
					);
					network.getDispatcher(NodeApplicationRequest.class, node)
						.dispatch(NodeApplicationRequest.create(action));
				});
				return epochChange;
			})
			.ignoreElement()
			.<TestInvariantError>onErrorReturn(e -> new TestInvariantError("ignored"));
	}
}
