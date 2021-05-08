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

package com.radixdlt.statecomputer.radixengine;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Names;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.Stakes;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.LastStoredProof;
import com.radixdlt.utils.UInt256;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.radix.TokenIssuance;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StakingTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private RadixEngine<LedgerAndBFTProof> sut;

	@Inject
	@Self
	private ECKeyPair self;

	@Inject
	private EngineStore<LedgerAndBFTProof> engineStore;

	// FIXME: Hack, need this in order to cause provider for genesis to be stored
	@Inject
	@LastStoredProof
	private LedgerProof ledgerProof;

	private ECKeyPair staker = ECKeyPair.generateNew();

	private Injector createInjector() {
		return Guice.createInjector(
			MempoolConfig.asModule(1000, 10),
			RadixEngineConfig.asModule(1, 100, 100, 50),
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(Names.named("numPeers")).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
				}

				@ProvidesIntoSet
				private TokenIssuance mempoolFillerIssuance() {
					return TokenIssuance.of(staker.getPublicKey(), UInt256.FIVE);
				}
			}
		);
	}

	@Test
	public void staking_increases_stake_to_validator() throws Exception {
		// Arrange
		createInjector().injectMembers(this);
		var stakes = sut.getComputedState(Stakes.class);
		var staked = stakes.toMap().get(self.getPublicKey());

		// Act
		var acct = REAddr.ofPubKeyAccount(staker.getPublicKey());
		var atom = sut.construct(new StakeTokens(acct, self.getPublicKey(), UInt256.FIVE))
			.signAndBuild(staker::sign);
		sut.execute(List.of(atom));

		// Assert
		var nextStaked = sut.getComputedState(Stakes.class);
		assertThat(nextStaked.toMap().get(self.getPublicKey()))
			.isEqualTo(UInt256.FIVE.add(staked));
	}

	@Test
	public void unstaking_decreases_stake_to_validator() throws Exception {
		// Arrange
		createInjector().injectMembers(this);
		var stakes = sut.getComputedState(Stakes.class);
		var staked = stakes.toMap().get(self.getPublicKey());
		var acct = REAddr.ofPubKeyAccount(staker.getPublicKey());
		var txn = sut.construct(new StakeTokens(acct, self.getPublicKey(), UInt256.FIVE))
			.signAndBuild(staker::sign);
		sut.execute(List.of(txn));

		// Act
		var nextTxn = sut.construct(new UnstakeTokens(acct, self.getPublicKey(), UInt256.THREE))
			.signAndBuild(staker::sign);
		sut.execute(List.of(nextTxn));

		// Assert
		var nextStaked = sut.getComputedState(Stakes.class);
		assertThat(nextStaked.toMap().get(self.getPublicKey()))
			.isEqualTo(UInt256.FIVE.add(staked).subtract(UInt256.THREE));
	}

	@Test
	public void cant_construct_transfer_with_unstaked_tokens_immediately() throws Exception {
		// Arrange
		createInjector().injectMembers(this);
		var acct = REAddr.ofPubKeyAccount(staker.getPublicKey());
		var acct2 = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
		var txn = sut.construct(new StakeTokens(acct, self.getPublicKey(), UInt256.FIVE))
			.signAndBuild(staker::sign);
		sut.execute(List.of(txn));
		var nextTxn = sut.construct(new UnstakeTokens(acct, self.getPublicKey(), UInt256.FIVE))
			.signAndBuild(staker::sign);
		sut.execute(List.of(nextTxn));

		// Act
		// Assert
		assertThatThrownBy(() -> sut.construct(new TransferToken(REAddr.ofNativeToken(), acct, acct2, UInt256.FIVE)))
			.isInstanceOf(TxBuilderException.class);
	}

	@Test
	public void cant_spend_unstaked_tokens_immediately() throws Exception {
		// Arrange
		createInjector().injectMembers(this);
		var acct = REAddr.ofPubKeyAccount(staker.getPublicKey());
		var acct2 = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
		var txn = sut.construct(new StakeTokens(acct, self.getPublicKey(), UInt256.FIVE))
			.signAndBuild(staker::sign);
		sut.execute(List.of(txn));
		var nextTxn = sut.construct(new UnstakeTokens(acct, self.getPublicKey(), UInt256.FIVE))
			.signAndBuild(staker::sign);
		sut.execute(List.of(nextTxn));

		// Act
		// Assert
		var txn2 = sut.construct(txBuilder ->
			txBuilder.swapFungible(
				TokensParticle.class,
				p -> p.getResourceAddr().equals(REAddr.ofNativeToken())
						&& p.getHoldingAddr().equals(acct),
				amt -> new TokensParticle(acct, amt, REAddr.ofNativeToken()),
				UInt256.FIVE,
				"Not enough balance for transfer."
			).with(amt -> new TokensParticle(acct2, amt, REAddr.ofNativeToken()))
		).signAndBuild(staker::sign);

		assertThatThrownBy(() -> sut.execute(List.of(txn2)))
			.isInstanceOf(RadixEngineException.class);
	}
}
