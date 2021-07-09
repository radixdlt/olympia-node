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

import com.radixdlt.application.system.construction.CreateSystemConstructorV2;
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(Parameterized.class)
public class NativeTokensTest {
	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][] {
			{new TokensConstraintScryptV3(), new TransferTokensConstructorV2()},
		});
	}

	private final ConstraintScrypt scrypt;
	private final ActionConstructor<TransferToken> transferTokensConstructor;
	private RadixEngine<Void> engine;
	private EngineStore<Void> store;

	public NativeTokensTest(ConstraintScrypt scrypt, ActionConstructor<TransferToken> transferTokensConstructor) {
		this.scrypt = scrypt;
		this.transferTokensConstructor = transferTokensConstructor;
	}

	@Before
	public void setup() throws Exception {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(scrypt);
		cmAtomOS.load(new SystemConstraintScrypt(Set.of()));
		var cm = new ConstraintMachine(
			cmAtomOS.getProcedures(),
			cmAtomOS.buildSubstateDeserialization(),
			cmAtomOS.buildVirtualSubstateDeserialization()
		);
		var parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		var serialization = cmAtomOS.buildSubstateSerialization();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			parser,
			serialization,
			REConstructor.newBuilder()
				.put(CreateSystem.class, new CreateSystemConstructorV2())
				.put(TransferToken.class, transferTokensConstructor)
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.build(),
			cm,
			store
		);
		var txn = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new CreateSystem(0))
				.action(new CreateMutableToken(null, "xrd", "xrd", "", "", ""))
		).buildWithoutSignature();
		this.engine.execute(List.of(txn), null, PermissionLevel.SYSTEM);
	}


	@Test
	public void mint_native_token_as_super_user_should_fail() throws Exception {
		// Arrange
		var addr = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
		var txn = this.engine.construct(
			new MintToken(REAddr.ofNativeToken(), addr, UInt256.TEN)
		).buildWithoutSignature();
		assertThatThrownBy(() -> this.engine.execute(List.of(txn), null, PermissionLevel.SUPER_USER))
			.isInstanceOf(RadixEngineException.class);
	}
}
