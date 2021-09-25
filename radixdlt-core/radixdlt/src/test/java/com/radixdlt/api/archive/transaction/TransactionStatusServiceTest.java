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

package com.radixdlt.api.archive.transaction;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.api.node.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.api.service.transactions.BerkeleyTransactionsByIdStore;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.safety.PersistentSafetyStateStore;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.deterministic.SingleNodeDeterministicRunner;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.qualifier.LocalSigner;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.REOutput;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForkConfigsModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.PrivateKeys;
import com.radixdlt.utils.UInt256;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class TransactionStatusServiceTest {
	@Parameterized.Parameters
	public static Collection<Object[]> params() {
		return List.of(
			new Object[] {
				(Function<REAddr, TxAction>) addr -> new TransferToken(
					REAddr.ofNativeToken(),
					addr,
					REAddr.ofPubKeyAccount(PrivateKeys.ofNumeric(2).getPublicKey()),
					UInt256.ONE
				),
			},
			new Object[]{
				(Function<REAddr, TxAction>) addr -> new StakeTokens(
					addr,
					addr.publicKey().orElseThrow(),
					Amount.ofTokens(1).toSubunits()
				)
			}
		);
	}

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	private static final ECKeyPair TEST_KEY = PrivateKeys.ofNumeric(1);

	@Inject
	@LocalSigner
	private HashSigner hashSigner;
	@Inject
	@Self
	private ECPublicKey self;
	@Inject
	private SingleNodeDeterministicRunner runner;
	@Inject
	private RadixEngine<LedgerAndBFTProof> radixEngine;
	@Inject
	private EventDispatcher<MempoolAdd> mempoolDispatcher;
	@Inject
	private TransactionStatusService transactionStatusService;

	private Injector injector;
	private final Function<REAddr, TxAction> actionMapper;

	public TransactionStatusServiceTest(Function<REAddr, TxAction> actionMapper) {
		this.actionMapper = actionMapper;
	}

	@Before
	public void setup() {
		this.injector = Guice.createInjector(
			MempoolConfig.asModule(1000, 10),
			new MainnetForkConfigsModule(),
			new RadixEngineForksLatestOnlyModule(RERulesConfig.testingDefault().overrideMinimumStake(Amount.ofTokens(1))),
			new ForksModule(),
			new SingleNodeAndPeersDeterministicNetworkModule(TEST_KEY),
			new MockedGenesisModule(
				Set.of(TEST_KEY.getPublicKey()),
				Amount.ofTokens(110),
				Amount.ofTokens(100)
			),
			new MempoolFillerModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(NumPeers.class).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
					var binder = Multibinder.newSetBinder(binder(), BerkeleyAdditionalStore.class);
					bind(BerkeleyTransactionsByIdStore.class).in(Scopes.SINGLETON);
					binder.addBinding().to(BerkeleyTransactionsByIdStore.class);
				}
			}
		);
		this.injector.injectMembers(this);
	}

	@After
	public void teardown() {
		injector.getInstance(BerkeleyLedgerEntryStore.class).close();
		injector.getInstance(PersistentSafetyStateStore.class).close();
		injector.getInstance(DatabaseEnvironment.class).stop();
	}


	@Test
	public void mempool_add_should_have_pending_status() throws Exception {
		// Arrange
		var acct = REAddr.ofPubKeyAccount(self);
		var request = TxnConstructionRequest.create()
			.feePayer(acct)
			.action(actionMapper.apply(acct));
		var txBuilder = radixEngine.construct(request);
		var transfer = txBuilder.signAndBuild(hashSigner::sign);

		transactionStatusService.mempoolAddSuccessEventProcessor()
			.process(MempoolAddSuccess.create(transfer));

		// Assert
		var status = transactionStatusService.getTransactionStatus(transfer.getId());
		assertThat(status).isEqualTo(TransactionStatus.MEMPOOL);
	}

	@Test
	public void mempool_add_should_not_change_status_of_transaction() throws Exception {
		// Arrange
		runner.start();
		var acct = REAddr.ofPubKeyAccount(self);
		var request = TxnConstructionRequest.create()
			.feePayer(acct)
			.action(actionMapper.apply(acct));
		var txBuilder = radixEngine.construct(request);
		var transfer = txBuilder.signAndBuild(hashSigner::sign);
		mempoolDispatcher.dispatch(MempoolAdd.create(transfer));
		runner.runNextEventsThrough(
			LedgerUpdate.class,
			u -> {
				var output = u.getStateComputerOutput().getInstance(REOutput.class);
				return output.getProcessedTxns().stream().anyMatch(txn -> txn.getTxn().getId().equals(transfer.getId()));
			}
		);

		// Act
		mempoolDispatcher.dispatch(MempoolAdd.create(transfer));
		runner.processNext(100);

		// Assert
		var status = transactionStatusService.getTransactionStatus(transfer.getId());
		assertThat(status).isEqualTo(TransactionStatus.COMMITTED);
	}
}
