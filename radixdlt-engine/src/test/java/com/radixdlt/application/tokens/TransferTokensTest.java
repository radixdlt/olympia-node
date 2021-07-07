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
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.application.tokens.state.AccountBucket;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(Parameterized.class)
public class TransferTokensTest {
	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][] {
			{UInt256.TEN, UInt256.TEN, new TokensConstraintScryptV3(), new TransferTokensConstructorV2()},
			{UInt256.TEN, UInt256.SIX, new TokensConstraintScryptV3(), new TransferTokensConstructorV2()},
		});
	}

	private RadixEngine<Void> engine;
	private EngineStore<Void> store;
	private final UInt256 startAmt;
	private final UInt256 transferAmt;
	private final ConstraintScrypt scrypt;
	private final ActionConstructor<TransferToken> transferTokensConstructor;

	public TransferTokensTest(
		UInt256 startAmt,
		UInt256 transferAmount,
		ConstraintScrypt scrypt,
		ActionConstructor<TransferToken> transferTokensConstructor
	) {
		this.startAmt = startAmt;
		this.transferAmt = transferAmount;
		this.scrypt = scrypt;
		this.transferTokensConstructor = transferTokensConstructor;
	}

	@Before
	public void setup() {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new SystemConstraintScrypt(Set.of()));
		cmAtomOS.load(scrypt);
		var cm = new ConstraintMachine(cmAtomOS.getProcedures());
		var parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		var serialization = cmAtomOS.buildSubstateSerialization();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			parser,
			serialization,
			REConstructor.newBuilder()
				.put(TransferToken.class, transferTokensConstructor)
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.build(),
			cm,
			store
		);
	}

	@Test
	public void cannot_transfer_others_tokens() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();
		var accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
		var tokenAddr = REAddr.ofHashedKey(key.getPublicKey(), "test");
		var txn = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new CreateMutableToken(key.getPublicKey(), "test", "Name", "", "", ""))
				.action(new MintToken(tokenAddr, accountAddr, startAmt))
		).signAndBuild(key::sign);
		this.engine.execute(List.of(txn));

		// Act
		var nextKey = ECKeyPair.generateNew();
		var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
		var transfer = this.engine.construct(new TransferToken(tokenAddr, accountAddr, to, transferAmt))
			.signAndBuild(nextKey::sign);
		assertThatThrownBy(() -> this.engine.execute(List.of(transfer)))
			.hasRootCauseInstanceOf(AuthorizationException.class);
	}

	@Test
	public void transfer_tokens() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();
		var accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
		var tokenAddr = REAddr.ofHashedKey(key.getPublicKey(), "test");
		var txn = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new CreateMutableToken(key.getPublicKey(), "test", "Name", "", "", ""))
				.action(new MintToken(tokenAddr, accountAddr, startAmt))
		).signAndBuild(key::sign);
		this.engine.execute(List.of(txn));

		// Act
		var to = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
		var transfer = this.engine.construct(new TransferToken(tokenAddr, accountAddr, to, transferAmt))
			.signAndBuild(key::sign);
		var processed = this.engine.execute(List.of(transfer));

		// Assert
		var accounting = REResourceAccounting.compute(processed.getFirst().get(0).getGroupedStateUpdates().get(0));
		assertThat(accounting.bucketAccounting())
			.hasSize(2)
			.containsEntry(
				new AccountBucket(tokenAddr, accountAddr),
				new BigInteger(-1, transferAmt.toByteArray(), 0, UInt256.BYTES)
			)
			.containsEntry(
				new AccountBucket(tokenAddr, to),
				new BigInteger(1, transferAmt.toByteArray(), 0, UInt256.BYTES)
			);
		assertThat(accounting.resourceAccounting()).isEmpty();
	}
}
