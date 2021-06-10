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
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atommodel.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.MintTokenConstructor;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV1;
import com.radixdlt.atommodel.tokens.state.TokenResource;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.UnclaimedREAddr;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.ConstraintMachine;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TokenDefinitionTest {
	private RadixEngine<Void> engine;
	private EngineStore<Void> store;
	private REParser parser;

	@Before
	public void setup() {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new TokensConstraintScryptV1());
		var cm = new ConstraintMachine(
			cmAtomOS.virtualizedUpParticles(),
			cmAtomOS.getProcedures()
		);
		this.parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			parser,
			ActionConstructors.newBuilder()
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.build(),
			cm,
			store
		);
	}

	@Test
	public void create_new_token_with_no_errors() throws RadixEngineException {
		// Arrange
		var keyPair = ECKeyPair.generateNew();
		var addr = REAddr.ofHashedKey(keyPair.getPublicKey(), "test");
		var addrParticle = new UnclaimedREAddr(addr);
		var tokenDefinitionParticle = new TokenResource(
			addr,
			"TEST",
			"description",
			"",
			"",
			UInt256.TEN
		);

		var holdingAddress = REAddr.ofPubKeyAccount(keyPair.getPublicKey());
		var tokensParticle = new TokensInAccount(
			holdingAddress,
			UInt256.TEN,
			addr
		);
		var builder = TxLowLevelBuilder.newBuilder()
			.virtualDown(addrParticle, "test".getBytes(StandardCharsets.UTF_8))
			.up(tokenDefinitionParticle)
			.up(tokensParticle)
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
		var addrParticle = new UnclaimedREAddr(addr);
		var tokenDefinitionParticle = new TokenResource(
			addr,
			"TEST",
			"description",
			"",
			"",
			UInt256.TEN
		);
		var builder = TxLowLevelBuilder.newBuilder()
			.virtualDown(addrParticle, "test".getBytes(StandardCharsets.UTF_8))
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
		var tokenDefinitionParticle = new TokenResource(
			addr,
			"TEST",
			"description",
			"",
			"",
			keyPair.getPublicKey()
		);
		var builder = TxBuilder.newBuilder(parser.getSubstateDeserialization())
			.toLowLevelBuilder()
			.virtualDown(new UnclaimedREAddr(addr), "smthng".getBytes(StandardCharsets.UTF_8))
			.up(tokenDefinitionParticle)
			.end();
		var sig = keyPair.sign(builder.hashToSign());
		var txn = builder.sig(sig).build();

		// Act and Assert
		assertThatThrownBy(() -> this.engine.execute(List.of(txn)))
			.isInstanceOf(RadixEngineException.class)
			.extracting("cause.errorCode")
			.containsExactly(CMErrorCode.AUTHORIZATION_ERROR);
	}
}
