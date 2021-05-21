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
import com.radixdlt.atommodel.tokens.construction.TransferTokensConstructorV1;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV1;
import com.radixdlt.atommodel.validators.scrypt.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class TransferTokensTest {
	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][] {
			{UInt256.TEN, UInt256.TEN},
			{UInt256.TEN, UInt256.SIX}
		});
	}

	private RadixEngine<Void> engine;
	private EngineStore<Void> store;
	private final UInt256 startAmt;
	private final UInt256 transferAmt;

	public TransferTokensTest(UInt256 startAmt, UInt256 transferAmount) {
		this.startAmt = startAmt;
		this.transferAmt = transferAmount;
	}

	@Before
	public void setup() {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new ValidatorConstraintScrypt());
		cmAtomOS.load(new TokensConstraintScryptV1());
		var cm = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(cmAtomOS.virtualizedUpParticles())
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.getProcedures())
			.build();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			ActionConstructors.newBuilder()
				.put(TransferToken.class, new TransferTokensConstructorV1())
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.build(),
			cm,
			store
		);
	}

	@Test
	public void transfer_tokens() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();
		var accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
		var tokenAddr = REAddr.ofHashedKey(key.getPublicKey(), "test");
		var txn = this.engine.construct(
			key.getPublicKey(),
			List.of(
				new CreateMutableToken("test", "Name", "", "", ""),
				new MintToken(tokenAddr, accountAddr, startAmt)
			)
		).signAndBuild(key::sign);
		this.engine.execute(List.of(txn));

		// Act
		var to = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
		var transfer = this.engine.construct(new TransferToken(tokenAddr, accountAddr, to, transferAmt))
			.signAndBuild(key::sign);
		var parsed = this.engine.execute(List.of(transfer));

		// Assert
		var action = (TransferToken) parsed.get(0).getActions().get(0).getTxAction();
		assertThat(action.amount()).isEqualTo(transferAmt);
		assertThat(action.from()).isEqualTo(accountAddr);
		assertThat(action.to()).isEqualTo(to);
		assertThat(action.resourceAddr()).isEqualTo(tokenAddr);
	}
}
