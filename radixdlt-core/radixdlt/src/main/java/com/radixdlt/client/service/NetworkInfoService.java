/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.client.service;

import com.google.inject.Inject;
import com.radixdlt.counters.SystemCounters;

import java.time.Duration;
import java.time.Instant;

import static com.radixdlt.counters.SystemCounters.CounterType;

public class NetworkInfoService {
	private final SystemCounters systemCounters;
	private final Instant startedAt;

	@Inject
	public NetworkInfoService(SystemCounters systemCounters) {
		this.startedAt = Instant.now();
		this.systemCounters = systemCounters;
	}

	public long throughput() {
		return lifeTimeAverage(CounterType.ELAPSED_BDB_LEDGER_COMMIT);
	}

	public long demand() {
		return lifeTimeAverage(CounterType.MEMPOOL_PROPOSED_TRANSACTION);
	}

	private long lifeTimeAverage(CounterType counterType) {
		var difference = Duration.between(startedAt, Instant.now()).getSeconds();

		return difference > 0 ? systemCounters.get(counterType) / difference : 0;
	}
}
