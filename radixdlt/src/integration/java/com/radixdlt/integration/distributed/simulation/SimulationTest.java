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
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import com.radixdlt.ConsensusModule;
import com.radixdlt.ConsensusRunnerModule;
import com.radixdlt.ConsensusRxModule;
import com.radixdlt.EpochsConsensusModule;
import com.radixdlt.EpochsSyncModule;
import com.radixdlt.LedgerCommandGeneratorModule;
import com.radixdlt.EpochsLedgerUpdateModule;
import com.radixdlt.EpochsLedgerUpdateRxModule;
import com.radixdlt.LedgerModule;
import com.radixdlt.LedgerRxModule;
import com.radixdlt.NoFeeModule;
import com.radixdlt.LedgerLocalMempoolModule;
import com.radixdlt.RadixEngineModule;
import com.radixdlt.RadixEngineRxModule;
import com.radixdlt.SyncServiceModule;
import com.radixdlt.SyncRxModule;
import com.radixdlt.SyncRunnerModule;
import com.radixdlt.SystemInfoRxModule;
import com.radixdlt.consensus.sync.GetVerticesResponse;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.integration.distributed.MockedBFTConfigurationModule;
import com.radixdlt.integration.distributed.MockedCommandGeneratorModule;
import com.radixdlt.integration.distributed.MockedCryptoModule;
import com.radixdlt.integration.distributed.MockedLedgerModule;
import com.radixdlt.integration.distributed.MockedLedgerUpdateSender;
import com.radixdlt.integration.distributed.MockedMempoolModule;
import com.radixdlt.integration.distributed.MockedRadixEngineStoreModule;
import com.radixdlt.integration.distributed.MockedStateComputerModule;
import com.radixdlt.integration.distributed.MockedStateComputerWithEpochsModule;
import com.radixdlt.integration.distributed.MockedCommittedReaderModule;
import com.radixdlt.integration.distributed.MockedSyncServiceModule;
import com.radixdlt.integration.distributed.simulation.TestInvariant.TestInvariantError;
import com.radixdlt.integration.distributed.simulation.application.BFTValidatorSetNodeSelector;
import com.radixdlt.integration.distributed.simulation.application.EpochsNodeSelector;
import com.radixdlt.integration.distributed.simulation.application.IncrementalBytes;
import com.radixdlt.integration.distributed.simulation.application.CommittedChecker;
import com.radixdlt.integration.distributed.simulation.application.NodeSelector;
import com.radixdlt.integration.distributed.simulation.application.RadixEngineValidatorRegistrator;
import com.radixdlt.integration.distributed.simulation.application.RadixEngineValidatorRegistratorAndUnregistrator;
import com.radixdlt.integration.distributed.simulation.application.RegisteredValidatorChecker;
import com.radixdlt.integration.distributed.simulation.invariants.epochs.EpochViewInvariant;
import com.radixdlt.integration.distributed.simulation.application.LocalMempoolPeriodicSubmittor;
import com.radixdlt.integration.distributed.simulation.invariants.ledger.ConsensusToLedgerCommittedInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.ledger.LedgerInOrderInvariant;
import com.radixdlt.integration.distributed.simulation.network.DroppingLatencyProvider;
import com.radixdlt.integration.distributed.simulation.network.OneNodePerEpochResponseDropper;
import com.radixdlt.integration.distributed.simulation.network.OneProposalPerViewDropper;
import com.radixdlt.integration.distributed.simulation.network.RandomLatencyProvider;
import com.radixdlt.integration.distributed.simulation.network.RandomNewViewDropper;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.mempool.LocalMempool;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.AllProposalsHaveDirectParentsInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.LivenessInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.NoTimeoutsInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.NoneCommittedInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.SafetyInvariant;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.LatencyProvider;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.utils.DurationParser;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * High level BFT Simulation Test Runner
 */
public class SimulationTest {
	private static final String ENVIRONMENT_VAR_NAME = "TEST_DURATION"; // Same as used by regression test suite
	private static final Duration DEFAULT_TEST_DURATION = Duration.ofSeconds(30);

	public interface SimulationNetworkActor {
		void run(RunningNetwork network);
	}

	private final ImmutableList<ECKeyPair> nodes;
	private final LatencyProvider latencyProvider;
	private final ImmutableSet<SimulationNetworkActor> runners;
	private final ImmutableMap<String, TestInvariant> checks;
	private final int pacemakerTimeout;
	private final Module baseNodeModule;
	private final Module overrideModule;
	private final Map<ECKeyPair, Module> byzantineNodeModules;

	private SimulationTest(
		ImmutableList<ECKeyPair> nodes,
		LatencyProvider latencyProvider,
		int pacemakerTimeout,
		Module baseNodeModule,
		Module overrideModule,
		Map<ECKeyPair, Module> byzantineNodeModules,
		ImmutableMap<String, TestInvariant> checks,
		ImmutableSet<SimulationNetworkActor> runners
	) {
		this.nodes = nodes;
		this.latencyProvider = latencyProvider;
		this.baseNodeModule = baseNodeModule;
		this.overrideModule = overrideModule;
		this.byzantineNodeModules = byzantineNodeModules;
		this.pacemakerTimeout = pacemakerTimeout;
		this.checks = checks;
		this.runners = runners;
	}

	public static class Builder {
		private enum LedgerType {
			MOCKED_LEDGER(false),
			LEDGER(false),
			LEDGER_AND_SYNC(false),
			LEDGER_AND_LOCALMEMPOOL(false),
			LEDGER_AND_EPOCHS(true),
			LEDGER_AND_EPOCHS_AND_SYNC(true),
			LEDGER_AND_LOCALMEMPOOL_AND_EPOCHS_AND_RADIXENGINE(true);

			private final boolean hasEpochs;
			LedgerType(boolean hasEpochs) {
				this.hasEpochs = hasEpochs;
			}
		}

		private final DroppingLatencyProvider latencyProvider = new DroppingLatencyProvider();
		private final ImmutableMap.Builder<String, Function<List<ECKeyPair>, TestInvariant>> checksBuilder = ImmutableMap.builder();
		private final ImmutableList.Builder<Function<List<ECKeyPair>, SimulationNetworkActor>> runnableBuilder = ImmutableList.builder();
		private ImmutableList<ECKeyPair> nodes = ImmutableList.of(ECKeyPair.generateNew());
		private int pacemakerTimeout = 12 * SimulationNetwork.DEFAULT_LATENCY;
		private View epochHighView = null;
		private Function<Long, IntStream> epochToNodeIndexMapper;
		private LedgerType ledgerType = LedgerType.MOCKED_LEDGER;
		private int numInitialValidators = 0;
		private Module overrideModule = null;
		private final ImmutableMap.Builder<ECKeyPair, Module> byzantineModules = ImmutableMap.builder();

		private Builder() {
		}

		public Builder addSingleByzantineModule(Module byzantineModule) {
			byzantineModules.put(nodes.get(0), byzantineModule);
			return this;
		}

		public Builder addByzantineModuleToAll(Module byzantineModule) {
			nodes.forEach(k -> byzantineModules.put(k, byzantineModule));
			return this;
		}

		public Builder overrideWithIncorrectModule(Module module) {
			this.overrideModule = module;
			return this;
		}

		public Builder addRandomNewViewDropper(double drops) {
			this.latencyProvider.addDropper(new RandomNewViewDropper(new Random(), drops));
			return this;
		}

		public Builder addVerticesSyncDropper() {
			this.latencyProvider.addDropper(msg -> {
				if (msg.getContent() instanceof GetVerticesRequest) {
					return true;
				}

				if (msg.getContent() instanceof GetVerticesResponse) {
					return true;
				}

				return false;
			});
			return this;
		}


		public Builder addOneNodeNeverReceiveProposalDropper() {
			this.latencyProvider.addDropper(new OneProposalPerViewDropper(
				ImmutableList.of(BFTNode.create(nodes.get(0).getPublicKey())), new Random())
			);
			return this;
		}

		public Builder addOneNodePerEpochResponseDropper() {
			this.latencyProvider.addDropper(new OneNodePerEpochResponseDropper());
			return this;
		}

		public Builder addOneProposalPerViewDropper() {
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
			this.numInitialValidators = 2;
			this.ledgerType = LedgerType.LEDGER_AND_EPOCHS;
			this.epochHighView = epochHighView;
			this.epochToNodeIndexMapper = epochToNodeIndexMapper;
			return this;
		}

		public Builder ledger() {
			this.ledgerType = LedgerType.LEDGER;
			return this;
		}

		public Builder ledgerAndSync() {
			this.ledgerType = LedgerType.LEDGER_AND_SYNC;
			return this;
		}

		public Builder ledgerAndEpochsAndSync(View epochHighView, Function<Long, IntStream> epochToNodeIndexMapper) {
			this.numInitialValidators = 2;
			this.ledgerType = LedgerType.LEDGER_AND_EPOCHS_AND_SYNC;
			this.epochHighView = epochHighView;
			this.epochToNodeIndexMapper = epochToNodeIndexMapper;
			return this;
		}

		public Builder ledgerAndMempool() {
			this.ledgerType = LedgerType.LEDGER_AND_LOCALMEMPOOL;
			return this;
		}

		public Builder ledgerAndRadixEngineWithEpochHighView(View epochHighView) {
			this.ledgerType = LedgerType.LEDGER_AND_LOCALMEMPOOL_AND_EPOCHS_AND_RADIXENGINE;
			this.epochHighView = epochHighView;
			return this;
		}


		public Builder randomLatency(int minLatency, int maxLatency) {
			this.latencyProvider.setBase(new RandomLatencyProvider(minLatency, maxLatency));
			return this;
		}

		public Builder addMempoolSubmissionsSteadyState(String invariantName) {
			IncrementalBytes incrementalBytes = new IncrementalBytes();
			NodeSelector nodeSelector = this.ledgerType.hasEpochs ? new EpochsNodeSelector() : new BFTValidatorSetNodeSelector();
			LocalMempoolPeriodicSubmittor mempoolSubmission = new LocalMempoolPeriodicSubmittor(
				incrementalBytes,
				nodeSelector
			);
			CommittedChecker committedChecker = new CommittedChecker(mempoolSubmission.issuedCommands().map(Pair::getFirst));
			this.runnableBuilder.add(nodes -> mempoolSubmission::run);
			this.checksBuilder.put(invariantName, nodes -> committedChecker);

			return this;
		}

		public Builder addRadixEngineValidatorRegisterUnregisterMempoolSubmissions() {
			this.runnableBuilder.add(nodes -> {
				RadixEngineValidatorRegistratorAndUnregistrator randomValidatorSubmittor
					= new RadixEngineValidatorRegistratorAndUnregistrator(nodes);
				NodeSelector nodeSelector = this.ledgerType.hasEpochs ? new EpochsNodeSelector() : new BFTValidatorSetNodeSelector();
				LocalMempoolPeriodicSubmittor submittor = new LocalMempoolPeriodicSubmittor(randomValidatorSubmittor, nodeSelector);
				return submittor::run;
			});
			return this;
		}

		public Builder addRadixEngineValidatorRegisterUnregisterMempoolSubmissions(String submittedInvariantName) {
			this.runnableBuilder.add(nodes -> {
				RadixEngineValidatorRegistratorAndUnregistrator randomValidatorSubmittor
					= new RadixEngineValidatorRegistratorAndUnregistrator(nodes);
				NodeSelector nodeSelector = this.ledgerType.hasEpochs ? new EpochsNodeSelector() : new BFTValidatorSetNodeSelector();
				LocalMempoolPeriodicSubmittor submittor = new LocalMempoolPeriodicSubmittor(randomValidatorSubmittor, nodeSelector);
				// TODO: Fix hack, hack required due to lack of Guice
				this.checksBuilder.put(
					submittedInvariantName,
					nodes2 -> new CommittedChecker(submittor.issuedCommands().map(Pair::getFirst))
				);
				return submittor::run;
			});
			return this;
		}

		public Builder addRadixEngineValidatorRegisterMempoolSubmissions(String submittedInvariantName, String registeredInvariantName) {
			this.runnableBuilder.add(nodes -> {
				RadixEngineValidatorRegistrator validatorRegistrator = new RadixEngineValidatorRegistrator(nodes);
				NodeSelector nodeSelector = this.ledgerType.hasEpochs ? new EpochsNodeSelector() : new BFTValidatorSetNodeSelector();
				LocalMempoolPeriodicSubmittor submittor = new LocalMempoolPeriodicSubmittor(validatorRegistrator, nodeSelector);
				// TODO: Fix hack, hack required due to lack of Guice
				this.checksBuilder.put(
					submittedInvariantName,
					nodes2 -> new CommittedChecker(submittor.issuedCommands().map(Pair::getFirst))
				);
				this.checksBuilder.put(
					registeredInvariantName,
					nodes2 -> new RegisteredValidatorChecker(validatorRegistrator.validatorRegistrationSubmissions())
				);
				return submittor::run;
			});
			return this;
		}

		public Builder checkConsensusLiveness(String invariantName) {
			this.checksBuilder.put(invariantName, nodes -> new LivenessInvariant(8 * SimulationNetwork.DEFAULT_LATENCY, TimeUnit.MILLISECONDS));
			return this;
		}

		public Builder checkConsensusLiveness(String invariantName, long duration, TimeUnit timeUnit) {
			this.checksBuilder.put(invariantName, nodes -> new LivenessInvariant(duration, timeUnit));
			return this;
		}

		public Builder checkConsensusSafety(String invariantName) {
			this.checksBuilder.put(invariantName, nodes -> new SafetyInvariant());
			return this;
		}

		public Builder checkConsensusNoTimeouts(String invariantName) {
			this.checksBuilder.put(invariantName, nodes -> new NoTimeoutsInvariant());
			return this;
		}

		public Builder checkConsensusAllProposalsHaveDirectParents(String invariantName) {
			this.checksBuilder.put(invariantName, nodes -> new AllProposalsHaveDirectParentsInvariant());
			return this;
		}

		public Builder checkConsensusNoneCommitted(String invariantName) {
			this.checksBuilder.put(invariantName, nodes -> new NoneCommittedInvariant());
			return this;
		}

		public Builder checkLedgerProcessesConsensusCommitted(String invariantName) {
			this.checksBuilder.put(invariantName, nodes -> new ConsensusToLedgerCommittedInvariant());
			return this;
		}

		public Builder checkLedgerInOrder(String invariantName) {
			this.checksBuilder.put(invariantName, nodes -> new LedgerInOrderInvariant());
			return this;
		}

		public Builder checkEpochsHighViewCorrect(String invariantName, View epochHighView) {
			this.checksBuilder.put(invariantName, nodes -> new EpochViewInvariant(epochHighView));
			return this;
		}

		public SimulationTest build() {
			ImmutableList.Builder<Module> modules = ImmutableList.builder();
			final Random sharedRandom = new Random();

			final long limit = numInitialValidators == 0 ? Long.MAX_VALUE : numInitialValidators;
			modules.add(new AbstractModule() {
				@Override
				public void configure() {
					bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
					bind(TimeSupplier.class).toInstance(System::currentTimeMillis);
					bind(Random.class).toInstance(sharedRandom);
				}

				@Provides
				ImmutableList<BFTNode> nodes() {
					return nodes.stream().map(node -> BFTNode.create(node.getPublicKey())).collect(ImmutableList.toImmutableList());
				}

				@Provides
				BFTValidatorSet initialValidatorSet(ImmutableList<BFTNode> nodes) {
					return BFTValidatorSet.from(nodes.stream().limit(limit).map(node -> BFTValidator.from(node, UInt256.ONE)));
				}
			});
			modules.add(new NoFeeModule());
			modules.add(new MockedCryptoModule());
			modules.add(new ConsensusModule(pacemakerTimeout, 2.0, 0)); // Use constant timeout for now
			modules.add(new ConsensusRxModule());
			modules.add(new SystemInfoRxModule());

			if (ledgerType == LedgerType.MOCKED_LEDGER) {
				modules.add(new MockedBFTConfigurationModule());
				modules.add(new MockedLedgerModule());
				modules.add(new LedgerRxModule());
				modules.add(new MockedConsensusRunnerModule());
				modules.add(new MockedLedgerUpdateRxModule());
			} else {
				modules.add(new LedgerModule());
				modules.add(new LedgerRxModule());

				if (ledgerType == LedgerType.LEDGER) {
					modules.add(new MockedLedgerUpdateRxModule());
					modules.add(new MockedLedgerUpdateSender());
					modules.add(new MockedConsensusRunnerModule());
					modules.add(new MockedCommandGeneratorModule());
					modules.add(new MockedMempoolModule());
					modules.add(new MockedStateComputerModule());
					modules.add(new MockedSyncServiceModule());
				} else if (ledgerType == LedgerType.LEDGER_AND_SYNC) {
					modules.add(new SyncServiceModule());
					modules.add(new SyncRxModule());
					modules.add(new MockedLedgerUpdateRxModule());
					modules.add(new MockedLedgerUpdateSender());
					modules.add(new MockedConsensusRunnerModule());
					modules.add(new MockedCommandGeneratorModule());
					modules.add(new MockedMempoolModule());
					modules.add(new MockedStateComputerModule());
					modules.add(new MockedCommittedReaderModule());
					modules.add(new MockedSyncRunnerModule());
				} else if (ledgerType == LedgerType.LEDGER_AND_LOCALMEMPOOL) {
					modules.add(new MockedLedgerUpdateRxModule());
					modules.add(new MockedLedgerUpdateSender());
					modules.add(new MockedConsensusRunnerModule());
					modules.add(new LedgerCommandGeneratorModule());
					modules.add(new LedgerLocalMempoolModule(10));
					modules.add(new AbstractModule() {
						@Override
						protected void configure() {
							bind(Mempool.class).to(LocalMempool.class);
						}
					});
					modules.add(new MockedSyncServiceModule());
					modules.add(new MockedStateComputerModule());
				} else if (ledgerType == LedgerType.LEDGER_AND_EPOCHS) {
					modules.add(new EpochsLedgerUpdateModule());
					modules.add(new EpochsLedgerUpdateRxModule());
					modules.add(new EpochsConsensusModule(pacemakerTimeout, 2.0, 0)); // constant for now
					modules.add(new ConsensusRunnerModule());
					modules.add(new LedgerCommandGeneratorModule());
					modules.add(new MockedMempoolModule());
					Function<Long, BFTValidatorSet> epochToValidatorSetMapping =
						epochToNodeIndexMapper.andThen(indices -> BFTValidatorSet.from(
							indices.mapToObj(nodes::get)
								.map(node -> BFTNode.create(node.getPublicKey()))
								.map(node -> BFTValidator.from(node, UInt256.ONE))
								.collect(Collectors.toList())));
					modules.add(new MockedStateComputerWithEpochsModule(epochHighView, epochToValidatorSetMapping));
					modules.add(new MockedSyncServiceModule());
				} else if (ledgerType == LedgerType.LEDGER_AND_EPOCHS_AND_SYNC) {
					modules.add(new EpochsLedgerUpdateModule());
					modules.add(new EpochsLedgerUpdateRxModule());
					modules.add(new EpochsConsensusModule(pacemakerTimeout, 2.0, 0)); // constant for now
					modules.add(new ConsensusRunnerModule());
					modules.add(new MockedCommandGeneratorModule());
					modules.add(new MockedMempoolModule());
					Function<Long, BFTValidatorSet> epochToValidatorSetMapping =
						epochToNodeIndexMapper.andThen(indices -> BFTValidatorSet.from(
							indices.mapToObj(nodes::get)
								.map(node -> BFTNode.create(node.getPublicKey()))
								.map(node -> BFTValidator.from(node, UInt256.ONE))
								.collect(Collectors.toList())));
					modules.add(new MockedStateComputerWithEpochsModule(epochHighView, epochToValidatorSetMapping));
					modules.add(new EpochsSyncModule());
					modules.add(new SyncServiceModule());
					modules.add(new SyncRunnerModule());
					modules.add(new SyncRxModule());
					modules.add(new MockedCommittedReaderModule());
				} else if (ledgerType == LedgerType.LEDGER_AND_LOCALMEMPOOL_AND_EPOCHS_AND_RADIXENGINE) {
					modules.add(new EpochsLedgerUpdateModule());
					modules.add(new EpochsLedgerUpdateRxModule());
					modules.add(new EpochsConsensusModule(pacemakerTimeout, 2.0, 0)); // constant for now
					modules.add(new ConsensusRunnerModule());
					modules.add(new LedgerCommandGeneratorModule());
					modules.add(new LedgerLocalMempoolModule(10));
					modules.add(new AbstractModule() {
						@Override
						protected void configure() {
							bind(Mempool.class).to(LocalMempool.class);
						}
					});
					modules.add(new RadixEngineModule(epochHighView));
					modules.add(new RadixEngineRxModule());
					modules.add(new MockedSyncServiceModule());
					modules.add(new MockedRadixEngineStoreModule());
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
				nodes,
				latencyProvider.copyOf(),
				pacemakerTimeout,
				Modules.combine(modules.build()), overrideModule,
				byzantineModules.build(),
				checks,
				runners
			);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	private Observable<Pair<String, Optional<TestInvariantError>>> runChecks(RunningNetwork runningNetwork, java.time.Duration duration) {
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
				.takeUntil(Observable.timer(duration.get(ChronoUnit.SECONDS), TimeUnit.SECONDS))
				.map(e -> Optional.of(e.getSecond()))
				.first(Optional.empty())
				.map(result -> Pair.of(assertion.getFirst(), result))
			)
			.collect(Collectors.toList());

		return Single.merge(results).toObservable()
			.doOnSubscribe(d -> runners.forEach(c -> c.run(runningNetwork)));
	}

	public static class TestResults {
		private final Map<String, Optional<TestInvariantError>> checkResults;
		private final RunningNetwork network;

		private TestResults(
			Map<String, Optional<TestInvariantError>> checkResults,
			RunningNetwork network
		) {
			this.checkResults = checkResults;
			this.network = network;
		}

		public Map<String, Optional<TestInvariantError>> getCheckResults() {
			return checkResults;
		}

		public RunningNetwork getNetwork() {
			return network;
		}
	}

	/**
	 * Runs the test for time configured via environment variable. If environment variable is missing then
	 * default duration is used. Returns either once the duration has passed or if a check has failed.
	 * Returns a map from the check name to the result.
	 *
	 * @return map of check results
	 */
	public TestResults run() {
		return run(getConfiguredDuration());
	}

	/**
	 * Get test duration.
	 *
	 * @return configured test duration.
	 */
	public static Duration getConfiguredDuration() {
		return Optional.ofNullable(System.getenv(ENVIRONMENT_VAR_NAME))
				.flatMap(DurationParser::parse)
				.orElse(DEFAULT_TEST_DURATION);
	}

	/**
	 * Runs the test for a given time. Returns either once the duration has passed or if a check has failed.
	 * Returns a map from the check name to the result.
	 *
	 * @param duration duration to run test for
	 * @return test results
	 */
	public TestResults run(Duration duration) {
		SimulationNetwork network = SimulationNetwork.builder()
			.latencyProvider(this.latencyProvider)
			.build();

		SimulationNodes bftNetwork = new SimulationNodes(
			nodes,
			network,
			pacemakerTimeout,
			baseNodeModule, overrideModule,
			byzantineNodeModules
		);
		RunningNetwork runningNetwork = bftNetwork.start();

		Map<String, Optional<TestInvariantError>> checkResults = runChecks(runningNetwork, duration)
			.doFinally(bftNetwork::stop)
			.blockingStream()
			.collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
		return new TestResults(checkResults, runningNetwork);
	}
}
