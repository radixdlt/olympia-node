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

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import com.radixdlt.CryptoModule;
import com.radixdlt.PersistedNodeForTestingModule;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.PayFee;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnregisterValidator;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atom.actions.UpdateAllowDelegationFlag;
import com.radixdlt.atom.actions.UpdateRake;
import com.radixdlt.atom.actions.UpdateValidatorOwnerAddress;
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV2;
import com.radixdlt.atommodel.system.state.ValidatorStakeData;
import com.radixdlt.atommodel.tokens.state.ExittingStake;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.atommodel.validators.state.AllowDelegationFlag;
import com.radixdlt.atommodel.validators.state.PreparedRakeUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
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
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolRelayTrigger;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.ForkOverwritesWithShorterEpochsModule;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForkRulesModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;
import com.radixdlt.utils.UInt256;
import io.reactivex.rxjava3.schedulers.Timed;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.radix.TokenIssuance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class StakingUnstakingValidatorsTest {
	private static final Logger logger = LogManager.getLogger();

	@Parameterized.Parameters
	public static Collection<Object[]> forksModule() {
		return List.of(new Object[][] {
			{new RadixEngineForksLatestOnlyModule(new RERulesConfig(false, 100)), false, 100},
			{new ForkOverwritesWithShorterEpochsModule(new RERulesConfig(false, 10)), false, 10},
			{new RadixEngineForksLatestOnlyModule(new RERulesConfig(true, 100)), true, 100},
			{new ForkOverwritesWithShorterEpochsModule(new RERulesConfig(true, 10)), true, 10},
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
	private final boolean payFees;
	private final long maxRounds;

	public StakingUnstakingValidatorsTest(Module forkModule, boolean payFees, long maxRounds) {
		this.nodeKeys = Stream.generate(ECKeyPair::generateNew)
			.limit(20)
			.sorted(Comparator.comparing(k -> k.getPublicKey().euid()))
			.collect(ImmutableList.toImmutableList());
		this.radixEngineConfiguration = Modules.combine(
			new ForksModule(),
			forkModule,
			RadixEngineConfig.asModule(1, 10, 50)
		);
		this.payFees = payFees;
		this.maxRounds = maxRounds;
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
					bind(new TypeLiteral<EngineStore<LedgerAndBFTProof>>() { }).toInstance(new InMemoryEngineStore<>());
					bind(SystemCounters.class).toInstance(new SystemCountersImpl());
					bind(new TypeLiteral<ImmutableList<ECKeyPair>>() { }).annotatedWith(Genesis.class).toInstance(nodeKeys);

					nodeKeys.forEach(key ->
						Multibinder.newSetBinder(binder(), TokenIssuance.class)
							.addBinding().toInstance(
								TokenIssuance.of(key.getPublicKey(), ValidatorStakeData.MINIMUM_STAKE.multiply(UInt256.TEN))
						)
					);
				}
			}
		).injectMembers(this);

		this.nodeCreators = nodeKeys.stream()
			.<Supplier<Injector>>map(k -> () -> createRunner(k, allNodes))
			.collect(Collectors.toList());

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

	private Injector createRunner(ECKeyPair ecKeyPair, List<BFTNode> allNodes) {
		return Guice.createInjector(
			MempoolConfig.asModule(10, 10),
			this.radixEngineConfiguration,
			new PersistedNodeForTestingModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(VerifiedTxnsAndProof.class).annotatedWith(Genesis.class).toInstance(genesis);
					bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(ecKeyPair);
					bind(new TypeLiteral<List<BFTNode>>() { }).toInstance(allNodes);
					bind(ControlledSenderFactory.class).toInstance(network::createSender);
					bindConstant().annotatedWith(DatabaseLocation.class)
						.to(folder.getRoot().getAbsolutePath() + "/" + ValidatorAddress.of(ecKeyPair.getPublicKey()));
					bind(new TypeLiteral<DeterministicSavedLastEvent<LedgerUpdate>>() { })
						.toInstance(new DeterministicSavedLastEvent<>(LedgerUpdate.class));
					Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessorOnDispatch<?>>() { })
						.addBinding().toProvider(new TypeLiteral<DeterministicSavedLastEvent<LedgerUpdate>>() { });
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
		ThreadContext.put("bftNode", " " + injector.getInstance(Key.get(BFTNode.class, Self.class)));
		try {
			r.run();
		} finally {
			ThreadContext.remove("bftNode");
		}
	}

	private Timed<ControlledMessage> processNext() {
		Timed<ControlledMessage> msg = this.network.nextMessage();
		logger.debug("Processing message {}", msg);

		int nodeIndex = msg.value().channelId().receiverIndex();
		Injector injector = this.nodes.get(nodeIndex);
		String bftNode = " " + injector.getInstance(Key.get(BFTNode.class, Self.class));
		ThreadContext.put("bftNode", bftNode);
		try {
			injector.getInstance(DeterministicProcessor.class)
				.handleMessage(msg.value().origin(), msg.value().message(), msg.value().typeLiteral());
		} finally {
			ThreadContext.remove("bftNode");
		}

		return msg;
	}

	private void processForCount(int messageCount) {
		for (int i = 0; i < messageCount; i++) {
			processNext();
		}
	}

	private static class NodeState {
		private final DeterministicSavedLastEvent<EpochViewUpdate> lastEpochView;
		private final EpochChange epochChange;
		private final BerkeleyLedgerEntryStore entryStore;
		private final Forks forks;

		@Inject
		private NodeState(
			DeterministicSavedLastEvent<EpochViewUpdate> lastEpochView,
			EpochChange epochChange,
			BerkeleyLedgerEntryStore entryStore,
			Forks forks
		) {
			this.lastEpochView = lastEpochView;
			this.epochChange = epochChange;
			this.entryStore = entryStore;
			this.forks = forks;
		}

		public long getEpoch() {
			return lastEpochView.getLastEvent() == null
				? epochChange.getEpoch()
				: lastEpochView.getLastEvent().getEpoch();
		}

		public Map<BFTNode, Map<String, String>> getValidators() {
			var forkConfig = forks.get(getEpoch());
			var reParser = forkConfig.getParser();
			Map<BFTNode, Map<String, String>> map = entryStore.reduceUpParticles(
				ValidatorStakeData.class,
				new HashMap<>(),
				(i, p) -> {
					var stakeData = (ValidatorStakeData) p;
					var data = new HashMap<String, String>();
					data.put("stake", stakeData.getAmount().toString());
					data.put("rake", stakeData.getRakePercentage().toString());
					i.put(BFTNode.create(stakeData.getValidatorKey()), data);
					return i;
				},
				reParser.getSubstateDeserialization()
			);

			entryStore.reduceUpParticles(
				AllowDelegationFlag.class,
				map,
				(i, p) -> {
					var flag = (AllowDelegationFlag) p;
					var data = new HashMap<String, String>();
					data.put("allowDelegation", Boolean.toString(flag.allowsDelegation()));
					i.merge(BFTNode.create(flag.getValidatorKey()), data, (a, b) -> {
						a.putAll(b);
						return a;
					});
					return i;
				},
				reParser.getSubstateDeserialization()
			);
			return map;
		}

		public UInt256 getTotalNativeTokens() {
			var forkConfig = forks.get(getEpoch());
			var reParser = forkConfig.getParser();
			var totalTokens = entryStore.reduceUpParticles(TokensInAccount.class, UInt256.ZERO,
				(i, p) -> {
					var tokens = (TokensInAccount) p;
					return i.add(tokens.getAmount());
				},
				reParser.getSubstateDeserialization()
			);
			logger.info("Total tokens: {}", totalTokens);
			var totalStaked = entryStore.reduceUpParticles(ValidatorStakeData.class, UInt256.ZERO,
				(i, p) -> {
					var tokens = (ValidatorStakeData) p;
					return i.add(tokens.getAmount());
				},
				reParser.getSubstateDeserialization()
			);
			logger.info("Total staked: {}", totalStaked);
			var totalStakePrepared = entryStore.reduceUpParticles(PreparedStake.class, UInt256.ZERO,
				(i, p) -> {
					var tokens = (PreparedStake) p;
					return i.add(tokens.getAmount());
				},
				reParser.getSubstateDeserialization()
			);
			logger.info("Total preparing stake: {}", totalStakePrepared);
			var totalStakeExitting = entryStore.reduceUpParticles(ExittingStake.class, UInt256.ZERO,
				(i, p) -> {
					var tokens = (ExittingStake) p;
					return i.add(tokens.getAmount());
				},
				reParser.getSubstateDeserialization()
			);
			logger.info("Total exitting stake: {}", totalStakeExitting);
			var total = totalTokens.add(totalStaked).add(totalStakePrepared).add(totalStakeExitting);
			logger.info("Total: {}", total);
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
				Key.get(new TypeLiteral<EventDispatcher<NodeApplicationRequest>>() { })
			);

			var privKey = nodeKeys.get(nodeIndex);
			var acct = REAddr.ofPubKeyAccount(privKey.getPublicKey());
			var to = nodeKeys.get(random.nextInt(nodeKeys.size())).getPublicKey();
			var amount = UInt256.from(random.nextInt(10)).multiply(ValidatorStakeData.MINIMUM_STAKE);

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
					if (random.nextInt(10) == 0) {
						action = new UnregisterValidator(privKey.getPublicKey(), null, null);
						break;
					}
					continue;
				case 5:
					restartNode(nodeIndex);
					continue;
				case 6:
					action = new UpdateRake(privKey.getPublicKey(), random.nextInt(PreparedRakeUpdate.RAKE_MAX + 1));
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

			var request = TxnConstructionRequest.create();
			if (payFees) {
				request.action(new PayFee(acct, MainnetForkRulesModule.FIXED_FEE));
			}
			request.action(action);
			dispatcher.dispatch(NodeApplicationRequest.create(request));
			this.nodes.forEach(n -> {
				n.getInstance(new Key<EventDispatcher<MempoolRelayTrigger>>() { }).dispatch(MempoolRelayTrigger.create());
				n.getInstance(new Key<EventDispatcher<SyncCheckTrigger>>() { }).dispatch(SyncCheckTrigger.create());
			});
		}

		var node = this.nodes.get(0).getInstance(Key.get(BFTNode.class, Self.class));
		logger.info("Node {}", node);
		logger.info("Initial {}", initialCount);
		var lastEpochView = this.nodes.get(0)
			.getInstance(Key.get(new TypeLiteral<DeterministicSavedLastEvent<LedgerUpdate>>() { }));
		var epoch = lastEpochView.getLastEvent() == null
			? this.nodes.get(0).getInstance(EpochChange.class).getEpoch()
			: ((Optional<EpochChange>) lastEpochView.getLastEvent().getStateComputerOutput())
				.map(EpochChange::getEpoch)
				.orElseGet(() -> lastEpochView.getLastEvent().getTail().getEpoch());

		logger.info("Epoch {}", epoch);
		var maxEmissions = UInt256.from(maxRounds).multiply(SystemConstraintScryptV2.REWARDS_PER_PROPOSAL).multiply(UInt256.from(epoch - 1));
		logger.info("Max emissions {}", maxEmissions);

		var nodeState = reloadNodeState();
		var finalCount = nodeState.getTotalNativeTokens();

		assertThat(finalCount).isGreaterThan(initialCount);
		var diff = finalCount.subtract(initialCount);
		logger.info("Difference {}", diff);
		assertThat(diff).isLessThanOrEqualTo(maxEmissions);

		for (var e : nodeState.getValidators().entrySet()) {
			logger.info("{} {}", e.getKey(), e.getValue());
		}
	}

}
