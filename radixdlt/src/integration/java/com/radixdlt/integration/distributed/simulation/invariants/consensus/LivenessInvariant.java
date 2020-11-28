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
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Observable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Check that the network is making progress by ensuring that new QCs and epochs
 * are progressively increasing.
 */
public class LivenessInvariant implements TestInvariant {
	private final long duration;
	private final TimeUnit timeUnit;

	public LivenessInvariant(long duration, TimeUnit timeUnit) {
		this.duration = duration;
		this.timeUnit = timeUnit;
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		AtomicReference<Pair<EpochView, Long>> highestHeader = new AtomicReference<>(
			Pair.of(EpochView.of(0, View.genesis()), 0L)
		);

		Observable<EpochView> highest =
			network.highQCs()
				.map(Pair::getSecond)
				.map(u -> u.getHighQC().highestQC().getProposed())
				.map(header -> EpochView.of(header.getLedgerHeader().getEpoch(), header.getView()))
				.scan(EpochView.of(0, View.genesis()), Ordering.natural()::max);

		return Observable.combineLatest(
			highest,
			Observable.interval(duration * 2, duration, timeUnit),
			Pair::of
		)
			.filter(pair -> pair.getSecond() > highestHeader.get().getSecond())
			.concatMap(pair -> {
				if (pair.getFirst().compareTo(highestHeader.get().getFirst()) <= 0) {
					return Observable.just(
						new TestInvariantError(
							String.format("Highest QC hasn't increased from %s after %s %s", highestHeader.get(), duration, timeUnit)
						)
					);
				} else {
					highestHeader.set(pair);
					return Observable.empty();
				}
			});
	}
}
