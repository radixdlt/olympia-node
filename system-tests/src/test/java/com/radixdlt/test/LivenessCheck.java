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

import io.reactivex.Single;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A liveness check that scans all nodes for the overall highest QC twice (with a delay) at the "api/vertices/highestqc"
 * endpoint, interpreting an increase in the overall highest QC between two invocations as "liveness".
 *
 * Note that if some nodes fail to respond to the query, they will be ignored; however, that may lead to inadvertent
 * failure of this test where liveness did not actually fail but nodes failed to respond.
 */
public class LivenessCheck implements RemoteBFTCheck {
	private final long patience;
	private final TimeUnit patienceUnit;

	private final long timeout;
	private final TimeUnit timeoutUnit;

	public LivenessCheck(long patience, TimeUnit patienceUnit, long timeout, TimeUnit timeoutUnit) {
		this.patience = patience;
		this.patienceUnit = Objects.requireNonNull(patienceUnit);
		this.timeout = timeout;
		this.timeoutUnit = Objects.requireNonNull(timeoutUnit);
	}

	@Override
	public Single<RemoteBFTCheckResult> check(RemoteBFTNetworkBridge network) {
		return Single.zip(
			getHighestHighestQCView(network),
			Single.timer(patience, patienceUnit).flatMap(l -> getHighestHighestQCView(network)),
			(previousHighestView, currentHighestView) -> {
				if (currentHighestView <= previousHighestView) { // didn't advance during patience interval
					return RemoteBFTCheckResult.error(new LivenessError(
						previousHighestView, currentHighestView, patience, patienceUnit
					));
				} else {
					return RemoteBFTCheckResult.success();
				}
			});
	}

	/**
	 * Gets the highest highest QC view across the entire network, returning 0 if all queries were unsuccessful.
	 * @param network The network to query
	 * @return The highest highest QC view
	 */
	private Single<Long> getHighestHighestQCView(RemoteBFTNetworkBridge network) {
		return Single.zip(
			network.getNodeIds().stream()
				.map(node -> network.queryEndpointJson(node, "api/vertices/highestqc")
					.map(LivenessCheck::extractView)
					.timeout(this.timeout, this.timeoutUnit)
					.doOnError(err -> System.err.printf(
						"error while querying %s for highest QC, excluding from evaluation due to: %s%n",
						node, err))
					.onErrorReturnItem(0L)) // unresponsive nodes are not our concern here
				.collect(Collectors.toList()),
			highestQCsAtNodes -> Arrays.stream(highestQCsAtNodes)
				.mapToLong(Long.class::cast)
				.max() // get overall highest QC
				.orElse(0L));
	}

	/**
	 * Extracts the view out of a QC
	 * @param qcJson The QC, represented as a {@link JSONObject}
	 * @return The QC's view
	 */
	private static long extractView(JSONObject qcJson) {
		return qcJson.getLong("view");
	}

	@Override
	public String toString() {
		return String.format("LivenessCheck{patience=%d %s, timeout=%d %s}", patience, patienceUnit, timeout, timeoutUnit);
	}

	/**
	 * An error that is thrown when liveness was not satisfied
	 */
	public static final class LivenessError extends AssertionError {
		private LivenessError(long previousHighestView, long currentHighestView, long patience, TimeUnit patienceUnit) {
			super(String.format("QC has not advanced from %d (current=%d) after %d %s",
				previousHighestView, currentHighestView, patience, patienceUnit));
		}
	}
}
