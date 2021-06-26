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

import com.radixdlt.atommodel.tokens.Amount;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.radix.TokenIssuance;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.StakedValidators;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.LastStoredProof;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
			new RadixEngineForksLatestOnlyModule(
				new RERulesConfig(
					false,
					100,
					2,
					Amount.ofTokens(10),
					Amount.ofTokens(10)
				)),
			new ForksModule(),
			RadixEngineConfig.asModule(1, 100, 50),
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(NumPeers.class).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
				}

				@ProvidesIntoSet
				private TokenIssuance issuance() {
					return TokenIssuance.of(staker.getPublicKey(), Amount.ofTokens(10).toSubunits());
				}
			}
		);
	}

	@Test
	public void staking_increases_stake_to_validator() throws Exception {
		// Arrange
		createInjector().injectMembers(this);
		var staked = sut.getComputedState(StakedValidators.class).getStake(self.getPublicKey());

		// Act
		var acct = REAddr.ofPubKeyAccount(staker.getPublicKey());
		var atom = sut.construct(new StakeTokens(acct, self.getPublicKey(), Amount.ofTokens(10).toSubunits()))
			.signAndBuild(staker::sign);
		sut.execute(List.of(atom));

		// Assert
		var nextStaked = sut.getComputedState(StakedValidators.class).getStake(self.getPublicKey());
		assertThat(nextStaked).isEqualTo(Amount.ofTokens(10).toSubunits().add(staked));
	}
}
