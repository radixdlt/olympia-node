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

package com.radixdlt.consensus.simulation.invariants.bft;

import com.radixdlt.consensus.View;
import com.radixdlt.consensus.simulation.TestInvariant;
import com.radixdlt.consensus.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.middleware2.InMemoryEpochInfo;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.TimeUnit;

/**
 * Checks that no local timeouts are occurring.
 * Only makes sense to check in networks where there are no failing nodes.
 */
public class NoTimeoutsInvariant implements TestInvariant {

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		return Observable.interval(1, TimeUnit.SECONDS, Schedulers.io())
			.flatMapIterable(i -> network.getNodes())
			.concatMap(node -> {
				SystemCounters counters = network.getCounters(node);
				InMemoryEpochInfo epochInfo = network.getEpochInfo(node);
				if (counters.get(CounterType.BFT_TIMEOUT) > 0) {
					Pair<Long, View> epochView = epochInfo.getLastTimeout();

					return Observable.just(new TestInvariantError("Timeout at node " + node.getSimpleName()
						+ " epoch " + epochView.getFirst() + " view " + epochView.getSecond()));

				} else {
					return Observable.empty();
				}
			});
	}
}
