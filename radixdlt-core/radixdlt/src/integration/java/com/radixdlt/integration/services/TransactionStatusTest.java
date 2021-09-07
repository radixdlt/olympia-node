/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
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

package com.radixdlt.integration.services;

import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.api.data.TransactionStatus;
import com.radixdlt.api.transactions.lookup.BerkeleyTransactionsByIdStore;
import com.radixdlt.api.transactions.lookup.TransactionStatusService;
import com.radixdlt.api.transactions.lookup.TransactionStatusServiceModule;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.deterministic.MultiNodeDeterministicRunner;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.MainnetForkConfigsModule;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.utils.PrivateKeys;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.radixdlt.PersistedNodeForTestingModule;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.radixdlt.api.data.TransactionStatus.TRANSACTION_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class TransactionStatusTest {
	private static final int NUM_NODES = 10;
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	private DeterministicNetwork network;
	private final ImmutableList<ECKeyPair> nodeKeys;
	private MultiNodeDeterministicRunner deterministicRunner;

	public TransactionStatusTest() {
		this.nodeKeys = PrivateKeys.numeric(1)
			.limit(NUM_NODES)
			.collect(ImmutableList.toImmutableList());
	}

	@Before
	public void setup() {
		this.network = new DeterministicNetwork(
			nodeKeys.stream().map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList()),
			MessageSelector.firstSelector(),
			MessageMutator.nothing()
		);

		var allNodes = nodeKeys.stream().map(k -> BFTNode.create(k.getPublicKey())).collect(Collectors.toList());
		var nodeCreators = Streams.mapWithIndex(nodeKeys.stream(), (k, i) ->
			(Supplier<Injector>) () -> createRunner(k, allNodes)).collect(Collectors.toList());

		deterministicRunner = new MultiNodeDeterministicRunner(
			nodeCreators,
			this::stopDatabase,
			network
		);
		deterministicRunner.start();
	}

	@After
	public void teardown() {
		deterministicRunner.tearDown();
	}

	private void stopDatabase(Injector injector) {
		injector.getInstance(BerkeleyLedgerEntryStore.class).close();
		injector.getInstance(PersistentSafetyStateStore.class).close();
		injector.getInstance(DatabaseEnvironment.class).stop();
	}

	private Injector createRunner(ECKeyPair ecKeyPair, List<BFTNode> allNodes) {
		return Guice.createInjector(
			new MockedGenesisModule(
				nodeKeys.stream().map(ECKeyPair::getPublicKey).collect(Collectors.toSet()),
				Amount.ofTokens(100000),
				Amount.ofTokens(1000)
			),
			MempoolConfig.asModule(10, 10),
			new MainnetForkConfigsModule(),
			new ForksModule(),
			new RadixEngineForksLatestOnlyModule(RERulesConfig.testingDefault()),
			new PersistedNodeForTestingModule(),
			new TransactionStatusServiceModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(ecKeyPair);
					bind(Environment.class).toInstance(network.createSender(BFTNode.create(ecKeyPair.getPublicKey())));
					bindConstant().annotatedWith(DatabaseLocation.class)
						.to(folder.getRoot().getAbsolutePath() + "/" + ecKeyPair.getPublicKey().toHex());

					var binder = Multibinder.newSetBinder(binder(), BerkeleyAdditionalStore.class);
					bind(BerkeleyTransactionsByIdStore.class).in(Scopes.SINGLETON);
					binder.addBinding().to(BerkeleyTransactionsByIdStore.class);
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

	@Test
	public void transaction_status_should_not_change_once_committed() throws Exception {
		var radixEngine = deterministicRunner.getNode(0)
			.getInstance(Key.get(new TypeLiteral<RadixEngine<LedgerAndBFTProof>>() { }));
		var acct = REAddr.ofPubKeyAccount(nodeKeys.get(0).getPublicKey());
		var action = new TransferToken(
			REAddr.ofNativeToken(),
			acct,
			REAddr.ofPubKeyAccount(PrivateKeys.ofNumeric(1).getPublicKey()),
			UInt256.ONE
		);
		var request = TxnConstructionRequest.create()
			.feePayer(acct)
			.action(action);
		var txBuilder = radixEngine.construct(request);
		var txn = txBuilder.signAndBuild(nodeKeys.get(0)::sign);
		var service = deterministicRunner.getNode(0).getInstance(TransactionStatusService.class);
		assertEquals(TRANSACTION_NOT_FOUND, service.getTransactionStatus(txn.getId()));

		var dispatcher = this.deterministicRunner.getNode(0)
			.getInstance(Key.get(new TypeLiteral<EventDispatcher<MempoolAdd>>() { }));


		dispatcher.dispatch(MempoolAdd.create(txn));
		TransactionStatus lastStatus = null;
		for (int i = 0; i < 20; i++) {
			for (int j = 0; j < 100; j++) {
				deterministicRunner.processNext();
				// Check that once confirmed, status does not change
				var status = service.getTransactionStatus(txn.getId());
				if (status != TransactionStatus.CONFIRMED) {
					assertThat(lastStatus).isNotEqualTo(TransactionStatus.CONFIRMED);
				}
				lastStatus = status;
			}
			// TODO: allow network to dispatch to itself rather than forcing it here
			dispatcher.dispatch(MempoolAdd.create(txn));
		}
	}
}
