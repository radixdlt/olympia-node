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

import com.google.inject.Provides;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.utils.PrivateKeys;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.api.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.qualifier.LocalSigner;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.REOutput;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@RunWith(Parameterized.class)
public class UnstakingLockedTokensTest {
	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][]{
			{1, 2, 3, false},
			{1, 2, 4, true},
			{3, 4, 5, false},
			{3, 4, 6, true},
		});
	}

	private static final ECKeyPair TEST_KEY = PrivateKeys.ofNumeric(1);

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	@LocalSigner
	private HashSigner hashSigner;
	@Inject @Self private ECPublicKey self;
	@Inject private DeterministicRunner runner;
	@Inject private EventDispatcher<NodeApplicationRequest> nodeApplicationRequestEventDispatcher;
	@Inject private EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;
	@Inject private RadixEngine<LedgerAndBFTProof> radixEngine;
	private final long stakingEpoch;
	private final long unstakingEpoch;
	private final long transferEpoch;
	private final boolean shouldSucceed;

	public UnstakingLockedTokensTest(long stakingEpoch, long unstakingEpoch, long transferEpoch, boolean shouldSucceed) {
		if (stakingEpoch < 1) {
			throw new IllegalArgumentException();
		}

		this.stakingEpoch = stakingEpoch;
		this.unstakingEpoch = unstakingEpoch;
		this.transferEpoch = transferEpoch;
		this.shouldSucceed = shouldSucceed;
	}

	private Injector createInjector() {
		return Guice.createInjector(
			MempoolConfig.asModule(1000, 10),
			new RadixEngineForksLatestOnlyModule(RERulesConfig.testingDefault()),
			new ForksModule(),
			new SingleNodeAndPeersDeterministicNetworkModule(TEST_KEY),
			new MockedGenesisModule(
				Set.of(TEST_KEY.getPublicKey()),
				Amount.ofTokens(100)
			),
			new MempoolFillerModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(NumPeers.class).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
				}

				@Provides
				@Genesis
				private List<TxAction> mempoolFillerIssuance() {
					return List.of(new MintToken(
						REAddr.ofNativeToken(),
						REAddr.ofPubKeyAccount(TEST_KEY.getPublicKey()),
						Amount.ofTokens(10).toSubunits())
					);
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

	public MempoolAddFailure dispatchAndCheckForError(Txn txn) {
		mempoolAddEventDispatcher.dispatch(MempoolAdd.create(txn));
		return runner.runNextEventsThrough(MempoolAddFailure.class);
	}

	public REProcessedTxn dispatchAndWaitForCommit(Txn txn) {
		mempoolAddEventDispatcher.dispatch(MempoolAdd.create(txn));
		return waitForCommit();
	}

	public REProcessedTxn dispatchAndWaitForCommit(TxAction action) {
		nodeApplicationRequestEventDispatcher.dispatch(NodeApplicationRequest.create(action));
		return waitForCommit();
	}

	@Test
	public void test_stake_unlocking() throws Exception {
		createInjector().injectMembers(this);

		runner.start();

		if (stakingEpoch > 1) {
			runner.runNextEventsThrough(
				LedgerUpdate.class,
				e -> {
					var epochChange = e.getStateComputerOutput().getInstance(EpochChange.class);
					return epochChange != null && epochChange.getEpoch() == stakingEpoch;
				}
			);
		}

		var accountAddr = REAddr.ofPubKeyAccount(self);
		var stakeTxn = dispatchAndWaitForCommit(new StakeTokens(accountAddr, self, Amount.ofTokens(10).toSubunits()));
		runner.runNextEventsThrough(
			LedgerUpdate.class,
			e -> {
				var epochChange = e.getStateComputerOutput().getInstance(EpochChange.class);
				return epochChange != null && epochChange.getEpoch() == unstakingEpoch;
			}
		);
		var unstakeTxn = dispatchAndWaitForCommit(new UnstakeTokens(accountAddr, self, Amount.ofTokens(10).toSubunits()));

		if (transferEpoch > unstakingEpoch) {
			runner.runNextEventsThrough(
				LedgerUpdate.class,
				e -> {
					var epochChange = e.getStateComputerOutput().getInstance(EpochChange.class);
					return epochChange != null && epochChange.getEpoch() == transferEpoch;
				}
			);
		}

		var otherAddr = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
		var transferAction = new TransferToken(REAddr.ofNativeToken(), accountAddr, otherAddr, Amount.ofTokens(10).toSubunits());

		// Build transaction through radix engine
		if (shouldSucceed) {
			radixEngine.construct(transferAction);
		} else {
			assertThatThrownBy(() -> radixEngine.construct(transferAction)).isInstanceOf(TxBuilderException.class);
		}
	}
}
