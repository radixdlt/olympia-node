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

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.statecomputer.ValidatorSetBuilder;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.UInt256;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides a validator set from the genesis atoms.
 */
public class GenesisValidatorSetFromUniverse implements GenesisValidatorSetProvider {
	private static final Logger log = LogManager.getLogger();
	private final BFTValidatorSet validatorSet;

	public GenesisValidatorSetFromUniverse(
		int minValidators,
		int maxValidators,
		Universe universe,
		RRI nativeToken
	) {
		// No deregistering validators in the genesis atoms
		allParticles(universe, RegisteredValidatorParticle.class, Spin.DOWN)
			.findAny()
			.ifPresent(downRvp -> {
				throw new IllegalStateException("Unexpected deregistration for " + downRvp.getAddress().getPublicKey());
			});
		allParticles(universe, UnregisteredValidatorParticle.class, Spin.UP)
			.findAny()
			.ifPresent(upUvp -> {
				throw new IllegalStateException("Unexpected unregistration for " + upUvp.getAddress().getPublicKey());
			});

		// No unstaking in the genesis atoms
		allParticles(universe, StakedTokensParticle.class, Spin.DOWN)
			.filter(stp -> nativeToken.equals(stp.getTokDefRef()))
			.findAny()
			.ifPresent(stp -> {
				throw new IllegalStateException(
					String.format(
						"Unexpected unstaking by %s for %s of %s",
						stp.getAddress().getPublicKey(), stp.getDelegateAddress().getPublicKey(), stp.getAmount()
					)
				);
			});

		final var registeredValidators = allParticles(universe, RegisteredValidatorParticle.class, Spin.UP)
			.map(RegisteredValidatorParticle::getAddress)
			.collect(ImmutableList.toImmutableList());

		// Check that we have no duplicate registrations (which should not be possible)
		final var uniqueRegisteredValidators = Sets.newHashSet(registeredValidators);
		if (uniqueRegisteredValidators.size() != registeredValidators.size()) {
			final var duplicates = Lists.newArrayList(registeredValidators);
			duplicates.removeIf(uniqueRegisteredValidators::remove);
			throw new IllegalStateException(
				"Duplicate registrations for: " + Collections2.transform(duplicates, RadixAddress::getPublicKey)
			);
		}

		// Collect up validator keys and staked amounts
		final ImmutableMap<ECPublicKey, UInt256> stakedAmounts = allParticles(universe, StakedTokensParticle.class, Spin.UP)
			.filter(stp -> nativeToken.equals(stp.getTokDefRef()))
			.filter(stp -> registeredValidators.contains(stp.getDelegateAddress()))
			.collect(ImmutableMap.toImmutableMap(stp -> stp.getDelegateAddress().getPublicKey(), StakedTokensParticle::getAmount, UInt256::add));

		this.validatorSet = ValidatorSetBuilder.create(minValidators, maxValidators)
			.buildValidatorSet(stakedAmounts);

		if (this.validatorSet == null) {
			throw new IllegalStateException(
				String.format(
					"Could not create validator set with min=%s, max=%s from: %s",
					minValidators, maxValidators, stakedAmounts
				)
			);
		}

		log.info("Genesis validator set is {}", this.validatorSet);
	}

	@Override
	public BFTValidatorSet genesisValidatorSet() {
		return this.validatorSet;
	}

	private <T extends Particle> Stream<T> allParticles(Universe universe, Class<T> type, Spin spin) {
		return universe.getGenesis().stream()
			.flatMap(a -> a.particles(type, spin));
	}
}
