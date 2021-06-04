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

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.client.service.TransactionStatusService;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
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
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.TxnsCommittedToLedger;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.BetanetForksModule;
import com.radixdlt.statecomputer.forks.RadixEngineOnlyLatestForkModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.utils.UInt256;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;

import static com.radixdlt.client.api.TransactionStatus.*;
import static org.junit.Assert.assertEquals;

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
	@Named("RadixEngine")
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
			new BetanetForksModule(),
			new RadixEngineOnlyLatestForkModule(View.of(100)),
			RadixEngineConfig.asModule(1, 10, 10),
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisModule(),
			new MempoolFillerModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(Names.named("numPeers")).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
				}
			}
		);
	}

	public REProcessedTxn waitForCommit() {
		var mempoolAdd = runner.runNextEventsThrough(MempoolAddSuccess.class);
		var committed = runner.runNextEventsThrough(
			TxnsCommittedToLedger.class,
			c -> c.getParsedTxs().stream().anyMatch(txn -> txn.getTxn().getId().equals(mempoolAdd.getTxn().getId()))
		);

		return committed.getParsedTxs().stream()
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
		var transferAction = new TransferToken(REAddr.ofNativeToken(), accountAddr, otherAddr,
			ValidatorStake.MINIMUM_STAKE);
		var transferDispatched = dispatchAndWaitForCommit(transferAction);
		assertEquals(CONFIRMED, transactionStatusService.getTransactionStatus(transferDispatched.getTxn().getId()));

	}
}