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
 */

package com.radixdlt.integration.mempool;

import com.google.inject.Provides;
import com.radixdlt.MainnetForkConfigsModule;
import com.radixdlt.api.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.api.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolRelayTrigger;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.utils.KeyComparator;
import org.apache.logging.log4j.ThreadContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.radixdlt.PersistedNodeForTestingModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.reactivex.rxjava3.schedulers.Timed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Mempool should periodically relay its unprocessed messages to other nodes.
 */
@RunWith(Parameterized.class)
public class MempoolRelayTest {
	private static final int MEMPOOL_FILLER_NODE = 0;

	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][]{
			{2, 1}, // 2 validators, 1 full node
			{5, 2} // 5 validators, 2 full nodes
		});
	}

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private final ImmutableList<Integer> validators;
	private final ImmutableList<Integer> fullNodes;
	private DeterministicNetwork network;
	private ImmutableList<ECKeyPair> nodeKeys;
	private ImmutableList<Injector> nodes;

	public MempoolRelayTest(int numValidators, int numFullNodes) {
		this.validators = IntStream.range(0, numValidators)
			.boxed().collect(ImmutableList.toImmutableList());
		this.fullNodes = IntStream.range(numValidators, numValidators + numFullNodes)
			.boxed().collect(ImmutableList.toImmutableList());
	}

	@Before
	public void setup() {
		final var numNodes = this.validators.size() + this.fullNodes.size();

		this.nodeKeys = Stream.generate(ECKeyPair::generateNew)
			.limit(numNodes)
			.sorted(Comparator.comparing(ECKeyPair::getPublicKey, KeyComparator.instance()))
			.collect(ImmutableList.toImmutableList());

		final var bftNodes = nodeKeys.stream()
			.map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList());

		this.network = new DeterministicNetwork(
			bftNodes,
			MessageSelector.firstSelector(),
			MessageMutator.nothing()
		);

		this.nodes = nodeKeys.stream()
			.<Supplier<Injector>>map(k -> () -> createRunner(k, bftNodes))
			.map(Supplier::get)
			.collect(ImmutableList.toImmutableList());

		this.nodes.forEach(i -> i.getInstance(DeterministicProcessor.class).start());
	}

	private Injector createRunner(ECKeyPair ecKeyPair, List<BFTNode> allNodes) {
		final var validatorsKeys = this.validators.stream()
			.map(nodeKeys::get)
			.map(ECKeyPair::getPublicKey)
			.collect(Collectors.toSet());

		return Guice.createInjector(
			new MockedGenesisModule(
				validatorsKeys,
				Amount.ofTokens(1000),
				Amount.ofTokens(1000)
			),
			MempoolConfig.asModule(500, 100, 10, 10, 10),
			new MainnetForkConfigsModule(),
			new RadixEngineForksLatestOnlyModule(RERulesConfig.testingDefault().overrideMaxSigsPerRound(50)),
			new ForksModule(),
			new PersistedNodeForTestingModule(),
			new MempoolFillerModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(ecKeyPair);
					bind(new TypeLiteral<List<BFTNode>>() { }).toInstance(allNodes);
					bind(Environment.class).toInstance(network.createSender(BFTNode.create(ecKeyPair.getPublicKey())));
					bindConstant().annotatedWith(DatabaseLocation.class)
						.to(folder.getRoot().getAbsolutePath() + "/" + ecKeyPair.getPublicKey().toHex());
				}

				@Provides
				private PeersView peersView(@Self BFTNode self) {
					return () -> allNodes.stream()
						.filter(n -> !self.equals(n))
						.map(PeersView.PeerInfo::fromBftNode);
				}

				@Provides
				@Genesis
				private List<TxAction> mempoolFillerIssuance(@Self ECPublicKey self) {
					return List.of(new MintToken(
						REAddr.ofNativeToken(),
						REAddr.ofPubKeyAccount(nodeKeys.get(MEMPOOL_FILLER_NODE).getPublicKey()),
						Amount.ofTokens(10000000000L).toSubunits()
					));
				}
			}
		);
	}

	@After
	public void teardown() {
		this.nodes.forEach(this::stopDatabase);
	}

	private void stopDatabase(Injector injector) {
		injector.getInstance(BerkeleyLedgerEntryStore.class).close();
		injector.getInstance(PersistentSafetyStateStore.class).close();
		injector.getInstance(DatabaseEnvironment.class).stop();
	}

	private void processForCount(int messageCount) {
		for (int i = 0; i < messageCount; i++) {
			processNext();
		}
	}

	private Timed<ControlledMessage> processNext() {
		final var msg = this.network.nextMessage();
		final var nodeIndex = msg.value().channelId().receiverIndex();
		final var injector = this.nodes.get(nodeIndex);
		withThreadCtx(injector, () ->
			injector.getInstance(DeterministicProcessor.class)
				.handleMessage(msg.value().origin(), msg.value().message(), msg.value().typeLiteral())
		);
		return msg;
	}

	private void withThreadCtx(Injector injector, Runnable r) {
		ThreadContext.put("self", " " + injector.getInstance(Key.get(String.class, Self.class)));
		try {
			r.run();
		} finally {
			ThreadContext.remove("self");
		}
	}

	private long getCounter(int nodeIndex, CounterType counterType) {
		return this.nodes.get(nodeIndex).getInstance(SystemCounters.class).get(counterType);
	}

	private <T> void dispatch(int nodeIndex, Class<T> clazz, T event) {
		network.createSender(nodeIndex).getDispatcher(clazz).dispatch(event);
	}

	@Test
	public void full_node_should_relay_mempool_messages_so_they_can_be_processed_by_validator() {
		dispatch(MEMPOOL_FILLER_NODE, MempoolFillerUpdate.class, MempoolFillerUpdate.enable(500, true));
		processForCount(100000);
		dispatch(MEMPOOL_FILLER_NODE, MempoolFillerUpdate.class, MempoolFillerUpdate.disable());
		processForCount(100000);

		// assert that validators have an empty mempool, but not the full nodes
		this.validators.forEach(n -> assertEquals(0L, getCounter(n, CounterType.MEMPOOL_COUNT)));
		this.fullNodes.forEach(n -> assertTrue(getCounter(n, CounterType.MEMPOOL_COUNT) >= 1L));

		// trigger mempool relay on the full nodes and process some more messages
		this.fullNodes.forEach(n -> dispatch(n, MempoolRelayTrigger.class, MempoolRelayTrigger.create()));
		processForCount(10000);

		// at this point all mempools should be empty
		this.validators.forEach(n -> assertEquals(0L, getCounter(n, CounterType.MEMPOOL_COUNT)));
		this.fullNodes.forEach(n -> assertEquals(0L, getCounter(n, CounterType.MEMPOOL_COUNT)));
	}
}
