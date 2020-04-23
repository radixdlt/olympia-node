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
import com.radixdlt.consensus.simulation.checks.AllProposalsHaveDirectParentsCheck;
import com.radixdlt.consensus.simulation.checks.LivenessCheck;
import com.radixdlt.consensus.simulation.checks.NoSyncExceptionCheck;
import com.radixdlt.consensus.simulation.checks.NoTimeoutCheck;
import com.radixdlt.consensus.simulation.checks.SafetyCheck;
import com.radixdlt.consensus.simulation.checks.SyncOccurrenceCheck;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork;
import com.radixdlt.middleware2.network.TestEventCoordinatorNetwork.LatencyProvider;
import io.reactivex.rxjava3.core.Completable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * High level BFT Simulation Test Runner
 */
public class BFTTest {
	private final ImmutableList<ECKeyPair> nodes;
	private final LatencyProvider latencyProvider;
	private final ImmutableList<BFTCheck> checks;
	private final int pacemakerTimeout;

	private BFTTest(
		ImmutableList<ECKeyPair> nodes,
		LatencyProvider latencyProvider,
		int pacemakerTimeout,
		ImmutableList<BFTCheck> checks
	) {
		this.nodes = nodes;
		this.latencyProvider = latencyProvider;
		this.checks = checks;
		this.pacemakerTimeout = pacemakerTimeout;
	}

	public static class Builder {
		private final DroppingLatencyProvider latencyProvider = new DroppingLatencyProvider();
		private final List<BFTCheck> checks = new ArrayList<>();
		private List<ECKeyPair> nodes = Collections.singletonList(ECKeyPair.generateNew());
		private int pacemakerTimeout = 8 * TestEventCoordinatorNetwork.DEFAULT_LATENCY;

		private Builder() {
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

		public Builder disableSync(boolean disableSync) {
			this.latencyProvider.disableSync(disableSync);
			return this;
		}

		public Builder randomLatency(int minLatency, int maxLatency) {
			this.latencyProvider.setBase(new RandomLatencyProvider(minLatency, maxLatency));
			return this;
		}

		public Builder checkSyncsHaveOccurred(long time, TimeUnit timeUnit) {
			this.checks.add(new SyncOccurrenceCheck(c -> assertThat(c).isGreaterThan(0), time, timeUnit));
			return this;
		}

		public Builder checkSyncsHaveNotOccurred(long time, TimeUnit timeUnit) {
			this.checks.add(new SyncOccurrenceCheck(c -> assertThat(c).isEqualTo(0), time, timeUnit));
			return this;
		}

		public Builder checkLiveness() {
			this.checks.add(new LivenessCheck(6 * TestEventCoordinatorNetwork.DEFAULT_LATENCY, TimeUnit.MILLISECONDS));
			return this;
		}

		public Builder checkLiveness(long duration, TimeUnit timeUnit) {
			this.checks.add(new LivenessCheck(duration, timeUnit));
			return this;
		}

		public Builder checkSafety() {
			this.checks.add(new SafetyCheck());
			return this;
		}

		public Builder checkNoTimeouts() {
			this.checks.add(new NoTimeoutCheck());
			return this;
		}

		public Builder checkNoSyncExceptions() {
			this.checks.add(new NoSyncExceptionCheck());
			return this;
		}

		public Builder checkAllProposalsHaveDirectParents() {
			this.checks.add(new AllProposalsHaveDirectParentsCheck());
			return this;

		}

		public BFTTest build() {
			return new BFTTest(ImmutableList.copyOf(nodes), latencyProvider.copyOf(), pacemakerTimeout, ImmutableList.copyOf(checks));
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	// TODO: return list of results
	public void run(long duration, TimeUnit timeUnit) {
		TestEventCoordinatorNetwork network = TestEventCoordinatorNetwork.builder()
			.latencyProvider(this.latencyProvider)
			.build();
		BFTNetworkSimulation bftNetwork =  new BFTNetworkSimulation(nodes, network, pacemakerTimeout);
		List<Completable> assertions = this.checks.stream().map(c -> c.check(bftNetwork)).collect(Collectors.toList());
		Completable.mergeArray(
			bftNetwork.processBFT().flatMapCompletable(e -> Completable.complete()),
			Completable.merge(assertions)
		)
			.blockingAwait(duration, timeUnit);
	}
}
