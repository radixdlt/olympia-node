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

import com.radixdlt.utils.Pair;
import io.reactivex.Single;
import java.util.ArrayList;
import java.util.Comparator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A liveness check that scans all nodes for the overall highest QC twice (with a delay) at the "api/vertices/highestqc"
 * endpoint, interpreting an increase in the overall highest QC between two invocations as "liveness".
 * <p>
 * Note that if some nodes fail to respond to the query, they will be ignored; however, that may lead to inadvertent
 * failure of this test where liveness did not actually fail but nodes failed to respond.
 */
public class LivenessCheck implements RemoteBFTCheck {
	private static final Logger logger = LogManager.getLogger();

	private final long patience;
	private final TimeUnit patienceUnit;

	private final long timeout;
	private final TimeUnit timeoutUnit;
	private List<String> nodesToIgnore;


	private LivenessCheck(long patience, TimeUnit patienceUnit, long timeout, TimeUnit timeoutUnit, List<String> nodesToIgnore) {
		this.patience = patience;
		this.patienceUnit = Objects.requireNonNull(patienceUnit);
		this.timeout = timeout;
		this.timeoutUnit = Objects.requireNonNull(timeoutUnit);
		this.nodesToIgnore = nodesToIgnore;
	}

	public static LivenessCheck with(long patience, TimeUnit patienceUnit, long timeout, TimeUnit timeoutUnit) {
		return new LivenessCheck(patience, patienceUnit, timeout, timeoutUnit, new ArrayList<String>());
	}

	public LivenessCheck withNodesToIgnore(List<String> nodesToIgnore) {
		this.nodesToIgnore = nodesToIgnore;
		return this;
	}

	private static final Comparator<Pair<Long, Long>> EPOCH_AND_VIEW_COMPARATOR =
		Comparator.<Pair<Long, Long>>comparingLong(Pair::getFirst).thenComparingLong(Pair::getSecond);

	@Override
	public Single<RemoteBFTCheckResult> check(RemoteBFTNetworkBridge network) {
		return Single.zip(
			getMaxOfHighestQCView(network),
			Single.timer(patience, patienceUnit).flatMap(l -> getMaxOfHighestQCView(network)),
			(previousHighestView, currentHighestView) -> {
				if (EPOCH_AND_VIEW_COMPARATOR.compare(currentHighestView, previousHighestView) <= 0) {
					// didn't advance during patience interval
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
	 *
	 * @param network The network to query
	 * @return The highest highest QC view
	 */
	private Single<Pair<Long, Long>> getMaxOfHighestQCView(RemoteBFTNetworkBridge network) {
		return Single.zip(
			network.getNodeIds().stream()
				.filter(nodename -> !nodesToIgnore.contains(nodename))
				.map(node -> network.queryEndpointJson(node, "api/vertices/highestqc")
					.map(LivenessCheck::extractEpochAndView)
					.timeout(this.timeout, this.timeoutUnit)
					.doOnError(err -> logger.debug(
						"error while querying {} for highest QC, excluding from evaluation due to: {}",
						node, err)
					)
					.onErrorReturnItem(Pair.of(0L, 0L))
				)// unresponsive nodes are not our concern here
				.collect(Collectors.toList()),
			highestQCsAtNodes -> Arrays.stream(highestQCsAtNodes)
				.map(i -> (Pair<Long, Long>) i)
				.max(EPOCH_AND_VIEW_COMPARATOR)
				.orElse(Pair.of(0L, 0L))
		);
	}

	/**
	 * Extracts the epoch and view out of a QC
	 *
	 * @param qcJson The QC, represented as a {@link JSONObject}
	 * @return The QC's epoch and view
	 */
	private static Pair<Long, Long> extractEpochAndView(JSONObject qcJson) {
		final long epoch = qcJson.has("epoch") ? qcJson.getLong("epoch") : 0L;
		return Pair.of(epoch, qcJson.getLong("view"));
	}

	@Override
	public String toString() {
		return String.format("LivenessCheck{patience=%d %s, timeout=%d %s}", patience, patienceUnit, timeout, timeoutUnit);
	}

	/**
	 * An error that is thrown when liveness was not satisfied
	 */
	public static final class LivenessError extends AssertionError {
		private LivenessError(
			Pair<Long, Long> previousHighestView,
			Pair<Long, Long> currentHighestView,
			long patience,
			TimeUnit patienceUnit
		) {
			super(String.format("QC has not advanced from {epoch=%d view=%d} (current={epoch=%d view=%d}) after %d %s",
				previousHighestView.getFirst(),
				previousHighestView.getSecond(),
				currentHighestView.getFirst(),
				currentHighestView.getSecond(),
				patience,
				patienceUnit
			));
		}
	}
}
