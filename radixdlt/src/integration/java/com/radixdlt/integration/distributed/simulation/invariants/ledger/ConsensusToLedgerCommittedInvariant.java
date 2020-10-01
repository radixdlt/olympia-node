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

package com.radixdlt.integration.distributed.simulation.invariants.ledger;

import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Observable;
import java.util.concurrent.TimeUnit;

/**
 * Checks to make sure that everything committed by consensus eventually makes it to the ledger
 * of atleast one node (TODO: test for every node)
 */
public class ConsensusToLedgerCommittedInvariant implements TestInvariant {

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		return network.bftCommittedUpdates()
			.map(Pair::getSecond)
			.concatMap(committedUpdate -> Observable.fromStream(committedUpdate.getCommitted().stream()))
			.filter(v -> v.getCommand() != null)
			.flatMapMaybe(v -> network
				.ledgerUpdates()
				.filter(nodeAndCmd -> nodeAndCmd.getSecond().getNewCommands().contains(v.getCommand()))
				.timeout(10, TimeUnit.SECONDS)
				.firstOrError()
				.ignoreElement()
				.onErrorReturn(e -> new TestInvariantError(
					"Committed vertex " + v + " has not been inserted into the ledger after 10 seconds")
				)
			);
	}
}
