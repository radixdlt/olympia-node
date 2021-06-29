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

import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.NextEpoch;
import com.radixdlt.atom.actions.NextRound;
import com.radixdlt.application.system.construction.CreateSystemConstructorV2;
import com.radixdlt.application.system.construction.NextEpochConstructorV3;
import com.radixdlt.application.system.construction.NextViewConstructorV3;
import com.radixdlt.application.system.scrypt.EpochUpdateConstraintScrypt;
import com.radixdlt.application.system.scrypt.RoundUpdateConstraintScrypt;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.construction.StakeTokensConstructorV3;
import com.radixdlt.application.tokens.scrypt.StakingConstraintScryptV4;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.application.validators.construction.RegisterValidatorConstructor;
import com.radixdlt.application.validators.scrypt.ValidatorConstraintScryptV2;
import com.radixdlt.application.validators.scrypt.ValidatorRegisterConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.exceptions.SignedSystemException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(Parameterized.class)
public class NextViewV2Test {
	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][] {
			{
				List.of(
					new EpochUpdateConstraintScrypt(10, Amount.ofTokens(10).toSubunits(), 9800, 1),
					new RoundUpdateConstraintScrypt(10)
				),
				new NextViewConstructorV3()
			}
		});
	}

	private ECKeyPair key;
	private RadixEngine<Void> sut;
	private EngineStore<Void> store;
	private final List<ConstraintScrypt> scrypts;
	private final ActionConstructor<NextRound> nextViewConstructor;

	public NextViewV2Test(List<ConstraintScrypt> scrypts, ActionConstructor<NextRound> nextViewConstructor) {
		this.scrypts = scrypts;
		this.nextViewConstructor = nextViewConstructor;
	}

	@Before
	public void setup() throws Exception {
		var cmAtomOS = new CMAtomOS();
		scrypts.forEach(cmAtomOS::load);
		cmAtomOS.load(new StakingConstraintScryptV4(Amount.ofTokens(10).toSubunits()));
		cmAtomOS.load(new TokensConstraintScryptV3());
		cmAtomOS.load(new ValidatorConstraintScryptV2(2));
		cmAtomOS.load(new ValidatorRegisterConstraintScrypt());
		var cm = new ConstraintMachine(
			cmAtomOS.virtualizedUpParticles(),
			cmAtomOS.getProcedures()
		);
		var parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		var serialization = cmAtomOS.buildSubstateSerialization();
		this.store = new InMemoryEngineStore<>();
		this.sut = new RadixEngine<>(
			parser,
			serialization,
			REConstructor.newBuilder()
				.put(NextEpoch.class, new NextEpochConstructorV3(Amount.ofTokens(10).toSubunits(), 9800, 1))
				.put(CreateSystem.class, new CreateSystemConstructorV2())
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.put(StakeTokens.class, new StakeTokensConstructorV3(Amount.ofTokens(10).toSubunits()))
				.put(NextRound.class, nextViewConstructor)
				.put(RegisterValidator.class, new RegisterValidatorConstructor())
				.build(),
			cm,
			store
		);
		this.key = ECKeyPair.generateNew();
		var accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
		var txn = this.sut.construct(
			TxnConstructionRequest.create()
				.action(new CreateSystem(0))
				.action(new CreateMutableToken(null, "xrd", "xrd", "", "", ""))
				.action(new MintToken(REAddr.ofNativeToken(), accountAddr, Amount.ofTokens(10).toSubunits()))
				.action(new StakeTokens(accountAddr, key.getPublicKey(), Amount.ofTokens(10).toSubunits()))
				.action(new RegisterValidator(key.getPublicKey(), Optional.empty()))
				.action(new NextEpoch(u -> List.of(key.getPublicKey()), 0))
		).buildWithoutSignature();
		this.sut.execute(List.of(txn), null, PermissionLevel.SYSTEM);
	}

	@Test
	public void system_update_should_succeed() throws Exception {
		// Arrange
		var txn = sut.construct(new NextRound(1, false, 1, i -> key.getPublicKey()))
			.buildWithoutSignature();

		// Act and Assert
		this.sut.execute(List.of(txn), null, PermissionLevel.SUPER_USER);
	}

	@Test
	public void including_sigs_in_system_update_should_fail() throws Exception {
		// Arrange
		var keyPair = ECKeyPair.generateNew();
		var txn = sut.construct(new NextRound(1, false, 1, i -> key.getPublicKey()))
			.signAndBuild(keyPair::sign);

		// Act and Assert
		assertThatThrownBy(() -> this.sut.execute(List.of(txn), null, PermissionLevel.SUPER_USER))
			.hasRootCauseInstanceOf(SignedSystemException.class);
	}
}
