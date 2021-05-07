/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.engine;

import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxActionListBuilder;
import com.radixdlt.atom.actions.MoveStake;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.DeprecatedUnstakeTokens;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atommodel.system.SystemConstraintScrypt;
import com.radixdlt.atommodel.tokens.StakingConstraintScryptV1;
import com.radixdlt.atommodel.tokens.StakingConstraintScryptV2;
import com.radixdlt.constraintmachine.PermissionLevel;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;

import java.util.List;

public class StakedTokensTest {
	private RadixEngine<Void> engine;
	private EngineStore<Void> store;
	private REAddr tokenRri;
	private ECKeyPair tokenOwnerKeyPair = ECKeyPair.generateNew();
	private REAddr tokenOwnerAccount = REAddr.ofPubKeyAccount(tokenOwnerKeyPair.getPublicKey());
	private ECKeyPair validatorKeyPair = ECKeyPair.generateNew();

	@Before
	public void setup() throws Exception {
		this.tokenRri = REAddr.ofNativeToken();

		final var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new SystemConstraintScrypt());
		cmAtomOS.load(new ValidatorConstraintScrypt());
		cmAtomOS.load(new TokensConstraintScrypt());
		cmAtomOS.load(new StakingConstraintScryptV2());
		final var cm = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(cmAtomOS.virtualizedUpParticles())
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures())
			.build();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(cm, this.store);

		var tokDef = new MutableTokenDefinition(
			"xrd",
			"Test",
			"description",
			null,
			null
		);
		var txn0 = engine.construct(
			TxActionListBuilder.create()
				.createMutableToken(tokDef)
				.mint(this.tokenRri, tokenOwnerAccount, UInt256.TEN)
				.registerAsValidator(this.validatorKeyPair.getPublicKey())
				.build()
		).buildWithoutSignature();

		this.engine.execute(List.of(txn0), null, PermissionLevel.SYSTEM);
	}

	@Test
	public void stake_tokens() throws Exception {
		var txn = engine.construct(
			new StakeTokens(this.tokenOwnerAccount, this.validatorKeyPair.getPublicKey(), UInt256.TEN)
		).signAndBuild(this.tokenOwnerKeyPair::sign);

		this.engine.execute(List.of(txn));
	}

	@Test
	public void unstake_tokens() throws Exception {
		var txn = engine.construct(
			new StakeTokens(this.tokenOwnerAccount, this.validatorKeyPair.getPublicKey(), UInt256.TEN)
		).signAndBuild(this.tokenOwnerKeyPair::sign);
		this.engine.execute(List.of(txn));

		var txn2 = engine.construct(
			new UnstakeTokens(this.tokenOwnerAccount, this.validatorKeyPair.getPublicKey(), UInt256.TEN)
		).signAndBuild(this.tokenOwnerKeyPair::sign);
		this.engine.execute(List.of(txn2));
	}

	@Test
	public void unstake_partial_tokens() throws Exception {
		var txn = engine.construct(
			new StakeTokens(this.tokenOwnerAccount, this.validatorKeyPair.getPublicKey(), UInt256.TEN)
		).signAndBuild(this.tokenOwnerKeyPair::sign);
		this.engine.execute(List.of(txn));

		var txn2 = engine.construct(
			new UnstakeTokens(this.tokenOwnerAccount, this.validatorKeyPair.getPublicKey(), UInt256.SEVEN)
		).signAndBuild(this.tokenOwnerKeyPair::sign);
		this.engine.execute(List.of(txn2));
	}

	@Test
	public void move_staked_tokens() throws Exception {
		var txn = this.engine.construct(
			new StakeTokens(this.tokenOwnerAccount, this.validatorKeyPair.getPublicKey(), UInt256.TEN)
		).signAndBuild(this.tokenOwnerKeyPair::sign);
		this.engine.execute(List.of(txn));

		var atom2 = this.engine.construct(
			new MoveStake(
				this.tokenOwnerAccount,
				validatorKeyPair.getPublicKey(),
				ECKeyPair.generateNew().getPublicKey(),
				UInt256.FIVE
			)
		).signAndBuild(this.tokenOwnerKeyPair::sign);
		this.engine.execute(List.of(atom2));
	}
}
