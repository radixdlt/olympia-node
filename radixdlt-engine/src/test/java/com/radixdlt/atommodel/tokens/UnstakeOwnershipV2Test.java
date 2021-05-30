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
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnstakeOwnership;
import com.radixdlt.atommodel.system.construction.CreateSystemConstructorV2;
import com.radixdlt.atommodel.system.construction.NextEpochConstructorV2;
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV2;
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.atommodel.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.MintTokenConstructor;
import com.radixdlt.atommodel.tokens.construction.StakeTokensConstructorV2;
import com.radixdlt.atommodel.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.atommodel.tokens.construction.UnstakeTokensConstructorV2;
import com.radixdlt.atommodel.tokens.scrypt.StakingConstraintScryptV3;
import com.radixdlt.atommodel.tokens.scrypt.TokensConstraintScryptV2;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(Parameterized.class)
public class UnstakeOwnershipV2Test {

	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][] {
			{
				List.of(UInt256.TEN),
				UInt256.TEN,
				List.of(
					new SystemConstraintScryptV2(),
					new TokensConstraintScryptV2(),
					new StakingConstraintScryptV3()
				),
				new StakeTokensConstructorV2(),
				new UnstakeTokensConstructorV2()
			},
			{
				List.of(UInt256.FIVE, UInt256.FIVE),
				UInt256.TEN,
				List.of(
					new SystemConstraintScryptV2(),
					new TokensConstraintScryptV2(),
					new StakingConstraintScryptV3()
				),
				new StakeTokensConstructorV2(),
				new UnstakeTokensConstructorV2()
			},
			{
				List.of(UInt256.TEN),
				UInt256.SIX,
				List.of(
					new SystemConstraintScryptV2(),
					new TokensConstraintScryptV2(),
					new StakingConstraintScryptV3()
				),
				new StakeTokensConstructorV2(),
				new UnstakeTokensConstructorV2()
			},
			{
				List.of(UInt256.FIVE, UInt256.FIVE),
				UInt256.SIX,
				List.of(
					new SystemConstraintScryptV2(),
					new TokensConstraintScryptV2(),
					new StakingConstraintScryptV3()
				),
				new StakeTokensConstructorV2(),
				new UnstakeTokensConstructorV2()
			},
		});
	}

	private ECKeyPair key;
	private REAddr accountAddr;
	private RadixEngine<Void> sut;
	private EngineStore<Void> store;
	private final List<UInt256> stakes;
	private final UInt256 totalStakes;
	private final UInt256 unstakeAmt;
	private final List<ConstraintScrypt> scrypts;
	private final ActionConstructor<StakeTokens> stakeTokensConstructor;
	private final ActionConstructor<UnstakeOwnership> unstakeTokensConstructor;

	public UnstakeOwnershipV2Test(
		List<UInt256> stakes,
		UInt256 unstakeAmt,
		List<ConstraintScrypt> scrypts,
		ActionConstructor<StakeTokens> stakeTokensConstructor,
		ActionConstructor<UnstakeOwnership> unstakeTokensConstructor
	) {
		this.stakes = stakes.stream().map(ValidatorStake.MINIMUM_STAKE::multiply).collect(Collectors.toList());
		this.totalStakes = this.stakes.stream().reduce(UInt256::add).orElseThrow();
		this.unstakeAmt = ValidatorStake.MINIMUM_STAKE.multiply(unstakeAmt);
		this.scrypts = scrypts;
		this.stakeTokensConstructor = stakeTokensConstructor;
		this.unstakeTokensConstructor = unstakeTokensConstructor;
	}

	@Before
	public void setup() throws Exception {
		var cmAtomOS = new CMAtomOS();
		scrypts.forEach(cmAtomOS::load);
		var cm = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(cmAtomOS.virtualizedUpParticles())
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.getProcedures())
			.build();
		this.store = new InMemoryEngineStore<>();
		this.sut = new RadixEngine<>(
			ActionConstructors.newBuilder()
				.put(CreateSystem.class, new CreateSystemConstructorV2())
				.put(SystemNextEpoch.class, new NextEpochConstructorV2())
				.put(StakeTokens.class, stakeTokensConstructor)
				.put(UnstakeOwnership.class, unstakeTokensConstructor)
				.put(CreateMutableToken.class, new CreateMutableTokenConstructor())
				.put(MintToken.class, new MintTokenConstructor())
				.put(TransferToken.class, new TransferTokensConstructorV2())
				.build(),
			cm,
			store
		);

		this.key = ECKeyPair.generateNew();
		this.accountAddr = REAddr.ofPubKeyAccount(key.getPublicKey());
		var txn = this.sut.construct(
			List.of(
				new CreateSystem(),
				new CreateMutableToken("xrd", "Name", "", "", ""),
				new MintToken(REAddr.ofNativeToken(), accountAddr, totalStakes)
			)
		).buildWithoutSignature();
		this.sut.execute(List.of(txn), null, PermissionLevel.SYSTEM);
	}

	@Test
	public void unstake_tokens_after_epoch() throws Exception {
		// Arrange
		var stake = this.sut.construct(
			this.stakes.stream().map(amt -> new StakeTokens(accountAddr, key.getPublicKey(), amt)).collect(Collectors.toList())
		).signAndBuild(key::sign);
		this.sut.execute(List.of(stake));
		var nextEpoch = sut.construct(new SystemNextEpoch(u -> List.of(key.getPublicKey()), 1))
			.buildWithoutSignature();
		this.sut.execute(List.of(nextEpoch), null, PermissionLevel.SUPER_USER);

		// Act
		var unstake = this.sut.construct(new UnstakeOwnership(accountAddr, key.getPublicKey(), unstakeAmt))
			.signAndBuild(key::sign);
		var parsed = this.sut.execute(List.of(unstake));
	}

	@Test
	public void cannot_unstake_others_tokens() throws Exception {
		// Arrange
		var stake = this.sut.construct(
			this.stakes.stream().map(amt -> new StakeTokens(accountAddr, key.getPublicKey(), amt)).collect(Collectors.toList())
		).signAndBuild(key::sign);
		this.sut.execute(List.of(stake));
		var nextEpoch = sut.construct(new SystemNextEpoch(u -> List.of(key.getPublicKey()), 1))
			.buildWithoutSignature();
		this.sut.execute(List.of(nextEpoch), null, PermissionLevel.SUPER_USER);

		// Act
		var nextKey = ECKeyPair.generateNew();
		var unstake = this.sut.construct(new UnstakeOwnership(accountAddr, key.getPublicKey(), unstakeAmt))
			.signAndBuild(nextKey::sign);

		assertThatThrownBy(() -> this.sut.execute(List.of(unstake)))
			.isInstanceOf(RadixEngineException.class)
			.extracting("cause.errorCode")
			.containsExactly(CMErrorCode.AUTHORIZATION_ERROR);
	}

	@Test
	public void cant_construct_transfer_with_unstaked_tokens_immediately() throws Exception {
		// Arrange
		var acct2 = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
		var txn = sut.construct(
			this.stakes.stream().map(amt -> new StakeTokens(accountAddr, key.getPublicKey(), amt)).collect(Collectors.toList())
		).signAndBuild(key::sign);
		sut.execute(List.of(txn));
		var nextEpoch = sut.construct(new SystemNextEpoch(u -> List.of(key.getPublicKey()), 1))
			.buildWithoutSignature();
		this.sut.execute(List.of(nextEpoch), null, PermissionLevel.SUPER_USER);
		var unstake = this.sut.construct(new UnstakeOwnership(accountAddr, key.getPublicKey(), unstakeAmt))
			.signAndBuild(key::sign);
		sut.execute(List.of(unstake));
		var nextEpoch2 = sut.construct(new SystemNextEpoch(u -> List.of(key.getPublicKey()), 1))
			.buildWithoutSignature();
		this.sut.execute(List.of(nextEpoch2), null, PermissionLevel.SUPER_USER);

		// Act
		// Assert
		assertThatThrownBy(() -> sut.construct(new TransferToken(REAddr.ofNativeToken(), accountAddr, acct2, unstakeAmt)))
			.isInstanceOf(TxBuilderException.class);
	}

	@Test
	public void cant_spend_unstaked_tokens_immediately() throws Exception {
		// Arrange
		var acct2 = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
		var txn = sut.construct(
			this.stakes.stream().map(amt -> new StakeTokens(accountAddr, key.getPublicKey(), amt)).collect(Collectors.toList())
		).signAndBuild(key::sign);
		sut.execute(List.of(txn));
		var nextEpoch = sut.construct(new SystemNextEpoch(u -> List.of(key.getPublicKey()), 1))
			.buildWithoutSignature();
		this.sut.execute(List.of(nextEpoch), null, PermissionLevel.SUPER_USER);
		var unstake = this.sut.construct(new UnstakeOwnership(accountAddr, key.getPublicKey(), unstakeAmt))
			.signAndBuild(key::sign);
		sut.execute(List.of(unstake));

		var nextEpoch2 = sut.construct(new SystemNextEpoch(u -> List.of(key.getPublicKey()), 1))
			.buildWithoutSignature();
		this.sut.execute(List.of(nextEpoch2), null, PermissionLevel.SUPER_USER);

		// Act
		// Assert
		var txn2 = sut.construct(
			txBuilder ->
				txBuilder.swapFungible(
					TokensParticle.class,
					p -> p.getResourceAddr().equals(REAddr.ofNativeToken())
						&& p.getHoldingAddr().equals(accountAddr),
					amt -> new TokensParticle(acct2, amt, REAddr.ofNativeToken()),
					unstakeAmt,
					"Not enough balance for transfer."
				).with(amt -> new TokensParticle(acct2, amt, REAddr.ofNativeToken()))
		).signAndBuild(key::sign);

		assertThatThrownBy(() -> sut.execute(List.of(txn2)))
			.isInstanceOf(RadixEngineException.class);
	}
}
