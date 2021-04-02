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

package com.radixdlt.integration.distributed.simulation.monitors.ledger;

import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.monitors.NodeEvents;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Checks to make sure that everything committed by consensus eventually makes it to the ledger
 * of atleast one node (TODO: test for every node)
 */
public class ConsensusToLedgerCommittedInvariant implements TestInvariant {
	private final NodeEvents commits;

	public ConsensusToLedgerCommittedInvariant(NodeEvents commits) {
		this.commits = commits;
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		BehaviorSubject<Set<Txn>> committedTxns = BehaviorSubject.create();
		Disposable d = network.ledgerUpdates().<Set<Txn>>scan(
			new HashSet<>(),
			(set, next) -> {
				set.addAll(next.getSecond().getNewTxns());
				return set;
			}
		).subscribe(committedTxns::onNext);

		return Observable.<BFTCommittedUpdate>create(emitter ->
			commits.addListener((node, event) -> emitter.onNext(event), BFTCommittedUpdate.class)
		).serialize()
			.concatMap(committedUpdate -> Observable.fromStream(committedUpdate.getCommitted().stream()
				.flatMap(PreparedVertex::successfulCommands)))
			.flatMapMaybe(txn -> committedTxns
				.filter(cmdSet -> cmdSet.contains(txn.txn()))
				.timeout(10, TimeUnit.SECONDS)
				.firstOrError()
				.ignoreElement()
				.onErrorReturn(e -> new TestInvariantError(
					"Committed command in vertex has not been inserted into the ledger after 10 seconds")
				)
			).doFinally(d::dispose);
	}
}
