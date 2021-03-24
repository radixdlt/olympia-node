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
 */

package com.radixdlt.statecomputer.checkpoint;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.radixdlt.atom.AtomBuilder;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokDefParticleFactory;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import org.radix.StakeDelegation;
import org.radix.TokenIssuance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utilities for creating particles for genesis + checkpoints.
 */
public final class CheckpointUtils {
	private CheckpointUtils() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static void createEpochUpdate(AtomBuilder atomBuilder) {
		atomBuilder.virtualSpinDown(new SystemParticle(0, 0, 0));
		atomBuilder.spinUp(new SystemParticle(1, 0, 0));
		atomBuilder.particleGroup();
	}

	private static Optional<UInt256> downTransferrableParticles(
		UInt256 amount,
		List<TransferrableTokensParticle> particles,
		Consumer<TransferrableTokensParticle> onDown
	) {
		UInt256 spent = UInt256.ZERO;
		while (spent.compareTo(amount) < 0 && !particles.isEmpty()) {
			var particle = particles.remove(particles.size() - 1);
			onDown.accept(particle);
			spent = spent.add(particle.getAmount());
		}

		if (spent.compareTo(amount) < 0) {
			return Optional.empty();
		}

		return Optional.of(spent.subtract(amount));
	}


	private static Optional<UInt256> downParticles(
		UInt256 amount,
		List<UnallocatedTokensParticle> particles,
		Consumer<UnallocatedTokensParticle> onDown
	) {
		UInt256 spent = UInt256.ZERO;
		while (spent.compareTo(amount) < 0 && !particles.isEmpty()) {
			var particle = particles.remove(particles.size() - 1);
			onDown.accept(particle);
			spent = spent.add(particle.getAmount());
		}

		if (spent.compareTo(amount) < 0) {
			return Optional.empty();
		}

		return Optional.of(spent.subtract(amount));
	}

	public static void createTokenDefinition(
		AtomBuilder atomBuilder,
		byte magic,
		ECPublicKey key,
		TokenDefinition tokenDefinition,
		ImmutableList<TokenIssuance> issuances
	) {
		final var universeAddress = new RadixAddress(magic, key);

		final var tokenRRI = RRI.of(universeAddress, tokenDefinition.getSymbol());
		final var factory = TokDefParticleFactory.create(
			tokenRRI, tokenDefinition.getTokenPermissions(), UInt256.ONE
		);

		final var unallocated = factory.createUnallocated(UInt256.MAX_VALUE);
		atomBuilder
			.virtualSpinDown(new RRIParticle(tokenRRI))
			.spinUp(new MutableSupplyTokenDefinitionParticle(
				tokenRRI,
				tokenDefinition.getName(),
				tokenDefinition.getDescription(),
				tokenDefinition.getGranularity(),
				tokenDefinition.getIconUrl(),
				tokenDefinition.getTokenUrl(),
				tokenDefinition.getTokenPermissions()
			))
			.spinUp(unallocated);
		atomBuilder.particleGroup();

		// Merge issuances so we only have one TTP per address
		final var issuedAmounts = issuances.stream()
			.collect(ImmutableMap.toImmutableMap(TokenIssuance::receiver, TokenIssuance::amount, UInt256::add));
		var unallocatedParticles = Lists.newArrayList(unallocated);
		for (final var issuance : issuedAmounts.entrySet()) {
			final var amount = issuance.getValue();
			if (!amount.isZero()) {
				atomBuilder.spinUp(factory.createTransferrable(new RadixAddress(magic, issuance.getKey()), amount, 0));
				UInt256 remainder = downParticles(amount, unallocatedParticles, atomBuilder::spinDown).orElseThrow();
				if (!remainder.isZero()) {
					UnallocatedTokensParticle particle = factory.createUnallocated(remainder);
					unallocatedParticles.add(particle);
					atomBuilder.spinUp(particle);
				}
			}
		}
		atomBuilder.particleGroup();
	}

	public static void createValidators(
		AtomBuilder atomBuilder,
		byte magic,
		ImmutableList<ECKeyPair> validatorKeys
	) {
		validatorKeys.forEach(key -> {
			RadixAddress validatorAddress = new RadixAddress(magic, key.getPublicKey());
			UnregisteredValidatorParticle validatorDown = new UnregisteredValidatorParticle(validatorAddress, 0L);
			RegisteredValidatorParticle validatorUp = new RegisteredValidatorParticle(validatorAddress, ImmutableSet.of(), 1L);
			atomBuilder.virtualSpinDown(validatorDown);
			atomBuilder.spinUp(validatorUp);
		});
		atomBuilder.particleGroup();
	}

	public static void createStakes(
		AtomBuilder atomBuilder,
		byte magic,
		ImmutableList<StakeDelegation> delegations
	) {
		final ImmutableMap<ECPublicKey, TransferrableTokensParticle> tokensByKey = atomBuilder.localUpParticles()
			.filter(TransferrableTokensParticle.class::isInstance)
			.map(TransferrableTokensParticle.class::cast)
			.collect(ImmutableMap.toImmutableMap(ttp -> ttp.getAddress().getPublicKey(), Function.identity()));

		final var stakesByKey = delegations.stream()
			.collect(Collectors.groupingBy(sd -> sd.staker().getPublicKey(), ImmutableList.toImmutableList()));

		Map<ECPublicKey, Long> delegateNonces = new HashMap<>();
		delegations.stream().map(StakeDelegation::delegate).distinct()
			.forEach(delegate -> delegateNonces.put(delegate, 1L));

		TokDefParticleFactory factory = TokDefParticleFactory.createFrom(atomBuilder.localUpParticles()
			.filter(TransferrableTokensParticle.class::isInstance)
			.map(TransferrableTokensParticle.class::cast)
			.findAny().orElseThrow()
		);

		for (final var entry : stakesByKey.entrySet()) {
			final var particles = Lists.newArrayList(tokensByKey.get(entry.getKey()));
			final var delegationsOfKey = entry.getValue();

			final var stakerAddress = new RadixAddress(magic, entry.getKey());

			for (final var delegation : delegationsOfKey) {
				ECPublicKey delegate = delegation.delegate();
				long nonce = delegateNonces.get(delegate);
				RadixAddress delegateAddress = new RadixAddress(magic, delegate);
				atomBuilder.spinDown(new RegisteredValidatorParticle(delegateAddress, ImmutableSet.of(), nonce));
				atomBuilder.spinUp(new RegisteredValidatorParticle(delegateAddress, ImmutableSet.of(), nonce + 1));
				delegateNonces.put(delegate, nonce + 1);

				final var amount = delegation.amount();
				atomBuilder.spinUp(factory.createStaked(delegateAddress, stakerAddress, amount, 0));

				UInt256 remainder = downTransferrableParticles(amount, particles, atomBuilder::spinDown)
					.orElseThrow();
				if (!remainder.isZero()) {
					var particle = factory.createTransferrable(stakerAddress, remainder, System.nanoTime());
					particles.add(particle);
					atomBuilder.spinUp(particle);
				}
			}
			atomBuilder.particleGroup();
		}
	}

}
