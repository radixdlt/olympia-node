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
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atommodel.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.MintTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.StakeTokensConstructorV1;
import com.radixdlt.atommodel.tokens.construction.UnstakeTokensConstructorV1;
import com.radixdlt.atommodel.tokens.scrypt.StakingConstraintScryptV2;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV1;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
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
import com.radixdlt.utils.UInt256;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(Parameterized.class)
public class UnstakeTokensV1Test {

	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][] {
			{
				UInt256.TEN, UInt256.TEN,
				List.of(new TokensConstraintScryptV1(), new StakingConstraintScryptV2()),
				new StakeTokensConstructorV1(),
				new UnstakeTokensConstructorV1()
			},
			{
				UInt256.TEN, UInt256.SIX,
				List.of(new TokensConstraintScryptV1(), new StakingConstraintScryptV2()),
				new StakeTokensConstructorV1(),
				new UnstakeTokensConstructorV1()
			}
		});
	}

	private RadixEngine<Void> engine;
	private EngineStore<Void> store;
	private final UInt256 startAmt;
	private final UInt256 unstakeAmt;
	private final List<ConstraintScrypt> scrypts;
	private final ActionConstructor<StakeTokens> stakeTokensConstructor;
	private final ActionConstructor<UnstakeTokens> unstakeTokensConstructor;

	public UnstakeTokensV1Test(
		UInt256 startAmt,
		UInt256 unstakeAmt,
		List<ConstraintScrypt> scrypts,
		ActionConstructor<StakeTokens> stakeTokensConstructor,
		ActionConstructor<UnstakeTokens> unstakeTokensConstructor
	) {
		this.startAmt = startAmt;
		this.unstakeAmt = unstakeAmt;
		this.scrypts = scrypts;
		this.stakeTokensConstructor = stakeTokensConstructor;
		this.unstakeTokensConstructor = unstakeTokensConstructor;
	}

	@Before
	public void setup() {
		var cmAtomOS = new CMAtomOS();
		scrypts.forEach(cmAtomOS::load);
		var cm = new ConstraintMachine(
			cmAtomOS.virtualizedUpParticles(),
			cmAtomOS.getProcedures()
		);
		var parser = new REParser(cmAtomOS.buildStatelessSubstateVerifier());
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			parser,
			ActionConstructors.newBuilder()
				.put(StakeTokens.class, stakeTokensConstructor)
				.put(UnstakeTokens.class, unstakeTokensConstructor)
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.build(),
			cm,
			store
		);
	}

	@Test
	public void unstake_tokens() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();
		var accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
		var txn = this.engine.construct(
			List.of(
				new CreateMutableToken("xrd", "Name", "", "", ""),
				new MintToken(REAddr.ofNativeToken(), accountAddr, startAmt)
			)
		).buildWithoutSignature();
		this.engine.execute(List.of(txn), null, PermissionLevel.SYSTEM);
		var stake = this.engine.construct(new StakeTokens(accountAddr, key.getPublicKey(), startAmt))
			.signAndBuild(key::sign);
		this.engine.execute(List.of(stake));

		// Act
		var unstake = this.engine.construct(new UnstakeTokens(accountAddr, key.getPublicKey(), unstakeAmt))
			.signAndBuild(key::sign);
		var processed = this.engine.execute(List.of(unstake));
	}

	@Test
	public void cannot_burn_stake() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();
		var accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
		var txn = this.engine.construct(
			List.of(
				new CreateMutableToken("xrd", "Name", "", "", ""),
				new MintToken(REAddr.ofNativeToken(), accountAddr, startAmt)
			)
		).buildWithoutSignature();
		this.engine.execute(List.of(txn), null, PermissionLevel.SYSTEM);
		var stake = this.engine.construct(new StakeTokens(accountAddr, key.getPublicKey(), unstakeAmt))
			.signAndBuild(key::sign);
		this.engine.execute(List.of(stake));

		// Arrange
		var burnStake = this.engine.construct(txBuilder -> {
			txBuilder.down(
				PreparedStake.class,
				d -> d.getOwner().equals(accountAddr),
				Optional.empty(),
				""
			);
			txBuilder.end();
		}).signAndBuild(key::sign);

		// Act and Assert
		assertThatThrownBy(() -> this.engine.execute(List.of(burnStake)))
			.isInstanceOf(RadixEngineException.class);
	}

	@Test
	public void cannot_unstake_others_tokens() throws Exception {
		// Arrange
		var key = ECKeyPair.generateNew();
		var accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
		var txn = this.engine.construct(
			List.of(
				new CreateMutableToken("xrd", "Name", "", "", ""),
				new MintToken(REAddr.ofNativeToken(), accountAddr, startAmt)
			)
		).buildWithoutSignature();
		this.engine.execute(List.of(txn), null, PermissionLevel.SYSTEM);
		var stake = this.engine.construct(new StakeTokens(accountAddr, key.getPublicKey(), startAmt))
			.signAndBuild(key::sign);
		this.engine.execute(List.of(stake));

		// Act
		var nextKey = ECKeyPair.generateNew();
		var unstake = this.engine.construct(new UnstakeTokens(accountAddr, key.getPublicKey(), unstakeAmt))
			.signAndBuild(nextKey::sign);

		assertThatThrownBy(() -> this.engine.execute(List.of(unstake)))
			.isInstanceOf(RadixEngineException.class)
			.extracting("cause.errorCode")
			.containsExactly(CMErrorCode.AUTHORIZATION_ERROR);
	}
}
