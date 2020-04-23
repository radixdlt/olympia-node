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

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ResponsivenessCheck implements RemoteBFTCheck {
	private final long checkInterval;
	private final TimeUnit checkIntervalUnit;
	private final long timeoutInterval;
	private final TimeUnit timeoutIntervalUnit;

	public ResponsivenessCheck(long checkInterval, TimeUnit checkIntervalUnit, long timeoutInterval, TimeUnit timeoutIntervalUnit) {
		if (checkInterval < 1) {
			throw new IllegalArgumentException("checkInterval must be >= 1 but was " + checkInterval);
		}
		this.checkInterval = checkInterval;
		this.checkIntervalUnit = Objects.requireNonNull(checkIntervalUnit);
		if (timeoutInterval < 1) {
			throw new IllegalArgumentException("timeoutInterval must be >= 1 but was " + timeoutInterval);
		}
		this.timeoutInterval = timeoutInterval;
		this.timeoutIntervalUnit = Objects.requireNonNull(timeoutIntervalUnit);
	}

	@Override
	public Observable<RemoteBFTCheckResult> check(DockerBFTTestNetwork network) {
		Observable<RemoteBFTCheckResult> periodicChecks = Observable.interval(checkInterval, checkIntervalUnit)
			.map(i -> network.getNodeNames().stream()
				.map(nodeName -> network.queryJson(nodeName, "api/ping")
					.timeout(timeoutInterval, timeoutIntervalUnit)
					.ignoreElement())
				.collect(Collectors.toList()))
			.map(Completable::merge)
			.map(c -> RemoteBFTCheckResult.success())
			.onErrorReturn(RemoteBFTCheckResult::error);
		return periodicChecks;
	}
}
