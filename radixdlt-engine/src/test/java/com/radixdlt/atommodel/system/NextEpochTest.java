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

import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atommodel.system.construction.CreateSystemConstructorV1;
import com.radixdlt.atommodel.system.construction.CreateSystemConstructorV2;
import com.radixdlt.atommodel.system.construction.NextEpochConstructorV1;
import com.radixdlt.atommodel.system.construction.NextEpochConstructorV2;
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV1;
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV2;
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.atommodel.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.MintTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.StakeTokensConstructorV1;
import com.radixdlt.atommodel.tokens.construction.StakeTokensConstructorV2;
import com.radixdlt.atommodel.tokens.scrypt.StakingConstraintScryptV2;
import com.radixdlt.atommodel.tokens.scrypt.StakingConstraintScryptV3;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV1;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV2;
import com.radixdlt.atommodel.unique.scrypt.UniqueParticleConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
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

@RunWith(Parameterized.class)
public class NextEpochTest {
	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][] {
			{
				List.of(
					new SystemConstraintScryptV1(),
					new TokensConstraintScryptV1(),
					new StakingConstraintScryptV2()
				),
				ActionConstructors.newBuilder()
					.put(SystemNextEpoch.class, new NextEpochConstructorV1())
					.put(CreateSystem.class, new CreateSystemConstructorV1())
					.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
					.put(MintToken.class, new MintTokenConstructor())
					.put(StakeTokens.class, new StakeTokensConstructorV1())
					.build()
			},
			{
				List.of(
					new SystemConstraintScryptV2(),
					new StakingConstraintScryptV3(),
					new TokensConstraintScryptV2()
				),
				ActionConstructors.newBuilder()
					.put(SystemNextEpoch.class, new NextEpochConstructorV2())
					.put(CreateSystem.class, new CreateSystemConstructorV2())
					.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
					.put(MintToken.class, new MintTokenConstructor())
					.put(StakeTokens.class, new StakeTokensConstructorV2())
					.build()
			}
		});
	}

	private RadixEngine<Void> sut;
	private EngineStore<Void> store;
	private final List<ConstraintScrypt> scrypts;
	private final ActionConstructors constructors;

	public NextEpochTest(
		List<ConstraintScrypt> scrypts,
		ActionConstructors constructors
	) {
		this.scrypts = scrypts;
		this.constructors = constructors;
	}

	@Before
	public void setup() {
		var cmAtomOS = new CMAtomOS();
		scrypts.forEach(cmAtomOS::load);
		cmAtomOS.load(new UniqueParticleConstraintScrypt()); // For v1 start
		var cm = new ConstraintMachine(
			cmAtomOS.virtualizedUpParticles(),
			cmAtomOS.getProcedures()
		);
		var parser = new REParser(cmAtomOS.buildStatelessSubstateVerifier());
		this.store = new InMemoryEngineStore<>();
		this.sut = new RadixEngine<>(parser, constructors, cm, store);
	}

	@Test
	public void no_prepared_stake_next_epoch_should_succeed() throws Exception {
		// Arrange
		var start = sut.construct(new CreateSystem()).buildWithoutSignature();
		sut.execute(List.of(start), null, PermissionLevel.SYSTEM);

		// Act and Assert
		var txn = sut.construct(new SystemNextEpoch(u -> List.of(ECKeyPair.generateNew().getPublicKey()), 1))
			.buildWithoutSignature();
		this.sut.execute(List.of(txn), null, PermissionLevel.SUPER_USER);
	}

	@Test
	public void prepared_stake_next_epoch_should_succeed() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew().getPublicKey();
		var accountAddr = REAddr.ofPubKeyAccount(key);
		var start = sut.construct(
			TxnConstructionRequest.create()
				.action(new CreateSystem())
				.action(new CreateMutableToken("xrd", "xrd", "", "", ""))
				.action(new MintToken(REAddr.ofNativeToken(), accountAddr, ValidatorStake.MINIMUM_STAKE))
				.action(new StakeTokens(accountAddr, key, ValidatorStake.MINIMUM_STAKE))
		).buildWithoutSignature();
		sut.execute(List.of(start), null, PermissionLevel.SYSTEM);

		// Act and Assert
		var txn = sut.construct(new SystemNextEpoch(u -> List.of(ECKeyPair.generateNew().getPublicKey()), 1))
			.buildWithoutSignature();
		this.sut.execute(List.of(txn), null, PermissionLevel.SUPER_USER);
	}
}