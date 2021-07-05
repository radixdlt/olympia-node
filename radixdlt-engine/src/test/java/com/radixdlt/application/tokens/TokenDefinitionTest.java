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

import com.radixdlt.application.system.scrypt.Syscall;
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.UnclaimedREAddr;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TokenDefinitionTest {
	private RadixEngine<Void> engine;
	private EngineStore<Void> store;
	private REParser parser;
	private SubstateSerialization serialization;

	@Before
	public void setup() {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new SystemConstraintScrypt(Set.of()));
		cmAtomOS.load(new TokensConstraintScryptV3());
		var cm = new ConstraintMachine(cmAtomOS.getProcedures());
		this.parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		this.serialization = cmAtomOS.buildSubstateSerialization();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			parser,
			serialization,
			REConstructor.newBuilder()
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.build(),
			cm,
			store
		);
	}

	@Test
	public void create_new_token_with_no_errors() throws Exception {
		// Arrange
		var keyPair = ECKeyPair.generateNew();
		var addr = REAddr.ofHashedKey(keyPair.getPublicKey(), "test");
		var tokenResource = TokenResource.createFixedSupplyResource(addr);
		var holdingAddress = REAddr.ofPubKeyAccount(keyPair.getPublicKey());
		var tokensParticle = new TokensInAccount(
			holdingAddress,
			addr,
			UInt256.TEN
		);
		var builder = TxLowLevelBuilder.newBuilder(serialization)
			.syscall(Syscall.READDR_CLAIM, "test".getBytes(StandardCharsets.UTF_8))
			.virtualDown(UnclaimedREAddr.class, addr)
			.up(tokenResource)
			.up(tokensParticle)
			.up(TokenResourceMetadata.empty(addr))
			.end();
		var sig = keyPair.sign(builder.hashToSign().asBytes());
		var txn = builder.sig(sig).build();

		// Act
		// Assert
		this.engine.execute(List.of(txn));
	}

	@Test
	public void create_fixed_token_with_no_tokens_should_error() throws Exception {
		// Arrange
		var keyPair = ECKeyPair.generateNew();
		var addr = REAddr.ofHashedKey(keyPair.getPublicKey(), "test");
		var tokenDefinitionParticle = TokenResource.createFixedSupplyResource(addr);
		var builder = TxLowLevelBuilder.newBuilder(serialization)
			.syscall(Syscall.READDR_CLAIM, "test".getBytes(StandardCharsets.UTF_8))
			.virtualDown(UnclaimedREAddr.class, addr)
			.up(tokenDefinitionParticle)
			.end();
		var sig = keyPair.sign(builder.hashToSign().asBytes());
		var txn = builder.sig(sig).build();

		// Act
		// Assert
		assertThatThrownBy(() -> this.engine.execute(List.of(txn))).isInstanceOf(RadixEngineException.class);
	}

	@Test
	public void using_someone_elses_address_should_fail() throws Exception {
		var keyPair = ECKeyPair.generateNew();
		// Arrange
		var addr = REAddr.ofHashedKey(ECKeyPair.generateNew().getPublicKey(), "smthng");
		var tokenDefinitionParticle = TokenResource.createMutableSupplyResource(addr, keyPair.getPublicKey());
		var builder = TxBuilder.newBuilder(parser.getSubstateDeserialization(), serialization)
			.toLowLevelBuilder()
			.syscall(Syscall.READDR_CLAIM, "smthng".getBytes(StandardCharsets.UTF_8))
			.virtualDown(UnclaimedREAddr.class, addr)
			.up(tokenDefinitionParticle)
			.end();
		var sig = keyPair.sign(builder.hashToSign());
		var txn = builder.sig(sig).build();

		// Act and Assert
		assertThatThrownBy(() -> this.engine.execute(List.of(txn)))
			.hasRootCauseInstanceOf(ProcedureException.class);
	}
}
