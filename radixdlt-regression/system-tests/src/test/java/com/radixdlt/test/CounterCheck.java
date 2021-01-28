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
import io.reactivex.Single;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * An implementation of {@link RemoteBFTCheck} for easily checking values of {@link SystemCounters}
 */
public class CounterCheck implements RemoteBFTCheck {
	private final Consumer<SystemCounters> assertion;
	private final String assertionDescription;
	private  List<String> nodesToIgnore;


	private CounterCheck(Consumer<SystemCounters> assertion, String assertionDescription) {
		this(assertion, assertionDescription, new ArrayList<String>());
	}

	private CounterCheck(Consumer<SystemCounters> assertion, String assertionDescription, List<String> nodesToIgnore) {
		this.assertion = Objects.requireNonNull(assertion);
		this.assertionDescription = assertionDescription;
		this.nodesToIgnore = nodesToIgnore;
	}


	public CounterCheck withNodesToIgnore(List<String> nodesToIgnore) {
		this.nodesToIgnore = nodesToIgnore;
		return this;
	}

	@Override
	public Single<RemoteBFTCheckResult> check(RemoteBFTNetworkBridge network) {
		return Completable.mergeDelayError(network.getNodeIds().stream()
			.filter(nodename -> !nodesToIgnore.contains(nodename))
			.map(nodeName -> network.queryEndpointJson(nodeName, "api/system")
				.map(system -> system.getJSONObject("info").getJSONObject("counters"))
				.map(SystemCounters::from)
				.doOnSuccess(assertion::accept)
				.ignoreElement())
			.collect(Collectors.toList()))
			.toSingleDefault(RemoteBFTCheckResult.success())
			.onErrorReturn(RemoteBFTCheckResult::error);
	}


	@Override
	public String toString() {
		return String.format("CounterCheck{%s}", assertionDescription == null ? "<no description>" : assertionDescription);
	}

	/**
	 * Create a check that asserts the given counterType to equal the given expected value
	 * @param counterType The {@link SystemCounters.CounterType}
	 * @param expectedValue The expected value
	 * @return The check
	 */
	public static CounterCheck checkEquals(SystemCounters.CounterType counterType, long expectedValue) {
		final String assertionDescription = String.format("%s is %d", counterType.toString(), expectedValue);
		return new CounterCheck(counters -> Assert.assertEquals(
			assertionDescription,
			expectedValue, counters.get(SystemCounters.CounterType.BFT_REJECTED)),
			assertionDescription);
	}
}
