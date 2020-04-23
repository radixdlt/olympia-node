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

package com.radixdlt.consensus.simulation.checks;

import com.radixdlt.consensus.simulation.BFTCheck;
import com.radixdlt.consensus.simulation.BFTNetworkSimulation;
import com.radixdlt.counters.SystemCounters.CounterType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

/**
 * Periodically checks new occurences of syncing success
 */
public final class SyncOccurrenceCheck implements BFTCheck {
	private final long duration;
	private final TimeUnit timeUnit;
	private final LongConsumer syncsInPeriodAssertion;

	/**
	 * Creates a new check on syncs that have occurred in a given period
	 * @param syncsInPeriodAssertion assertion to be run every period
	 * @param duration duration of the period
	 * @param timeUnit time unit of the period
	 */
	public SyncOccurrenceCheck(LongConsumer syncsInPeriodAssertion, long duration, TimeUnit timeUnit) {
		this.duration = duration;
		this.timeUnit = timeUnit;
		this.syncsInPeriodAssertion = syncsInPeriodAssertion;
	}

	@Override
	public Completable check(BFTNetworkSimulation network) {
		return Observable.interval(duration, timeUnit)
			.map(i -> network.getNodes().stream()
				.map(network::getCounters)
				.mapToLong(c -> c.get(CounterType.CONSENSUS_SYNC_SUCCESS))
				.sum()
			)
			.buffer(2, 1)
			.map(l -> l.get(1) - l.get(0))
			.doOnNext(syncsInPeriodAssertion::accept)
			.flatMapCompletable(p -> Completable.complete());
	}
}
