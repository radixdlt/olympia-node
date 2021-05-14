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

package com.radixdlt.integration.recovery;

import com.google.inject.Provides;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.SimpleLedgerAccumulatorAndVerifier;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.forks.BetanetForksModule;
import com.radixdlt.statecomputer.forks.RadixEngineOnlyLatestForkModule;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.sync.CommittedReader;
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
import org.junit.runners.Parameterized.Parameters;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.radixdlt.CryptoModule;
import com.radixdlt.PersistedNodeForTestingModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.DeterministicSavedLastEvent;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageQueue;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.sync.messages.local.SyncCheckTrigger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.reactivex.rxjava3.schedulers.Timed;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Various liveness+recovery tests
 */
@RunWith(Parameterized.class)
public class RecoveryLivenessTest {
	private static final Logger logger = LogManager.getLogger();

	@Parameters
	public static Collection<Object[]> numNodes() {
		return List.of(new Object[][] {
			{1, 88L}, {2, 88L}, {3, 88L}, {4, 88L},
			{2, 1L}, {10, 100L}
		});
	}

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	@Genesis
	private VerifiedTxnsAndProof genesisTxns;

	private DeterministicNetwork network;
	private List<Supplier<Injector>> nodeCreators;
	private List<Injector> nodes = new ArrayList<>();
	private final ImmutableList<ECKeyPair> nodeKeys;
	private final long epochCeilingView;
	private MessageMutator messageMutator;

	public RecoveryLivenessTest(int numNodes, long epochCeilingView) {
		this.nodeKeys = Stream.generate(ECKeyPair::generateNew)
			.limit(numNodes)
			.sorted(Comparator.comparing(k -> k.getPublicKey().euid()))
			.collect(ImmutableList.toImmutableList());
		this.epochCeilingView = epochCeilingView;
	}

	@Before
	public void setup() {
		this.messageMutator = MessageMutator.nothing();
		this.network = new DeterministicNetwork(
			nodeKeys.stream().map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList()),
			MessageSelector.firstSelector(),
			this::mutate
		);

		List<BFTNode> allNodes = nodeKeys.stream()
			.map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList());

		Guice.createInjector(
			new MockedGenesisModule(),
			new CryptoModule(),
			new BetanetForksModule(),
			new RadixEngineOnlyLatestForkModule(View.of(epochCeilingView)),
			new RadixEngineModule(),
			RadixEngineConfig.asModule(1, 100, 50),
			new AbstractModule() {
				@Override
				public void configure() {
					bind(CommittedReader.class).toInstance(CommittedReader.mocked());
					bind(LedgerAccumulator.class).to(SimpleLedgerAccumulatorAndVerifier.class);
					bind(new TypeLiteral<EngineStore<LedgerAndBFTProof>>() { }).toInstance(new InMemoryEngineStore<>());
					bind(SystemCounters.class).toInstance(new SystemCountersImpl());
					bind(new TypeLiteral<ImmutableList<ECKeyPair>>() { }).annotatedWith(Genesis.class).toInstance(nodeKeys);
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

	boolean mutate(ControlledMessage message, MessageQueue queue) {
		return messageMutator.mutate(message, queue);
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
			new RadixEngineOnlyLatestForkModule(View.of(epochCeilingView)),
			RadixEngineConfig.asModule(1, 100, 50),
			new PersistedNodeForTestingModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(VerifiedTxnsAndProof.class).annotatedWith(Genesis.class).toInstance(genesisTxns);
					bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(ecKeyPair);
					bind(new TypeLiteral<List<BFTNode>>() { }).toInstance(allNodes);
					bind(ControlledSenderFactory.class).toInstance(network::createSender);
					bindConstant().annotatedWith(DatabaseLocation.class)
						.to(folder.getRoot().getAbsolutePath() + "/" + ValidatorAddress.of(ecKeyPair.getPublicKey()));
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

	private void initSync() {
		for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
			final var injector = nodeCreators.get(nodeIndex).get();
			withThreadCtx(injector, () -> {
				// need to manually init sync check, normally sync runner schedules it periodically
				injector.getInstance(new Key<EventDispatcher<SyncCheckTrigger>>() { }).dispatch(SyncCheckTrigger.create());
			});
		}
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

	private Optional<EpochView> lastCommitViewEmitted() {
		return network.allMessages().stream()
			.filter(msg -> msg.message() instanceof EpochViewUpdate)
			.map(msg -> (EpochViewUpdate) msg.message())
			.map(e -> new EpochView(e.getEpoch(), e.getViewUpdate().getHighQC().highestCommittedQC().getView()))
			.max(Comparator.naturalOrder());
	}

	private EpochView latestEpochView() {
		final var lastEventKey = Key.get(new TypeLiteral<DeterministicSavedLastEvent<EpochViewUpdate>>() { });
		return this.nodes.stream()
			.map(i -> i.getInstance(lastEventKey).getLastEvent())
			.map(e -> e == null ? new EpochView(0, View.genesis()) : e.getEpochView())
			.max(Comparator.naturalOrder()).orElse(new EpochView(0, View.genesis()));
	}

	private int processUntilNextCommittedEmitted(int maxSteps) {
		EpochView lastCommitted = this.lastCommitViewEmitted().orElse(new EpochView(0, View.genesis()));
		int count = 0;
		int senderIndex;
		do {
			if (count > maxSteps) {
				throw new IllegalStateException("Already lost liveness");
			}

			Timed<ControlledMessage> msg = processNext();
			senderIndex = msg.value().channelId().senderIndex();
			count++;
		} while (this.lastCommitViewEmitted().stream().noneMatch(v -> v.compareTo(lastCommitted) > 0));

		return senderIndex;
	}

	private void processForCount(int messageCount) {
		for (int i = 0; i < messageCount; i++) {
			processNext();
		}
	}

	/**
	 * Given that one validator is always alive means that that validator will
	 * always have the latest committed vertex which the validator can sync
	 * others with.
	 */
	@Test
	public void liveness_check_when_restart_all_but_one_node() {
		EpochView epochView = this.latestEpochView();

		for (int restart = 0; restart < 5; restart++) {
			processForCount(5000);

			EpochView nextEpochView = latestEpochView();
			assertThat(nextEpochView).isGreaterThan(epochView);
			epochView = nextEpochView;

			logger.info("Restarting " + restart);
			for (int nodeIndex = 1; nodeIndex < nodes.size(); nodeIndex++) {
				restartNode(nodeIndex);
			}
			initSync();
		}

		assertThat(epochView.getEpoch()).isGreaterThan(1);
	}

	@Test
	public void liveness_check_when_restart_node_on_view_update_with_commit() {
		EpochView epochView = this.latestEpochView();

		for (int restart = 0; restart < 5; restart++) {
			processForCount(5000);

			EpochView nextEpochView = latestEpochView();
			assertThat(nextEpochView).isGreaterThan(epochView);
			epochView = nextEpochView;

			int nodeToRestart = processUntilNextCommittedEmitted(5000);

			logger.info("Restarting " + restart);
			for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
				if (nodeIndex != (nodeToRestart + 1) % nodes.size()) {
					restartNode(nodeIndex);
				}
			}
			initSync();
		}

		assertThat(epochView.getEpoch()).isGreaterThan(1);
	}

	@Test
	public void liveness_check_when_restart_all_nodes() {
		EpochView epochView = this.latestEpochView();

		for (int restart = 0; restart < 5; restart++) {
			processForCount(5000);

			EpochView nextEpochView = latestEpochView();
			assertThat(nextEpochView).isGreaterThan(epochView);
			epochView = nextEpochView;

			logger.info("Restarting " + restart);
			for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
				restartNode(nodeIndex);
			}
			initSync();
		}

		assertThat(epochView.getEpoch()).isGreaterThan(1);
	}

	/**
	 * This test tests for recovery when there is a vertex chain > 3 due to timeouts.
	 * Probably won't be an issue once timeout certificates implemented.
	 */
	@Test
	public void liveness_check_when_restart_all_nodes_and_f_nodes_down() {
		int f = (nodes.size() - 1) / 3;
		if (f <= 0) {
			// if f <= 0, this is equivalent to liveness_check_when_restart_all_nodes();
			return;
		}

		this.messageMutator = (message, queue) -> message.channelId().receiverIndex() < f || message.channelId().senderIndex() < f;

		EpochView epochView = this.latestEpochView();

		for (int restart = 0; restart < 5; restart++) {
			processForCount(5000);

			EpochView nextEpochView = latestEpochView();
			assertThat(nextEpochView).isGreaterThan(epochView);
			epochView = nextEpochView;

			logger.info("Restarting " + restart);
			for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
				restartNode(nodeIndex);
			}
			initSync();
		}

		assertThat(epochView.getEpoch()).isGreaterThan(1);
	}
}
