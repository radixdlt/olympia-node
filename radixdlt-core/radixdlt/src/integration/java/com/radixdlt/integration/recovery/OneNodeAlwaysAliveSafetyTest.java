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

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Names;
import com.radixdlt.CryptoModule;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewQuorumReached;
import com.radixdlt.consensus.bft.ViewVotingResult.FormedQC;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.DeterministicEpochsConsensusProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.integration.distributed.deterministic.NodeEvents;
import com.radixdlt.integration.distributed.deterministic.NodeEvents.NodeEventProcessor;
import com.radixdlt.integration.distributed.deterministic.NodeEventsModule;
import com.radixdlt.integration.distributed.deterministic.SafetyCheckerModule;
import com.radixdlt.PersistedNodeForTestingModule;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolThrottleMs;
import com.radixdlt.network.addressbook.PeersView;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.utils.Base58;
import io.reactivex.rxjava3.schedulers.Timed;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.radix.database.DatabaseEnvironment;

@RunWith(Parameterized.class)
public class OneNodeAlwaysAliveSafetyTest {
	private static final Logger logger = LogManager.getLogger();

	@Parameters
	public static Collection<Object[]> numNodes() {
		return List.of(new Object[][] {
			{5}
		});
	}

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private DeterministicNetwork network;
	private List<Supplier<Injector>> nodeCreators;
	private List<Injector> nodes = new ArrayList<>();
	private final ECKeyPair universeKey = ECKeyPair.generateNew();
	private final List<ECKeyPair> nodeKeys;

	@Inject
	private NodeEvents nodeEvents;

	@Inject
	@Genesis
	private Atom genesisAtom;

	private int lastNodeToCommit;

	public OneNodeAlwaysAliveSafetyTest(int numNodes) {
		this.nodeKeys = Stream.generate(ECKeyPair::generateNew).limit(numNodes)
			.sorted(Comparator.comparing(n -> n.getPublicKey().euid())) // Sort this so that it matches order of proposers
			.collect(Collectors.toList());
	}

	@Before
	public void setup() {
		List<BFTNode> allNodes = nodeKeys.stream()
			.map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList());

		this.network = new DeterministicNetwork(allNodes,
			MessageSelector.firstSelector(),
			(message, queue) -> message.message() instanceof GetVerticesRequest
				|| message.message() instanceof LocalSyncRequest
		);

		Guice.createInjector(
			new MockedGenesisAtomModule(),
			new CryptoModule(),
			new AbstractModule() {
				@Override
				public void configure() {
					bind(SystemCounters.class).toInstance(new SystemCountersImpl());
					bind(ECKeyPair.class).annotatedWith(Names.named("universeKey")).toInstance(universeKey);
					bind(new TypeLiteral<ImmutableList<ECKeyPair>>() { }).annotatedWith(Genesis.class)
						.toInstance(ImmutableList.copyOf(nodeKeys));
				}
			},
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(new TypeLiteral<List<BFTNode>>() {
					}).toInstance(allNodes);
				}

				@ProvidesIntoSet
				public NodeEventProcessor<?> updateChecker() {
					return new NodeEventProcessor<>(
						ViewQuorumReached.class,
						(node, viewQuorumReached) -> {
							if (viewQuorumReached.votingResult() instanceof FormedQC
									&& ((FormedQC) viewQuorumReached.votingResult())
										.getQC().getCommittedAndLedgerStateProof().isPresent()) {
								lastNodeToCommit = network.lookup(node);
							}
						}
					);
				}
			},
			new SafetyCheckerModule(),
			new NodeEventsModule()
		).injectMembers(this);

		this.nodeCreators = nodeKeys.stream()
			.<Supplier<Injector>>map(k -> () -> createRunner(k, allNodes))
			.collect(Collectors.toList());

		for (Supplier<Injector> nodeCreator : nodeCreators) {
			this.nodes.add(nodeCreator.get());
		}
	}

	private void stopDatabase(Injector injector) {
		injector.getInstance(LedgerEntryStore.class).close();
		injector.getInstance(PersistentSafetyStateStore.class).close();
		injector.getInstance(DatabaseEnvironment.class).stop();
	}

	@After
	public void teardown() {
		this.nodes.forEach(this::stopDatabase);
	}

	private Injector createRunner(ECKeyPair ecKeyPair, List<BFTNode> allNodes) {
		return Guice.createInjector(
			new PersistedNodeForTestingModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(Names.named("magic")).to(0);
					bind(Atom.class).annotatedWith(Genesis.class).toInstance(genesisAtom);
					bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(ecKeyPair);
					bind(ECKeyPair.class).annotatedWith(Names.named("universeKey")).toInstance(universeKey);
					bind(new TypeLiteral<List<BFTNode>>() { }).toInstance(allNodes);
					bind(PeersView.class).toInstance(List::of);
					bind(ControlledSenderFactory.class).toInstance(network::createSender);
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(88L));
					bindConstant().annotatedWith(MempoolThrottleMs.class).to(10L);
					bindConstant().annotatedWith(MempoolMaxSize.class).to(10);
					bindConstant().annotatedWith(DatabaseLocation.class)
						.to(folder.getRoot().getAbsolutePath() + "/" + Base58.toBase58(ecKeyPair.getPublicKey().getBytes()));
				}

				@ProvidesIntoSet
				@ProcessOnDispatch
				private EventProcessor<BFTCommittedUpdate> committedUpdateEventProcessor(@Self BFTNode node) {
					return nodeEvents.processor(node, BFTCommittedUpdate.class);
				}

				@ProvidesIntoSet
				@ProcessOnDispatch
				private EventProcessor<ViewQuorumReached> viewQuorumReachedEventProcessor(@Self BFTNode node) {
					return nodeEvents.processor(node, ViewQuorumReached.class);
				}
			}
		);
	}

	private void restartNode(int index) {
		this.network.dropMessages(m -> m.channelId().receiverIndex() == index);
		Injector injector = nodeCreators.get(index).get();
		this.nodes.set(index, injector);
	}

	private void startNode(int index) {
		Injector injector = nodes.get(index);
		String bftNode = " " + injector.getInstance(Key.get(BFTNode.class, Self.class));
		ThreadContext.put("bftNode", bftNode);
		try {
			injector.getInstance(DeterministicEpochsConsensusProcessor.class).start();
		} finally {
			ThreadContext.remove("bftNode");
		}
	}

	private void processNext() {
		Timed<ControlledMessage> msg = this.network.nextMessage();
		logger.debug("Processing message {}", msg);

		int nodeIndex = msg.value().channelId().receiverIndex();
		Injector injector = this.nodes.get(nodeIndex);
		String bftNode = " " + injector.getInstance(Key.get(BFTNode.class, Self.class));
		ThreadContext.put("bftNode", bftNode);
		try {
			injector.getInstance(DeterministicEpochsConsensusProcessor.class).handleMessage(msg.value().origin(), msg.value().message());
		} finally {
			ThreadContext.remove("bftNode");
		}
	}

	private void processUntilNextCommittedUpdate() {
		lastNodeToCommit = -1;

		while (lastNodeToCommit == -1) {
			processNext();
		}
	}

	private void processForCount(int messageCount) {
		for (int i = 0; i < messageCount; i++) {
			processNext();
		}
	}

	@Test
	public void dropper_and_crasher_adversares_should_not_cause_safety_failures() {
		// Start
		for (int i = 0; i < nodes.size(); i++) {
			this.startNode(i);
		}

		// Drop first proposal so view 2 will be committed
		this.network.dropMessages(m -> m.message() instanceof Proposal);

		// process until view 2 committed
		this.processUntilNextCommittedUpdate();

		// Restart all except last committed
		logger.info("Restarting...");
		for (int i = 0; i < nodes.size(); i++) {
			if (i != this.lastNodeToCommit) {
				this.restartNode(i);
			}
		}
		for (int i = 0; i < nodes.size(); i++) {
			if (i != this.lastNodeToCommit) {
				this.startNode(i);
			}
		}

		// If nodes restart with correct safety precautions then view 1 should be skipped
		// otherwise, this will cause failure
		this.processForCount(5000);
	}
}
