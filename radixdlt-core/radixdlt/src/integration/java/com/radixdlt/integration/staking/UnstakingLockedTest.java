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
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateStore;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.REParsedTxn;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolRejectedException;
import com.radixdlt.statecomputer.RadixEngineMempoolException;
import com.radixdlt.statecomputer.TxnsCommittedToLedger;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.BetanetForksModule;
import com.radixdlt.statecomputer.forks.RadixEngineOnlyLatestForkModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.utils.UInt256;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.radix.TokenIssuance;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class UnstakingLockedTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	@Named("RadixEngine")
	private HashSigner hashSigner;
	@Inject @Self private ECPublicKey self;
	@Inject private DeterministicRunner runner;
	@Inject private DeterministicProcessor processor;
	@Inject private DeterministicNetwork network;
	@Inject private EventDispatcher<NodeApplicationRequest> nodeApplicationRequestEventDispatcher;
	@Inject private SystemCounters systemCounters;
	@Inject private EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;

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
					return TokenIssuance.of(self, TokenUnitConversions.unitsToSubunits(10000000000L));
				}
			}
		);
	}

	public MempoolAddFailure dispatchAndCheckForError(Txn txn) {
		mempoolAddEventDispatcher.dispatch(MempoolAdd.create(txn));
		var mempoolAddFailure = runner.runNextEventsThrough(MempoolAddFailure.class);
		return mempoolAddFailure;
	}

	public REParsedTxn dispatchAndCommit(TxAction action) {
		nodeApplicationRequestEventDispatcher.dispatch(NodeApplicationRequest.create(action));
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

	@Test
	public void check_that_full_mempool_empties_itself() {
		createInjector().injectMembers(this);

		runner.start();

		var accountAddr = REAddr.ofPubKeyAccount(self);
		var stakeTxn = dispatchAndCommit(new StakeTokens(accountAddr, self, UInt256.from(100)));
		var unstakeTxn = dispatchAndCommit(new UnstakeTokens(accountAddr, self, UInt256.from(100)));

		// Build transaction which uses locked transaction
		var tokenResource = unstakeTxn.instructions()
			.filter(REStateUpdate::isBootUp)
			.filter(u -> u.getParticle() instanceof TokensParticle)
			.findFirst().orElseThrow();
		var otherAddr = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
		var builder = TxLowLevelBuilder.newBuilder()
			.down(tokenResource.getSubstate().getId())
			.up(new TokensParticle(otherAddr, UInt256.from(100), REAddr.ofNativeToken()))
			.end();
		var sig = hashSigner.sign(builder.hashToSign());
		var txn = builder.sig(sig).build();
		var transferFailure = dispatchAndCheckForError(txn);
		var ex = (MempoolRejectedException) transferFailure.getException();
		var reException = (RadixEngineException) ex.getCause();
		assertThat(reException).extracting("cause.errorCode")
			.containsExactly(CMErrorCode.AUTHORIZATION_ERROR);
	}
}
