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

package com.radixdlt.statecomputer.checkpoint;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.utils.UInt256;
import org.radix.StakeDelegation;
import org.radix.TokenIssuance;

import java.util.stream.Stream;

/**
 * Generates a genesis atom
 */
public final class GenesisAtomProvider implements Provider<Atom> {
	private final byte magic;
	private final ECKeyPair universeKey;
	private final UInt256 selfIssuance;
	private final ImmutableList<TokenIssuance> tokenIssuances;
	private final ImmutableList<ECKeyPair> validatorKeys;
	private final ImmutableList<StakeDelegation> stakeDelegations;
	private final Hasher hasher;
	private final TokenDefinition tokenDefinition;

    @Inject
	public GenesisAtomProvider(
		@Named("magic") int magic,
		@Named("universeKey") ECKeyPair universeKey, // TODO: Remove
		@NativeToken TokenDefinition tokenDefinition,
		@Genesis UInt256 selfIssuance,
		@Genesis ImmutableList<TokenIssuance> tokenIssuances,
		@Genesis ImmutableList<StakeDelegation> stakeDelegations,
		@Genesis ImmutableList<ECKeyPair> validatorKeys, // TODO: Remove private keys, replace with public keys
		Hasher hasher
	) {
    	this.magic = (byte) magic;
    	this.universeKey = universeKey;
		this.tokenDefinition = tokenDefinition;
    	this.selfIssuance = selfIssuance;
    	this.tokenIssuances = tokenIssuances;
    	this.validatorKeys = validatorKeys;
    	this.stakeDelegations = stakeDelegations;
    	this.hasher = hasher;
	}

	@Override
	public Atom get() {
		// Check that issuances are sufficient for delegations
		final var issuances = tokenIssuances.stream()
			.collect(ImmutableMap.toImmutableMap(TokenIssuance::receiver, TokenIssuance::amount, UInt256::add));
		final var requiredDelegations = stakeDelegations.stream()
			.collect(ImmutableMap.toImmutableMap(sd -> sd.staker().getPublicKey(), StakeDelegation::amount, UInt256::add));
		requiredDelegations.forEach((pk, amount) -> {
			final var issuedAmount = issuances.getOrDefault(pk, UInt256.ZERO);
			if (amount.compareTo(issuedAmount) > 0) {
				throw new IllegalStateException(
					String.format(
						"%s wants to stake %s, but was only issued %s",
						new RadixAddress(magic, pk), amount, issuedAmount
					)
				);
			}
		});
		final var epochParticles = CheckpointUtils.createEpochUpdate();
		final var xrdParticles = CheckpointUtils.createTokenDefinition(
			magic,
			universeKey.getPublicKey(),
			tokenDefinition,
			selfIssuance,
			tokenIssuances
		);

		final var validatorParticles = CheckpointUtils.createValidators(
			magic,
			validatorKeys
		);
		final var stakingParticles = CheckpointUtils.createStakes(
			magic,
			stakeDelegations,
			xrdParticles
		);

		final var genesisAtom = new Atom(helloMessage());
		genesisAtom.addParticleGroup(ParticleGroup.of(epochParticles));
		genesisAtom.addParticleGroup(ParticleGroup.of(xrdParticles));
		if (!validatorParticles.isEmpty()) {
			genesisAtom.addParticleGroup(ParticleGroup.of(validatorParticles));
		}
		if (!stakingParticles.isEmpty()) {
			genesisAtom.addParticleGroup(ParticleGroup.of(stakingParticles));
		}

		final var signingKeys = Streams.concat(
			Stream.of(this.universeKey),
			validatorKeys.stream(),
			stakeDelegations.stream().map(StakeDelegation::staker)
		).collect(ImmutableList.toImmutableList());

		genesisAtom.sign(signingKeys, this.hasher);
		signingKeys.forEach(key -> verifySignature(key, genesisAtom));

		return genesisAtom;
	}

	/*
	 * Create the 'hello' message particle at the given universes
	 */
	private static String helloMessage() {
		return "Radix... just imagine!";
	}

	private void verifySignature(ECKeyPair key, Atom genesisAtom) {
		try {
			if (!genesisAtom.verify(key.getPublicKey(), this.hasher)) {
				// A bug somewhere that needs fixing
				throw new IllegalStateException(
					String.format(
						"Signature verification failed - GENESIS TRANSACTION HASH: %s",
						this.hasher.hash(genesisAtom)
					)
				);
			}
		} catch (PublicKeyException ex) {
			throw new IllegalStateException("Error while verifying universe", ex);
		}
	}
}
