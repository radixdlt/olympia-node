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

import io.reactivex.Observable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RemoteBFTTest {
	private final DockerBFTTestNetwork testNetwork;
	private final List<RemoteBFTCheck> checks = new ArrayList<>();

	public RemoteBFTTest(DockerBFTTestNetwork testNetwork) {
		this.testNetwork = Objects.requireNonNull(testNetwork);
	}

	public void waitUntilResponsive(long maxWaitTime, TimeUnit maxWaitTimeUnits) {
		Observable.interval(5, TimeUnit.SECONDS)
			.map(i -> testNetwork.checkResponsive(1, TimeUnit.SECONDS))
			.retry()
			.timeout(maxWaitTime, maxWaitTimeUnits)
			.take(1)
			.blockingSubscribe();
	}

	public void assertResponsiveness() {
		this.checks.add(new ResponsivenessCheck(1, TimeUnit.SECONDS, 1, TimeUnit.SECONDS));
	}

	public void run(long runtime, TimeUnit runtimeUnit) {
		List<Observable<Object>> assertions = checks.stream().map(check -> check.check(testNetwork)).collect(Collectors.toList());
		Observable.merge(assertions)
			.take(runtime, runtimeUnit)
			.blockingSubscribe();
	}

	public DockerBFTTestNetwork getTestNetwork() {
		return testNetwork;
	}
}
