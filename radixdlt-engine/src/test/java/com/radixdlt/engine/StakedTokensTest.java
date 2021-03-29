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
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.constraintmachine.Particle;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;

import java.util.ArrayList;
import java.util.List;

public class StakedTokensTest {
	private static final byte MAGIC = (byte) 0;
	private RadixEngine<Void> engine;
	private EngineStore<Void> store;
	private RRI tokenRri;
	private ECKeyPair tokenOwnerKeyPair = ECKeyPair.generateNew();
	private RadixAddress tokenOwnerAddress = new RadixAddress(MAGIC, this.tokenOwnerKeyPair.getPublicKey());
	private ECKeyPair validatorKeyPair = ECKeyPair.generateNew();
	private RadixAddress validatorAddress = new RadixAddress(MAGIC, this.validatorKeyPair.getPublicKey());
	private List<Particle> upParticles = new ArrayList<>();

	@Before
	public void setup() throws Exception {
		final var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new ValidatorConstraintScrypt());
		cmAtomOS.load(new TokensConstraintScrypt());
		final var cm = new ConstraintMachine.Builder()
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures())
			.build();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(
			cm,
			cmAtomOS.virtualizedUpParticles(),
			this.store
		);

		this.tokenRri = RRI.of(this.tokenOwnerAddress, "TEST");
		var tokDef = new MutableTokenDefinition(
			"TEST",
			"Test",
			"description",
			null,
			null,
			ImmutableMap.of(
				MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
				MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
			)
		);
		var tokDefBuilder = TxBuilder.newBuilder(this.tokenOwnerAddress)
			.createMutableToken(tokDef)
			.mint(this.tokenRri, this.tokenOwnerAddress, UInt256.TEN);
		var atom0 = tokDefBuilder.signAndBuild(this.tokenOwnerKeyPair::sign);
		tokDefBuilder.upParticles().forEach(upParticles::add);

		var validatorBuilder = TxBuilder.newBuilder(this.validatorAddress)
			.registerAsValidator();
		var atom1 = validatorBuilder.signAndBuild(this.validatorKeyPair::sign);
		validatorBuilder.upParticles().forEach(upParticles::add);

		this.engine.execute(List.of(atom0, atom1));
	}

	@Test
	public void stake_tokens() throws Exception {
		var atom = TxBuilder.newBuilder(this.tokenOwnerAddress, upParticles)
			.stakeTo(this.tokenRri, this.validatorAddress, UInt256.TEN)
			.signAndBuild(this.tokenOwnerKeyPair::sign);
		this.engine.execute(List.of(atom));
	}

	@Test
	public void unstake_tokens() throws Exception {
		var builder = TxBuilder.newBuilder(this.tokenOwnerAddress, upParticles)
			.stakeTo(this.tokenRri, this.validatorAddress, UInt256.TEN);
		var atom = builder.signAndBuild(this.tokenOwnerKeyPair::sign);
		this.engine.execute(List.of(atom));

		var atom2 = TxBuilder.newBuilder(this.tokenOwnerAddress, builder.upParticles())
			.unstakeFrom(this.tokenRri, this.validatorAddress, UInt256.TEN)
			.signAndBuild(this.tokenOwnerKeyPair::sign);

		this.engine.execute(List.of(atom2));
	}

	@Test
	public void unstake_partial_tokens() throws Exception {
		var builder = TxBuilder.newBuilder(this.tokenOwnerAddress, upParticles)
			.stakeTo(this.tokenRri, this.validatorAddress, UInt256.TEN);
		var atom = builder.signAndBuild(this.tokenOwnerKeyPair::sign);
		this.engine.execute(List.of(atom));

		var atom2 = TxBuilder.newBuilder(this.tokenOwnerAddress, builder.upParticles())
			.unstakeFrom(this.tokenRri, this.validatorAddress, UInt256.SEVEN)
			.signAndBuild(this.tokenOwnerKeyPair::sign);

		this.engine.execute(List.of(atom2));
	}

	@Test
	public void move_staked_tokens() throws Exception {
		var builder = TxBuilder.newBuilder(this.tokenOwnerAddress, upParticles)
			.stakeTo(this.tokenRri, this.validatorAddress, UInt256.TEN);
		var atom = builder.signAndBuild(this.tokenOwnerKeyPair::sign);
		this.engine.execute(List.of(atom));

		var atom2 = TxBuilder.newBuilder(this.tokenOwnerAddress, builder.upParticles())
			.moveStake(this.tokenRri, this.validatorAddress, newAddress(), UInt256.SEVEN)
			.signAndBuild(this.tokenOwnerKeyPair::sign);

		this.engine.execute(List.of(atom2));
	}

	private RadixAddress newAddress() {
		return new RadixAddress(MAGIC, ECKeyPair.generateNew().getPublicKey());
	}
}
