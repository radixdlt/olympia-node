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

package com.radixdlt.atommodel.system;

import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atom.actions.SystemNextView;
import com.radixdlt.atommodel.system.construction.CreateSystemConstructorV2;
import com.radixdlt.atommodel.system.construction.NextEpochConstructorV2;
import com.radixdlt.atommodel.system.construction.NextViewConstructorV2;
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV2;
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.atommodel.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.MintTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.StakeTokensConstructorV2;
import com.radixdlt.atommodel.tokens.scrypt.StakingConstraintScryptV3;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV2;
import com.radixdlt.atommodel.validators.construction.RegisterValidatorConstructor;
import com.radixdlt.atommodel.validators.scrypt.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(Parameterized.class)
public class NextViewV2Test {
	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][] {
			{new SystemConstraintScryptV2(), new NextViewConstructorV2()}
		});
	}

	private ECKeyPair key;
	private RadixEngine<Void> sut;
	private EngineStore<Void> store;
	private final ConstraintScrypt scrypt;
	private final ActionConstructor<SystemNextView> nextViewConstructor;

	public NextViewV2Test(ConstraintScrypt scrypt, ActionConstructor<SystemNextView> nextViewConstructor) {
		this.scrypt = scrypt;
		this.nextViewConstructor = nextViewConstructor;
	}

	@Before
	public void setup() throws Exception {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(scrypt);
		cmAtomOS.load(new StakingConstraintScryptV3());
		cmAtomOS.load(new TokensConstraintScryptV2());
		cmAtomOS.load(new ValidatorConstraintScrypt());
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
			ActionConstructors.newBuilder()
				.put(SystemNextEpoch.class, new NextEpochConstructorV2())
				.put(CreateSystem.class, new CreateSystemConstructorV2())
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.put(StakeTokens.class, new StakeTokensConstructorV2())
				.put(SystemNextView.class, nextViewConstructor)
				.put(RegisterValidator.class, new RegisterValidatorConstructor())
				.build(),
			cm,
			store
		);
		this.key = ECKeyPair.generateNew();
		var accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
		var txn = this.sut.construct(
			TxnConstructionRequest.create()
				.action(new CreateSystem())
				.action(new CreateMutableToken(null, "xrd", "xrd", "", "", ""))
				.action(new MintToken(REAddr.ofNativeToken(), accountAddr, ValidatorStake.MINIMUM_STAKE))
				.action(new StakeTokens(accountAddr, key.getPublicKey(), ValidatorStake.MINIMUM_STAKE))
				.action(new RegisterValidator(key.getPublicKey()))
				.action(new SystemNextEpoch(u -> List.of(key.getPublicKey()), 0))
		).buildWithoutSignature();
		this.sut.execute(List.of(txn), null, PermissionLevel.SYSTEM);
	}

	@Test
	public void system_update_should_succeed() throws Exception {
		// Arrange
		var txn = sut.construct(new SystemNextView(1, 1, key.getPublicKey()))
			.buildWithoutSignature();

		// Act and Assert
		this.sut.execute(List.of(txn), null, PermissionLevel.SUPER_USER);
	}

	@Test
	public void including_sigs_in_system_update_should_fail() throws Exception {
		// Arrange
		var keyPair = ECKeyPair.generateNew();
		var txn = sut.construct(new SystemNextView(1, 1, key.getPublicKey()))
			.signAndBuild(keyPair::sign);

		// Act and Assert
		assertThatThrownBy(() -> this.sut.execute(List.of(txn), null, PermissionLevel.SUPER_USER))
			.isInstanceOf(RadixEngineException.class)
			.extracting("cause.errorCode")
			.containsExactly(CMErrorCode.AUTHORIZATION_ERROR);
	}
}
