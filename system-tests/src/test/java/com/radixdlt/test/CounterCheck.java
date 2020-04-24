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
 *
 */

package com.radixdlt.test;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CounterCheck implements RemoteBFTCheck {
	private final long checkInterval;
	private final TimeUnit checkIntervalUnit;
	private final Consumer<SystemCounters> assertion;

	public CounterCheck(long checkInterval, TimeUnit checkIntervalUnit, Consumer<SystemCounters> assertion) {
		this.assertion = Objects.requireNonNull(assertion);
		this.checkInterval = checkInterval;
		this.checkIntervalUnit = Objects.requireNonNull(checkIntervalUnit);
	}

	@Override
	public Observable<RemoteBFTCheckResult> check(RemoteBFTNetworkBridge network) {
		return Observable.interval(checkInterval, checkIntervalUnit)
			.map(i -> network.getNodeIds().stream()
				.map(nodeName -> network.queryEndpointJson(nodeName, "api/system")
						.map(system -> system.getJSONObject("counters"))
						.map(SystemCounters::from)
						.doOnSuccess(assertion::accept)
						.ignoreElement())
				.collect(Collectors.toList()))
			.map(Completable::mergeDelayError)
			.map(c -> c.toSingleDefault(RemoteBFTCheckResult.success()))
			.flatMap(Single::toObservable)
			.onErrorReturn(RemoteBFTCheckResult::error);
	}

	@Override
	public String toString() {
		return String.format("CounterCheck{checkInterval=%d %s}", checkInterval, checkIntervalUnit);
	}
}
