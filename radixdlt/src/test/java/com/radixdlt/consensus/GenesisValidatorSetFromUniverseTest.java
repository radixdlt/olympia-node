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

package com.radixdlt.consensus;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.UInt256;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Ensure that validator set is generated correctly from universe / genesis.
 */
public class GenesisValidatorSetFromUniverseTest {

	@Test
	public void when_genesis_has_validator_deregistration__then_fails() {
		final var nativeToken = mock(RRI.class);
		final var validatorKey = ECKeyPair.generateNew().getPublicKey();
		final var genesis = new Atom();
		genesis.addParticleGroupWith(validatorRegistration(validatorKey, 1), Spin.DOWN);
		final var universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(ImmutableList.of(genesis));

		assertThatThrownBy(() -> new GenesisValidatorSetFromUniverse(1, 1, universe, nativeToken))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Unexpected deregistration");
	}

	@Test
	public void when_genesis_has_unstaking__then_fails() {
		final var nativeToken = mock(RRI.class);
		final var validatorKey = ECKeyPair.generateNew().getPublicKey();
		final var genesis = new Atom();
		genesis.addParticleGroupWith(staking(validatorKey, nativeToken, UInt256.ONE), Spin.DOWN);
		final var universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(ImmutableList.of(genesis));

		assertThatThrownBy(() -> new GenesisValidatorSetFromUniverse(1, 1, universe, nativeToken))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Unexpected unstaking");
	}

	@Test
	public void when_genesis_has_duplicate_registration__then_fails() {
		final var nativeToken = mock(RRI.class);
		final var validatorKey = ECKeyPair.generateNew().getPublicKey();
		final var genesis = new Atom();
		genesis.addParticleGroupWith(validatorRegistration(validatorKey, 1), Spin.UP);
		genesis.addParticleGroupWith(validatorRegistration(validatorKey, 1), Spin.UP);
		final var universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(ImmutableList.of(genesis));

		assertThatThrownBy(() -> new GenesisValidatorSetFromUniverse(1, 1, universe, nativeToken))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Duplicate registrations for: " + ImmutableSet.of(validatorKey));
	}

	@Test
	public void when_genesis_has_too_few_registrations__then_fails() {
		final var nativeToken = mock(RRI.class);
		final var genesis = new Atom();
		final var universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(ImmutableList.of(genesis));

		assertThatThrownBy(() -> new GenesisValidatorSetFromUniverse(1, 1, universe, nativeToken))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Could not create validator set");
	}

	@Test
	public void when_genesis_has_registrations_but_no_stake__then_fails() {
		final var nativeToken = mock(RRI.class);
		final var validatorKey = ECKeyPair.generateNew().getPublicKey();
		final var genesis = new Atom();
		genesis.addParticleGroupWith(validatorRegistration(validatorKey, 1), Spin.UP);
		final var universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(ImmutableList.of(genesis));

		assertThatThrownBy(() -> new GenesisValidatorSetFromUniverse(1, 1, universe, nativeToken))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Could not create validator set");
	}

	@Test
	public void when_genesis_has_registration_and_stake__then_validator_set_returned() {
		final var nativeToken = mock(RRI.class);
		final var validatorKey = ECKeyPair.generateNew().getPublicKey();
		final var genesis = new Atom();
		genesis.addParticleGroupWith(validatorRegistration(validatorKey, 1), Spin.UP);
		genesis.addParticleGroupWith(staking(validatorKey, nativeToken, UInt256.ONE), Spin.UP);
		final var universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(ImmutableList.of(genesis));

		final var vsetFromUniverse = new GenesisValidatorSetFromUniverse(1, 1, universe, nativeToken);

		final var vset = vsetFromUniverse.genesisValidatorSet();
		assertThat(vset).isNotNull();
		assertThat(vset.getValidators())
			.containsExactly(BFTValidator.from(BFTNode.create(validatorKey), UInt256.ONE));
	}

	@Test
	public void when_genesis_has_too_many_registrations_and_stake__then_limited_validator_set_returned() {
		final var nativeToken = mock(RRI.class);
		final var validatorKey1 = ECKeyPair.generateNew().getPublicKey();
		final var validatorKey2 = ECKeyPair.generateNew().getPublicKey();
		final var genesis = new Atom();
		genesis.addParticleGroupWith(validatorRegistration(validatorKey1, 1), Spin.UP);
		genesis.addParticleGroupWith(staking(validatorKey1, nativeToken, UInt256.ONE), Spin.UP);
		genesis.addParticleGroupWith(validatorRegistration(validatorKey2, 1), Spin.UP);
		genesis.addParticleGroupWith(staking(validatorKey2, nativeToken, UInt256.TWO), Spin.UP);
		final var universe = mock(Universe.class);
		when(universe.getGenesis()).thenReturn(ImmutableList.of(genesis));

		final var vsetFromUniverse = new GenesisValidatorSetFromUniverse(1, 1, universe, nativeToken);

		final var vset = vsetFromUniverse.genesisValidatorSet();
		assertThat(vset).isNotNull();
		assertThat(vset.getValidators())
			.containsExactly(BFTValidator.from(BFTNode.create(validatorKey2), UInt256.TWO));
	}

	private StakedTokensParticle staking(ECPublicKey delegateKey, RRI token, UInt256 amount) {
		final var address = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		final var delegateAddress = new RadixAddress((byte) 0, delegateKey);
		return new StakedTokensParticle(delegateAddress, address, amount, UInt256.ONE, token, ImmutableMap.of());
	}

	private RegisteredValidatorParticle validatorRegistration(ECPublicKey validatorKey, long nonce) {
		final var address = new RadixAddress((byte) 0, validatorKey);
		return new RegisteredValidatorParticle(address, ImmutableSet.of(), nonce);
	}
}
