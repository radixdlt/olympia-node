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
import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.radixdlt.atom.Atom;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.atom.ClientAtom;
import com.radixdlt.utils.UInt256;
import org.radix.StakeDelegation;
import org.radix.TokenIssuance;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates a genesis atom
 */
public final class GenesisAtomProvider implements Provider<ClientAtom> {
	private final byte magic;
	private final ECKeyPair universeKey;
	private final ImmutableList<TokenIssuance> tokenIssuances;
	private final ImmutableList<ECKeyPair> validatorKeys;
	private final ImmutableList<StakeDelegation> stakeDelegations;
	private final TokenDefinition tokenDefinition;

	@Inject
	public GenesisAtomProvider(
		@Named("magic") int magic,
		@Named("universeKey") ECKeyPair universeKey, // TODO: Remove
		@NativeToken TokenDefinition tokenDefinition,
		@Genesis ImmutableList<TokenIssuance> tokenIssuances,
		@Genesis ImmutableList<StakeDelegation> stakeDelegations,
		@Genesis ImmutableList<ECKeyPair> validatorKeys // TODO: Remove private keys, replace with public keys
	) {
		this.magic = (byte) magic;
		this.universeKey = universeKey;
		this.tokenDefinition = tokenDefinition;
		this.tokenIssuances = tokenIssuances;
		this.validatorKeys = validatorKeys;
		this.stakeDelegations = stakeDelegations;
	}

	@Override
	public ClientAtom get() {
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
		final var xrdParticleGroups = CheckpointUtils.createTokenDefinition(
			magic,
			universeKey.getPublicKey(),
			tokenDefinition,
			tokenIssuances
		);

		final var validatorParticles = CheckpointUtils.createValidators(
			magic,
			validatorKeys
		);
		final var stakingParticleGroups = CheckpointUtils.createStakes(
			magic,
			stakeDelegations,
			xrdParticleGroups.stream().flatMap(ParticleGroup::spunParticles).collect(Collectors.toList())
		);

		final var genesisAtom = new Atom(helloMessage());
		xrdParticleGroups.forEach(genesisAtom::addParticleGroup);
		if (!validatorParticles.isEmpty()) {
			genesisAtom.addParticleGroup(ParticleGroup.of(validatorParticles));
		}
		if (!stakingParticleGroups.isEmpty()) {
			stakingParticleGroups.forEach(genesisAtom::addParticleGroup);
		}
		genesisAtom.addParticleGroup(ParticleGroup.of(epochParticles));

		final var signingKeys = Streams.concat(
			Stream.of(this.universeKey),
			validatorKeys.stream(),
			stakeDelegations.stream().map(StakeDelegation::staker)
		).collect(ImmutableList.toImmutableList());

		signingKeys.forEach(keyPair -> {
			HashCode hashToSign = genesisAtom.computeHashToSign();
			genesisAtom.setSignature(keyPair.euid(), keyPair.sign(hashToSign));
		});
		signingKeys.forEach(key -> verifySignature(key, genesisAtom));

		return genesisAtom.buildAtom();
	}

	/*
	 * Create the 'hello' message particle at the given universes
	 */
	private static String helloMessage() {
		return "Radix... just imagine!";
	}

	private void verifySignature(ECKeyPair key, Atom genesisAtom) {
		ClientAtom atom = genesisAtom.buildAtom();
		ECDSASignature signature = atom.getSignature(key.euid()).orElseThrow();
		key.getPublicKey().verify(atom.getWitness(), signature);
	}
}
