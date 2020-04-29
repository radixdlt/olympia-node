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
	private final ImmutableList<RemoteBFTCheck> checks;
	private final RemoteBFTCheckSchedule schedule;
	private final long prerequisiteTimeout;
	private final TimeUnit prerequisiteTimeoutUnit;
	private final boolean startConsensusOnRun;

	private RemoteBFTTest(RemoteBFTNetworkBridge testNetwork,
	                      ImmutableList<RemoteBFTCheck> prerequisites,
	                      ImmutableList<RemoteBFTCheck> checks,
	                      RemoteBFTCheckSchedule schedule,
	                      long prerequisiteTimeout,
	                      TimeUnit prerequisiteTimeoutUnit,
	                      boolean startConsensusOnRun) {
		this.testNetwork = testNetwork;
		this.prerequisites = prerequisites;
		this.prerequisiteTimeout = prerequisiteTimeout;
		this.prerequisiteTimeoutUnit = prerequisiteTimeoutUnit;
		this.checks = checks;
		this.schedule = schedule;
		this.startConsensusOnRun = startConsensusOnRun;
	}

	/**
	 * Waits for all configured prerequisites to be satisfied simultaneously with the configured timeout.
	 * @param timeout The wait timeout
	 * @param timeoutUnit The unit of the wait timeout
	 */
	public void waitForPrerequisitesBlocking(long timeout, TimeUnit timeoutUnit) {
		System.out.println("waiting for prerequisites to be satisfied: " + prerequisites);
		// create cold observables containing the prerequisite check schedules
		List<Observable<RemoteBFTCheckResult>> prerequisiteRuns = this.prerequisites.stream()
			.map(prerequisite -> this.schedule.schedule(prerequisite)
				.map(prerequisiteToRun -> prerequisiteToRun.check(this.testNetwork))
				.flatMap(Single::toObservable))
			.collect(Collectors.toList());
		// combine the latest results of executing all prerequisite schedules
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
			.firstOrError() // error and retry if not all check were successful
			.retry()
			.timeout(timeout, timeoutUnit)
			.ignoreElement()
			.blockingAwait();
	}

	/**
	 * Run this test as configured for the specified duration, waiting for prerequisites if required.
	 * This method blocks and completes when the test has concluded or throws an exception if there were errors.
	 * @param duration The duration this test should be run for
	 * @param durationUnit The unit of the duration
	 */
	public void runBlocking(long duration, TimeUnit durationUnit) {
		// wait for prerequisites with the configured timeout if any were specified
		if (!this.prerequisites.isEmpty()) {
			waitForPrerequisitesBlocking(this.prerequisiteTimeout, this.prerequisiteTimeoutUnit);
		}

		// start consensus if required, waiting until all requests have come through (important for some checks)
		if (this.startConsensusOnRun) {
			System.out.println("starting consensus in all nodes");
			testNetwork.startConsensus()
				.blockingAwait();
		}

		// run the actual tests for the configured duration
		// TODO do proper logging instead of using stdout/err
		System.out.printf("running test for %d %s: %s%n", duration, durationUnit, this.checks);
		Observable.merge(
			this.checks.stream()
				.map(check -> this.schedule.schedule(check)
					.map(checkToRun -> checkToRun.check(this.testNetwork)
						.onErrorReturn(error -> RemoteBFTCheckResult.error(InternalBFTCheckError.from(check, error)))
						.doOnSuccess(result -> result.assertSuccess(String.format("check %s failed", checkToRun))))
					.flatMap(Single::toObservable))
				.collect(Collectors.toList()))
			.take(duration, durationUnit)
			.blockingSubscribe();
		System.out.println("test done");
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
		private boolean startConsensusOnRun;

		private Builder() {
		}

		/**
		 * Configures this test to start consensus in all nodes first when run.
		 * @return This builder
		 */
		public Builder startConsensusOnRun() {
			this.startConsensusOnRun = true;
			return this;
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
		 * Asserts liveness using the {@link LivenessCheck}
		 * @return This builder
		 */
		public Builder assertLiveness() {
			return addCheck(new LivenessCheck(1, TimeUnit.SECONDS, patience, patienceUnit));
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
				ImmutableList.copyOf(this.checks),
				this.schedule,
				this.prerequisiteTimeout,
				this.prerequisiteTimeoutUnit,
				startConsensusOnRun);
		}
	}

	/**
	 * An internal {@link RemoteBFTCheck} error thrown by a check and wrapped by the managing test
	 */
	public static final class InternalBFTCheckError extends AssertionError {
		private final RemoteBFTCheck failedCheck;
		private final Throwable error;

		private InternalBFTCheckError(RemoteBFTCheck failedCheck, Throwable error) {
			this.failedCheck = failedCheck;
			this.error = error;
		}

		/**
		 * Creates an {@link InternalBFTCheckError} wrapping the specified error of the given check
		 * @param failedCheck The check that failed
		 * @param error The error the check threw
		 * @return An {@link InternalBFTCheckError} wrapping the given error
		 */
		private static InternalBFTCheckError from(RemoteBFTCheck failedCheck, Throwable error) {
			return new InternalBFTCheckError(failedCheck, error);
		}
	}
}
