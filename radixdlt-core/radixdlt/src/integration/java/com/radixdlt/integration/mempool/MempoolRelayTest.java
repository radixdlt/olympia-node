/* Copyright 2021 Radix DLT Ltd incorporated in England.
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.integration.mempool;

import com.google.inject.Provides;
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
import com.radixdlt.statecomputer.forks.MainnetForkConfigsModule;
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
