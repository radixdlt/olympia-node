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

package com.radixdlt.integration.distributed.simulation.invariants.epochs;

import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.NodeEvents;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import io.reactivex.rxjava3.core.Observable;
import java.util.Objects;

/**
 * Invariant which checks that a committed vertex never goes above some view
 */
public class EpochViewInvariant implements TestInvariant {
	private final NodeEvents<BFTCommittedUpdate> commits;
	private final View epochHighView;

	public EpochViewInvariant(View epochHighView, NodeEvents<BFTCommittedUpdate> commits) {
		this.commits = commits;
		this.epochHighView = Objects.requireNonNull(epochHighView);
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		return Observable.<BFTCommittedUpdate>create(
			emitter -> this.commits.addListener(commit -> emitter.onNext(commit.event()))
		).serialize()
			.concatMap(committedUpdate -> Observable.fromStream(committedUpdate.getCommitted().stream()))
			.flatMap(vertex -> {
				final View view = vertex.getView();
				if (view.compareTo(epochHighView) > 0) {
					return Observable.just(
						new TestInvariantError(
							String.format("Vertex committed with view %s but epochHighView is %s", view, epochHighView)
						)
					);
				}

				return Observable.empty();
			});
	}

}
