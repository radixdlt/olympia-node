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
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.simulation.TestInvariant.TestInvariantError;
import com.radixdlt.consensus.simulation.invariants.epochs.EpochViewInvariant;
import com.radixdlt.consensus.simulation.configuration.ChangingEpochSyncedStateComputer;
import com.radixdlt.consensus.simulation.configuration.DroppingLatencyProvider;
import com.radixdlt.consensus.simulation.configuration.OneProposalPerViewDropper;
import com.radixdlt.consensus.simulation.configuration.RandomLatencyProvider;
import com.radixdlt.consensus.simulation.network.SimulationNodes;
import com.radixdlt.consensus.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.consensus.simulation.invariants.bft.AllProposalsHaveDirectParentsInvariant;
import com.radixdlt.consensus.simulation.invariants.bft.LivenessInvariant;
import com.radixdlt.consensus.simulation.invariants.bft.NoTimeoutsInvariant;
import com.radixdlt.consensus.simulation.invariants.bft.NoneCommittedInvariant;
import com.radixdlt.consensus.simulation.invariants.bft.SafetyInvariant;
import com.radixdlt.consensus.simulation.network.SimulationNodes.SimulatedStateComputer;
import com.radixdlt.consensus.simulation.configuration.SingleEpochAlwaysSyncedStateComputer;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.consensus.simulation.network.SimulationNetwork;
import com.radixdlt.consensus.simulation.network.SimulationNetwork.LatencyProvider;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * High level BFT Simulation Test Runner
 */
public class SimulationTest {
	private final ImmutableList<ECKeyPair> nodes;
	private final LatencyProvider latencyProvider;
	private final ImmutableMap<String, TestInvariant> checks;
	private final int pacemakerTimeout;
	private final Function<Long, ValidatorSet> validatorSetMapping;
	private final boolean getVerticesRPCEnabled;
	private final View epochHighView;

	private SimulationTest(
		ImmutableList<ECKeyPair> nodes,
		LatencyProvider latencyProvider,
		int pacemakerTimeout,
		View epochHighView,
		Function<Long, ValidatorSet> validatorSetMapping,
		boolean getVerticesRPCEnabled,
		ImmutableMap<String, TestInvariant> checks
	) {
		this.nodes = nodes;
		this.latencyProvider = latencyProvider;
		this.checks = checks;
		this.pacemakerTimeout = pacemakerTimeout;
		this.epochHighView = epochHighView;
		this.validatorSetMapping = validatorSetMapping;
		this.getVerticesRPCEnabled = getVerticesRPCEnabled;
	}

	public static class Builder {
		private final DroppingLatencyProvider latencyProvider = new DroppingLatencyProvider();
		private final ImmutableMap.Builder<String, TestInvariant> checksBuilder = ImmutableMap.builder();
		private List<ECKeyPair> nodes = Collections.singletonList(ECKeyPair.generateNew());
		private int pacemakerTimeout = 12 * SimulationNetwork.DEFAULT_LATENCY;
		private boolean getVerticesRPCEnabled = true;
		private View epochHighView = null;
		private Function<Long, Set<Integer>> epochToNodeIndexMapper;

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

		public Builder epochHighView(View epochHighView) {
			this.epochHighView = epochHighView;
			return this;
		}

		public Builder epochToNodesMapper(Function<Long, Set<Integer>> epochToNodeIndexMapper) {
			this.epochToNodeIndexMapper = epochToNodeIndexMapper;
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

		public Builder checkLiveness(String invariantName) {
			this.checksBuilder.put(invariantName, new LivenessInvariant(8 * SimulationNetwork.DEFAULT_LATENCY, TimeUnit.MILLISECONDS));
			return this;
		}

		public Builder checkLiveness(String invariantName, long duration, TimeUnit timeUnit) {
			this.checksBuilder.put(invariantName, new LivenessInvariant(duration, timeUnit));
			return this;
		}

		public Builder checkSafety(String invariantName) {
			this.checksBuilder.put(invariantName, new SafetyInvariant());
			return this;
		}

		public Builder checkNoTimeouts(String invariantName) {
			this.checksBuilder.put(invariantName, new NoTimeoutsInvariant());
			return this;
		}

		public Builder checkAllProposalsHaveDirectParents(String invariantName) {
			this.checksBuilder.put(invariantName, new AllProposalsHaveDirectParentsInvariant());
			return this;
		}

		public Builder checkNoneCommitted(String invariantName) {
			this.checksBuilder.put(invariantName, new NoneCommittedInvariant());
			return this;
		}

		public Builder checkEpochHighView(String invariantName, View epochHighView) {
			this.checksBuilder.put(invariantName, new EpochViewInvariant(epochHighView));
			return this;
		}

		public SimulationTest build() {
			final List<ECPublicKey> publicKeys = nodes.stream().map(ECKeyPair::getPublicKey).collect(Collectors.toList());
			Function<Long, ValidatorSet> epochToValidatorSetMapping =
				epochToNodeIndexMapper == null
					? epoch -> ValidatorSet.from(
						publicKeys.stream()
							.map(pk -> Validator.from(pk, UInt256.ONE))
							.collect(Collectors.toList()))
					: epochToNodeIndexMapper.andThen(indices -> ValidatorSet.from(
						indices.stream()
							.map(nodes::get)
							.map(kp -> Validator.from(kp.getPublicKey(), UInt256.ONE))
							.collect(Collectors.toList())));
			return new SimulationTest(
				ImmutableList.copyOf(nodes),
				latencyProvider.copyOf(),
				pacemakerTimeout,
				epochHighView,
				epochToValidatorSetMapping,
				getVerticesRPCEnabled,
				this.checksBuilder.build()
			);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	private Observable<Pair<String, Optional<TestInvariantError>>> runChecks(RunningNetwork runningNetwork, long duration, TimeUnit timeUnit) {
		List<Pair<String, Observable<Pair<String, TestInvariantError>>>> assertions = this.checks.keySet().stream()
			.map(name -> {
				TestInvariant check = this.checks.get(name);
				return
					Pair.of(
						name,
						check.check(runningNetwork).map(e -> Pair.of(name, e)).publish().autoConnect(2)
					);
			})
			.collect(Collectors.toList());

		Single<String> firstErrorSignal = Observable.merge(assertions.stream().map(Pair::getSecond).collect(Collectors.toList()))
			.firstOrError()
			.map(Pair::getFirst);

		List<Single<Pair<String, Optional<TestInvariantError>>>> results = assertions.stream()
			.map(assertion -> assertion.getSecond()
				.takeUntil(firstErrorSignal.flatMapObservable(name ->
					!assertion.getFirst().equals(name) ? Observable.just(name) : Observable.never()))
				.takeUntil(Observable.timer(duration, timeUnit))
				.map(e -> Optional.of(e.getSecond()))
				.first(Optional.empty())
				.map(result -> Pair.of(assertion.getFirst(), result))
			)
			.collect(Collectors.toList());

		return Single.merge(results).toObservable();
	}

	/**
	 * Runs the test for a given time. Returns either once the duration has passed or if a check has failed.
	 * Returns a map from the check name to the result.
	 *
	 * @param duration duration to run test for
	 * @param timeUnit time unit of duration
	 * @return map of check results
	 */
	public Map<String, Optional<TestInvariantError>> run(long duration, TimeUnit timeUnit) {
		SimulationNetwork network = SimulationNetwork.builder()
			.latencyProvider(this.latencyProvider)
			.build();

		final Supplier<SimulatedStateComputer> stateComputerSupplier;
		final List<ECPublicKey> publicKeys = nodes.stream().map(ECKeyPair::getPublicKey).collect(Collectors.toList());
		if (epochHighView == null) {
			stateComputerSupplier = () -> new SingleEpochAlwaysSyncedStateComputer(publicKeys);
		} else {
			stateComputerSupplier = () -> new ChangingEpochSyncedStateComputer(epochHighView, validatorSetMapping);
		}

		SimulationNodes bftNetwork =  new SimulationNodes(nodes, network, pacemakerTimeout, stateComputerSupplier, getVerticesRPCEnabled);

		return bftNetwork.start()
			.timeout(10, TimeUnit.SECONDS)
			.flatMapObservable(runningNetwork -> runChecks(runningNetwork, duration, timeUnit))
			.doFinally(bftNetwork::stop)
			.blockingStream()
			.collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
	}
}
