/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.integration.staking;

import com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.constraintmachine.exceptions.SubstateNotFoundException;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.serialization.DeserializeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.assertj.core.util.Throwables;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.radix.TokenIssuance;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.util.Modules;
import com.radixdlt.CryptoModule;
import com.radixdlt.PersistedNodeForTestingModule;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.tokens.state.ExittingStake;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnregisterValidator;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atom.actions.UpdateAllowDelegationFlag;
import com.radixdlt.atom.actions.UpdateRake;
import com.radixdlt.atom.actions.UpdateValidatorOwnerAddress;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessorOnDispatch;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.DeterministicSavedLastEvent;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolRelayTrigger;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.statecomputer.InvalidProposedTxn;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.statecomputer.forks.ForkManager;
import com.radixdlt.statecomputer.forks.ForkOverwritesWithShorterEpochsModule;
import com.radixdlt.statecomputer.forks.ForkManagerModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.utils.KeyComparator;
import com.radixdlt.utils.UInt256;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.reactivex.rxjava3.schedulers.Timed;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class StakingUnstakingValidatorsTest {
	private static final Logger logger = LogManager.getLogger();
	private static final Amount REWARDS_PER_PROPOSAL = Amount.ofTokens(10);
	private static final RERulesConfig config = RERulesConfig.testingDefault().overrideMaxSigsPerRound(2);
	private static final Amount PER_BYTE_FEE = Amount.ofMicroTokens(2);

	@Parameterized.Parameters
	public static Collection<Object[]> forksModule() {
		return List.of(new Object[][]{
			{new RadixEngineForksLatestOnlyModule(config.overrideMaxRounds(100)), 100, null},
			{new ForkOverwritesWithShorterEpochsModule(config), 10, null},
			{
				new ForkOverwritesWithShorterEpochsModule(config), 10,
				new ForkOverwritesWithShorterEpochsModule(config.removeSigsPerRoundLimit())
			},
			{
				new RadixEngineForksLatestOnlyModule(
					config.overrideMaxRounds(100).overrideFeeTable(
						FeeTable.create(
							PER_BYTE_FEE,
							Amount.zero()
						)
					)
				), 100, null},
			{
				new ForkOverwritesWithShorterEpochsModule(
					config.overrideFeeTable(
						FeeTable.create(
							PER_BYTE_FEE,
							Amount.zero()
						)
					)
				), 10, null},
		});
	}

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	@Genesis
	private VerifiedTxnsAndProof genesis;

	private DeterministicNetwork network;
	private List<Supplier<Injector>> nodeCreators;
	private List<Injector> nodes = new ArrayList<>();
	private final ImmutableList<ECKeyPair> nodeKeys;
	private final Module radixEngineConfiguration;
	private final Module byzantineModule;
	private final long maxRounds;

	public StakingUnstakingValidatorsTest(Module forkModule, long maxRounds, Module byzantineModule) {
		this.nodeKeys = Stream.generate(ECKeyPair::generateNew)
			.limit(20)
			.sorted(Comparator.comparing(ECKeyPair::getPublicKey, KeyComparator.instance()))
			.collect(ImmutableList.toImmutableList());
		this.radixEngineConfiguration = Modules.combine(
			new ForkManagerModule(),
			new MainnetForksModule(),
			forkModule
		);
		this.maxRounds = maxRounds;
		this.byzantineModule = byzantineModule;
	}

	@Before
	public void setup() {
		this.network = new DeterministicNetwork(
			nodeKeys.stream().map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList()),
			MessageSelector.firstSelector(),
			MessageMutator.nothing()
		);

		List<BFTNode> allNodes = nodeKeys.stream()
			.map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList());

		Guice.createInjector(
			new MockedGenesisModule(),
			new CryptoModule(),
			new RadixEngineModule(),
			this.radixEngineConfiguration,
			new AbstractModule() {
				@Override
				public void configure() {
					bind(CommittedReader.class).toInstance(CommittedReader.mocked());
					bind(LedgerAccumulator.class).to(SimpleLedgerAccumulatorAndVerifier.class);
					bind(new TypeLiteral<EngineStore<LedgerAndBFTProof>>() {}).toInstance(new InMemoryEngineStore<>());
					bind(SystemCounters.class).toInstance(new SystemCountersImpl());
					bind(new TypeLiteral<ImmutableList<ECPublicKey>>() {}).annotatedWith(Genesis.class)
						.toInstance(nodeKeys.stream().map(ECKeyPair::getPublicKey).collect(ImmutableList.toImmutableList()));

					nodeKeys.forEach(key ->
						Multibinder.newSetBinder(binder(), TokenIssuance.class)
							.addBinding().toInstance(TokenIssuance.of(key.getPublicKey(), Amount.ofTokens(10 * 10).toSubunits()))
					);
				}
			}
		).injectMembers(this);

		this.nodeCreators = Streams.mapWithIndex(nodeKeys.stream(), (k, i) ->
			(Supplier<Injector>) () -> createRunner(i == 1, k, allNodes)).collect(Collectors.toList());

		for (Supplier<Injector> nodeCreator : nodeCreators) {
			this.nodes.add(nodeCreator.get());
		}
		this.nodes.forEach(i -> i.getInstance(DeterministicProcessor.class).start());
	}

	private void stopDatabase(Injector injector) {
		injector.getInstance(BerkeleyLedgerEntryStore.class).close();
		injector.getInstance(PersistentSafetyStateStore.class).close();
		injector.getInstance(DatabaseEnvironment.class).stop();
	}

	@After
	public void teardown() {
		this.nodes.forEach(this::stopDatabase);
	}

	private Injector createRunner(boolean byzantine, ECKeyPair ecKeyPair, List<BFTNode> allNodes) {
		var reConfig = byzantine && byzantineModule != null
			? Modules.override(this.radixEngineConfiguration).with(byzantineModule)
			: this.radixEngineConfiguration;

		return Guice.createInjector(
			MempoolConfig.asModule(10, 10),
			reConfig,
			new PersistedNodeForTestingModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(VerifiedTxnsAndProof.class).annotatedWith(Genesis.class).toInstance(genesis);
					bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(ecKeyPair);
					bind(new TypeLiteral<List<BFTNode>>() {}).toInstance(allNodes);
					bind(ControlledSenderFactory.class).toInstance(network::createSender);
					bindConstant().annotatedWith(DatabaseLocation.class)
						.to(folder.getRoot().getAbsolutePath() + "/" + ecKeyPair.getPublicKey().toHex());
					bind(new TypeLiteral<DeterministicSavedLastEvent<LedgerUpdate>>() {})
						.toInstance(new DeterministicSavedLastEvent<>(LedgerUpdate.class));
					Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessorOnDispatch<?>>() {})
						.addBinding().toProvider(new TypeLiteral<DeterministicSavedLastEvent<LedgerUpdate>>() {});
				}

				@ProvidesIntoSet
				EventProcessorOnDispatch<?> failOnEvent() {
					return new EventProcessorOnDispatch<>(
						InvalidProposedTxn.class,
						i -> {
							throw new IllegalStateException("Invalid proposed transaction occurred: " + i, i.getException());
						}
					);
				}

				@ProvidesIntoSet
				EventProcessorOnDispatch<?> mempoolFail() {
					return new EventProcessorOnDispatch<>(
						MempoolAddFailure.class,
						e -> {
							if (e.getException().getCause() instanceof RadixEngineException) {
								var rootCause = Throwables.getRootCause(e.getException());
								if (!(rootCause instanceof SubstateNotFoundException)) {
									throw new IllegalStateException("Found unexpected mempool exception", e.getException());
								}
							}
						}
					);
				}

				@Provides
				private PeersView peersView(@Self BFTNode self) {
					return () -> allNodes.stream()
						.filter(n -> !self.equals(n))
						.map(PeersView.PeerInfo::fromBftNode);
				}
			}
		);
	}

	private void restartNode(int index) {
		this.network.dropMessages(m -> m.channelId().receiverIndex() == index);
		Injector injector = nodeCreators.get(index).get();
		stopDatabase(this.nodes.set(index, injector));
		withThreadCtx(injector, () -> injector.getInstance(DeterministicProcessor.class).start());
	}

	private void withThreadCtx(Injector injector, Runnable r) {
		ThreadContext.put("self", " " + injector.getInstance(Key.get(String.class, Self.class)));
		try {
			r.run();
		} finally {
			ThreadContext.remove("self");
		}
	}

	private Timed<ControlledMessage> processNext() {
		Timed<ControlledMessage> msg = this.network.nextMessage();
		logger.debug("Processing message {}", msg);

		int nodeIndex = msg.value().channelId().receiverIndex();
		Injector injector = this.nodes.get(nodeIndex);
		ThreadContext.put("self", " " + injector.getInstance(Key.get(String.class, Self.class)));
		try {
			injector.getInstance(DeterministicProcessor.class)
				.handleMessage(msg.value().origin(), msg.value().message(), msg.value().typeLiteral());
		} finally {
			ThreadContext.remove("self");
		}

		return msg;
	}

	private void processForCount(int messageCount) {
		for (int i = 0; i < messageCount; i++) {
			processNext();
		}
	}

	private static class NodeState {
		private final String self;
		private final DeterministicSavedLastEvent<LedgerUpdate> lastLedgerUpdate;
		private final EpochChange epochChange;
		private final BerkeleyLedgerEntryStore entryStore;
		private final ForkManager forkManager;

		@Inject
		private NodeState(
			@Self String self,
			DeterministicSavedLastEvent<LedgerUpdate> lastLedgerUpdate,
			EpochChange epochChange,
			BerkeleyLedgerEntryStore entryStore,
			ForkManager forkManager
		) {
			this.self = self;
			this.lastLedgerUpdate = lastLedgerUpdate;
			this.epochChange = epochChange;
			this.entryStore = entryStore;
			this.forkManager = forkManager;
		}

		public String getSelf() {
			return self;
		}

		public long getEpoch() {
			if (lastLedgerUpdate.getLastEvent() == null) {
				return epochChange.getEpoch();
			}
			var epochChange = lastLedgerUpdate.getLastEvent().getStateComputerOutput().getInstance(EpochChange.class);
			if (epochChange != null) {
				return epochChange.getEpoch();
			} else {
				return lastLedgerUpdate.getLastEvent().getTail().getEpoch();
			}
		}

		public Map<BFTNode, Map<String, String>> getValidators() {
			final var forkConfig = forkManager.getCurrentFork(entryStore.getEpochsForkHashes());
			var reParser = forkConfig.getEngineRules().getParser();
			Map<BFTNode, Map<String, String>> map = entryStore.reduceUpParticles(
				new HashMap<>(), (i, p) -> {
					var stakeData = (ValidatorStakeData) p;
					var data = new HashMap<String, String>();
					data.put("stake", Amount.ofSubunits(stakeData.getAmount()).toString());
					data.put("rake", Integer.toString(stakeData.getRakePercentage()));
					i.put(BFTNode.create(stakeData.getValidatorKey()), data);
					return i;
				},
				reParser.getSubstateDeserialization(),
				ValidatorStakeData.class
			);

			entryStore.reduceUpParticles(
				map, (i, p) -> {
					var flag = (AllowDelegationFlag) p;
					var data = new HashMap<String, String>();
					data.put("allowDelegation", Boolean.toString(flag.allowsDelegation()));
					i.merge(BFTNode.create(flag.getValidatorKey()), data, (a, b) -> {
						a.putAll(b);
						return a;
					});
					return i;
				},
				reParser.getSubstateDeserialization(),
				AllowDelegationFlag.class
			);
			return map;
		}

		private <T extends Particle> T deserialize(SubstateDeserialization deserialization, byte[] data) {
			try {
				return (T) deserialization.deserialize(data);
			} catch (DeserializeException e) {
				throw new IllegalStateException(e);
			}
		}

		@SuppressWarnings("unchecked")
		public UInt256 getTotalNativeTokens() {
			final var forkConfig = forkManager.getCurrentFork(entryStore.getEpochsForkHashes());
			var reParser = forkConfig.getEngineRules().getParser();
			var deserialization = reParser.getSubstateDeserialization();
			var totalTokens = entryStore.reduceUpParticles(
				UInt256.ZERO,
				(i, p) -> {
					var tokens = (TokensInAccount) p;
					return i.add(tokens.getAmount());
				},
				reParser.getSubstateDeserialization(),
				TokensInAccount.class
			);
			logger.info("Total tokens: {}", Amount.ofSubunits(totalTokens));
			var index = deserialization.index(ValidatorStakeData.class);
			final UInt256 totalStaked;
			try (var stakeCursor = entryStore.openIndexedCursor(index)) {
				totalStaked = Streams.stream(stakeCursor)
					.map(s -> {
						ValidatorStakeData validatorStake = deserialize(deserialization, s.getData());
						return validatorStake.getAmount();
					}).reduce(UInt256::add).orElse(UInt256.ZERO);
			}
			logger.info("Total staked: {}", Amount.ofSubunits(totalStaked));
			var totalStakePrepared = entryStore.reduceUpParticles(
				UInt256.ZERO,
				(i, p) -> {
					var tokens = (PreparedStake) p;
					return i.add(tokens.getAmount());
				},
				reParser.getSubstateDeserialization(),
				PreparedStake.class
			);
			logger.info("Total preparing stake: {}", Amount.ofSubunits(totalStakePrepared));
			var totalStakeExitting = entryStore.reduceUpParticles(
				UInt256.ZERO,
				(i, p) -> {
					var tokens = (ExittingStake) p;
					return i.add(tokens.getAmount());
				},
				reParser.getSubstateDeserialization(),
				ExittingStake.class
			);
			logger.info("Total exitting stake: {}", Amount.ofSubunits(totalStakeExitting));
			var total = totalTokens.add(totalStaked).add(totalStakePrepared).add(totalStakeExitting);
			logger.info("Total: {}", Amount.ofSubunits(total));
			return total;
		}
	}

	private NodeState reloadNodeState() {
		return this.nodes.get(0).getInstance(NodeState.class);
	}

	/**
	 * TODO: Figure out why if run for long enough, # of validators
	 * trends to minimum.
	 */
	@Test
	public void stake_unstake_transfers_restarts() {
		var initialCount = reloadNodeState().getTotalNativeTokens();

		var random = new Random(12345);

		for (int i = 0; i < 5000; i++) {
			processForCount(100);

			var nodeIndex = random.nextInt(nodeKeys.size());
			var dispatcher = this.nodes.get(nodeIndex).getInstance(
				Key.get(new TypeLiteral<EventDispatcher<NodeApplicationRequest>>() {})
			);

			var privKey = nodeKeys.get(nodeIndex);
			var acct = REAddr.ofPubKeyAccount(privKey.getPublicKey());
			var to = nodeKeys.get(random.nextInt(nodeKeys.size())).getPublicKey();
			var amount = Amount.ofTokens(random.nextInt(10) * 10).toSubunits();

			var next = random.nextInt(16);
			final TxAction action;
			switch (next) {
				case 0:
					action = new TransferToken(REAddr.ofNativeToken(), acct, REAddr.ofPubKeyAccount(to), amount);
					break;
				case 1:
					action = new StakeTokens(acct, to, amount);
					break;
				case 2:
					var unstakeAmt = random.nextBoolean() ? UInt256.from(random.nextLong()) : amount;
					action = new UnstakeTokens(acct, to, unstakeAmt);
					break;
				case 3:
					action = new RegisterValidator(privKey.getPublicKey());
					break;
				case 4:
					// Only unregister once in a while
					if (nodeIndex <= 1) {
						continue;
					}

					action = new UnregisterValidator(privKey.getPublicKey());
					break;
				case 5:
					restartNode(nodeIndex);
					continue;
				case 6:
					action = new UpdateRake(privKey.getPublicKey(), random.nextInt(ValidatorUpdateRakeConstraintScrypt.RAKE_MAX + 1));
					break;
				case 7:
					action = new UpdateValidatorOwnerAddress(privKey.getPublicKey(), REAddr.ofPubKeyAccount(to));
					break;
				case 8:
					action = new UpdateAllowDelegationFlag(privKey.getPublicKey(), random.nextBoolean());
					break;
				default:
					continue;
			}

			var request = TxnConstructionRequest.create().action(action);
			dispatcher.dispatch(NodeApplicationRequest.create(request));
			this.nodes.forEach(n -> {
				n.getInstance(new Key<EventDispatcher<MempoolRelayTrigger>>() {}).dispatch(MempoolRelayTrigger.create());
				n.getInstance(new Key<EventDispatcher<SyncCheckTrigger>>() {}).dispatch(SyncCheckTrigger.create());
			});
		}

		var nodeState = reloadNodeState();
		logger.info("Node {}", nodeState.getSelf());
		logger.info("Initial {}", Amount.ofSubunits(initialCount));
		var epoch = nodeState.getEpoch();
		logger.info("Epoch {}", epoch);
		var maxEmissions = UInt256.from(maxRounds).multiply(REWARDS_PER_PROPOSAL.toSubunits()).multiply(UInt256.from(epoch - 1));
		logger.info("Max emissions {}", Amount.ofSubunits(maxEmissions));
		var finalCount = nodeState.getTotalNativeTokens();

		assertThat(finalCount).isGreaterThan(initialCount);
		var diff = finalCount.subtract(initialCount);
		logger.info("Difference {}", Amount.ofSubunits(diff));
		assertThat(diff).isLessThanOrEqualTo(maxEmissions);

		for (var e : nodeState.getValidators().entrySet()) {
			logger.info("{} {}", e.getKey(), e.getValue());
		}
	}

}
