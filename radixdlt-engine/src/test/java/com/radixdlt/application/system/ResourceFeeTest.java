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

package com.radixdlt.application.system;

import com.radixdlt.application.system.construction.FeeReserveCompleteConstructor;
import com.radixdlt.application.system.construction.FeeReservePutConstructor;
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.FeeReserveComplete;
import com.radixdlt.atom.actions.FeeReservePut;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.exceptions.DefaultedSystemLoanException;
import com.radixdlt.constraintmachine.meter.ResourceFeeMeter;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public final class ResourceFeeTest {
	private RadixEngine<Void> engine;
	private EngineStore<Void> store;
	private final ECKeyPair key = ECKeyPair.generateNew();
	private final REAddr accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());

	@Before
	public void setup() throws Exception {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new TokensConstraintScryptV3());
		cmAtomOS.load(new SystemConstraintScrypt(Set.of("xrd")));
		var cm = new ConstraintMachine(
			cmAtomOS.getProcedures(),
			cmAtomOS.buildVirtualSubstateDeserialization(),
			ResourceFeeMeter.create(Amount.ofTokens(1).toSubunits())
		);
		var parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		var serialization = cmAtomOS.buildSubstateSerialization();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			parser,
			serialization,
			REConstructor.newBuilder()
				.put(TransferToken.class, new TransferTokensConstructorV2())
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.put(FeeReservePut.class, new FeeReservePutConstructor())
				.put(FeeReserveComplete.class, new FeeReserveCompleteConstructor(FeeTable.create(Amount.zero(), Amount.ofTokens(1))))
				.build(),
			cm,
			store
		);
		var txn = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new CreateMutableToken(null, "xrd", "xrd", "", "", ""))
				.action(new MintToken(REAddr.ofNativeToken(), accountAddr, Amount.ofTokens(4).toSubunits()))
		).buildWithoutSignature();
		this.engine.execute(List.of(txn), null, PermissionLevel.SYSTEM);
	}

	@Test
	public void paying_for_fees_should_work() throws Exception {
		// Arrange
		var tokDef = new MutableTokenDefinition(key.getPublicKey(), "test");
		var create = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new FeeReservePut(accountAddr, Amount.ofTokens(1).toSubunits()))
				.action(new CreateMutableToken(tokDef)))
			.signAndBuild(key::sign);

		// Act
		this.engine.execute(List.of(create));
	}

	@Test
	public void paying_for_fees_should_work_2() throws Exception {
		// Arrange
		var tokDef1 = new MutableTokenDefinition(key.getPublicKey(), "test");
		var tokDef2 = new MutableTokenDefinition(key.getPublicKey(), "test2");
		var tokDef3 = new MutableTokenDefinition(key.getPublicKey(), "test3");
		var create = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new FeeReservePut(accountAddr, Amount.ofTokens(3).toSubunits()))
				.action(new CreateMutableToken(tokDef1))
				.action(new CreateMutableToken(tokDef2))
				.action(new CreateMutableToken(tokDef3)))
			.signAndBuild(key::sign);

		// Act
		this.engine.execute(List.of(create));
	}

	@Test
	public void paying_too_little_fees_should_fail() throws Exception {
		// Arrange
		var tokDef = new MutableTokenDefinition(key.getPublicKey(), "test");
		var create = this.engine.construct(
			TxnConstructionRequest.create()
				.action(new FeeReservePut(accountAddr, Amount.ofMicroTokens(999999).toSubunits()))
				.action(new CreateMutableToken(tokDef)))
			.signAndBuild(key::sign);

		// Act
		assertThatThrownBy(() -> this.engine.execute(List.of(create)))
			.hasRootCauseInstanceOf(DefaultedSystemLoanException.class);
	}
}
