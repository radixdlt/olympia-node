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

import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atommodel.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.MintTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.StakeTokensConstructorV1;
import com.radixdlt.atommodel.tokens.construction.StakeTokensConstructorV2;
import com.radixdlt.atommodel.tokens.scrypt.StakingConstraintScryptV2;
import com.radixdlt.atommodel.tokens.scrypt.StakingConstraintScryptV3;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV1;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV2;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(Parameterized.class)
public class StakeTokensTest {

	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][] {
			{
				UInt256.TEN, UInt256.TEN,
				List.of(new TokensConstraintScryptV1(), new StakingConstraintScryptV2()),
				new StakeTokensConstructorV1()
			},
			{
				UInt256.TEN, UInt256.SIX,
				List.of(new TokensConstraintScryptV1(), new StakingConstraintScryptV2()),
				new StakeTokensConstructorV1()
			},
			{
				UInt256.TEN, UInt256.TEN,
				List.of(new TokensConstraintScryptV2(), new StakingConstraintScryptV3()),
				new StakeTokensConstructorV2()
			},
			{
				UInt256.TEN, UInt256.SIX,
				List.of(new TokensConstraintScryptV2(), new StakingConstraintScryptV3()),
				new StakeTokensConstructorV2()
			},
		});
	}

	private RadixEngine<Void> engine;
	private EngineStore<Void> store;
	private final UInt256 startAmt;
	private final UInt256 stakeAmt;
	private final List<ConstraintScrypt> scrypts;
	private final ActionConstructor<StakeTokens> stakeTokensConstructor;

	public StakeTokensTest(
		UInt256 startAmt,
		UInt256 stakeAmt,
		List<ConstraintScrypt> scrypts,
		ActionConstructor<StakeTokens> stakeTokensConstructor
	) {
		this.startAmt = startAmt;
		this.stakeAmt = stakeAmt;
		this.scrypts = scrypts;
		this.stakeTokensConstructor = stakeTokensConstructor;
	}

	@Before
	public void setup() {
		var cmAtomOS = new CMAtomOS();
		scrypts.forEach(cmAtomOS::load);
		var cm = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(cmAtomOS.virtualizedUpParticles())
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.getProcedures())
			.build();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			ActionConstructors.newBuilder()
				.put(StakeTokens.class, stakeTokensConstructor)
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.build(),
			cm,
			store
		);
	}

	@Test
	public void stake_tokens() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();
		var accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
		var tokenAddr = REAddr.ofHashedKey(key.getPublicKey(), "test");
		var txn = this.engine.construct(
			List.of(
				new CreateMutableToken("xrd", "Name", "", "", ""),
				new MintToken(REAddr.ofNativeToken(), accountAddr, startAmt)
			)
		).buildWithoutSignature();
		this.engine.execute(List.of(txn), null, PermissionLevel.SYSTEM);

		// Act
		var transfer = this.engine.construct(new StakeTokens(accountAddr, key.getPublicKey(), stakeAmt))
			.signAndBuild(key::sign);
		var parsed = this.engine.execute(List.of(transfer));
		var action = (StakeTokens) parsed.get(0).getActions().get(0).getTxAction();
		assertThat(action.amount()).isEqualTo(stakeAmt);
		assertThat(action.from()).isEqualTo(accountAddr);
		assertThat(action.to()).isEqualTo(key.getPublicKey());
	}

	@Test
	public void cannot_stake_others_tokens() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();
		var accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
		var tokenAddr = REAddr.ofHashedKey(key.getPublicKey(), "test");
		var txn = this.engine.construct(
			List.of(
				new CreateMutableToken("xrd", "Name", "", "", ""),
				new MintToken(REAddr.ofNativeToken(), accountAddr, startAmt)
			)
		).buildWithoutSignature();
		this.engine.execute(List.of(txn), null, PermissionLevel.SYSTEM);

		// Act
		var nextKey = ECKeyPair.generateNew();
		var to = REAddr.ofPubKeyAccount(nextKey.getPublicKey());
		var transfer = this.engine.construct(new StakeTokens(accountAddr, key.getPublicKey(), stakeAmt))
			.signAndBuild(nextKey::sign);
		assertThatThrownBy(() -> this.engine.execute(List.of(transfer)))
			.isInstanceOf(RadixEngineException.class)
			.extracting("cause.error.errorCode")
			.containsExactly(CMErrorCode.AUTHORIZATION_ERROR);
	}
}
