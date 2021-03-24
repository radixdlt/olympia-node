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
 */

package com.radixdlt.integration.distributed.simulation.monitors.consensus;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import io.reactivex.rxjava3.core.Observable;

/**
 * Checks that at all times ledger sync lag is no more than a specified maximum number of state versions
 * (compared to the highest state version in the network) for any node.
 */
public final class MaxLedgerSyncLagInvariant implements TestInvariant {

	private final long maxLag;

	public MaxLedgerSyncLagInvariant(long maxLag) {
		this.maxLag = maxLag;
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		return network.ledgerUpdates().flatMap(unused -> {
			final var maxStateVersion = network
				.getSystemCounters().values().stream()
				.map(sc -> sc.get(SystemCounters.CounterType.LEDGER_STATE_VERSION))
				.max(Long::compareTo)
				.get();

			final var maybeTooMuchLag = network.getSystemCounters().entrySet().stream()
				.filter(e -> e.getValue().get(CounterType.LEDGER_STATE_VERSION) + maxLag < maxStateVersion)
				.findAny();

			return maybeTooMuchLag
				.map(e -> Observable.just(new TestInvariantError(
					String.format("Node %s ledger sync lag exceeded maximum of %s state versions", e.getKey(), maxLag))
				))
				.orElse(Observable.empty());
		});
	}
}
