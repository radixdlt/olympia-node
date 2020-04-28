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
import io.reactivex.Single;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A BFT test running checks against a test network on a specific schedule.
 * Optionally this test can wait for certain conditions to be met before running the actual checks.
 */
public final class RemoteBFTTest {
	private final RemoteBFTNetworkBridge testNetwork;
	private final ImmutableList<RemoteBFTCheck> prerequisites;
	private final long prerequisiteTimeout;
	private final TimeUnit prerequisiteTimeoutUnit;
	private final ImmutableList<RemoteBFTCheck> checks;
	private final RemoteBFTCheckSchedule schedule;

	private RemoteBFTTest(RemoteBFTNetworkBridge testNetwork,
	                      ImmutableList<RemoteBFTCheck> prerequisites,
	                      long prerequisiteTimeout,
	                      TimeUnit prerequisiteTimeoutUnit,
	                      ImmutableList<RemoteBFTCheck> checks,
	                      RemoteBFTCheckSchedule schedule) {
		this.testNetwork = testNetwork;
		this.prerequisites = prerequisites;
		this.prerequisiteTimeout = prerequisiteTimeout;
		this.prerequisiteTimeoutUnit = prerequisiteTimeoutUnit;
		this.checks = checks;
		this.schedule = schedule;
	}

	/**
	 * Run this test as configured, waiting for prerequisites if required.
	 * This method blocks and completes when the test has concluded or throws an exception if there were errors.
	 * @param runtime The runtime of this test
	 * @param runtimeUnit The unit of the runtime
	 */
	public void runBlocking(long runtime, TimeUnit runtimeUnit) {
		if (!this.prerequisites.isEmpty()) {
			waitForPrerequisitesBlocking();
		}

		// TODO do proper logging instead of using stdout/err
		System.out.printf("running test for %d %s: %s%n", this.prerequisiteTimeout, this.prerequisiteTimeoutUnit, this.checks);
		Observable.merge(
			this.checks.stream()
				.map(check -> this.schedule.schedule(check)
					.map(checkToRun -> checkToRun.check(this.testNetwork)
						.onErrorReturn(RemoteBFTCheckResult::error)
						.doOnSuccess(result -> result.assertSuccess(String.format("check %s failed", checkToRun))))
					.flatMap(Single::toObservable))
				.collect(Collectors.toList()))
			.take(runtime, runtimeUnit)
			.blockingSubscribe();
		System.out.println("test done");
	}

	private void waitForPrerequisitesBlocking() {
		System.out.println("waiting for prerequisites to be satisfied: " + prerequisites);
		List<Observable<RemoteBFTCheckResult>> prerequisiteRuns = this.prerequisites.stream()
			.map(prerequisite -> this.schedule.schedule(prerequisite)
				.map(prerequisiteToRun -> prerequisiteToRun.check(this.testNetwork))
				.flatMap(Single::toObservable))
			.collect(Collectors.toList());
		Observable.combineLatest(prerequisiteRuns, results -> Arrays.stream(results)
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

	/**
	 * Create a {@link Builder} for creating {@link RemoteBFTTest}s.
	 * @return The builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * A builder for {@link RemoteBFTTest} objects.
	 */
	public static class Builder {
		private RemoteBFTNetworkBridge testNetwork;
		private final List<RemoteBFTCheck> checks = new ArrayList<>();
		private final List<RemoteBFTCheck> prerequisites = new ArrayList<>();
		private long prerequisiteTimeout = 5;
		private TimeUnit prerequisiteTimeoutUnit = TimeUnit.MINUTES;
		private RemoteBFTCheckSchedule schedule = RemoteBFTCheckSchedule.interval(3, TimeUnit.SECONDS);

		private Builder() {
		}

		/**
		 * Sets the timeout used when waiting for prerequisites to be satisfied before running the actual test
		 * @param prerequisiteTimeout The prerequisite timeout
		 * @param prerequisiteTimeoutUnit The unit of the prerequisite timeout
		 * @return This builder
		 */
		public Builder prerequisiteTimeout(long prerequisiteTimeout, TimeUnit prerequisiteTimeoutUnit) {
			if (prerequisiteTimeout < 1) {
				throw new IllegalArgumentException("prerequisiteTimeout must be > 0 but was " + prerequisiteTimeout);
			}
			this.prerequisiteTimeout = prerequisiteTimeout;
			this.prerequisiteTimeoutUnit = Objects.requireNonNull(prerequisiteTimeoutUnit);
			return this;
		}

		/**
		 * Sets a given schedule to use for scheduling the individual checks
		 * @param schedule The schedule
		 * @return This builder
		 */
		public Builder schedule(RemoteBFTCheckSchedule schedule) {
			this.schedule = Objects.requireNonNull(schedule, "schedule");
			return this;
		}

		/**
		 * Configures the test to wait until a {@link ResponsivenessCheck} is satisfied with the configured timeout.
		 * @return This builder
		 */
		public Builder waitUntilResponsive() {
			return waitUntil(new ResponsivenessCheck(1, TimeUnit.SECONDS));
		}

		/**
		 * Configures the test to wait until the given prerequisite is satisfied before running the actual test.
		 * This waiting observes the timeout set in prerequisiteTimeout.
		 * @param prerequisite The prerequisite to wait on
		 * @return This builder
		 */
		public Builder waitUntil(RemoteBFTCheck prerequisite) {
			this.prerequisites.add(Objects.requireNonNull(prerequisite, "prerequisite"));
			return this;
		}

		/**
		 * Sets the test network to use.
		 * @param testNetwork The test network
		 * @return This builder
		 */
		public Builder network(RemoteBFTNetworkBridge testNetwork) {
			this.testNetwork = Objects.requireNonNull(testNetwork);
			return this;
		}

		/**
		 * Asserts responsiveness using the {@link ResponsivenessCheck}
		 * @return This builder
		 */
		public Builder assertResponsiveness() {
			return addCheck(new ResponsivenessCheck(1, TimeUnit.SECONDS));
		}

		/**
		 * Asserts safety using the {@link SafetyCheck}
		 * @return This builder
		 */
		public Builder assertSafety() {
			return addCheck(new SafetyCheck(1, TimeUnit.SECONDS));
		}

		/**
		 * Asserts that CONSENSUS_TIMEOUT is zero.
		 * @return This builder
		 */
		public Builder assertNoTimeouts() {
			return addCheck(CounterCheck.checkEquals(SystemCounters.CounterType.CONSENSUS_TIMEOUT, 0L));
		}

		/**
		 * Asserts that CONSENSUS_SYNC_EXCEPTION is zero.
		 * @return This builder
		 */
		public Builder assertNoRejectedProposals() {
			return addCheck(CounterCheck.checkEquals(SystemCounters.CounterType.CONSENSUS_SYNC_EXCEPTION, 0L));
		}

		/**
		 * Asserts that CONSENSUS_SYNC_EXCEPTION is zero.
		 * @return This builder
		 */
		public Builder assertNoSyncExceptions() {
			return addCheck(CounterCheck.checkEquals(SystemCounters.CounterType.CONSENSUS_SYNC_EXCEPTION, 0L));
		}

		/**
		 * Asserts that CONSENSUS_INDIRECT_PARENT is zero.
		 * @return This builder
		 */
		public Builder assertAllProposalsHaveDirectParents() {
			return addCheck(CounterCheck.checkEquals(SystemCounters.CounterType.CONSENSUS_INDIRECT_PARENT, 0L));
		}

		/**
		 * Adds the given check to be asserted during this test
		 * @param check The check to add
		 * @return This builder
		 */
		public Builder addCheck(RemoteBFTCheck check) {
			this.checks.add(Objects.requireNonNull(check, "check"));
			return this;
		}

		/**
		 * Builds the configured test.
		 * This requires a testNetwork to be set using the network method.
		 * @return This builder
		 */
		public RemoteBFTTest build() {
			if (this.testNetwork == null) {
				throw new IllegalStateException("testNetwork not set");
			}

			return new RemoteBFTTest(
				this.testNetwork,
				ImmutableList.copyOf(this.prerequisites),
				this.prerequisiteTimeout,
				this.prerequisiteTimeoutUnit,
				ImmutableList.copyOf(this.checks),
				this.schedule
			);
		}
	}
}
