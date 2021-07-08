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

import com.google.inject.TypeLiteral;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.system.FeeTable;
import com.radixdlt.atom.TxAction;
import com.radixdlt.constraintmachine.exceptions.InvalidPermissionException;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.utils.PrivateKeys;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;
import com.radixdlt.store.LastStoredProof;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class MutableTokenAndResourceFeeTest {
	private static final ECKeyPair VALIDATOR_KEY = PrivateKeys.ofNumeric(1);
	private static final ECKeyPair keyPair = ECKeyPair.generateNew();

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	private RadixEngine<LedgerAndBFTProof> sut;

	@Inject
	private BerkeleyLedgerEntryStore ledgerEntryStore;

	// FIXME: Hack, need this in order to cause provider for genesis to be stored
	@Inject
	@LastStoredProof
	private LedgerProof ledgerProof;

	private Injector createInjector() {
		return Guice.createInjector(
			MempoolConfig.asModule(1000, 10),
			new RadixEngineForksLatestOnlyModule(RERulesConfig.testingDefault().overrideFeeTable(
				FeeTable.create(
					Amount.zero(),
					Amount.ofTokens(1)
				)
			)),
			new ForksModule(),
			new SingleNodeAndPeersDeterministicNetworkModule(VALIDATOR_KEY),
			new MockedGenesisModule(
				Set.of(VALIDATOR_KEY.getPublicKey()),
				Amount.ofTokens(10 * 10)
			),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(NumPeers.class).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
					bind(new TypeLiteral<List<TxAction>>() { }).annotatedWith(Genesis.class)
						.toInstance(List.of(new MintToken(
							REAddr.ofNativeToken(),
							REAddr.ofPubKeyAccount(keyPair.getPublicKey()),
							Amount.ofTokens(1).toSubunits())
						));
				}
			}
		);
	}

	@Test
	public void cannot_create_xrd_token() throws Exception {
		// Arrange
		createInjector().injectMembers(this);
		var account = REAddr.ofPubKeyAccount(keyPair.getPublicKey());
		var tokDef = new MutableTokenDefinition(
			keyPair.getPublicKey(),
			"xrd",
			"XRD",
			"XRD",
			null,
			null
		);
		var txn = sut.construct(
			TxnConstructionRequest.create()
				.feePayer(account)
				.action(new CreateMutableToken(tokDef))
		).signAndBuild(keyPair::sign);

		// Act/Assert
		assertThatThrownBy(() -> sut.execute(List.of(txn)))
			.hasRootCauseInstanceOf(ProcedureException.class);
	}

	@Test
	public void cannot_mint_xrd_token() throws Exception {
		// Arrange
		createInjector().injectMembers(this);

		// Act/Assert
		var account = REAddr.ofPubKeyAccount(keyPair.getPublicKey());
		var txn = sut.construct(new MintToken(REAddr.ofNativeToken(), account, UInt256.SEVEN))
			.signAndBuild(keyPair::sign);
		assertThatThrownBy(() -> sut.execute(List.of(txn)))
			.hasRootCauseInstanceOf(InvalidPermissionException.class);
	}

	@Test
	public void atomic_token_creation_with_fees_and_spend_should_succeed() throws Exception {
		// Arrange
		createInjector().injectMembers(this);
		var tokDef = new MutableTokenDefinition(
			keyPair.getPublicKey(),
			"test",
			"test",
			"desc",
			null,
			null
		);

		var account = REAddr.ofPubKeyAccount(keyPair.getPublicKey());
		var tokenAddr = REAddr.ofHashedKey(keyPair.getPublicKey(), "test");
		var txn = sut.construct(
			TxnConstructionRequest.create()
				.feePayer(account)
				.createMutableToken(tokDef)
				.mint(tokenAddr, account, UInt256.SEVEN)
				.transfer(tokenAddr, account, account, UInt256.FIVE)
		).signAndBuild(keyPair::sign);

		// Act/Assert
		var branch = sut.transientBranch();
		branch.execute(List.of(txn));
	}

	@Test
	public void mint_to_non_account_address_should_fail() throws Exception {
		// Arrange
		createInjector().injectMembers(this);
		var tokDef = new MutableTokenDefinition(
			keyPair.getPublicKey(),
			"test",
			"test",
			"desc",
			null,
			null
		);

		var account = REAddr.ofPubKeyAccount(keyPair.getPublicKey());
		var tokenAddr = REAddr.ofHashedKey(keyPair.getPublicKey(), "test");
		var txn = sut.construct(
			TxnConstructionRequest.create()
				.feePayer(account)
				.createMutableToken(tokDef)
				.mint(tokenAddr, REAddr.ofHashedKey(keyPair.getPublicKey(), "test"), UInt256.SEVEN)
		).signAndBuild(keyPair::sign);

		// Act/Assert
		assertThatThrownBy(() -> sut.execute(List.of(txn))).hasRootCauseInstanceOf(DeserializeException.class);
	}

	@Test
	public void can_create_no_description_token() throws TxBuilderException, RadixEngineException {
		// Arrange
		createInjector().injectMembers(this);
		var tokDef = new MutableTokenDefinition(
			keyPair.getPublicKey(),
			"test",
			"test",
			null,
			null,
			null
		);
		var account = REAddr.ofPubKeyAccount(keyPair.getPublicKey());
		var txn = sut.construct(
			TxnConstructionRequest.create()
				.feePayer(account)
				.action(new CreateMutableToken(tokDef))
			).signAndBuild(keyPair::sign);

		var branch = sut.transientBranch();
		// Act/Assert
		branch.execute(List.of(txn));
	}
}
