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
import org.junit.Assert;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class NoTimeoutCheck implements RemoteBFTCheck {
	private final long checkInterval;
	private final TimeUnit checkIntervalUnit;

	public NoTimeoutCheck(long checkInterval, TimeUnit checkIntervalUnit) {
		this.checkInterval = checkInterval;
		this.checkIntervalUnit = checkIntervalUnit;
	}

	@Override
	public Observable<RemoteBFTCheckResult> check(DockerBFTTestNetwork network) {
		return Observable.interval(checkInterval, checkIntervalUnit)
			.map(i -> network.getNodeNames().stream()
				.map(nodeName -> network.queryJson(nodeName, "api/system")
						.map(system -> system.getJSONObject("counters"))
						.map(SystemCounters::from)
						.doOnSuccess(counters -> Assert.assertEquals("timeout counter is zero",
							0, counters.get(SystemCounters.SystemCounterType.CONSENSUS_TIMEOUT)))
						.ignoreElement())
				.collect(Collectors.toList()))
			.map(Completable::mergeDelayError)
			.flatMap(Completable::toObservable)
			.map(c -> RemoteBFTCheckResult.success())
			.onErrorReturn(RemoteBFTCheckResult::error);
	}
}
