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
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.atommodel.tokens.state.ExittingStake;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolRelayTrigger;
import com.radixdlt.network.addressbook.PeersView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.BetanetForksModule;
import com.radixdlt.statecomputer.forks.ForkOverwritesWithShorterEpochsModule;
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
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.radixdlt.statecomputer.transaction.TokenFeeChecker.FIXED_FEE;

@RunWith(Parameterized.class)
public class StakingUnstakingValidatorsTest {
	private static final Logger logger = LogManager.getLogger();
	@Parameterized.Parameters
	public static Collection<Object[]> forksModule() {
		return List.of(new Object[][] {
			{new RadixEngineForksLatestOnlyModule(View.of(100), false), false},
			{new ForkOverwritesWithShorterEpochsModule(false), false},
			{new RadixEngineForksLatestOnlyModule(View.of(100), true), true},
			{new ForkOverwritesWithShorterEpochsModule(true), true},
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

	public StakingUnstakingValidatorsTest(Module forkModule, boolean payFees) {
		this.nodeKeys = Stream.generate(ECKeyPair::generateNew)
			.limit(20)
			.sorted(Comparator.comparing(k -> k.getPublicKey().euid()))
			.collect(ImmutableList.toImmutableList());
		this.radixEngineConfiguration = Modules.combine(
			new BetanetForksModule(),
			forkModule,
			RadixEngineConfig.asModule(1, 10, 50)
		);
		this.payFees = payFees;
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
								TokenIssuance.of(key.getPublicKey(), ValidatorStake.MINIMUM_STAKE.multiply(UInt256.TEN))
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
				}

				@Provides
				private PeersView peersView(@Self BFTNode self) {
					var peers = allNodes.stream().filter(n -> !self.equals(n)).collect(Collectors.toList());
					return () -> peers;
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

	/**
	 * TODO: Figure out why if run for long enough, # of validators
	 * trends to minimum.
	 */
	@Test
	public void stake_unstake_transfers_restarts() {
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
			var amount = UInt256.from(random.nextInt(10)).multiply(ValidatorStake.MINIMUM_STAKE);

			var next = random.nextInt(10);
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
				default:
					continue;
			}

			var request = TxnConstructionRequest.create();
			if (payFees) {
				request.action(new PayFee(acct, FIXED_FEE));
			}
			request.action(action);
			dispatcher.dispatch(NodeApplicationRequest.create(request));
			this.nodes.forEach(n -> {
				n.getInstance(new Key<EventDispatcher<MempoolRelayTrigger>>() { }).dispatch(MempoolRelayTrigger.create());
				n.getInstance(new Key<EventDispatcher<SyncCheckTrigger>>() { }).dispatch(SyncCheckTrigger.create());
			});
		}

		var entryStore = this.nodes.get(0).getInstance(BerkeleyLedgerEntryStore.class);
		var totalTokens = entryStore.reduceUpParticles(TokensInAccount.class, UInt256.ZERO,
			(i, p) -> {
				var tokens = (TokensInAccount) p;
				return i.add(tokens.getAmount());
			}
		);
		logger.info("Total tokens: {}", totalTokens);
		var totalStaked = entryStore.reduceUpParticles(ValidatorStake.class, UInt256.ZERO,
			(i, p) -> {
				var tokens = (ValidatorStake) p;
				return i.add(tokens.getAmount());
			}
		);
		logger.info("Total staked: {}", totalStaked);
		var totalStakePrepared = entryStore.reduceUpParticles(PreparedStake.class, UInt256.ZERO,
			(i, p) -> {
				var tokens = (PreparedStake) p;
				return i.add(tokens.getAmount());
			}
		);
		logger.info("Total preparing stake: {}", totalStakePrepared);
		var totalStakeExitting = entryStore.reduceUpParticles(ExittingStake.class, UInt256.ZERO,
			(i, p) -> {
				var tokens = (ExittingStake) p;
				return i.add(tokens.getAmount());
			}
		);
		logger.info("Total exitting stake: {}", totalStakeExitting);
		logger.info("Total: {}", totalTokens.add(totalStaked).add(totalStakePrepared).add(totalStakeExitting));
	}

}
