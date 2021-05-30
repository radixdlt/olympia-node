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
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnstakeOwnership;
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.TxnsCommittedToLedger;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.BetanetForksModule;
import com.radixdlt.statecomputer.forks.RadixEngineOnlyLatestForkModule;
import com.radixdlt.store.DatabaseLocation;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.radix.TokenIssuance;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
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

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	@Named("RadixEngine")
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

				@ProvidesIntoSet
				private TokenIssuance mempoolFillerIssuance(@Self ECPublicKey self) {
					return TokenIssuance.of(self, ValidatorStake.MINIMUM_STAKE);
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
				EpochsLedgerUpdate.class,
				e -> e.getEpochChange().map(c -> c.getEpoch() == stakingEpoch).orElse(false)
			);
		}

		var accountAddr = REAddr.ofPubKeyAccount(self);
		var stakeTxn = dispatchAndWaitForCommit(new StakeTokens(accountAddr, self, ValidatorStake.MINIMUM_STAKE));
		runner.runNextEventsThrough(
			EpochsLedgerUpdate.class,
			e -> e.getEpochChange().map(c -> c.getEpoch() == unstakingEpoch).orElse(false)
		);
		var unstakeTxn = dispatchAndWaitForCommit(new UnstakeOwnership(accountAddr, self, ValidatorStake.MINIMUM_STAKE));

		if (transferEpoch > unstakingEpoch) {
			runner.runNextEventsThrough(
				EpochsLedgerUpdate.class,
				e -> e.getEpochChange().map(c -> c.getEpoch() == transferEpoch).orElse(false)
			);
		}

		var otherAddr = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
		var transferAction = new TransferToken(REAddr.ofNativeToken(), accountAddr, otherAddr, ValidatorStake.MINIMUM_STAKE);

		// Build transaction through radix engine
		if (shouldSucceed) {
			radixEngine.construct(transferAction);
		} else {
			assertThatThrownBy(() -> radixEngine.construct(transferAction)).isInstanceOf(TxBuilderException.class);
		}

		// Build transaction manually which spends locked transaction
		var txn = radixEngine.construct(txBuilder -> {
			txBuilder.swapFungible(
				TokensParticle.class,
				p -> p.getResourceAddr().isNativeToken()
					&& p.getHoldingAddr().equals(accountAddr)
					&& p.getEpochUnlocked().isPresent(),
				amt -> new TokensParticle(accountAddr, amt, REAddr.ofNativeToken()),
				ValidatorStake.MINIMUM_STAKE,
				"Not enough balance for transfer."
			).with(amt -> new TokensParticle(otherAddr, amt, REAddr.ofNativeToken()));
			txBuilder.end();
		}).signAndBuild(hashSigner::sign);
		if (shouldSucceed) {
			dispatchAndWaitForCommit(txn);
		} else {
			var transferFailure = dispatchAndCheckForError(txn);
			var ex = (MempoolRejectedException) transferFailure.getException();
			var reException = (RadixEngineException) ex.getCause();
			assertThat(reException).extracting("cause.errorCode")
				.containsExactly(CMErrorCode.AUTHORIZATION_ERROR);
		}
	}
}
