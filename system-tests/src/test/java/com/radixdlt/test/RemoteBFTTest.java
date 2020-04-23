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

import com.google.common.collect.ImmutableList;
import io.reactivex.Observable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RemoteBFTTest {
	private final DockerBFTTestNetwork testNetwork;
	private final ImmutableList<RemoteBFTCheck> checks;

	private RemoteBFTTest(DockerBFTTestNetwork testNetwork, ImmutableList<RemoteBFTCheck> checks) {
		this.testNetwork = testNetwork;
		this.checks = checks;
	}

	public void waitUntilResponsive(long maxWaitTime, TimeUnit maxWaitTimeUnits) {
		new ResponsivenessCheck(5, TimeUnit.SECONDS, 1, TimeUnit.SECONDS)
			.check(this.testNetwork)
			.takeUntil(RemoteBFTCheckResult::isSuccess)
			.timeout(maxWaitTime, maxWaitTimeUnits)
			.blockingSubscribe();
	}

	public void run(long runtime, TimeUnit runtimeUnit) {
		List<Observable<RemoteBFTCheckResult>> ongoingChecks = this.checks.stream()
			.map(check -> check.check(testNetwork)
				.doOnNext(result -> result.assertSuccess(check)))
			.collect(Collectors.toList());
		Observable.merge(ongoingChecks)
			.take(runtime, runtimeUnit)
			.blockingSubscribe();
	}

	public DockerBFTTestNetwork getTestNetwork() {
		return testNetwork;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private DockerBFTTestNetwork testNetwork;
		private final List<RemoteBFTCheck> checks = new ArrayList<>();

		private Builder() {
		}

		public Builder network(DockerBFTTestNetwork testNetwork) {
			this.testNetwork = Objects.requireNonNull(testNetwork);
			return this;
		}

		public Builder assertResponsiveness() {
			this.checks.add(new ResponsivenessCheck(1, TimeUnit.SECONDS, 1, TimeUnit.SECONDS));
			return this;
		}

		public Builder addCheck(RemoteBFTCheck check) {
			this.checks.add(Objects.requireNonNull(check, "check"));
			return this;
		}

		public RemoteBFTTest build() {
			if (this.testNetwork == null) {
				throw new IllegalStateException("testNetwork not set");
			}

			return new RemoteBFTTest(this.testNetwork, ImmutableList.copyOf(this.checks));
		}
	}

}
