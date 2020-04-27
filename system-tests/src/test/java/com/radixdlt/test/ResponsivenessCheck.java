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
import java.util.stream.Collectors;

public class ResponsivenessCheck implements RemoteBFTCheck {
	private final long timeout;
	private final TimeUnit timeoutUnit;

	public ResponsivenessCheck(long timeout, TimeUnit timeoutUnit) {
		if (timeout < 1) {
			throw new IllegalArgumentException("timeout must be >= 1 but was " + timeout);
		}
		this.timeout = timeout;
		this.timeoutUnit = Objects.requireNonNull(timeoutUnit);
	}

	@Override
	public Single<RemoteBFTCheckResult> check(RemoteBFTNetworkBridge network) {
		return Completable.mergeDelayError(
			network.getNodeIds().stream()
				.map(nodeName -> network.queryEndpointJson(nodeName, "api/ping")
					.timeout(timeout, timeoutUnit)
					.ignoreElement())
				.collect(Collectors.toList()))
			.toSingleDefault(RemoteBFTCheckResult.success())
			.onErrorReturn(RemoteBFTCheckResult::error);
	}

	@Override
	public String toString() {
		return String.format("ResponsivenessCheck{timeout=%d %s}", timeout, timeoutUnit);
	}
}
