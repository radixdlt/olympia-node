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

import com.google.common.collect.Ordering;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import io.reactivex.rxjava3.core.Observable;
import java.util.concurrent.TimeUnit;

/**
 * Check that the network is making progress by ensuring that new QCs and epochs
 * are progressively increasing.
 */
public class LivenessInvariant implements TestInvariant {
	private final NodeEvents nodeEvents;
	private final long duration;
	private final TimeUnit timeUnit;

	public LivenessInvariant(NodeEvents nodeEvents, long duration, TimeUnit timeUnit) {
		this.nodeEvents = nodeEvents;
		this.duration = duration;
		this.timeUnit = timeUnit;
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		return
			Observable.<QuorumCertificate>create(emitter -> {
				nodeEvents.addListener((node, highQCUpdate) -> {
					emitter.onNext(highQCUpdate.getHighQC().highestQC());
				}, BFTHighQCUpdate.class);
				nodeEvents.addListener((node, committed) -> {
					emitter.onNext(committed.getVertexStoreState().getHighQC().highestQC());
				}, BFTCommittedUpdate.class);
			})
				.serialize()
				.map(QuorumCertificate::getProposed)
				.map(header -> EpochView.of(header.getLedgerHeader().getEpoch(), header.getView()))
				.scan(EpochView.of(0, View.genesis()), Ordering.natural()::max)
				.distinctUntilChanged()
				.debounce(duration, timeUnit)
				.map(epochView -> new TestInvariantError(
					String.format("Highest QC hasn't increased from %s after %s %s", epochView, duration, timeUnit)
				));
	}
}
