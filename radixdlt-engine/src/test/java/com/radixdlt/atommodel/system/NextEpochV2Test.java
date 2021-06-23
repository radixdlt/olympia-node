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
import com.radixdlt.atom.actions.SystemNextView;
import com.radixdlt.atommodel.system.construction.CreateSystemConstructorV2;
import com.radixdlt.atommodel.system.construction.NextEpochConstructorV3;
import com.radixdlt.atommodel.system.construction.NextViewConstructorV3;
import com.radixdlt.atommodel.system.scrypt.EpochUpdateConstraintScrypt;
import com.radixdlt.atommodel.system.scrypt.RoundUpdateConstraintScrypt;
import com.radixdlt.atommodel.system.state.ValidatorStakeData;
import com.radixdlt.atommodel.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.MintTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.StakeTokensConstructorV3;
import com.radixdlt.atommodel.tokens.scrypt.StakingConstraintScryptV4;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.unique.scrypt.UniqueParticleConstraintScrypt;
import com.radixdlt.atommodel.validators.scrypt.ValidatorConstraintScryptV2;
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

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class NextEpochV2Test {
	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][] {
			{
				List.of(
					new RoundUpdateConstraintScrypt(10),
					new EpochUpdateConstraintScrypt(10),
					new StakingConstraintScryptV4(),
					new TokensConstraintScryptV3(),
					new ValidatorConstraintScryptV2()
				),
				ActionConstructors.newBuilder()
					.put(SystemNextView.class, new NextViewConstructorV3())
					.put(SystemNextEpoch.class, new NextEpochConstructorV3())
					.put(CreateSystem.class, new CreateSystemConstructorV2())
					.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
					.put(MintToken.class, new MintTokenConstructor())
					.put(StakeTokens.class, new StakeTokensConstructorV3())
					.build()
			}
		});
	}

	private RadixEngine<Void> sut;
	private EngineStore<Void> store;
	private REParser parser;
	private final List<ConstraintScrypt> scrypts;
	private final ActionConstructors constructors;

	public NextEpochV2Test(
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
		this.parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		var serialization = cmAtomOS.buildSubstateSerialization();
		this.store = new InMemoryEngineStore<>();
		this.sut = new RadixEngine<>(parser, serialization, constructors, cm, store);
	}

	@Test
	public void prepared_stake_should_disappear_on_next_epoch() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew().getPublicKey();
		var accountAddr = REAddr.ofPubKeyAccount(key);
		var start = sut.construct(
			TxnConstructionRequest.create()
				.action(new CreateSystem())
				.action(new CreateMutableToken(null, "xrd", "xrd", "", "", ""))
				.action(new MintToken(REAddr.ofNativeToken(), accountAddr, ValidatorStakeData.MINIMUM_STAKE))
				.action(new StakeTokens(accountAddr, key, ValidatorStakeData.MINIMUM_STAKE))
		).buildWithoutSignature();
		sut.execute(List.of(start), null, PermissionLevel.SYSTEM);

		var request = TxnConstructionRequest.create()
			.action(new SystemNextEpoch(u -> List.of(key), 1));

		// Act
		var txn = sut.construct(request)
			.buildWithoutSignature();
		this.sut.execute(List.of(txn), null, PermissionLevel.SUPER_USER);

		// Assert
		assertThat(store.openIndexedCursor(PreparedStake.class, parser.getSubstateDeserialization())).isEmpty();
	}
}
