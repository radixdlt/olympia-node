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

package com.radixdlt.application.tokens;

import com.radixdlt.accounting.REResourceAccounting;
import com.radixdlt.application.system.construction.CreateSystemConstructorV2;
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.application.tokens.construction.BurnTokenConstructor;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.exceptions.ResourceAllocationAndDestructionException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BurnTokensV3Test {
	private RadixEngine<Void> engine;

	@Before
	public void setup() throws Exception {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new SystemConstraintScrypt(Set.of()));
		cmAtomOS.load(new TokensConstraintScryptV3());
		var cm = new ConstraintMachine(
			cmAtomOS.getProcedures(),
			cmAtomOS.buildSubstateDeserialization(),
			cmAtomOS.buildVirtualSubstateDeserialization()
		);
		var parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		var serialization = cmAtomOS.buildSubstateSerialization();
		EngineStore<Void> store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			parser,
			serialization,
			REConstructor.newBuilder()
				.put(CreateSystem.class, new CreateSystemConstructorV2())
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.put(BurnToken.class, new BurnTokenConstructor())
				.build(),
			cm,
			store
		);
		var genesis = this.engine.construct(new CreateSystem(0)).buildWithoutSignature();
		this.engine.execute(List.of(genesis), null, PermissionLevel.SYSTEM);
	}

	@Test
	public void can_burn_tokens_if_owner_of_token_resource() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();
		var tokenAddr = REAddr.ofHashedKey(key.getPublicKey(), "test");
		var txn = this.engine.construct(
			new CreateMutableToken(key.getPublicKey(), "test", "Name", "", "", "")
		).signAndBuild(key::sign);
		this.engine.execute(List.of(txn));
		var account = REAddr.ofPubKeyAccount(key.getPublicKey());
		var mintTxn = this.engine.construct(new MintToken(tokenAddr, account, UInt256.TEN)).signAndBuild(key::sign);
		this.engine.execute(List.of(mintTxn));

		// Act
		var burnTxn = this.engine.construct(new BurnToken(tokenAddr, account, UInt256.TEN))
			.signAndBuild(key::sign);
		var processed = this.engine.execute(List.of(burnTxn));

		// Assert
		var accounting = REResourceAccounting.compute(processed.get(0).getGroupedStateUpdates().get(0));
		assertThat(accounting.resourceAccounting())
			.hasSize(1)
			.containsEntry(tokenAddr, BigInteger.valueOf(-10));
	}

	@Test
	public void cannot_burn_tokens_if_deallocation_disabled() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();
		var tokenAddr = REAddr.ofHashedKey(key.getPublicKey(), "test");
		var txn = this.engine.construct(
			new CreateMutableToken(key.getPublicKey(), "test", "Name", "", "", "")
		).signAndBuild(key::sign);
		this.engine.execute(List.of(txn));
		var account = REAddr.ofPubKeyAccount(key.getPublicKey());
		var mintTxn = this.engine.construct(new MintToken(tokenAddr, account, UInt256.TEN))
			.signAndBuild(key::sign);
		this.engine.execute(List.of(mintTxn));

		// Act
		var request = TxnConstructionRequest.create()
			.disableResourceAllocAndDestroy()
			.action(new BurnToken(tokenAddr, account, UInt256.TEN));
		var burnTxn = this.engine.construct(request).signAndBuild(key::sign);

		// Assert
		assertThatThrownBy(() -> this.engine.execute(List.of(burnTxn)))
			.hasRootCauseInstanceOf(ResourceAllocationAndDestructionException.class);
	}

	@Test
	public void cannot_burn_tokens_if_not_owner_of_token_resource() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();
		var tokenAddr = REAddr.ofHashedKey(key.getPublicKey(), "test");
		var txn = this.engine.construct(
			new CreateMutableToken(key.getPublicKey(), "test", "Name", "", "", "")
		).signAndBuild(key::sign);
		this.engine.execute(List.of(txn));
		var nextKey = ECKeyPair.generateNew();
		var nextAccountAddr = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
		var mintTxn = this.engine.construct(new MintToken(tokenAddr, nextAccountAddr, UInt256.TEN)).signAndBuild(key::sign);
		this.engine.execute(List.of(mintTxn));

		// Act
		var burnTxn = this.engine.construct(new BurnToken(tokenAddr, nextAccountAddr, UInt256.TEN)).signAndBuild(nextKey::sign);
		assertThatThrownBy(() -> this.engine.execute(List.of(burnTxn)))
			.isInstanceOf(RadixEngineException.class)
			.hasRootCauseExactlyInstanceOf(AuthorizationException.class);
	}
}
