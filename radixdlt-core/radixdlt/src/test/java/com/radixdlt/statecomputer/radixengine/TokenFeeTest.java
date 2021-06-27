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

import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.statecomputer.forks.RERulesVersion;
import com.radixdlt.statecomputer.forks.RERules;
import org.junit.Before;
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
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.actions.PayFee;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.LastStoredProof;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TokenFeeTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private RadixEngine<LedgerAndBFTProof> sut;

	@Inject
	private RERules rules;

	private ECKeyPair ecKeyPair = ECKeyPair.generateNew();

	@Inject
	@Genesis
	private VerifiedTxnsAndProof genesisTxns;

	// FIXME: Hack, need this in order to cause provider for genesis to be stored
	@Inject
	@LastStoredProof
	private LedgerProof ledgerProof;

	@Inject
	private EngineStore<LedgerAndBFTProof> engineStore;

	private Injector createInjector() {
		return Guice.createInjector(
			MempoolConfig.asModule(1000, 10),
			new RadixEngineForksLatestOnlyModule(RERulesConfig.testingDefault()),
			new ForksModule(),
			RadixEngineConfig.asModule(1, 100),
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(NumPeers.class).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
				}

				@ProvidesIntoSet
				private TokenIssuance mempoolFillerIssuance() {
					return TokenIssuance.of(ecKeyPair.getPublicKey(), TokenUnitConversions.unitsToSubunits(10000000000L));
				}
			}
		);
	}

	@Before
	public void setup() {
		createInjector().injectMembers(this);
	}

	@Test
	public void when_validating_atom_with_particles__result_has_no_error() throws Exception {
		var account = REAddr.ofPubKeyAccount(ecKeyPair.getPublicKey());
		var atom = sut.construct(new PayFee(account, RERulesVersion.FIXED_FEE))
			.mutex(ecKeyPair.getPublicKey(), "test").signAndBuild(ecKeyPair::sign);

		sut.execute(List.of(atom));
	}

	@Test
	public void when_validating_atom_without_particles__result_has_error() {
		var txn = TxLowLevelBuilder.newBuilder(rules.getSerialization()).build();
		assertThatThrownBy(() -> sut.execute(List.of(txn)))
			.isInstanceOf(RadixEngineException.class);
	}

	@Test
	public void when_validating_atom_with_fee_and_no_change__result_has_no_error() throws Exception {
		var account = REAddr.ofPubKeyAccount(ecKeyPair.getPublicKey());
		var txn = sut.construct(new PayFee(account, RERulesVersion.FIXED_FEE))
			.signAndBuild(ecKeyPair::sign);

		sut.execute(List.of(txn));
	}
}
