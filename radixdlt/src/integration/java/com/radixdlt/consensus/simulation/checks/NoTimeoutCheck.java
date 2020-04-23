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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.radixdlt.consensus.simulation.BFTCheck;
import com.radixdlt.consensus.simulation.BFTNetworkSimulation;
import com.radixdlt.counters.SystemCounters.CounterType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Condition;

/**
 * Checks that no local timeouts are occurring.
 * Only makes sense to check in networks where there are no failing nodes.
 */
public class NoTimeoutCheck implements BFTCheck {

	@Override
	public Completable check(BFTNetworkSimulation network) {
		return Observable.interval(1, TimeUnit.SECONDS)
			.flatMapIterable(i -> network.getNodes())
			.map(network::getCounters)
			.doOnNext(counters -> {
				assertThat(counters.get(CounterType.CONSENSUS_TIMEOUT))
					.satisfies(new Condition<>(c -> c == 0, "Timeout counter is zero."));
				assertThat(counters.get(CounterType.CONSENSUS_REJECTED))
					.satisfies(new Condition<>(c -> c == 0, "Rejected Proposal counter is zero."));
			})
			.flatMapCompletable(c -> Completable.complete());
	}
}
