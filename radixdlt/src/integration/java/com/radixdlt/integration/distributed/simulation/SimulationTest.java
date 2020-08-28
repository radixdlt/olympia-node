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

package com.radixdlt.integration.distributed.simulation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.radixdlt.LedgerEpochChangeModule;
import com.radixdlt.LedgerEpochChangeRxModule;
import com.radixdlt.LedgerModule;
import com.radixdlt.LedgerRxModule;
import com.radixdlt.LedgerLocalMempoolModule;
import com.radixdlt.RadixEngineModule;
import com.radixdlt.RadixEngineRxModule;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.TestInvariant.TestInvariantError;
import com.radixdlt.integration.distributed.simulation.application.IncrementalBytesSubmittor;
import com.radixdlt.integration.distributed.simulation.application.CommittedChecker;
import com.radixdlt.integration.distributed.simulation.application.RadixEngineValidatorRegistrator;
import com.radixdlt.integration.distributed.simulation.application.RadixEngineValidatorRegistratorAndUnregistrator;
import com.radixdlt.integration.distributed.simulation.application.RegisteredValidatorChecker;
import com.radixdlt.integration.distributed.simulation.invariants.epochs.EpochViewInvariant;
import com.radixdlt.integration.distributed.simulation.application.LocalMempoolPeriodicSubmittor;
import com.radixdlt.integration.distributed.simulation.network.DroppingLatencyProvider;
import com.radixdlt.integration.distributed.simulation.network.OneProposalPerViewDropper;
import com.radixdlt.integration.distributed.simulation.network.RandomLatencyProvider;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.mempool.LocalMempool;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.integration.distributed.simulation.invariants.bft.AllProposalsHaveDirectParentsInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.bft.LivenessInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.bft.NoTimeoutsInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.bft.NoneCommittedInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.bft.SafetyInvariant;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.LatencyProvider;
import com.radixdlt.ledger.CommittedCommand;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * High level BFT Simulation Test Runner
 */
public class SimulationTest {
	public interface SimulationNetworkActor {
		void run(RunningNetwork network);
	}

	private final ImmutableList<BFTNode> nodes;
	private final LatencyProvider latencyProvider;
	private final ImmutableSet<SimulationNetworkActor> runners;
	private final ImmutableMap<String, TestInvariant> checks;
	private final int pacemakerTimeout;
	private final boolean getVerticesRPCEnabled;
	private final ImmutableList<Module> modules;

	private SimulationTest(
		ImmutableList<BFTNode> nodes,
		LatencyProvider latencyProvider,
		int pacemakerTimeout,
		boolean getVerticesRPCEnabled,
		ImmutableList<Module> modules,
		ImmutableMap<String, TestInvariant> checks,
		ImmutableSet<SimulationNetworkActor> runners
	) {
		this.nodes = nodes;
		this.latencyProvider = latencyProvider;
		this.modules = modules;
		this.pacemakerTimeout = pacemakerTimeout;
		this.getVerticesRPCEnabled = getVerticesRPCEnabled;
		this.checks = checks;
		this.runners = runners;
	}

	public static class Builder {
		private enum LedgerType {
			MOCKED_LEDGER, LEDGER, LEDGER_AND_EPOCHS, LEDGER_AND_LOCALMEMPOOL, LEDGER_AND_RADIXENGINE
		}

		private final DroppingLatencyProvider latencyProvider = new DroppingLatencyProvider();
		private final ImmutableMap.Builder<String, Function<List<ECKeyPair>, TestInvariant>> checksBuilder = ImmutableMap.builder();
		private final ImmutableList.Builder<Function<List<ECKeyPair>, SimulationNetworkActor>> runnableBuilder = ImmutableList.builder();
		private ImmutableList<ECKeyPair> nodes = ImmutableList.of(ECKeyPair.generateNew());
		private int pacemakerTimeout = 12 * SimulationNetwork.DEFAULT_LATENCY;
		private boolean getVerticesRPCEnabled = true;
		private View epochHighView = null;
		private Function<Long, IntStream> epochToNodeIndexMapper;
		private LedgerType ledgerType = LedgerType.MOCKED_LEDGER;
		private int numInitialValidators = 0;

		private Builder() {
		}

		public Builder addProposalDropper() {
			ImmutableList<BFTNode> bftNodes = nodes.stream().map(kp -> BFTNode.create(kp.getPublicKey()))
				.collect(ImmutableList.toImmutableList());
			this.latencyProvider.addDropper(new OneProposalPerViewDropper(bftNodes, new Random()));
			return this;
		}

		public Builder pacemakerTimeout(int pacemakerTimeout) {
			this.pacemakerTimeout = pacemakerTimeout;
			return this;
		}

		public Builder numInitialValidators(int numInitialValidators) {
			this.numInitialValidators = numInitialValidators;
			return this;
		}

		public Builder numNodes(int numNodes) {
			this.nodes = Stream.generate(ECKeyPair::generateNew)
				.limit(numNodes)
				.collect(ImmutableList.toImmutableList());
			return this;
		}

		public Builder numNodesAndLatencies(int numNodes, int... latencies) {
			if (latencies.length != numNodes) {
				throw new IllegalArgumentException(String.format("Number of latencies (%d) not equal to numNodes (%d)", numNodes, latencies.length));
			}
			this.nodes = Stream.generate(ECKeyPair::generateNew)
				.limit(numNodes)
				.collect(ImmutableList.toImmutableList());
			Map<BFTNode, Integer> nodeLatencies = IntStream.range(0, numNodes)
				.boxed()
				.collect(Collectors.toMap(i -> BFTNode.create(this.nodes.get(i).getPublicKey()), i -> latencies[i]));
			this.latencyProvider.setBase(msg -> Math.max(nodeLatencies.get(msg.getSender()), nodeLatencies.get(msg.getReceiver())));
			return this;
		}

		public Builder ledgerAndEpochs(View epochHighView, Function<Long, IntStream> epochToNodeIndexMapper) {
			this.ledgerType = LedgerType.LEDGER_AND_EPOCHS;
			this.epochHighView = epochHighView;
			this.epochToNodeIndexMapper = epochToNodeIndexMapper;
			return this;
		}

		public Builder ledger() {
			this.ledgerType = LedgerType.LEDGER;
			return this;
		}

		public Builder ledgerAndMempool() {
			this.ledgerType = LedgerType.LEDGER_AND_LOCALMEMPOOL;
			return this;
		}

		public Builder ledgerAndRadixEngineWithEpochHighView(View epochHighView) {
			this.ledgerType = LedgerType.LEDGER_AND_RADIXENGINE;
			this.epochHighView = epochHighView;
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

		public Builder addMempoolSubmissionsSteadyState(String invariantName) {
			LocalMempoolPeriodicSubmittor mempoolSubmission = new IncrementalBytesSubmittor();
			CommittedChecker committedChecker
				= new CommittedChecker(mempoolSubmission.issuedCommands().map(Pair::getFirst));
			this.runnableBuilder.add(nodes -> mempoolSubmission::run);
			this.checksBuilder.put(invariantName, nodes -> committedChecker);

			return this;
		}

		public Builder addRadixEngineValidatorRegisterUnregisterMempoolSubmissions(String submittedInvariantName) {
			this.runnableBuilder.add(nodes -> {
				RadixEngineValidatorRegistratorAndUnregistrator randomValidatorSubmittor
					= new RadixEngineValidatorRegistratorAndUnregistrator(nodes);
				// TODO: Fix hack, hack required due to lack of Guice
				this.checksBuilder.put(
					submittedInvariantName,
					nodes2 -> new CommittedChecker(randomValidatorSubmittor.issuedCommands().map(Pair::getFirst))
				);
				return randomValidatorSubmittor::run;
			});
			return this;
		}

		public Builder addRadixEngineValidatorRegisterMempoolSubmissions(String submittedInvariantName, String registeredInvariantName) {
			this.runnableBuilder.add(nodes -> {
				RadixEngineValidatorRegistrator validatorRegistrator = new RadixEngineValidatorRegistrator(nodes);
				// TODO: Fix hack, hack required due to lack of Guice
				this.checksBuilder.put(
					submittedInvariantName,
					nodes2 -> new CommittedChecker(validatorRegistrator.issuedCommands().map(Pair::getFirst))
				);
				this.checksBuilder.put(
					registeredInvariantName,
					nodes2 -> new RegisteredValidatorChecker(validatorRegistrator.validatorRegistrationSubmissions())
				);
				return validatorRegistrator::run;
			});
			return this;
		}

		public Builder checkLiveness(String invariantName) {
			this.checksBuilder.put(invariantName, nodes -> new LivenessInvariant(8 * SimulationNetwork.DEFAULT_LATENCY, TimeUnit.MILLISECONDS));
			return this;
		}

		public Builder checkLiveness(String invariantName, long duration, TimeUnit timeUnit) {
			this.checksBuilder.put(invariantName, nodes -> new LivenessInvariant(duration, timeUnit));
			return this;
		}

		public Builder checkSafety(String invariantName) {
			this.checksBuilder.put(invariantName, nodes -> new SafetyInvariant());
			return this;
		}

		public Builder checkNoTimeouts(String invariantName) {
			this.checksBuilder.put(invariantName, nodes -> new NoTimeoutsInvariant());
			return this;
		}

		public Builder checkAllProposalsHaveDirectParents(String invariantName) {
			this.checksBuilder.put(invariantName, nodes -> new AllProposalsHaveDirectParentsInvariant());
			return this;
		}

		public Builder checkNoneCommitted(String invariantName) {
			this.checksBuilder.put(invariantName, nodes -> new NoneCommittedInvariant());
			return this;
		}

		public Builder checkEpochHighView(String invariantName, View epochHighView) {
			this.checksBuilder.put(invariantName, nodes -> new EpochViewInvariant(epochHighView));
			return this;
		}

		public SimulationTest build() {
			ImmutableList.Builder<Module> ledgerModules = ImmutableList.builder();
			if (ledgerType == LedgerType.MOCKED_LEDGER) {
				BFTValidatorSet validatorSet = BFTValidatorSet.from(
					nodes.stream()
						.map(node -> BFTNode.create(node.getPublicKey()))
						.map(node -> BFTValidator.from(node, UInt256.ONE))
						.collect(Collectors.toList())
				);
				ledgerModules.add(new MockedLedgerModule(validatorSet));
			} else {
				BFTValidatorSet validatorSet = BFTValidatorSet.from(
					nodes.stream()
						.map(node -> BFTNode.create(node.getPublicKey()))
						.map(node -> BFTValidator.from(node, UInt256.ONE))
						.limit(numInitialValidators == 0 ? Long.MAX_VALUE : numInitialValidators)
						.collect(Collectors.toList())
				);
				ConcurrentHashMap<Long, CommittedCommand> sharedCommittedCmds = new ConcurrentHashMap<>();
				ledgerModules.add(new LedgerModule());
				ledgerModules.add(new LedgerRxModule());
				ledgerModules.add(new LedgerEpochChangeRxModule());
				ledgerModules.add(new MockedSyncServiceModule(sharedCommittedCmds));

				if (ledgerType == LedgerType.LEDGER) {
					ledgerModules.add(new MockedMempoolModule());
					ledgerModules.add(new MockedStateComputerModule(validatorSet));
				} else if (ledgerType == LedgerType.LEDGER_AND_EPOCHS) {
					ledgerModules.add(new MockedMempoolModule());
					ledgerModules.add(new LedgerEpochChangeModule());
					Function<Long, BFTValidatorSet> epochToValidatorSetMapping =
						epochToNodeIndexMapper.andThen(indices -> BFTValidatorSet.from(
							indices.mapToObj(nodes::get)
								.map(node -> BFTNode.create(node.getPublicKey()))
								.map(node -> BFTValidator.from(node, UInt256.ONE))
								.collect(Collectors.toList())));
					ledgerModules.add(new MockedStateComputerWithEpochsModule(epochHighView, epochToValidatorSetMapping));
				} else if (ledgerType == LedgerType.LEDGER_AND_LOCALMEMPOOL) {
					ledgerModules.add(new LedgerLocalMempoolModule(10));
					ledgerModules.add(new AbstractModule() {
						@Override
						protected void configure() {
							bind(Mempool.class).to(LocalMempool.class);
						}
					});
					ledgerModules.add(new MockedStateComputerModule(validatorSet));
				} else if (ledgerType == LedgerType.LEDGER_AND_RADIXENGINE) {
					ledgerModules.add(new LedgerLocalMempoolModule(10));
					ledgerModules.add(new AbstractModule() {
						@Override
						protected void configure() {
							bind(Mempool.class).to(LocalMempool.class);
						}
					});
					ledgerModules.add(new LedgerEpochChangeModule());
					ledgerModules.add(new RadixEngineModule(epochHighView, true));
					ledgerModules.add(new RadixEngineRxModule());
					ledgerModules.add(new MockedRadixEngineStoreModule(validatorSet));
				}
			}

			ImmutableSet<SimulationNetworkActor> runners = this.runnableBuilder.build().stream()
				.map(f -> f.apply(nodes))
				.collect(ImmutableSet.toImmutableSet());

			ImmutableMap<String, TestInvariant> checks = this.checksBuilder.build().entrySet()
				.stream()
				.collect(
					ImmutableMap.toImmutableMap(
						Entry::getKey,
						e -> e.getValue().apply(nodes)
					)
				);


			return new SimulationTest(
				nodes.stream().map(node -> BFTNode.create(node.getPublicKey())).collect(ImmutableList.toImmutableList()),
				latencyProvider.copyOf(),
				pacemakerTimeout,
				getVerticesRPCEnabled,
				ledgerModules.build(),
				checks,
				runners
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

		return Single.merge(results).toObservable()
			.doOnSubscribe(d -> runners.forEach(c -> c.run(runningNetwork)));
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

		SimulationNodes bftNetwork = new SimulationNodes(
			nodes,
			network,
			pacemakerTimeout,
			modules,
			getVerticesRPCEnabled
		);
		RunningNetwork runningNetwork = bftNetwork.start();

		return runChecks(runningNetwork, duration, timeUnit)
			.doFinally(bftNetwork::stop)
			.blockingStream()
			.collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
	}
}
