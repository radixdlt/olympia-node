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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RemoteBFTTest {
	private final DockerBFTTestNetwork testNetwork;
	private final ImmutableList<RemoteBFTCheck> prerequisites;
	private final long prerequisiteTimeout;
	private final TimeUnit prerequisiteTimeoutUnit;
	private final ImmutableList<RemoteBFTCheck> checks;

	private RemoteBFTTest(DockerBFTTestNetwork testNetwork,
	                      ImmutableList<RemoteBFTCheck> prerequisites,
	                      long prerequisiteTimeout,
	                      TimeUnit prerequisiteTimeoutUnit,
	                      ImmutableList<RemoteBFTCheck> checks) {
		this.testNetwork = testNetwork;
		this.prerequisites = prerequisites;
		this.prerequisiteTimeout = prerequisiteTimeout;
		this.prerequisiteTimeoutUnit = prerequisiteTimeoutUnit;
		this.checks = checks;
	}

	public void run(long runtime, TimeUnit runtimeUnit) {
		if (!this.prerequisites.isEmpty()) {
			// TODO do proper logging instead of using stdout/err
			System.out.println("waiting for prerequisites to be satisfied: " + prerequisites);
			List<Observable<RemoteBFTCheckResult>> prerequisiteChecks = this.prerequisites.stream()
				.map(prerequisite -> prerequisite.check(this.testNetwork))
				.collect(Collectors.toList());
			Observable.combineLatest(prerequisiteChecks, results -> Arrays.stream(results)
					.map(RemoteBFTCheckResult.class::cast)
					.collect(Collectors.toList()))
				.doOnNext(results -> {
					if (results.stream().anyMatch(RemoteBFTCheckResult::isError)) {
						System.out.println("prerequisites failing, retrying: " + results.stream()
							.filter(RemoteBFTCheckResult::isError)
							.map(RemoteBFTCheckResult::toString)
							.collect(Collectors.joining(", ")));
					}
				})
				.filter(results -> results.stream().allMatch(RemoteBFTCheckResult::isSuccess))
				.firstOrError()
				.retry()
				.timeout(this.prerequisiteTimeout, this.prerequisiteTimeoutUnit)
				.ignoreElement()
				.blockingAwait();
		}

		System.out.printf("running test for %d %s:%s%n", this.prerequisiteTimeout, this.prerequisiteTimeoutUnit, this.checks);
		List<Observable<RemoteBFTCheckResult>> ongoingChecks = this.checks.stream()
			.map(check -> check.check(testNetwork)
				.doOnNext(result -> result.assertSuccess(String.format("check %s failed", check))))
			.collect(Collectors.toList());
		Observable.merge(ongoingChecks)
			.take(runtime, runtimeUnit)
			.blockingSubscribe();
		System.out.println("test done");
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
		private final List<RemoteBFTCheck> prerequisites = new ArrayList<>();
		private long prerequisiteTimeout = 2;
		private TimeUnit prerequisiteTimeoutUnit = TimeUnit.MINUTES;

		private Builder() {
		}

		public Builder prerequisiteTimeout(long prerequisiteTimeout, TimeUnit prerequisiteTimeoutUnit) {
			if (prerequisiteTimeout < 1) {
				throw new IllegalArgumentException("prerequisiteTimeout must be > 0 but was " + prerequisiteTimeout);
			}
			this.prerequisiteTimeout = prerequisiteTimeout;
			this.prerequisiteTimeoutUnit = Objects.requireNonNull(prerequisiteTimeoutUnit);
			return this;
		}

		public Builder waitUntilResponsive() {
			return waitUntil(new ResponsivenessCheck(5, TimeUnit.SECONDS, 1, TimeUnit.SECONDS));
		}

		public Builder waitUntil(RemoteBFTCheck prerequisite) {
			this.prerequisites.add(Objects.requireNonNull(prerequisite, "prerequisite"));
			return this;
		}

		public Builder network(DockerBFTTestNetwork testNetwork) {
			this.testNetwork = Objects.requireNonNull(testNetwork);
			return this;
		}

		public Builder assertResponsiveness() {
			return addCheck(new ResponsivenessCheck(1, TimeUnit.SECONDS, 1, TimeUnit.SECONDS));
		}

		public Builder assertNoTimeouts() {
			return addCheck(new NoTimeoutCheck(1, TimeUnit.SECONDS));
		}

		public Builder addCheck(RemoteBFTCheck check) {
			this.checks.add(Objects.requireNonNull(check, "check"));
			return this;
		}

		public RemoteBFTTest build() {
			if (this.testNetwork == null) {
				throw new IllegalStateException("testNetwork not set");
			}

			return new RemoteBFTTest(this.testNetwork,
				ImmutableList.copyOf(this.prerequisites),
				this.prerequisiteTimeout,
				this.prerequisiteTimeoutUnit,
				ImmutableList.copyOf(this.checks));
		}
	}
}
