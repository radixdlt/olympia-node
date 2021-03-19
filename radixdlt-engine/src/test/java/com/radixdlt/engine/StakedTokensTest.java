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

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TokensConstraintScrypt;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.atommodel.validators.ValidatorConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import com.radixdlt.utils.UInt256;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StakedTokensTest {
	private static final byte MAGIC = (byte) 0;
	private RadixEngine<RadixEngineAtom> engine;
	private EngineStore<RadixEngineAtom> store;
	private RRI tokenRri;
	private ECKeyPair tokenOwnerKeyPair = ECKeyPair.generateNew();
	private RadixAddress tokenOwnerAddress = new RadixAddress(MAGIC, this.tokenOwnerKeyPair.getPublicKey());
	private ECKeyPair validatorKeyPair = ECKeyPair.generateNew();
	private RadixAddress validatorAddress = new RadixAddress(MAGIC, this.validatorKeyPair.getPublicKey());

	private TransferrableTokensParticle transferrableTokensParticle;

	@Before
	public void setup() throws RadixEngineException {
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
			cmAtomOS.buildVirtualLayer(),
			this.store
		);

		this.tokenRri = RRI.of(this.tokenOwnerAddress, "TEST");
		final var rriParticle = new RRIParticle(this.tokenRri);
		final var tokenDefinitionParticle = new FixedSupplyTokenDefinitionParticle(
			this.tokenRri,
			"TEST",
			"description",
			UInt256.TEN,
			UInt256.ONE,
			null,
			null
		);
		this.transferrableTokensParticle = transferrableTokens(UInt256.TEN);
		ImmutableList<CMMicroInstruction> instructions = ImmutableList.of(
			CMMicroInstruction.checkSpinAndPush(rriParticle, Spin.UP),
			CMMicroInstruction.checkSpinAndPush(tokenDefinitionParticle, Spin.NEUTRAL),
			CMMicroInstruction.checkSpinAndPush(this.transferrableTokensParticle, Spin.NEUTRAL),
			CMMicroInstruction.particleGroup(),
			CMMicroInstruction.checkSpinAndPush(unregisterValidator(0), Spin.UP),
			CMMicroInstruction.checkSpinAndPush(registerValidator(1), Spin.NEUTRAL),
			CMMicroInstruction.particleGroup()
		);
		final var instruction = new CMInstruction(
			instructions,
			ImmutableMap.of(
				this.tokenOwnerKeyPair.euid(), this.tokenOwnerKeyPair.sign(HashUtils.zero256()),
				this.validatorKeyPair.euid(), this.validatorKeyPair.sign(HashUtils.zero256())
			)
		);
		this.engine.execute(new BaseAtom(instruction, HashUtils.zero256()));
	}

	@Test
	public void stake_tokens() throws RadixEngineException {
		final var stakeParticle = stakedTokens(this.transferrableTokensParticle.getAmount(), this.tokenOwnerAddress);
		final var instructions = ImmutableList.of(
			CMMicroInstruction.checkSpinAndPush(registerValidator(1), Spin.UP),
			CMMicroInstruction.checkSpinAndPush(registerValidator(2), Spin.NEUTRAL),
			CMMicroInstruction.checkSpinAndPush(stakeParticle, Spin.NEUTRAL),
			CMMicroInstruction.checkSpinAndPush(this.transferrableTokensParticle, Spin.UP),
			CMMicroInstruction.particleGroup()
		);
		final var instruction = new CMInstruction(
			instructions,
			ImmutableMap.of(this.tokenOwnerKeyPair.euid(), this.tokenOwnerKeyPair.sign(HashUtils.zero256()))
		);

		this.engine.execute(new BaseAtom(instruction, HashUtils.zero256()));

		assertThat(this.store.getSpin(this.transferrableTokensParticle)).isEqualTo(Spin.DOWN);
		assertThat(this.store.getSpin(stakeParticle)).isEqualTo(Spin.UP);
	}

	@Test
	public void unstake_tokens() throws RadixEngineException {
		final var stakeParticle = stakedTokens(this.transferrableTokensParticle.getAmount(), this.tokenOwnerAddress);
		final var instructions = ImmutableList.of(
			CMMicroInstruction.checkSpinAndPush(registerValidator(1), Spin.UP),
			CMMicroInstruction.checkSpinAndPush(registerValidator(2), Spin.NEUTRAL),
			CMMicroInstruction.checkSpinAndPush(stakeParticle, Spin.NEUTRAL),
			CMMicroInstruction.checkSpinAndPush(this.transferrableTokensParticle, Spin.UP),
			CMMicroInstruction.particleGroup()
		);
		final var instruction = new CMInstruction(
			instructions,
			ImmutableMap.of(this.tokenOwnerKeyPair.euid(), this.tokenOwnerKeyPair.sign(HashUtils.zero256()))
		);
		this.engine.execute(new BaseAtom(instruction, HashUtils.zero256()));
		final var tranferrableParticle = transferrableTokens(UInt256.TEN);
		ImmutableList<CMMicroInstruction> instructions2 = ImmutableList.of(
			CMMicroInstruction.checkSpinAndPush(stakeParticle, Spin.UP),
			CMMicroInstruction.checkSpinAndPush(tranferrableParticle, Spin.NEUTRAL),
			CMMicroInstruction.particleGroup()
		);
		CMInstruction instruction2 = new CMInstruction(
			instructions2,
			ImmutableMap.of(this.tokenOwnerKeyPair.euid(), this.tokenOwnerKeyPair.sign(HashUtils.zero256()))
		);

		this.engine.execute(new BaseAtom(instruction2, HashUtils.zero256()));

		assertThat(this.store.getSpin(tranferrableParticle)).isEqualTo(Spin.UP);
		assertThat(this.store.getSpin(stakeParticle)).isEqualTo(Spin.DOWN);
	}

	@Test
	public void unstake_partial_tokens() throws RadixEngineException {
		final var stakeParticle = stakedTokens(this.transferrableTokensParticle.getAmount(), this.tokenOwnerAddress);
		final var instructions = ImmutableList.of(
			CMMicroInstruction.checkSpinAndPush(registerValidator(1), Spin.UP),
			CMMicroInstruction.checkSpinAndPush(registerValidator(2), Spin.NEUTRAL),
			CMMicroInstruction.checkSpinAndPush(stakeParticle, Spin.NEUTRAL),
			CMMicroInstruction.checkSpinAndPush(this.transferrableTokensParticle, Spin.UP),
			CMMicroInstruction.particleGroup()
		);
		final var instruction = new CMInstruction(
			instructions,
			ImmutableMap.of(this.tokenOwnerKeyPair.euid(), this.tokenOwnerKeyPair.sign(HashUtils.zero256()))
		);
		this.engine.execute(new BaseAtom(instruction, HashUtils.zero256()));
		final var tranferrableParticle = transferrableTokens(UInt256.THREE);
		final var partialStakeParticle = stakedTokens(UInt256.SEVEN, this.tokenOwnerAddress);
		ImmutableList<CMMicroInstruction> instructions2 = ImmutableList.of(
			CMMicroInstruction.checkSpinAndPush(stakeParticle, Spin.UP),
			CMMicroInstruction.checkSpinAndPush(partialStakeParticle, Spin.NEUTRAL),
			CMMicroInstruction.checkSpinAndPush(tranferrableParticle, Spin.NEUTRAL),
			CMMicroInstruction.particleGroup()
		);
		CMInstruction instruction2 = new CMInstruction(
			instructions2,
			ImmutableMap.of(this.tokenOwnerKeyPair.euid(), this.tokenOwnerKeyPair.sign(HashUtils.zero256()))
		);

		this.engine.execute(new BaseAtom(instruction2, HashUtils.zero256()));

		assertThat(this.store.getSpin(tranferrableParticle)).isEqualTo(Spin.UP);
		assertThat(this.store.getSpin(partialStakeParticle)).isEqualTo(Spin.UP);
		assertThat(this.store.getSpin(stakeParticle)).isEqualTo(Spin.DOWN);
	}

	@Test
	public void move_staked_tokens() throws RadixEngineException {
		final var stakeParticle = stakedTokens(this.transferrableTokensParticle.getAmount(), this.tokenOwnerAddress);
		final var instructions = ImmutableList.of(
			CMMicroInstruction.checkSpinAndPush(registerValidator(1), Spin.UP),
			CMMicroInstruction.checkSpinAndPush(registerValidator(2), Spin.NEUTRAL),
			CMMicroInstruction.checkSpinAndPush(stakeParticle, Spin.NEUTRAL),
			CMMicroInstruction.checkSpinAndPush(this.transferrableTokensParticle, Spin.UP),
			CMMicroInstruction.particleGroup()
		);
		final var instruction = new CMInstruction(
			instructions,
			ImmutableMap.of(this.tokenOwnerKeyPair.euid(), this.tokenOwnerKeyPair.sign(HashUtils.zero256()))
		);
		this.engine.execute(new BaseAtom(instruction, HashUtils.zero256()));
		final var restakeParticle = stakedTokens(UInt256.TEN, newAddress());
		ImmutableList<CMMicroInstruction> instructions2 = ImmutableList.of(
			CMMicroInstruction.checkSpinAndPush(stakeParticle, Spin.UP),
			CMMicroInstruction.checkSpinAndPush(restakeParticle, Spin.NEUTRAL),
			CMMicroInstruction.particleGroup()
		);
		CMInstruction instruction2 = new CMInstruction(
			instructions2,
			ImmutableMap.of(this.tokenOwnerKeyPair.euid(), this.tokenOwnerKeyPair.sign(HashUtils.zero256()))
		);

		assertThatThrownBy(() -> this.engine.execute(new BaseAtom(instruction2, HashUtils.zero256())))
			.isInstanceOf(RadixEngineException.class)
			.hasMessageContaining("Can't send staked tokens");
	}

	private UnregisteredValidatorParticle unregisterValidator(long nonce) {
		return new UnregisteredValidatorParticle(this.validatorAddress, nonce);
	}

	private RegisteredValidatorParticle registerValidator(long nonce) {
		return new RegisteredValidatorParticle(this.validatorAddress, ImmutableSet.of(), nonce);
	}

	private TransferrableTokensParticle transferrableTokens(UInt256 amount) {
		return new TransferrableTokensParticle(this.tokenOwnerAddress, amount, UInt256.ONE, this.tokenRri, ImmutableMap.of());
	}

	private StakedTokensParticle stakedTokens(UInt256 amount, RadixAddress ownerAddress) {
		return new StakedTokensParticle(this.validatorAddress, ownerAddress, amount, UInt256.ONE, this.tokenRri, ImmutableMap.of());
	}

	private RadixAddress newAddress() {
		return new RadixAddress(MAGIC, ECKeyPair.generateNew().getPublicKey());
	}
}
