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
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.util.Modules;
import com.radixdlt.ConsensusModule;
import com.radixdlt.ConsensusRunnerModule;
import com.radixdlt.ConsensusRxModule;
import com.radixdlt.DispatcherModule;
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
import com.radixdlt.RxEnvironmentModule;
import com.radixdlt.SyncServiceModule;
import com.radixdlt.SyncRxModule;
import com.radixdlt.SyncRunnerModule;
import com.radixdlt.SystemInfoRxModule;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
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
import com.radixdlt.integration.distributed.simulation.application.TimestampChecker;
import com.radixdlt.integration.distributed.simulation.invariants.consensus.NodeEvents;
import com.radixdlt.integration.distributed.simulation.invariants.epochs.EpochViewInvariant;
import com.radixdlt.integration.distributed.simulation.application.LocalMempoolPeriodicSubmittor;
import com.radixdlt.integration.distributed.simulation.invariants.ledger.ConsensusToLedgerCommittedInvariant;
import com.radixdlt.integration.distributed.simulation.invariants.ledger.LedgerInOrderInvariant;
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
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.statecomputer.ValidatorSetBuilder;
import com.radixdlt.sync.SyncPatienceMillis;
import com.radixdlt.utils.DurationParser;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * High level BFT Simulation Test Runner
 */
public class SimulationTest {
	private static final ECKeyPair UNIVERSE_KEY = ECKeyPair.generateNew();
	private static final RadixAddress UNIVERSE_ADDRESS = new RadixAddress((byte) 0, UNIVERSE_KEY.getPublicKey());
	private static final RRI NATIVE_TOKEN = RRI.of(UNIVERSE_ADDRESS, TokenDefinitionUtils.getNativeTokenShortCode());
	private static final String ENVIRONMENT_VAR_NAME = "TEST_DURATION"; // Same as used by regression test suite
	private static final Duration DEFAULT_TEST_DURATION = Duration.ofSeconds(30);

	private static final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	public interface SimulationNetworkActor {
		void run(RunningNetwork network);
	}

	private final ImmutableList<ECKeyPair> nodes;
	private final SimulationNetwork simulationNetwork;
	private final ImmutableSet<SimulationNetworkActor> runners;
	private final ImmutableMap<String, TestInvariant> checks;
	private final Module baseNodeModule;
	private final Module overrideModule;
	private final Map<ECKeyPair, Module> byzantineNodeModules;

	private SimulationTest(
		ImmutableList<ECKeyPair> nodes,
		SimulationNetwork simulationNetwork,
		Module baseNodeModule,
		Module overrideModule,
		Map<ECKeyPair, Module> byzantineNodeModules,
		ImmutableMap<String, TestInvariant> checks,
		ImmutableSet<SimulationNetworkActor> runners
	) {
		this.nodes = nodes;
		this.simulationNetwork = simulationNetwork;
		this.baseNodeModule = baseNodeModule;
		this.overrideModule = overrideModule;
		this.byzantineNodeModules = byzantineNodeModules;
		this.checks = checks;
		this.runners = runners;
	}

	public static class Builder {
		private enum LedgerType {
			MOCKED_LEDGER(false, false),
			LEDGER(false, false),
			LEDGER_AND_SYNC(false, true),
			LEDGER_AND_LOCALMEMPOOL(false, false),
			LEDGER_AND_EPOCHS(true, false),
			LEDGER_AND_EPOCHS_AND_SYNC(true, true),
			LEDGER_AND_LOCALMEMPOOL_AND_EPOCHS_AND_RADIXENGINE(true, false);

			private final boolean hasEpochs;
			private final boolean hasSync;

			LedgerType(boolean hasEpochs, boolean hasSync) {
				this.hasEpochs = hasEpochs;
				this.hasSync = hasSync;
			}
		}

		private final ImmutableMap.Builder<String, Function<List<ECKeyPair>, TestInvariant>> checksBuilder = ImmutableMap.builder();
		private final ImmutableList.Builder<Function<List<ECKeyPair>, SimulationNetworkActor>> runnableBuilder = ImmutableList.builder();
		private ImmutableList<ECKeyPair> nodes = ImmutableList.of(ECKeyPair.generateNew());
		private long pacemakerTimeout = 12 * SimulationNetwork.DEFAULT_LATENCY;
		private View epochHighView = null;
		private Function<Long, IntStream> epochToNodeIndexMapper;
		private LedgerType ledgerType = LedgerType.MOCKED_LEDGER;


		private NodeEvents nodeEvents = new NodeEvents();
		private Module initialNodesModule;
		private ImmutableList.Builder<Module> modules = ImmutableList.builder();
		private Module networkModule;
		private Module overrideModule = null;
		private Function<ImmutableList<ECKeyPair>, ImmutableMap<ECKeyPair, Module>> byzantineModuleCreator = i -> ImmutableMap.of();
		// TODO: Fix pacemaker so can Default 1 so can debug in IDE, possibly from properties at some point
		// TODO: Specifically, simulation test with engine, epochs and mempool gets stuck on a single validator
		private final int minValidators = 2;
		private int maxValidators = Integer.MAX_VALUE;

		private Builder() {
		}

		public Builder addSingleByzantineModule(Module byzantineModule) {
			this.byzantineModuleCreator = nodes -> ImmutableMap.of(nodes.get(0), byzantineModule);
			return this;
		}

		public Builder addByzantineModuleToAll(Module byzantineModule) {
			this.byzantineModuleCreator = nodes -> nodes.stream()
				.collect(ImmutableMap.<ECKeyPair, ECKeyPair, Module>toImmutableMap(n -> n, n -> byzantineModule));
			return this;
		}

		public Builder overrideWithIncorrectModule(Module module) {
			this.overrideModule = module;
			return this;
		}

		public Builder networkModules(Module... networkModules) {
			this.networkModule = Modules.combine(networkModules);
			return this;
		}

		public Builder addNetworkModule(Module networkModule) {
			this.networkModule = Modules.combine(this.networkModule, networkModule);
			return this;
		}

		public Builder pacemakerTimeout(long pacemakerTimeout) {
			this.pacemakerTimeout = pacemakerTimeout;
			return this;
		}

		public Builder numNodes(int numNodes, int numInitialValidators, int maxValidators, Iterable<UInt256> initialStakes) {
			this.maxValidators = maxValidators;
			this.nodes = Stream.generate(ECKeyPair::generateNew)
				.limit(numNodes)
				.collect(ImmutableList.toImmutableList());

			final var stakesIterator = repeatLast(initialStakes);
			final var initialStakesMap = nodes.stream()
				.collect(ImmutableMap.toImmutableMap(ECKeyPair::getPublicKey, k -> stakesIterator.next()));

			final var vsetBuilder = ValidatorSetBuilder.create(this.minValidators, numInitialValidators);
			final var initialVset = vsetBuilder.buildValidatorSet(initialStakesMap);
			if (initialVset == null) {
				throw new IllegalStateException(
					String.format(
						"Can't build a validator set between %s and %s validators from %s",
						this.minValidators, numInitialValidators, initialStakesMap
					)
				);
			}

			final var bftNodes = initialStakesMap.keySet().stream()
					.map(BFTNode::create)
					.collect(ImmutableList.toImmutableList());
			final var validators = initialStakesMap.entrySet().stream()
					.map(e -> BFTValidator.from(BFTNode.create(e.getKey()), e.getValue()))
					.collect(ImmutableList.toImmutableList());

			this.initialNodesModule = new AbstractModule() {
				@Override
				protected void configure() {
					bind(new TypeLiteral<ImmutableList<BFTNode>>() { }).toInstance(bftNodes);
					bind(new TypeLiteral<ImmutableList<BFTValidator>>() { }).toInstance(validators);
				}
			};

			modules.add(new AbstractModule() {
				@Override
				protected void configure() {
					bind(new TypeLiteral<ImmutableList<BFTNode>>() { }).toInstance(bftNodes);
					bind(new TypeLiteral<ImmutableList<BFTValidator>>() { }).toInstance(validators);
					bind(BFTValidatorSet.class).toInstance(initialVset);
				}
			});

			return this;
		}

		public Builder numNodes(int numNodes, int numInitialValidators, Iterable<UInt256> initialStakes) {
			return numNodes(numNodes, numInitialValidators, numInitialValidators, initialStakes);
		}

		public Builder numNodes(int numNodes, int numInitialValidators, int maxValidators) {
			return numNodes(numNodes, numInitialValidators, maxValidators, ImmutableList.of(UInt256.ONE));
		}

		public Builder numNodes(int numNodes, int numInitialValidators) {
			return numNodes(numNodes, numInitialValidators, ImmutableList.of(UInt256.ONE));
		}

		public Builder numNodes(int numNodes) {
			return numNodes(numNodes, numNodes);
		}

		public Builder maxValidators(int maxValidators) {
			this.maxValidators = maxValidators;
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

		public Builder ledgerAndSync() {
			this.ledgerType = LedgerType.LEDGER_AND_SYNC;
			return this;
		}

		public Builder ledgerAndEpochsAndSync(View epochHighView, Function<Long, IntStream> epochToNodeIndexMapper) {
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

		public Builder addTimestampChecker(String invariantName) {
			TimestampChecker timestampChecker = new TimestampChecker();
			this.checksBuilder.put(invariantName, nodes -> timestampChecker);
			return this;
		}

		public Builder addMempoolSubmissionsSteadyState(String invariantName) {
			this.modules.add(new AbstractModule() {
				@ProvidesIntoSet
				@ProcessOnDispatch
				private EventProcessor<BFTCommittedUpdate> committedProcessor(@Self BFTNode node) {
					return nodeEvents.processor(node, BFTCommittedUpdate.class);
				}
			});

			IncrementalBytes incrementalBytes = new IncrementalBytes();
			NodeSelector nodeSelector = this.ledgerType.hasEpochs ? new EpochsNodeSelector() : new BFTValidatorSetNodeSelector();
			LocalMempoolPeriodicSubmittor mempoolSubmission = new LocalMempoolPeriodicSubmittor(
				incrementalBytes,
				nodeSelector
			);
			CommittedChecker committedChecker = new CommittedChecker(mempoolSubmission.issuedCommands().map(Pair::getFirst), nodeEvents);
			this.runnableBuilder.add(nodes -> mempoolSubmission::run);
			this.checksBuilder.put(invariantName, nodes -> committedChecker);

			return this;
		}

		public Builder addRadixEngineValidatorRegisterUnregisterMempoolSubmissions() {
			this.runnableBuilder.add(nodes -> {
				RadixEngineValidatorRegistratorAndUnregistrator randomValidatorSubmittor
					= new RadixEngineValidatorRegistratorAndUnregistrator(nodes, hasher);
				NodeSelector nodeSelector = this.ledgerType.hasEpochs ? new EpochsNodeSelector() : new BFTValidatorSetNodeSelector();
				LocalMempoolPeriodicSubmittor submittor = new LocalMempoolPeriodicSubmittor(randomValidatorSubmittor, nodeSelector);
				return submittor::run;
			});
			return this;
		}

		public Builder addRadixEngineValidatorRegisterUnregisterMempoolSubmissions(String submittedInvariantName) {
			this.modules.add(new AbstractModule() {
				@ProvidesIntoSet
				@ProcessOnDispatch
				private EventProcessor<BFTCommittedUpdate> committedProcessor(@Self BFTNode node) {
					return nodeEvents.processor(node, BFTCommittedUpdate.class);
				}
			});

			this.runnableBuilder.add(nodes -> {
				RadixEngineValidatorRegistratorAndUnregistrator randomValidatorSubmittor
					= new RadixEngineValidatorRegistratorAndUnregistrator(nodes, hasher);
				NodeSelector nodeSelector = this.ledgerType.hasEpochs ? new EpochsNodeSelector() : new BFTValidatorSetNodeSelector();
				LocalMempoolPeriodicSubmittor submittor = new LocalMempoolPeriodicSubmittor(randomValidatorSubmittor, nodeSelector);
				// TODO: Fix hack, hack required due to lack of Guice
				this.checksBuilder.put(
					submittedInvariantName,
					nodes2 -> new CommittedChecker(submittor.issuedCommands().map(Pair::getFirst), nodeEvents)
				);
				return submittor::run;
			});
			return this;
		}

		public Builder addRadixEngineValidatorRegisterMempoolSubmissions(String submittedInvariantName, String registeredInvariantName) {
			this.modules.add(new AbstractModule() {
				@ProvidesIntoSet
				@ProcessOnDispatch
				private EventProcessor<BFTCommittedUpdate> committedProcessor(@Self BFTNode node) {
					return nodeEvents.processor(node, BFTCommittedUpdate.class);
				}
			});

			this.runnableBuilder.add(nodes -> {
				RadixEngineValidatorRegistrator validatorRegistrator = new RadixEngineValidatorRegistrator(nodes);
				NodeSelector nodeSelector = this.ledgerType.hasEpochs ? new EpochsNodeSelector() : new BFTValidatorSetNodeSelector();
				LocalMempoolPeriodicSubmittor submittor = new LocalMempoolPeriodicSubmittor(validatorRegistrator, nodeSelector);
				// TODO: Fix hack, hack required due to lack of Guice
				this.checksBuilder.put(
					submittedInvariantName,
					nodes2 -> new CommittedChecker(submittor.issuedCommands().map(Pair::getFirst), nodeEvents)
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
			this.modules.add(new AbstractModule() {
				@ProvidesIntoSet
				@ProcessOnDispatch
				private EventProcessor<BFTCommittedUpdate> committedProcessor(@Self BFTNode node) {
					return nodeEvents.processor(node, BFTCommittedUpdate.class);
				}
			});
			this.checksBuilder.put(invariantName, nodes -> new SafetyInvariant(nodeEvents));
			return this;
		}

		public Builder checkConsensusNoTimeouts(String invariantName) {
			this.modules.add(new AbstractModule() {
				@ProcessOnDispatch
				@ProvidesIntoSet
				private EventProcessor<Timeout> timeoutEventProcessor(@Self BFTNode node) {
					return nodeEvents.processor(node, Timeout.class);
				}
			});
			this.checksBuilder.put(invariantName, nodes -> new NoTimeoutsInvariant(nodeEvents));
			return this;
		}

		public Builder checkConsensusAllProposalsHaveDirectParents(String invariantName) {
			this.checksBuilder.put(invariantName, nodes -> new AllProposalsHaveDirectParentsInvariant());
			return this;
		}

		public Builder checkConsensusNoneCommitted(String invariantName) {
			this.modules.add(new AbstractModule() {
				@ProvidesIntoSet
				@ProcessOnDispatch
				private EventProcessor<BFTCommittedUpdate> committedProcessor(@Self BFTNode node) {
					return nodeEvents.processor(node, BFTCommittedUpdate.class);
				}
			});
			this.checksBuilder.put(invariantName, nodes -> new NoneCommittedInvariant(nodeEvents));
			return this;
		}

		public Builder checkLedgerProcessesConsensusCommitted(String invariantName) {
			this.modules.add(new AbstractModule() {
				@ProvidesIntoSet
				@ProcessOnDispatch
				private EventProcessor<BFTCommittedUpdate> committedProcessor(@Self BFTNode node) {
					return nodeEvents.processor(node, BFTCommittedUpdate.class);
				}
			});
			this.checksBuilder.put(invariantName, nodes -> new ConsensusToLedgerCommittedInvariant(nodeEvents));
			return this;
		}

		public Builder checkLedgerInOrder(String invariantName) {
			this.checksBuilder.put(invariantName, nodes -> new LedgerInOrderInvariant());
			return this;
		}

		public Builder checkEpochsHighViewCorrect(String invariantName, View epochHighView) {
			this.modules.add(new AbstractModule() {
				@ProvidesIntoSet
				@ProcessOnDispatch
				private EventProcessor<BFTCommittedUpdate> committedProcessor(@Self BFTNode node) {
					return nodeEvents.processor(node, BFTCommittedUpdate.class);
				}
			});
			this.checksBuilder.put(invariantName, nodes -> new EpochViewInvariant(epochHighView, nodeEvents));
			return this;
		}

		public SimulationTest build() {
			modules.add(new AbstractModule() {
				@Override
				public void configure() {
					bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
					bindConstant().annotatedWith(BFTSyncPatienceMillis.class).to(50);
					bindConstant().annotatedWith(PacemakerTimeout.class).to(pacemakerTimeout);
					bindConstant().annotatedWith(PacemakerRate.class).to(2.0);
					bindConstant().annotatedWith(PacemakerMaxExponent.class).to(0); // Use constant timeout for now
				}
			});
			modules.add(new MockedSystemModule());
			modules.add(new NoFeeModule());
			modules.add(new MockedCryptoModule());
			modules.add(new ConsensusModule());
			modules.add(new ConsensusRxModule());
			modules.add(new SystemInfoRxModule());
			modules.add(new LedgerRxModule());
			modules.add(new DispatcherModule());
			modules.add(new RxEnvironmentModule());
			if (ledgerType == LedgerType.MOCKED_LEDGER) {
				modules.add(new MockedBFTConfigurationModule());
				modules.add(new MockedLedgerModule());
				modules.add(new MockedConsensusRunnerModule());
				modules.add(new MockedLedgerUpdateRxModule());
			} else {
				modules.add(new LedgerModule());

				if (ledgerType == LedgerType.LEDGER) {
					modules.add(new MockedLedgerUpdateRxModule());
					modules.add(new MockedLedgerUpdateSender());
					modules.add(new MockedConsensusRunnerModule());
					modules.add(new MockedCommandGeneratorModule());
					modules.add(new MockedMempoolModule());
					modules.add(new MockedStateComputerModule());
					modules.add(new MockedSyncServiceModule());
				} else if (ledgerType == LedgerType.LEDGER_AND_SYNC) {
					modules.add(new MockedLedgerUpdateRxModule());
					modules.add(new MockedLedgerUpdateSender());
					modules.add(new MockedConsensusRunnerModule());
					modules.add(new MockedCommandGeneratorModule());
					modules.add(new MockedMempoolModule());
					modules.add(new MockedStateComputerModule());
					modules.add(new MockedCommittedReaderModule());
					modules.add(new MockedSyncRunnerModule());
				} else if (ledgerType == LedgerType.LEDGER_AND_LOCALMEMPOOL) {
					final var mempool = new LocalMempool(10, hasher);
					modules.add(new MockedLedgerUpdateRxModule());
					modules.add(new MockedLedgerUpdateSender());
					modules.add(new MockedConsensusRunnerModule());
					modules.add(new LedgerCommandGeneratorModule());
					modules.add(new LedgerLocalMempoolModule(10));
					modules.add(new AbstractModule() {
						@Override
						protected void configure() {
							bind(Mempool.class).toInstance(mempool);
						}
					});
					modules.add(new MockedSyncServiceModule());
					modules.add(new MockedStateComputerModule());
				} else if (ledgerType == LedgerType.LEDGER_AND_EPOCHS) {
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
					modules.add(new SyncRunnerModule());
					modules.add(new MockedCommittedReaderModule());
				} else if (ledgerType == LedgerType.LEDGER_AND_LOCALMEMPOOL_AND_EPOCHS_AND_RADIXENGINE) {
					modules.add(new LedgerCommandGeneratorModule());
					modules.add(new LedgerLocalMempoolModule(10));
					modules.add(new AbstractModule() {
						@Override
						protected void configure() {
							bind(Mempool.class).to(LocalMempool.class);
							bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(epochHighView);
							bind(Integer.class).annotatedWith(MinValidators.class).toInstance(minValidators);
							bind(Integer.class).annotatedWith(MaxValidators.class).toInstance(maxValidators);
							bind(RRI.class).annotatedWith(NativeToken.class).toInstance(NATIVE_TOKEN);
						}
					});
					modules.add(new RadixEngineModule());
					modules.add(new SimulationValidatorComputersModule());
					modules.add(new RadixEngineRxModule());
					modules.add(new MockedSyncServiceModule());
					modules.add(new MockedRadixEngineStoreModule());
				}
			}

			if (ledgerType.hasEpochs) {
				modules.add(new EpochsLedgerUpdateModule());
				modules.add(new EpochsLedgerUpdateRxModule());
				modules.add(new EpochsConsensusModule()); // constant for now
				modules.add(new ConsensusRunnerModule());
			}
			if (ledgerType.hasSync) {
				modules.add(new AbstractModule() {
					@Override
					public void configure() {
						bind(Integer.class).annotatedWith(SyncPatienceMillis.class).toInstance(50);
					}
				});
				modules.add(new SyncServiceModule());
				modules.add(new SyncRxModule());
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

			final SimulationNetwork simulationNetwork = Guice.createInjector(
				initialNodesModule,
				new SimulationNetworkModule(),
				networkModule
			).getInstance(SimulationNetwork.class);

			return new SimulationTest(
				nodes,
				simulationNetwork,
				Modules.combine(modules.build()),
				overrideModule,
				byzantineModuleCreator.apply(this.nodes),
				checks,
				runners
			);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	private Observable<Pair<String, Optional<TestInvariantError>>> runChecks(RunningNetwork runningNetwork, Duration duration) {
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
		SimulationNodes bftNetwork = new SimulationNodes(
			nodes,
			simulationNetwork,
			baseNodeModule,
			overrideModule,
			byzantineNodeModules
		);
		RunningNetwork runningNetwork = bftNetwork.start();

		Map<String, Optional<TestInvariantError>> checkResults = runChecks(runningNetwork, duration)
			.doFinally(bftNetwork::stop)
			.blockingStream()
			.collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
		return new TestResults(checkResults, runningNetwork);
	}

	private static <T> Iterator<T> repeatLast(Iterable<T> iterable) {
		final var iterator = iterable.iterator();
		if (!iterator.hasNext()) {
			throw new IllegalArgumentException("Can't repeat an empty iterable");
		}
		return new Iterator<>() {
			T lastValue = null;

			@Override
			public boolean hasNext() {
				return true;
			}

			@Override
			public T next() {
				if (iterator.hasNext()) {
					this.lastValue = iterator.next();
				}
				return this.lastValue;
			}
		};
	}
}
