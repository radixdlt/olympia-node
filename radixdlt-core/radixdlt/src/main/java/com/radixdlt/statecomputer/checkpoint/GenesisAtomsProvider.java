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
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.atom.Atom;
import com.radixdlt.utils.UInt256;
import org.radix.StakeDelegation;
import org.radix.TokenIssuance;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a genesis atom
 */
public final class GenesisAtomsProvider implements Provider<List<Atom>> {
	private final byte magic;
	private final ECKeyPair universeKey;
	private final ImmutableList<TokenIssuance> tokenIssuances;
	private final ImmutableList<ECKeyPair> validatorKeys;
	private final ImmutableList<StakeDelegation> stakeDelegations;
	private final TokenDefinition tokenDefinition;

	@Inject
	public GenesisAtomsProvider(
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
	public List<Atom> get() {
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

		var genesisAtoms = new ArrayList<Atom>();

		final var tokenBuilder = Atom.newBuilder();
		CheckpointUtils.createTokenDefinition(
			tokenBuilder,
			magic,
			universeKey.getPublicKey(),
			tokenDefinition,
			tokenIssuances
		);
		var tokenHashToSign = tokenBuilder.computeHashToSign();
		tokenBuilder.setSignature(universeKey.euid(), universeKey.sign(tokenHashToSign));
		genesisAtoms.add(tokenBuilder.buildAtom());

		var upParticles = new ArrayList<Particle>();
		tokenBuilder.allUpParticles().forEach(upParticles::add);

		for (var validatorKey : validatorKeys) {
			var validatorBuilder = Atom.newBuilder();
			CheckpointUtils.createValidator(
				validatorBuilder,
				magic,
				validatorKey
			);

			var hashToSign = validatorBuilder.computeHashToSign();
			validatorBuilder.setSignature(validatorKey.euid(), validatorKey.sign(hashToSign));
			genesisAtoms.add(validatorBuilder.buildAtom());

			validatorBuilder.allUpParticles().forEach(upParticles::add);
		}

		for (var stakeDelegation : stakeDelegations) {
			var stakesBuilder = Atom.newBuilder(upParticles);
			CheckpointUtils.createStake(
				stakesBuilder,
				magic,
				stakeDelegation
			);
			var hashToSign = stakesBuilder.computeHashToSign();
			stakesBuilder.setSignature(stakeDelegation.staker().euid(), stakeDelegation.staker().sign(hashToSign));
			genesisAtoms.add(stakesBuilder.buildAtom());
			upParticles.clear();
			stakesBuilder.allUpParticles().forEach(upParticles::add);
		}

		var epochUpdateBuilder = Atom.newBuilder();
		CheckpointUtils.createEpochUpdate(epochUpdateBuilder);
		genesisAtoms.add(epochUpdateBuilder.buildAtom());

		return genesisAtoms;
	}
}
