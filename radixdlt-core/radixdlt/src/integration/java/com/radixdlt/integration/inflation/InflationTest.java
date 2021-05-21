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

package com.radixdlt.integration.inflation;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.CryptoModule;
import com.radixdlt.PersistedNodeForTestingModule;
import com.radixdlt.SystemInfoModule;
import com.radixdlt.atommodel.system.SystemConstraintScryptV2;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.network.addressbook.PeersView;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.Rewards;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.BetanetForksModule;
import com.radixdlt.statecomputer.forks.RadixEngineOnlyLatestForkModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.systeminfo.InMemorySystemInfo;
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
import org.radix.TokenIssuance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class InflationTest {
	private static final Logger logger = LogManager.getLogger();
	private static final long VIEWS_PER_EPOCH = 100;
	private static final int NUM_VALIDATORS = 8;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	@Genesis
	private VerifiedTxnsAndProof genesis;

	private DeterministicNetwork network;
	private List<Supplier<Injector>> nodeCreators;
	private List<Injector> nodes = new ArrayList<>();
	private final ImmutableList<ECKeyPair> nodeKeys;

	public InflationTest() {
		this.nodeKeys = Stream.generate(ECKeyPair::generateNew)
			.limit(NUM_VALIDATORS)
			.sorted(Comparator.comparing(k -> k.getPublicKey().euid()))
			.collect(ImmutableList.toImmutableList());
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
			new BetanetForksModule(),
			new RadixEngineOnlyLatestForkModule(View.of(VIEWS_PER_EPOCH)),
			new RadixEngineModule(),
			RadixEngineConfig.asModule(1, NUM_VALIDATORS, 50),
			new AbstractModule() {
				@Override
				public void configure() {
					bind(CommittedReader.class).toInstance(CommittedReader.mocked());
					bind(LedgerAccumulator.class).to(SimpleLedgerAccumulatorAndVerifier.class);
					bind(new TypeLiteral<EngineStore<LedgerAndBFTProof>>() { }).toInstance(new InMemoryEngineStore<>());
					bind(SystemCounters.class).toInstance(new SystemCountersImpl());
					bind(new TypeLiteral<ImmutableList<ECKeyPair>>() { }).annotatedWith(Genesis.class).toInstance(nodeKeys);

					nodeKeys.forEach(key -> {
						Multibinder.newSetBinder(binder(), TokenIssuance.class)
							.addBinding().toInstance(TokenIssuance.of(key.getPublicKey(), UInt256.from(100)));
					});
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
			new BetanetForksModule(),
			new RadixEngineOnlyLatestForkModule(View.of(VIEWS_PER_EPOCH)),
			RadixEngineConfig.asModule(2, NUM_VALIDATORS, 50),
			new PersistedNodeForTestingModule(),
			new SystemInfoModule(),
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

	private static UInt256 diff(UInt256 a, UInt256 b) {
		return a.compareTo(b) >= 0 ? a.subtract(b) : b.subtract(a);
	}

	@Test
	public void inflation_test() {
		processForCount(100000);

		var radixEngine = nodes.get(0)
			.getInstance(Key.get(new TypeLiteral<RadixEngine<LedgerAndBFTProof>>() { }));
		var stakes = radixEngine.getComputedState(Rewards.class, "inflation");
		var systemInfo = nodes.get(0)
			.getInstance(Key.get(new TypeLiteral<InMemorySystemInfo>() { }));
		var epochView = systemInfo.getCurrentView();
		var totalViews = ((epochView.getEpoch() - 1) * (VIEWS_PER_EPOCH - 1)) + epochView.getView().number();
		var expectedRewardsTotal = SystemConstraintScryptV2.REWARDS_PER_PROPOSAL
			.multiply(UInt256.from(totalViews));
		var expectedRewards = expectedRewardsTotal
			.divide(UInt256.from(NUM_VALIDATORS));

		// Make sure inflation rewards is within 20% of expected per validator.
		assertThat(stakes.toMap().values())
			.allMatch(i -> diff(i, expectedRewards)
				.compareTo(expectedRewards.multiply(UInt256.from(20)).divide(UInt256.from(100))) <= 0);
		logger.info("Inflation expected {} actual {}", expectedRewards, stakes.toMap().values());

		// Make sure inflation rewards is within 5% of expected per validator.
		var actualTotal = stakes.toMap().values().stream().reduce(UInt256::add).orElse(UInt256.ZERO);
		assertThat(diff(expectedRewardsTotal, actualTotal))
			.isLessThan(expectedRewardsTotal.multiply(UInt256.from(5)).divide(UInt256.from(100)));
		logger.info("Inflation expectedTotal {} actual {}", expectedRewardsTotal, actualTotal);
	}
}
