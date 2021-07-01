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

import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.NextEpoch;
import com.radixdlt.atom.actions.NextRound;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnstakeOwnership;
import com.radixdlt.application.system.construction.CreateSystemConstructorV2;
import com.radixdlt.application.system.construction.NextEpochConstructorV3;
import com.radixdlt.application.system.construction.NextViewConstructorV3;
import com.radixdlt.application.system.scrypt.EpochUpdateConstraintScrypt;
import com.radixdlt.application.system.scrypt.RoundUpdateConstraintScrypt;
import com.radixdlt.application.tokens.construction.CreateMutableTokenConstructor;
import com.radixdlt.application.tokens.construction.MintTokenConstructor;
import com.radixdlt.application.tokens.construction.StakeTokensConstructorV3;
import com.radixdlt.application.tokens.construction.TransferTokensConstructorV2;
import com.radixdlt.application.tokens.construction.UnstakeOwnershipConstructor;
import com.radixdlt.application.tokens.scrypt.StakingConstraintScryptV4;
import com.radixdlt.application.tokens.scrypt.TokensConstraintScryptV3;
import com.radixdlt.application.validators.scrypt.ValidatorConstraintScrypt;
import com.radixdlt.application.validators.scrypt.ValidatorRegisterConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.ConstraintScrypt;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
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

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(Parameterized.class)
public class UnstakeTokensV2Test {

	@Parameterized.Parameters
	public static Collection<Object[]> parameters() {
		return List.of(new Object[][] {
			{
				List.of(10),
				10,
				List.of(
					new RoundUpdateConstraintScrypt(10),
					new EpochUpdateConstraintScrypt(10, Amount.ofTokens(10).toSubunits(), 9800, 1),
					new TokensConstraintScryptV3(),
					new StakingConstraintScryptV4(Amount.ofTokens(10).toSubunits()),
					new ValidatorConstraintScrypt(2),
					new ValidatorRegisterConstraintScrypt()
				),
				new StakeTokensConstructorV3(Amount.ofTokens(10).toSubunits()),
				new UnstakeOwnershipConstructor()
			},
			{
				List.of(5, 5),
				10,
				List.of(
					new RoundUpdateConstraintScrypt(10),
					new EpochUpdateConstraintScrypt(10, Amount.ofTokens(10).toSubunits(), 9800, 1),
					new TokensConstraintScryptV3(),
					new StakingConstraintScryptV4(Amount.ofTokens(10).toSubunits()),
					new ValidatorConstraintScrypt(2),
					new ValidatorRegisterConstraintScrypt()
				),
				new StakeTokensConstructorV3(Amount.ofTokens(10).toSubunits()),
				new UnstakeOwnershipConstructor()
			},
			{
				List.of(10),
				6,
				List.of(
					new RoundUpdateConstraintScrypt(10),
					new EpochUpdateConstraintScrypt(10, Amount.ofTokens(10).toSubunits(), 9800, 1),
					new TokensConstraintScryptV3(),
					new StakingConstraintScryptV4(Amount.ofTokens(10).toSubunits()),
					new ValidatorConstraintScrypt(2),
					new ValidatorRegisterConstraintScrypt()
				),
				new StakeTokensConstructorV3(Amount.ofTokens(10).toSubunits()),
				new UnstakeOwnershipConstructor()
			},
			{
				List.of(5, 5),
				6,
				List.of(
					new RoundUpdateConstraintScrypt(10),
					new EpochUpdateConstraintScrypt(10, Amount.ofTokens(10).toSubunits(), 9800, 1),
					new TokensConstraintScryptV3(),
					new StakingConstraintScryptV4(Amount.ofTokens(10).toSubunits()),
					new ValidatorConstraintScrypt(2),
					new ValidatorRegisterConstraintScrypt()
				),
				new StakeTokensConstructorV3(Amount.ofTokens(10).toSubunits()),
				new UnstakeOwnershipConstructor()
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

	public UnstakeTokensV2Test(
		List<Integer> stakes,
		int unstakeAmt,
		List<ConstraintScrypt> scrypts,
		ActionConstructor<StakeTokens> stakeTokensConstructor,
		ActionConstructor<UnstakeOwnership> unstakeTokensConstructor
	) {
		this.stakes = stakes.stream().map(i -> Amount.ofTokens(i * 10).toSubunits()).collect(Collectors.toList());
		this.totalStakes = this.stakes.stream().reduce(UInt256::add).orElseThrow();
		this.unstakeAmt = Amount.ofTokens(unstakeAmt * 10).toSubunits();
		this.scrypts = scrypts;
		this.stakeTokensConstructor = stakeTokensConstructor;
		this.unstakeTokensConstructor = unstakeTokensConstructor;
	}

	@Before
	public void setup() throws Exception {
		var cmAtomOS = new CMAtomOS();
		scrypts.forEach(cmAtomOS::load);
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
				.put(CreateSystem.class, new CreateSystemConstructorV2())
				.put(NextRound.class, new NextViewConstructorV3())
				.put(NextEpoch.class, new NextEpochConstructorV3(Amount.ofTokens(10).toSubunits(), 9800, 1))
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
			TxnConstructionRequest.create()
				.action(new CreateSystem(0))
				.action(new CreateMutableToken(null, "xrd", "Name", "", "", ""))
				.action(new MintToken(REAddr.ofNativeToken(), accountAddr, totalStakes))
		).buildWithoutSignature();
		this.sut.execute(List.of(txn), null, PermissionLevel.SYSTEM);
	}

	@Test
	public void unstake_tokens_after_epoch() throws Exception {
		// Arrange
		var stakeActions = this.stakes.stream()
			.map(amt -> new StakeTokens(accountAddr, key.getPublicKey(), amt))
			.collect(Collectors.<TxAction>toList());
		var stake = this.sut.construct(
			TxnConstructionRequest.create().actions(stakeActions)
		).signAndBuild(key::sign);
		this.sut.execute(List.of(stake));
		var nextEpoch = sut.construct(new NextEpoch(u -> List.of(key.getPublicKey()), 1))
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
		var stakeActions = this.stakes.stream()
			.map(amt -> new StakeTokens(accountAddr, key.getPublicKey(), amt))
			.collect(Collectors.<TxAction>toList());
		var stake = this.sut.construct(
			TxnConstructionRequest.create().actions(stakeActions)
		).signAndBuild(key::sign);
		this.sut.execute(List.of(stake));
		var nextEpoch = sut.construct(new NextEpoch(u -> List.of(key.getPublicKey()), 1))
			.buildWithoutSignature();
		this.sut.execute(List.of(nextEpoch), null, PermissionLevel.SUPER_USER);

		// Act
		var nextKey = ECKeyPair.generateNew();
		var unstake = this.sut.construct(new UnstakeOwnership(accountAddr, key.getPublicKey(), unstakeAmt))
			.signAndBuild(nextKey::sign);

		assertThatThrownBy(() -> this.sut.execute(List.of(unstake)))
			.hasRootCauseInstanceOf(AuthorizationException.class);
	}

	@Test
	public void cant_construct_transfer_with_unstaked_tokens_immediately() throws Exception {
		// Arrange
		var acct2 = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
		var stakeActions = this.stakes.stream()
			.map(amt -> new StakeTokens(accountAddr, key.getPublicKey(), amt))
			.collect(Collectors.<TxAction>toList());
		var txn = sut.construct(
			TxnConstructionRequest.create().actions(stakeActions)
		).signAndBuild(key::sign);
		sut.execute(List.of(txn));
		var nextEpoch = sut.construct(new NextEpoch(u -> List.of(key.getPublicKey()), 1))
			.buildWithoutSignature();
		this.sut.execute(List.of(nextEpoch), null, PermissionLevel.SUPER_USER);
		var unstake = this.sut.construct(new UnstakeOwnership(accountAddr, key.getPublicKey(), unstakeAmt))
			.signAndBuild(key::sign);
		sut.execute(List.of(unstake));
		var request = TxnConstructionRequest.create()
			.action(new NextRound(10, true, 1, u -> key.getPublicKey()))
			.action(new NextEpoch(u -> List.of(key.getPublicKey()), 1));
		var nextEpoch2 = sut.construct(request).buildWithoutSignature();
		this.sut.execute(List.of(nextEpoch2), null, PermissionLevel.SUPER_USER);

		// Act
		// Assert
		assertThatThrownBy(() -> sut.construct(new TransferToken(REAddr.ofNativeToken(), accountAddr, acct2, unstakeAmt)))
			.isInstanceOf(TxBuilderException.class);
	}
}
