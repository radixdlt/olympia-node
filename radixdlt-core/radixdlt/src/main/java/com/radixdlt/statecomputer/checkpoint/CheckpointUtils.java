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
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TokDefParticleFactory;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import org.radix.StakeDelegation;
import org.radix.TokenIssuance;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utilities for creating particles for genesis + checkpoints.
 */
public final class CheckpointUtils {
	private CheckpointUtils() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static ImmutableList<SpunParticle> createEpochUpdate() {
		ImmutableList.Builder<SpunParticle> particles = ImmutableList.builder();
		particles.add(SpunParticle.down(new SystemParticle(0, 0, 0)));
		particles.add(SpunParticle.up(new SystemParticle(1, 0, 0)));
		return particles.build();
	}

	public static ImmutableList<SpunParticle> createTokenDefinition(
		byte magic,
		ECPublicKey key,
		TokenDefinition tokenDefinition,
		UInt256 selfIssuance,
		ImmutableList<TokenIssuance> issuances
	) {
		final var universeAddress = new RadixAddress(magic, key);

		final var tokenRRI = RRI.of(universeAddress, tokenDefinition.getSymbol());

		final var particles = ImmutableList.<SpunParticle>builder();

		particles.add(SpunParticle.down(new RRIParticle(tokenRRI)));
		particles.add(SpunParticle.up(new MutableSupplyTokenDefinitionParticle(
			tokenRRI,
			tokenDefinition.getName(),
			tokenDefinition.getDescription(),
			tokenDefinition.getGranularity(),
			tokenDefinition.getIconUrl(),
			tokenDefinition.getTokenUrl(),
			tokenDefinition.getTokenPermissions()
		)));
		TokDefParticleFactory tokDefParticleFactory = TokDefParticleFactory.create(
			tokenRRI, tokenDefinition.getTokenPermissions(), UInt256.ONE
		);

		var issuedTokens = UInt384.from(selfIssuance);
		if (!selfIssuance.isZero()) {
			particles.add(SpunParticle.up(tokDefParticleFactory.createTransferrable(universeAddress, selfIssuance, 0)));
		}
		// Merge issuances so we only have one TTP per address
		final var issuedAmounts = issuances.stream()
				.collect(ImmutableMap.toImmutableMap(TokenIssuance::receiver, TokenIssuance::amount, UInt256::add));
		for (final var issuance : issuedAmounts.entrySet()) {
			final var amount = issuance.getValue();
			if (!amount.isZero()) {
				particles.add(SpunParticle.up(
					tokDefParticleFactory.createTransferrable(new RadixAddress(magic, issuance.getKey()), amount, 0)
				));
				issuedTokens = issuedTokens.add(amount);
			}
		}
		if (!issuedTokens.getHigh().isZero()) {
			// TokenOverflowException
			throw new IllegalStateException("Too many issued tokens: " + issuedTokens);
		}
		if (!issuedTokens.getLow().equals(UInt256.MAX_VALUE)) {
			particles.add(SpunParticle.up(tokDefParticleFactory.createUnallocated(UInt256.MAX_VALUE.subtract(issuedTokens.getLow()))));
		}
		return particles.build();
	}


	public static List<SpunParticle> createValidators(byte magic, ImmutableList<ECKeyPair> validatorKeys) {
		final List<SpunParticle> validatorParticles = Lists.newArrayList();
		validatorKeys.forEach(key -> {
			RadixAddress validatorAddress = new RadixAddress(magic, key.getPublicKey());
			UnregisteredValidatorParticle validatorDown = new UnregisteredValidatorParticle(validatorAddress, 0L);
			RegisteredValidatorParticle validatorUp = new RegisteredValidatorParticle(validatorAddress, ImmutableSet.of(), 1L);
			validatorParticles.add(SpunParticle.down(validatorDown));
			validatorParticles.add(SpunParticle.up(validatorUp));
		});
		return validatorParticles;
	}

	public static List<SpunParticle> createStakes(
		byte magic,
		ImmutableList<StakeDelegation> delegations,
		List<SpunParticle> xrdParticles
	) {
		final ImmutableMap<ECPublicKey, TransferrableTokensParticle> tokensByKey = xrdParticles.stream()
			.filter(SpunParticle::isUp)
			.map(SpunParticle::getParticle)
			.filter(TransferrableTokensParticle.class::isInstance)
			.map(TransferrableTokensParticle.class::cast)
			.collect(ImmutableMap.toImmutableMap(ttp -> ttp.getAddress().getPublicKey(), Function.identity()));

		final var stakesByKey = delegations.stream()
			.collect(Collectors.groupingBy(sd -> sd.staker().getPublicKey(), ImmutableList.toImmutableList()));

		final List<SpunParticle> stakeParticles = Lists.newArrayList();
		for (final var entry : stakesByKey.entrySet()) {
			final var downParticle = tokensByKey.get(entry.getKey());
			if (downParticle == null) {
				// Has been checked previously - logic error introduced somewhere
				throw new IllegalStateException("Unexpected missing token particle");
			}
			var delegatedAmount = UInt256.ZERO;
			stakeParticles.add(SpunParticle.down(downParticle));
			for (final var delegation : entry.getValue()) {
				final var amount = delegation.amount();
				final var stp = new StakedTokensParticle(
					new RadixAddress(magic, delegation.delegate()),
					new RadixAddress(magic, delegation.staker().getPublicKey()),
					amount,
					downParticle.getGranularity(),
					downParticle.getTokDefRef(),
					downParticle.getTokenPermissions()
				);
				stakeParticles.add(SpunParticle.up(stp));
				delegatedAmount = delegatedAmount.add(amount);
			}
			if (downParticle.getAmount().compareTo(delegatedAmount) < 0) {
				// Has been previously checked to ensure no underflow - logic error
				throw new IllegalStateException("Trying to delegate more than issued");
			}
			final var balance = downParticle.getAmount().subtract(delegatedAmount);
			final var outputTtp = new TransferrableTokensParticle(
				downParticle.getAddress(),
				balance,
				downParticle.getGranularity(),
				downParticle.getTokDefRef(),
				downParticle.getTokenPermissions()
			);
			stakeParticles.add(SpunParticle.up(outputTtp));
		}
		return stakeParticles;
	}

}
