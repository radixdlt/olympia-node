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
 */

package com.radixdlt.consensus.simulation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.simulation.BFTCheck.BFTCheckError;
import com.radixdlt.consensus.simulation.checks.AllProposalsHaveDirectParentsCheck;
import com.radixdlt.consensus.simulation.checks.LivenessCheck;
import com.radixdlt.consensus.simulation.checks.NoSyncExceptionCheck;
import com.radixdlt.consensus.simulation.checks.NoTimeoutCheck;
import com.radixdlt.consensus.simulation.checks.SafetyCheck;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork.LatencyProvider;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * High level BFT Simulation Test Runner
 */
public class BFTSimulatedTest {
	private final ImmutableList<ECKeyPair> nodes;
	private final LatencyProvider latencyProvider;
	private final ImmutableMap<String, BFTCheck> checks;
	private final int pacemakerTimeout;
	private final boolean getVerticesRPCEnabled;

	private BFTSimulatedTest(
		ImmutableList<ECKeyPair> nodes,
		LatencyProvider latencyProvider,
		int pacemakerTimeout,
		boolean getVerticesRPCEnabled,
		ImmutableMap<String, BFTCheck> checks
	) {
		this.nodes = nodes;
		this.latencyProvider = latencyProvider;
		this.checks = checks;
		this.pacemakerTimeout = pacemakerTimeout;
		this.getVerticesRPCEnabled = getVerticesRPCEnabled;
	}

	public static class Builder {
		private final DroppingLatencyProvider latencyProvider = new DroppingLatencyProvider();
		private final ImmutableMap.Builder<String, BFTCheck> checksBuilder = ImmutableMap.builder();
		private List<ECKeyPair> nodes = Collections.singletonList(ECKeyPair.generateNew());
		private int pacemakerTimeout = 8 * TestEventCoordinatorNetwork.DEFAULT_LATENCY;
		private boolean getVerticesRPCEnabled = true;

		private Builder() {
		}

		public Builder addProposalDropper() {
			ImmutableList<ECPublicKey> keys = nodes.stream().map(ECKeyPair::getPublicKey).collect(ImmutableList.toImmutableList());
			this.latencyProvider.addDropper(new OneProposalPerViewDropper(keys, new Random()));
			return this;
		}

		public Builder pacemakerTimeout(int pacemakerTimeout) {
			this.pacemakerTimeout = pacemakerTimeout;
			return this;
		}

		public Builder numNodes(int numNodes) {
			this.nodes = Stream.generate(ECKeyPair::generateNew).limit(numNodes).collect(Collectors.toList());
			return this;
		}

		public Builder numNodesAndLatencies(int numNodes, int... latencies) {
			if (latencies.length != numNodes) {
				throw new IllegalArgumentException(String.format("Number of latencies (%d) not equal to numNodes (%d)", numNodes, latencies.length));
			}
			this.nodes = Stream.generate(ECKeyPair::generateNew).limit(numNodes).collect(Collectors.toList());
			Map<ECPublicKey, Integer> nodeLatencies = IntStream.range(0, numNodes)
				.boxed()
				.collect(Collectors.toMap(i -> this.nodes.get(i).getPublicKey(), i -> latencies[i]));
			this.latencyProvider.setBase(msg -> Math.max(nodeLatencies.get(msg.getSender()), nodeLatencies.get(msg.getReceiver())));
			return this;
		}

		public Builder setGetVerticesRPCEnabled(boolean getVerticesRPCEnabled) {
			this.getVerticesRPCEnabled = getVerticesRPCEnabled;
			return this;
		}

		public Builder randomLatency(int minLatency, int maxLatency) {
			this.latencyProvider.setBase(new RandomLatencyProvider(minLatency, maxLatency));
			return this;
		}

		public Builder checkLiveness(String checkName) {
			this.checksBuilder.put(checkName, new LivenessCheck(8 * TestEventCoordinatorNetwork.DEFAULT_LATENCY, TimeUnit.MILLISECONDS));
			return this;
		}

		public Builder checkLiveness(String checkName, long duration, TimeUnit timeUnit) {
			this.checksBuilder.put(checkName, new LivenessCheck(duration, timeUnit));
			return this;
		}

		public Builder checkSafety(String checkName) {
			this.checksBuilder.put(checkName, new SafetyCheck());
			return this;
		}

		public Builder checkNoTimeouts(String checkName) {
			this.checksBuilder.put(checkName, new NoTimeoutCheck());
			return this;
		}

		public Builder checkNoSyncExceptions(String checkName) {
			this.checksBuilder.put(checkName, new NoSyncExceptionCheck());
			return this;
		}

		public Builder checkAllProposalsHaveDirectParents(String checkName) {
			this.checksBuilder.put(checkName, new AllProposalsHaveDirectParentsCheck());
			return this;

		}

		public BFTSimulatedTest build() {
			return new BFTSimulatedTest(
				ImmutableList.copyOf(nodes),
				latencyProvider.copyOf(),
				pacemakerTimeout,
				getVerticesRPCEnabled,
				this.checksBuilder.build()
			);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Runs the test for a given time. Returns either once the duration has passed or if a check has failed.
	 * Returns a map from the check name to the result.
	 *
	 * @param duration duration to run test for
	 * @param timeUnit time unit of duration
	 * @return map of check results
	 */
	public Map<String, Boolean> run(long duration, TimeUnit timeUnit) {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder()
			.latencyProvider(this.latencyProvider)
			.build();
		SimulatedBFTNetwork bftNetwork =  new SimulatedBFTNetwork(nodes, network, pacemakerTimeout, getVerticesRPCEnabled);
		List<Pair<String, Observable<Pair<String, BFTCheckError>>>> assertions = this.checks.keySet().stream()
			.map(name -> {
				BFTCheck check = this.checks.get(name);
				return
					Pair.of(
						name,
						check.check(bftNetwork).map(e -> Pair.of(name, e)).publish().autoConnect(2)
					);
			})
			.collect(Collectors.toList());

		Single<String> firstErrorSignal = Observable.merge(assertions.stream().map(Pair::getSecond).collect(Collectors.toList()))
			.firstOrError()
			.map(Pair::getFirst);

		List<Single<Pair<String, Boolean>>> results = assertions.stream()
			.map(assertion -> assertion.getSecond()
				.takeUntil(firstErrorSignal.flatMapObservable(name ->
					!assertion.getFirst().equals(name) ? Observable.just(name) : Observable.never()))
				.takeUntil(Observable.timer(duration, timeUnit))
				.map(e -> false)
				.first(true)
				.map(result -> Pair.of(assertion.getFirst(), result))
			)
			.collect(Collectors.toList());

		return bftNetwork.start()
			.timeout(10, TimeUnit.SECONDS)
			.andThen(Single.merge(results))
			.doFinally(bftNetwork::stop)
			.blockingStream()
			.collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
	}
}
