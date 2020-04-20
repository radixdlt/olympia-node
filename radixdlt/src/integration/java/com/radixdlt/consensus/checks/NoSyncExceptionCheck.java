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

package com.radixdlt.consensus.checks;

import com.radixdlt.consensus.BFTCheck;
import com.radixdlt.consensus.BFTTestNetwork;
import com.radixdlt.counters.SystemCounters.CounterType;
import io.reactivex.rxjava3.core.Observable;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.Condition;

/**
 * Checks that there are no synchronisation errors.
 */
public class NoSyncExceptionCheck implements BFTCheck {

	@Override
	public Observable<Object> check(BFTTestNetwork network) {
		return Observable.interval(1, TimeUnit.SECONDS)
			.flatMapIterable(i -> network.getNodes())
			.doOnNext(cn -> AssertionsForClassTypes.assertThat(network.getCounters(cn).get(CounterType.CONSENSUS_SYNC_EXCEPTION))
				.satisfies(new Condition<>(c -> c == 0,
					"Sync Exception counter is zero in correct node %s", cn.getPublicKey().euid())))
			.map(o -> o);
	}
}
