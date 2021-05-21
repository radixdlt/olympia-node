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

package com.radixdlt.atommodel.tokens;

import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atommodel.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.MintTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.TransferTokensConstructor;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScrypt;
import com.radixdlt.atommodel.validators.scrypt.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MintTokensTest {
	private RadixEngine<Void> engine;
	private EngineStore<Void> store;

	@Before
	public void setup() {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new ValidatorConstraintScrypt());
		cmAtomOS.load(new TokensConstraintScrypt());
		var cm = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(cmAtomOS.virtualizedUpParticles())
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.getProcedures())
			.build();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			ActionConstructors.newBuilder()
				.put(TransferToken.class, new TransferTokensConstructor())
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.build(),
			cm,
			store
		);
	}

	@Test
	public void mint_tokens_as_owner() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();
		var accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
		var tokenAddr = REAddr.ofHashedKey(key.getPublicKey(), "test");
		var txn = this.engine.construct(
			key.getPublicKey(),
			new CreateMutableToken("test", "Name", "", "", "")
		).signAndBuild(key::sign);
		this.engine.execute(List.of(txn));

		// Act and Assert
		var mintTxn = this.engine.construct(new MintToken(tokenAddr, accountAddr, UInt256.TEN)).signAndBuild(key::sign);
		var parsed = this.engine.execute(List.of(mintTxn));
		var action = (MintToken) parsed.get(0).getActions().get(0).getTxAction();
		assertThat(action.amount()).isEqualTo(UInt256.TEN);
		assertThat(action.to()).isEqualTo(accountAddr);
		assertThat(action.resourceAddr()).isEqualTo(tokenAddr);
	}

	@Test
	public void authorization_failure_on_mint() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();
		var txn = this.engine.construct(
			key.getPublicKey(),
			new CreateMutableToken("test", "Name", "", "", "")
		).signAndBuild(key::sign);
		this.engine.execute(List.of(txn));

		// Act, Assert
		var addr = REAddr.ofHashedKey(key.getPublicKey(), "test");
		var nextKey = ECKeyPair.generateNew();
		var mintTxn = this.engine.construct(
			new MintToken(addr, REAddr.ofPubKeyAccount(key.getPublicKey()), UInt256.ONE)
		).signAndBuild(nextKey::sign);
		assertThatThrownBy(() -> this.engine.execute(List.of(mintTxn))).isInstanceOf(RadixEngineException.class);
	}
}
