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

package com.radixdlt.integration.mempool;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.radix.TokenIssuance;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.api.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.api.service.TransactionStatusService;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.integration.staking.DeterministicRunner;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.qualifier.LocalSigner;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.REOutput;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import static com.radixdlt.api.data.TransactionStatus.CONFIRMED;
import static com.radixdlt.api.data.TransactionStatus.TRANSACTION_NOT_FOUND;

@RunWith(Parameterized.class)
public class TxStatusTest {

	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][]{
			{true}
		});
	}

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	@LocalSigner
	private HashSigner hashSigner;
	@Inject
	@Self
	private ECPublicKey self;
	@Inject
	private DeterministicRunner runner;
	@Inject
	private EventDispatcher<NodeApplicationRequest> nodeApplicationRequestEventDispatcher;
	@Inject
	private EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;
	@Inject
	private RadixEngine<LedgerAndBFTProof> radixEngine;
	@Inject
	private TransactionStatusService transactionStatusService;

	private final boolean shouldSucceed;

	public TxStatusTest(boolean shouldSucceed) {
		this.shouldSucceed = shouldSucceed;
	}

	private Injector createInjector() {
		return Guice.createInjector(
			MempoolConfig.asModule(1000, 10),
			new RadixEngineForksLatestOnlyModule(RERulesConfig.testingDefault()),
			new ForksModule(),
			RadixEngineConfig.asModule(1),
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisModule(),
			new MempoolFillerModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(NumPeers.class).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
					bind(ClientApiStore.class).toInstance(mock(ClientApiStore.class));
				}

				@ProvidesIntoSet
				private TokenIssuance mempoolFillerIssuance(@Self ECPublicKey self) {
					return TokenIssuance.of(self, Amount.ofTokens(10).toSubunits());
				}
			}
		);
	}

	public REProcessedTxn waitForCommit() {
		var mempoolAdd = runner.runNextEventsThrough(MempoolAddSuccess.class);
		var committed = runner.runNextEventsThrough(
			LedgerUpdate.class,
			u -> {
				var output = u.getStateComputerOutput().getInstance(REOutput.class);
				return output.getProcessedTxns().stream().anyMatch(txn -> txn.getTxn().getId().equals(mempoolAdd.getTxn().getId()));
			}
		);

		return committed.getStateComputerOutput().getInstance(REOutput.class).getProcessedTxns().stream()
			.filter(t -> t.getTxn().getId().equals(mempoolAdd.getTxn().getId()))
			.findFirst()
			.orElseThrow();
	}

	public REProcessedTxn dispatchAndWaitForCommit(TxAction action) {
		nodeApplicationRequestEventDispatcher.dispatch(NodeApplicationRequest.create(action));
		return waitForCommit();
	}

	@Test
	public void singletx() throws Exception {
		createInjector().injectMembers(this);
		runner.start();

		var accountAddr = REAddr.ofPubKeyAccount(self);
		var otherAddr = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());

		// Not existing transaction Id
		var notExistingTxId = AID.from(HashUtils.random256().asBytes());
		assertEquals(TRANSACTION_NOT_FOUND, transactionStatusService.getTransactionStatus(notExistingTxId));

		// Correct transfer
		var transferAction = new TransferToken(REAddr.ofNativeToken(), accountAddr, otherAddr, Amount.ofTokens(10).toSubunits());
		var transferDispatched = dispatchAndWaitForCommit(transferAction);
		assertEquals(CONFIRMED, transactionStatusService.getTransactionStatus(transferDispatched.getTxn().getId()));
	}
}